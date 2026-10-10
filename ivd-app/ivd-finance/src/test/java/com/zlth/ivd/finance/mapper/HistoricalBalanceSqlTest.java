package com.zlth.ivd.finance.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Optional real-MySQL regression. All fixture tables are connection-local TEMPORARY tables. */
@EnabledIfEnvironmentVariable(named="IVD_RECON_TEST_URL", matches=".+")
class HistoricalBalanceSqlTest {
    private Connection fixture() throws Exception {
        Connection connection = DriverManager.getConnection(System.getenv("IVD_RECON_TEST_URL"),
                System.getenv("IVD_RECON_TEST_USER"),System.getenv("IVD_RECON_TEST_PASSWORD"));
        try {
            try (Statement s = connection.createStatement()) {
                s.execute("CREATE TEMPORARY TABLE fixture_batches(id BIGINT,reagent_id BIGINT,batch_no VARCHAR(64),warehouse_id BIGINT,stock DECIMAL(10,2),unit_price DECIMAL(12,2),avg_cost DECIMAL(12,2))");
                s.execute("CREATE TEMPORARY TABLE fixture_ledger(id BIGINT,record_type VARCHAR(20),reagent_id BIGINT,batch_no VARCHAR(64),warehouse_id BIGINT,from_warehouse_id BIGINT,requisition_id BIGINT,occurred_at DATETIME(3),quantity DECIMAL(10,2),amount DECIMAL(12,2))");
                s.execute("CREATE TEMPORARY TABLE fixture_receipts(transfer_record_id BIGINT PRIMARY KEY,received_at DATETIME(3))");
                s.execute("CREATE TEMPORARY TABLE fixture_opening(batch_id BIGINT,reagent_id BIGINT,batch_no VARCHAR(64),warehouse_id BIGINT,quantity DECIMAL(10,2),amount DECIMAL(12,2),effective_at DATETIME(3))");
                s.execute("CREATE TEMPORARY TABLE fixture_snapshots(reagent_id BIGINT,warehouse_id BIGINT,snapshot_date DATE,closing DECIMAL(12,2))");
                s.execute("INSERT INTO fixture_batches VALUES (1,1,'LOT',1,119,1200,1200),(2,1,'LOT',5,1,1200,1200),(3,7,'CRP',1,7,3200,3200),(4,7,'CRP',2,15,3200,3200)");
                s.execute("INSERT INTO fixture_opening VALUES (1,1,'LOT',1,120,144000,'2026-08-07 15:36:12.203'),(3,7,'CRP',1,22,70400,'2026-08-07 15:36:12.203')");
                s.execute("INSERT INTO fixture_ledger VALUES (8003,'INBOUND',1,'LOT',1,NULL,NULL,'2026-07-06 16:00:00',30,36000),(10,'TRANSFER',1,'LOT',5,1,NULL,'2026-08-08 22:06:13',1,1200),(11,'TRANSFER',7,'CRP',2,1,2003,'2026-08-08 22:41:04',15,48000)");
                s.execute("INSERT INTO fixture_receipts VALUES (11,'2026-08-08 22:42:11.788')");
            }
            return connection;
        } catch (Exception error) { connection.close(); throw error; }
    }

    private List<Map<String,Object>> balances(Connection connection, String date) throws Exception {
        String sql = String.join(" ",DailyInventorySnapshotMapper.class.getMethod("getDailyBalances",java.time.LocalDate.class)
                .getAnnotation(Select.class).value()).replace("#{date}","'"+date+"'");
        // Expand isolated fixture rows into literal CTE inputs: MySQL cannot reopen TEMPORARY tables.
        String inputs = input(connection,"reagent_batch","fixture_batches") +
                input(connection,"consumption_record","fixture_ledger") +
                input(connection,"requisition_transfer_receipt","fixture_receipts") +
                input(connection,"inventory_opening_balance","fixture_opening") +
                input(connection,"daily_inventory_snapshot","fixture_snapshots");
        sql = sql.replaceFirst("WITH ", "WITH " + inputs);
        List<Map<String,Object>> rows = new ArrayList<>();
        try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery(sql)) {
            while(r.next()) {
                Map<String,Object> row = new LinkedHashMap<>();
                for(int i=1;i<=r.getMetaData().getColumnCount();i++) row.put(r.getMetaData().getColumnLabel(i),r.getObject(i));
                rows.add(row);
            }
        }
        return rows;
    }

    private String input(Connection connection,String alias,String table) throws SQLException {
        try(Statement statement=connection.createStatement(); ResultSet rows=statement.executeQuery("SELECT * FROM "+table)) {
            var metadata=rows.getMetaData(); List<String> columns=new ArrayList<>(), data=new ArrayList<>(), empty=new ArrayList<>();
            for(int i=1;i<=metadata.getColumnCount();i++) {
                columns.add(metadata.getColumnName(i));
                String type=switch(metadata.getColumnType(i)) {
                    case Types.DATE -> "DATE"; case Types.TIMESTAMP -> "DATETIME";
                    case Types.DECIMAL,Types.NUMERIC -> "DECIMAL(12,2)";
                    case Types.BIGINT,Types.INTEGER -> "SIGNED"; default -> "CHAR(64)";
                };
                empty.add("CAST(NULL AS "+type+")");
            }
            while(rows.next()) {
                List<String> values=new ArrayList<>();
                for(int i=1;i<=metadata.getColumnCount();i++) {
                    Object value=rows.getObject(i);
                    values.add(value==null?"NULL":value instanceof Number?value.toString():"'"+value.toString().replace("'","''")+"'");
                }
                data.add("SELECT "+String.join(",",values));
            }
            if(data.isEmpty()) data.add("SELECT "+String.join(",",empty)+" WHERE FALSE");
            return alias+"("+String.join(",",columns)+") AS ("+String.join(" UNION ALL ",data)+"), ";
        }
    }

    @Test void seedOpeningReplacesPreImportDemoFlows() throws Exception {
        try (Connection c = fixture()) {
            var row = balances(c,"2026-08-08").stream().filter(r->((Number)r.get("reagentId")).longValue()==1 && ((Number)r.get("warehouseId")).longValue()==1).findFirst().orElseThrow();
            assertEquals(0,new BigDecimal("144000").compareTo((BigDecimal)row.get("opening")));
            assertEquals(0,new BigDecimal("142800").compareTo((BigDecimal)row.get("closing")));
        }
    }

    @Test void recoveredReceiptBalancesBothWarehousesWithoutIncreasingStock() throws Exception {
        try (Connection c = fixture()) {
            for(var row:balances(c,"2026-08-08")) {
                BigDecimal expected=((BigDecimal)row.get("opening")).add((BigDecimal)row.get("inbound")).subtract((BigDecimal)row.get("outbound"));
                assertEquals(0,expected.compareTo((BigDecimal)row.get("closing")),row.toString());
            }
        }
    }

    @Test void dayBeforeTransferKeepsTheOpeningInTheSourceWarehouse() throws Exception {
        try(Connection c=fixture()) {
            var rows=balances(c,"2026-08-07");
            for(var row:rows) {
                assertEquals(0,((BigDecimal)row.get("opening")).compareTo((BigDecimal)row.get("closing")),row.toString());
                assertEquals(0,((BigDecimal)row.get("inbound")).signum());
            }
        }
    }
}
