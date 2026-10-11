package com.zlth.ivd.server.config;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SupplyFlowSchemaConfigTest {
    @Test void missingBillingEvidenceSchemaFailsBeforeAnyMutation(){
        var source=new org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:h2:mem:guardFlow"+java.util.UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");
        var guard=new SupplyFlowSchemaConfig().supplyFlowSchemaGuard(source);assertThrows(IllegalStateException.class,guard::afterSingletonsInstantiated);
        var jdbc=new org.springframework.jdbc.core.JdbcTemplate(source);
        jdbc.execute("CREATE TABLE reagent_batch(inventory_generation INT,origin_purchase_order_item_id BIGINT,settlement_mode VARCHAR)");
        jdbc.execute("CREATE TABLE consumption_record(settlement_mode VARCHAR)");
        assertThrows(IllegalStateException.class,guard::afterSingletonsInstantiated);
        jdbc.execute("CREATE TABLE settlement_event(settlement_id BIGINT)");assertDoesNotThrow(guard::afterSingletonsInstantiated);
    }
}
