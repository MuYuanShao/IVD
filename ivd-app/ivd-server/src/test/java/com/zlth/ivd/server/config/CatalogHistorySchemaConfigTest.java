package com.zlth.ivd.server.config;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CatalogHistorySchemaConfigTest {
    @Test void missingHistorySchemaFailsBeforeRequestsCanMutateCatalog() {
        var source = new org.h2.jdbcx.JdbcDataSource();
        source.setURL("jdbc:h2:mem:catalogGuard"+java.util.UUID.randomUUID()+";DB_CLOSE_DELAY=-1");
        var guard = new CatalogHistorySchemaConfig().catalogHistorySchemaGuard(source);
        assertThrows(IllegalStateException.class,guard::afterSingletonsInstantiated);
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(source);
        jdbc.execute("CREATE TABLE catalog_draft(reagent_id BIGINT)");
        assertThrows(IllegalStateException.class,guard::afterSingletonsInstantiated);
        jdbc.execute("CREATE TABLE catalog_change_log(id BIGINT)");
        assertDoesNotThrow(guard::afterSingletonsInstantiated);
    }
}
