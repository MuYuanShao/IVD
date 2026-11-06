package com.zlth.ivd.server.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class StockCommandSchemaConfigTest {
    @Test void tableWithoutUniquenessMustNotStart() throws Exception {
        var source=new DriverManagerDataSource("jdbc:h2:mem:commandGuard"+java.util.UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        var guard=new StockCommandSchemaConfig().stockCommandSchemaGuard(source);
        assertThrows(IllegalStateException.class,guard::afterSingletonsInstantiated);
        try(var stream=new ClassPathResource("stock-command-h2.sql").getInputStream()){
            new JdbcTemplate(source).execute(new String(stream.readAllBytes(),StandardCharsets.UTF_8).replace(",UNIQUE(institution_id,operation_code,request_key)",""));
        }
        assertThrows(IllegalStateException.class,guard::afterSingletonsInstantiated);
        new JdbcTemplate(source).execute("CREATE UNIQUE INDEX uk_command_guard ON stock_command_receipt(institution_id,operation_code,request_key)");
        assertDoesNotThrow(guard::afterSingletonsInstantiated);
    }
}
