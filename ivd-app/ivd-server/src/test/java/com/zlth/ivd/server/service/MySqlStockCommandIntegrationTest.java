package com.zlth.ivd.server.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.dao.DataAccessException;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="IVD_STOCK_MYSQL_TEST",matches="true")
class MySqlStockCommandIntegrationTest extends StockCommandIntegrationTest {
    private final MySqlStockFixture fixture=new MySqlStockFixture();
    @Override protected AnnotationConfigApplicationContext fixtureContext(){return fixture.open(true);}
    @Override protected boolean migrationApplied(){return true;}
    @Override @AfterEach void close(){try{super.close();}finally{fixture.close();}}

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void dailyBalanceRecognizesCrossDayTransferAtCorrectPostingTime(boolean deferred) {
        db.execute("CREATE TABLE daily_inventory_snapshot(institution_id BIGINT,reagent_id BIGINT,warehouse_id BIGINT,snapshot_date DATE,closing DECIMAL(12,2))");
        var shipped = java.time.LocalDate.of(2026, 10, 30);
        var signed = shipped.plusDays(1);
        db.update("INSERT INTO reagent_batch(id,institution_id,reagent_id,batch_no,warehouse_id,stock,unit_price,udi_status) VALUES(5011,1,1,'QA-CROSS-DAY',101,7,10,'IN_STOCK'),(5012,1,1,'QA-CROSS-DAY',103,3,10,'IN_STOCK')");
        db.update("""
                INSERT INTO inventory_opening_balance(institution_id,batch_id,reagent_id,batch_no,warehouse_id,
                    quantity,unit_price,amount,effective_at,evidence_source,evidence_reference)
                VALUES(1,5011,1,'QA-CROSS-DAY',101,10,10,100,?,'QA_AUDITED_IMPORT','QA original opening')
                """, shipped.minusDays(1).atStartOfDay());
        db.update("INSERT INTO consumption_record(id,institution_id,record_type,reagent_id,batch_no,warehouse_id,from_warehouse_id,requisition_id,amount,occurred_at,stock_deduct_on_receipt) VALUES(9301,1,'TRANSFER',1,'QA-CROSS-DAY',103,101,9300,30,?,?)", shipped.atTime(23, 59), deferred);
        db.update("INSERT INTO requisition_transfer_receipt(transfer_record_id,institution_id,target_batch_id,received_at) VALUES(9301,1,5012,?)", signed.atTime(0, 1));
        var mapper = context.getBean(com.zlth.ivd.finance.mapper.DailyInventorySnapshotMapper.class);
        var scope = new com.zlth.ivd.common.security.InstitutionContext.Scope(1, null, 1003L, "FINANCE");
        com.zlth.ivd.common.security.InstitutionContext.within(scope, () -> {
            var shipmentDay = mapper.getDailyBalances(shipped);
            var receiptDay = mapper.getDailyBalances(signed);
            assertBalance(shipmentDay, 101, 100, 0, deferred ? 0 : 30, deferred ? 100 : 70);
            assertBalance(shipmentDay, 103, 0, 0, 0, 0);
            assertBalance(receiptDay, 101, deferred ? 100 : 70, 0, deferred ? 30 : 0, 70);
            assertBalance(receiptDay, 103, 0, 30, 0, 30);
            return null;
        });
    }

    private void assertBalance(java.util.List<java.util.Map<String,Object>> rows, long warehouse,
                               int opening, int inbound, int outbound, int closing) {
        var row = rows.stream().filter(value -> ((Number)value.get("warehouseId")).longValue() == warehouse
                && ((Number)value.get("reagentId")).longValue() == 1).findFirst().orElseThrow();
        java.util.Map.of("opening", opening, "inbound", inbound, "outbound", outbound, "closing", closing)
                .forEach((key, expected) -> assertEquals(0, java.math.BigDecimal.valueOf(expected)
                        .compareTo(new java.math.BigDecimal(row.get(key).toString())), key + " warehouse=" + warehouse));
    }

    @Test void dailyCloseSecondInsertFailureRollsBackAndCanRetry() {
        db.execute("""
                CREATE TABLE daily_inventory_snapshot(
                  id BIGINT PRIMARY KEY,institution_id BIGINT,snapshot_date DATE,reagent_id BIGINT,warehouse_id BIGINT,
                  opening DECIMAL(12,2),inbound DECIMAL(12,2),outbound DECIMAL(12,2),adjustment DECIMAL(12,2),
                  closing DECIMAL(12,2),difference DECIMAL(12,2),difference_ratio DECIMAL(12,4),is_balanced INT,
                  created_at DATETIME(3),UNIQUE KEY uk_qa_snapshot(institution_id,snapshot_date,reagent_id,warehouse_id))
                """);
        var day = java.time.LocalDate.now().minusDays(1);
        db.update("INSERT INTO reagent_batch(id,institution_id,reagent_id,batch_no,warehouse_id,stock,unit_price,udi_status) VALUES(5010,1,2,'QA-DAY-CLOSE',101,2,20,'IN_STOCK')");
        OpeningEvidenceFixture.audit(db, 5010, day.atStartOfDay());
        db.execute("""
                CREATE TRIGGER qa_fail_second_snapshot BEFORE INSERT ON daily_inventory_snapshot FOR EACH ROW
                BEGIN
                  SET @qa_snapshot_attempts = COALESCE(@qa_snapshot_attempts,0) + 1;
                  IF @qa_snapshot_attempts = 2 THEN
                    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='QA_SECOND_SNAPSHOT_INSERT';
                  END IF;
                END
                """);
        var service = context.getBean(com.zlth.ivd.finance.service.FinanceService.class);
        var scope = new com.zlth.ivd.common.security.InstitutionContext.Scope(1, null, 1003L, "FINANCE");
        var error = com.zlth.ivd.common.security.InstitutionContext.within(scope,
                () -> assertThrows(DataAccessException.class, () -> service.dailySettlement(day)));
        assertTrue(error.getMostSpecificCause().getMessage().contains("QA_SECOND_SNAPSHOT_INSERT"), error::getMessage);
        assertEquals(0, count("daily_inventory_snapshot"));
        db.execute("DROP TRIGGER qa_fail_second_snapshot");
        com.zlth.ivd.common.security.InstitutionContext.within(scope, () -> {
            service.dailySettlement(day);
            return null;
        });
        assertEquals(2, count("daily_inventory_snapshot"));
        assertEquals(new java.math.BigDecimal("140.00"), db.queryForObject(
                "SELECT SUM(closing) FROM daily_inventory_snapshot WHERE institution_id=1 AND snapshot_date=?",
                java.math.BigDecimal.class, day));
    }

    @Test void monthlyClosePersistsOneSummaryOnlyAfterAllDaysAreCovered() {
        db.execute("CREATE TABLE daily_inventory_snapshot(id BIGINT PRIMARY KEY,institution_id BIGINT,reagent_id BIGINT,warehouse_id BIGINT,snapshot_date DATE,opening DECIMAL(12,2),inbound DECIMAL(12,2),outbound DECIMAL(12,2),adjustment DECIMAL(12,2),closing DECIMAL(12,2),is_balanced INT)");
        var service = context.getBean(com.zlth.ivd.finance.service.FinanceService.class);
        var month = java.time.YearMonth.now().minusMonths(1);
        var scope = new com.zlth.ivd.common.security.InstitutionContext.Scope(1, null, 1003L, "FINANCE");
        com.zlth.ivd.common.security.InstitutionContext.within(scope, () -> {
            assertEquals(40001, assertThrows(com.zlth.ivd.common.exception.BizException.class,
                    () -> service.monthlySettlement(java.time.YearMonth.now().toString())).getErrorCode());
            assertEquals(40001, assertThrows(com.zlth.ivd.common.exception.BizException.class,
                    () -> service.monthlySettlement(month.toString())).getErrorCode());
            return null;
        });
        for (int day=1; day<=month.lengthOfMonth(); day++)
            db.update("INSERT INTO daily_inventory_snapshot VALUES(?,1,1,101,?,100,2,2,0,100,1)", day, month.atDay(day));
        com.zlth.ivd.common.security.InstitutionContext.within(scope, () -> {
            service.monthlySettlement(month.toString());
            assertEquals(40900, assertThrows(com.zlth.ivd.common.exception.BizException.class,
                    () -> service.monthlySettlement(month.toString())).getErrorCode());
            return null;
        });
        assertEquals(1, count("monthly_inventory_close"));
        var stored = db.queryForMap("SELECT * FROM monthly_inventory_close");
        assertEquals(month.toString(), stored.get("period_month"));
        assertEquals(1003L, stored.get("closed_by"));
        assertEquals(new java.math.BigDecimal("100.00"), stored.get("opening"));
        assertEquals(new java.math.BigDecimal("100.00"), stored.get("closing"));
        assertEquals(new java.math.BigDecimal(month.lengthOfMonth() * 2).setScale(2), stored.get("inbound"));
    }

    @Test void mysqlMigrationProtectsCompletedReceiptFromRewriteAndDelete(){
        call(TECH,consume(5001,"2"),"qa-immutable-key",200);
        assertThrows(DataAccessException.class,()->db.update("UPDATE stock_command_receipt SET response_json='tamper'"));
        assertThrows(DataAccessException.class,()->db.update("DELETE FROM stock_command_receipt"));
        call(TECH,consume(5001,"2"),"qa-immutable-key",200);
        assertEquals(1,count("stock_command_receipt"));assertEquals(1,count("settlement_record"));
    }
}
