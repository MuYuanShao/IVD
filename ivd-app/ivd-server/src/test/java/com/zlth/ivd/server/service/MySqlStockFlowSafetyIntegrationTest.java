package com.zlth.ivd.server.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** 显式启用的真实MySQL回归：每例只创建并清理自己新建的随机临时库，不连接正式业务库。 */
@EnabledIfEnvironmentVariable(named="IVD_STOCK_MYSQL_TEST",matches="true")
class MySqlStockFlowSafetyIntegrationTest extends StockFlowSafetyIntegrationTest {
    private final MySqlStockFixture fixture=new MySqlStockFixture();

    @Test void periodBalanceUsesFirstAndLastSnapshotPerReagentWarehouse() {
        db.execute("CREATE TABLE daily_inventory_snapshot(id BIGINT PRIMARY KEY,institution_id BIGINT,reagent_id BIGINT,warehouse_id BIGINT,snapshot_date DATE,opening DECIMAL(12,2),inbound DECIMAL(12,2),outbound DECIMAL(12,2),adjustment DECIMAL(12,2),closing DECIMAL(12,2),is_balanced INT)");
        db.update("""
                INSERT INTO daily_inventory_snapshot VALUES
                (1,1,1,101,'2026-11-01',100,20,10,0,110,1),
                (2,1,1,101,'2026-11-02',110,5,0,0,115,1),
                (3,1,1,102,'2026-11-02',40,0,12,2,30,1),
                (4,2,3,202,'2026-11-02',900,0,0,0,900,1),
                (5,1,1,101,'2026-11-03',115,500,0,0,615,1)
                """);
        var mapper = context.getBean(com.zlth.ivd.finance.mapper.DailyInventorySnapshotMapper.class);
        var result = as(keeper, () -> mapper.getBalanceAggregation(
                java.time.LocalDate.of(2026,11,1), java.time.LocalDate.of(2026,11,2)));
        var expected = java.util.Map.of("totalOpening", "140", "totalClosing", "145",
                "totalInbound", "25", "totalOutbound", "22", "totalAdjustment", "2");
        expected.forEach((key, amount) -> org.junit.jupiter.api.Assertions.assertEquals(0,
                new java.math.BigDecimal(amount).compareTo(new java.math.BigDecimal(result.get(key).toString())), key));
    }

    @Test void workbenchIncludesGroupWarehouseThroughItsParentDepartment() {
        db.update("""
                INSERT INTO warehouse(id,institution_id,name,level,parent_id,dept_id,status,is_active)
                VALUES (104,1,'QA group',3,102,NULL,1,TRUE),
                       (105,1,'QA other group',3,103,NULL,1,TRUE),
                       (106,1,'QA invalid foreign parent',3,202,NULL,1,TRUE)
                """);
        db.update("""
                INSERT INTO reagent_batch(id,institution_id,reagent_id,batch_no,warehouse_id,stock,udi_status,expiry_date)
                VALUES (8111,1,1,'QA-GROUP-OWN',104,2,'IN_STOCK','2026-11-08'),
                       (8112,1,1,'QA-GROUP-OTHER',105,3,'IN_STOCK','2026-11-08'),
                       (8113,1,1,'QA-GROUP-FOREIGN-PARENT',106,3,'IN_STOCK','2026-11-08')
                """);
        db.update("""
                INSERT INTO consumption_record(id,institution_id,record_type,reagent_id,warehouse_id,from_warehouse_id,amount,occurred_at)
                VALUES (8121,1,'CONSUME',1,104,NULL,20,'2026-11-04 09:00:00'),
                       (8122,1,'CONSUME',1,105,NULL,90,'2026-11-04 09:00:00'),
                       (8123,1,'TRANSFER',1,105,104,30,'2026-11-04 09:00:00'),
                       (8124,1,'TRANSFER',1,104,105,80,'2026-11-04 09:00:00')
                """);
        var mapper = context.getBean(com.zlth.ivd.analytics.mapper.WorkbenchMapper.class);
        var today = java.time.LocalDate.of(2026,11,4);
        var start = today.atStartOfDay();
        var end = today.plusDays(1).atStartOfDay();
        as(keeper, () -> {
            org.junit.jupiter.api.Assertions.assertEquals(1, mapper.productCount(11L, "QA-GROUP"));
            org.junit.jupiter.api.Assertions.assertEquals("QA-GROUP-OWN", mapper.products(11L, "QA-GROUP", 0, 10).get(0).getBatch());
            org.junit.jupiter.api.Assertions.assertEquals(1, mapper.alerts(11L, today, today.plusDays(7)).getExpiringBatches());
            org.junit.jupiter.api.Assertions.assertEquals(0, mapper.productCount(21L, "QA-GROUP"));
            var metrics = mapper.metrics(11L, start, end);
            org.junit.jupiter.api.Assertions.assertEquals(1, metrics.getConsumedRecords());
            org.junit.jupiter.api.Assertions.assertEquals(0, new java.math.BigDecimal("20").compareTo(metrics.getConsumptionAmount()));
            org.junit.jupiter.api.Assertions.assertEquals(1, metrics.getTransferRecords());
            org.junit.jupiter.api.Assertions.assertEquals(0, new java.math.BigDecimal("30")
                    .compareTo(mapper.trend(11L, start, end).get(0).getOutbound()));
            return null;
        });
    }

    @Test void brandCostCountsConsumptionOnly() {
        db.execute("CREATE TABLE supplier(id BIGINT PRIMARY KEY,institution_id BIGINT,name VARCHAR(100))");
        db.execute("CREATE TABLE reagent_supplier_rel(id BIGINT PRIMARY KEY,institution_id BIGINT,reagent_id BIGINT,supplier_id BIGINT,is_default INT)");
        db.update("INSERT INTO supplier VALUES(1,1,'QA brand'),(2,2,'QA foreign')");
        db.update("INSERT INTO reagent_supplier_rel VALUES(1,1,1,1,1),(2,2,3,2,1)");
        db.update("""
                INSERT INTO consumption_record(id,institution_id,record_type,reagent_id,warehouse_id,amount,unit_price,occurred_at)
                VALUES (8101,1,'INBOUND',1,101,100,100,'2026-10-31 12:00:00'),
                       (8102,1,'TRANSFER',1,102,100,100,'2026-10-31 13:00:00'),
                       (8103,1,'CONSUME',1,102,100,100,'2026-10-31 23:59:59'),
                       (8104,1,'CONSUME',1,102,700,700,'2026-11-01 00:00:00'),
                       (8105,2,'CONSUME',3,202,800,800,'2026-10-31 12:00:00')
                """);
        var mapper = context.getBean(com.zlth.ivd.analytics.mapper.EfficiencyMapper.class);
        var brands = as(keeper, () -> mapper.getBrandComparison("2026-10-31", "2026-10-31"));
        org.junit.jupiter.api.Assertions.assertEquals(1, brands.size());
        var brand = brands.get(0);
        org.junit.jupiter.api.Assertions.assertEquals("QA brand", brand.get("brand"));
        org.junit.jupiter.api.Assertions.assertEquals(1L, ((Number) brand.get("testCount")).longValue());
        org.junit.jupiter.api.Assertions.assertEquals(0, new java.math.BigDecimal("100")
                .compareTo(new java.math.BigDecimal(brand.get("totalCost").toString())));

        db.update("UPDATE consumption_record SET purpose='RETEST' WHERE id=8103");
        db.update("""
                INSERT INTO consumption_record(id,institution_id,record_type,reagent_id,warehouse_id,purpose,occurred_at)
                VALUES (8106,1,'CONSUME',1,102,'RETEST','2026-10-31 15:00:00'),
                       (8107,1,'CONSUME',2,102,'RETEST','2026-10-31 16:00:00')
                """);
        var mixed = as(keeper, () -> mapper.getBrandComparison("2026-10-31", "2026-10-31"))
                .stream().filter(row -> "QA brand".equals(row.get("brand"))).findFirst().orElseThrow();
        org.junit.jupiter.api.Assertions.assertNull(mixed.get("avgPrice"));
        org.junit.jupiter.api.Assertions.assertNull(mixed.get("totalCost"));
        org.junit.jupiter.api.Assertions.assertEquals(1L, ((Number) mixed.get("missingPriceCount")).longValue());
        var costs = as(keeper, () -> mapper.getCostPerTest("2026-10-31", "2026-10-31"));
        org.junit.jupiter.api.Assertions.assertEquals(2, costs.size());
        org.junit.jupiter.api.Assertions.assertTrue(costs.stream().allMatch(row -> row.get("totalCost") == null
                && row.get("reagentCost") == null && ((Number) row.get("missingPriceCount")).longValue() == 1));
        var waste = as(keeper, () -> mapper.getWasteTop10("2026-10-31", "2026-10-31"));
        org.junit.jupiter.api.Assertions.assertEquals(2, waste.size());
        org.junit.jupiter.api.Assertions.assertTrue(waste.stream().allMatch(row -> row.get("amount") == null));
    }

    @Override protected AnnotationConfigApplicationContext fixtureContext(){
        return fixture.open(false);
    }

    @Override @AfterEach void close(){
        try {super.close();}
        finally {fixture.close();}
    }
}
