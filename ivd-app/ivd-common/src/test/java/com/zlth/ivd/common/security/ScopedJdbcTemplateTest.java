package com.zlth.ivd.common.security;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScopedJdbcTemplateTest {
    @Test void nativeJdbcCannotReadOrModifyAnotherInstitution() {
        var dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:"+java.util.UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        var setup = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        setup.execute("CREATE TABLE reagent_batch(id BIGINT PRIMARY KEY,institution_id BIGINT NOT NULL,stock INT)");
        setup.update("INSERT INTO reagent_batch VALUES(1,11,5),(2,22,9)");
        var jdbc = new ScopedJdbcTemplate(dataSource, true);
        InstitutionContext.within(new InstitutionContext.Scope(11L,1L,1L,"STOREKEEPER"),()->{
            assertTrue(jdbc.queryForList("SELECT * FROM reagent_batch WHERE id=?",2L).isEmpty());
            assertEquals(0,jdbc.update("UPDATE reagent_batch SET stock=0 WHERE id=?",2L));
            return null;
        });
        assertEquals(9,setup.queryForObject("SELECT stock FROM reagent_batch WHERE id=2",Integer.class));
    }
}
