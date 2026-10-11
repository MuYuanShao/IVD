package com.zlth.ivd.server.config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.flywaydb.core.Flyway;
import static org.junit.jupiter.api.Assertions.*;
/** Prepared schema-only task DB at V20261010008; cannot point at the business database. */
@EnabledIfEnvironmentVariable(named="IVD_QA_FLYWAY_URL",matches="jdbc:mysql://127\\.0\\.0\\.1:3306/ivd_labflyway_[a-f0-9]{8}.*")
class ClinicalFlywayIntegrationTest {
    @Test void newClinicalMigrationsUseNormalFlywayExecutionAndChecksumValidation(){
        String url=System.getenv("IVD_QA_FLYWAY_URL");assertTrue(url.matches("jdbc:mysql://127\\.0\\.0\\.1:3306/ivd_labflyway_[a-f0-9]{8}.*"));
        var flyway=Flyway.configure().dataSource(url,System.getenv("IVD_RECON_TEST_USER"),System.getenv("IVD_RECON_TEST_PASSWORD"))
                .locations("classpath:db/migration").table("clinical_qa_history").baselineVersion("20261010008").cleanDisabled(true).validateOnMigrate(true).load();
        // The schema-only copy contains an empty legacy history table. Do not delete or
        // repair it: this test uses a separate history table confined to the task DB.
        if(flyway.info().current()==null)flyway.baseline();
        int pending=flyway.info().pending().length;assertTrue(pending==0 || pending==4);
        assertEquals(pending,flyway.migrate().migrationsExecuted);assertEquals("20261011004",flyway.info().current().getVersion().getVersion());assertDoesNotThrow(flyway::validate);
    }
}
