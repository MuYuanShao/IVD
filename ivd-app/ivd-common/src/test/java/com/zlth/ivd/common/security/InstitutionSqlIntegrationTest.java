package com.zlth.ivd.common.security;

import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.junit.jupiter.api.Test;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises rewritten SQL against an isolated database; not an HTTP/full-application isolation test. */
class InstitutionSqlIntegrationTest {
    @Test void directForeignIdReadAndUpdateCannotCrossInstitutions() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:"+java.util.UUID.randomUUID()+";MODE=MySQL")) {
            try(Statement statement=connection.createStatement()) {
                statement.execute("CREATE TABLE reagent_batch(id BIGINT PRIMARY KEY,institution_id BIGINT NOT NULL,stock DECIMAL(10,2))");
                statement.execute("INSERT INTO reagent_batch VALUES(10,11,5),(20,22,9)");
            }
            var interceptor=new TenantLineInnerInterceptor(new InstitutionTenantHandler());
            InstitutionContext.within(new InstitutionContext.Scope(11L,2L,3L,"LAB_TECH"),()->{
                try(Statement statement=connection.createStatement()) {
                    try(ResultSet rows=statement.executeQuery(interceptor.parserSingle("SELECT id FROM reagent_batch WHERE id=20",null))) {
                        assertFalse(rows.next());
                    }
                    assertEquals(0,statement.executeUpdate(interceptor.parserSingle("UPDATE reagent_batch SET stock=0 WHERE id=20",null)));
                    assertEquals(0,statement.executeUpdate(interceptor.parserSingle("DELETE FROM reagent_batch WHERE id=20",null)));
                } catch(SQLException error){ throw new IllegalStateException(error); }
                return null;
            });
            InstitutionContext.within(new InstitutionContext.Scope(22L,4L,6L,"LAB_TECH"),()->{
                try(Statement statement=connection.createStatement();ResultSet rows=statement.executeQuery(
                        interceptor.parserSingle("SELECT stock FROM reagent_batch WHERE id=20",null))) {
                    assertTrue(rows.next()); assertEquals(0,new java.math.BigDecimal("9").compareTo(rows.getBigDecimal(1)));
                } catch(SQLException error){ throw new IllegalStateException(error); }
                return null;
            });
        }
    }
}
