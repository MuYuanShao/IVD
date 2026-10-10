package com.zlth.ivd.server.config;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InstitutionMigrationConfigTest {
    @Test void pendingOwnershipDoesNotMigrateWhenIsolationIsDisabled(){
        var flyway=mock(Flyway.class);var info=mock(MigrationInfoService.class);var migration=mock(MigrationInfo.class);
        when(flyway.info()).thenReturn(info);when(info.pending()).thenReturn(new MigrationInfo[]{migration});
        when(migration.getVersion()).thenReturn(MigrationVersion.fromVersion("20261010005"));
        assertThrows(IllegalStateException.class,()->new InstitutionMigrationConfig().checkedInstitutionMigration(false).migrate(flyway));
        verify(flyway,never()).migrate();
    }
    @Test void explicitIsolationOptInKeepsNormalMigration(){
        var flyway=mock(Flyway.class);
        new InstitutionMigrationConfig().checkedInstitutionMigration(true).migrate(flyway);
        verify(flyway).migrate();
    }
    @Test void schemaAndSwitchMismatchFailClosed(){
        var source=new org.h2.jdbcx.JdbcDataSource();source.setURL("jdbc:h2:mem:guard"+java.util.UUID.randomUUID()+";DB_CLOSE_DELAY=-1");
        var jdbc=new org.springframework.jdbc.core.JdbcTemplate(source);
        jdbc.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY)");
        var config=new InstitutionMigrationConfig();
        assertDoesNotThrow(()->config.institutionSchemaGuard(source,false).afterSingletonsInstantiated());
        assertThrows(IllegalStateException.class,()->config.institutionSchemaGuard(source,true).afterSingletonsInstantiated());
        jdbc.execute("ALTER TABLE sys_user ADD institution_id BIGINT");
        assertThrows(IllegalStateException.class,()->config.institutionSchemaGuard(source,false).afterSingletonsInstantiated());
        assertThrows(IllegalStateException.class,()->config.institutionSchemaGuard(source,true).afterSingletonsInstantiated());
        jdbc.execute("CREATE TABLE lab_test_item(id BIGINT PRIMARY KEY)");jdbc.execute("CREATE TABLE lab_interval(id BIGINT PRIMARY KEY)");
        assertDoesNotThrow(()->config.institutionSchemaGuard(source,true).afterSingletonsInstantiated());
    }
}
