package com.zlth.ivd.common.security;

import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.zlth.ivd.common.exception.BizException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InstitutionSqlTest {
    @Test void catalogDraftAndHistoryCannotBypassInstitutionIsolation() {
        InstitutionContext.within(new InstitutionContext.Scope(11L,2L,3L,"SYSTEM_ADMIN"), () -> {
            var interceptor = new InstitutionSqlInterceptor();
            for (String table : java.util.List.of("catalog_draft", "catalog_change_log")) {
                assertTrue(interceptor.parserSingle("SELECT * FROM " + table, null).contains("institution_id = 11"));
                assertTrue(interceptor.parserSingle("INSERT INTO " + table + " (reagent_id) VALUES (1)", null).contains("institution_id"));
            }
            String query = "SELECT id FROM (SELECT id FROM catalog_change_log UNION ALL SELECT id FROM catalog_approval_log) history";
            String rewritten = interceptor.parserSingle(query, null);
            assertEquals(2, rewritten.split("institution_id = 11", -1).length - 1, rewritten);
            return null;
        });
    }
    @Test void selectUpdateAndDeleteAreRestrictedToTheActiveInstitution() {
        var scope = new InstitutionContext.Scope(11L, 2L, 3L, "LAB_TECH");
        InstitutionContext.within(scope, () -> {
            var interceptor = new TenantLineInnerInterceptor(new InstitutionTenantHandler());
            for (String sql : java.util.List.of(
                    "SELECT id FROM reagent_batch WHERE id=10",
                    "UPDATE reagent_batch SET stock=0 WHERE id=10",
                    "DELETE FROM reagent_batch WHERE id=10")) {
                String rewritten = interceptor.parserSingle(sql, null);
                assertTrue(rewritten.contains("institution_id = 11"), rewritten);
            }
            return null;
        });
    }

    @Test void insertGetsServerOwnedInstitution() {
        var scope = new InstitutionContext.Scope(11L, 2L, 3L, "LAB_TECH");
        InstitutionContext.within(scope, () -> {
            String sql = new TenantLineInnerInterceptor(new InstitutionTenantHandler())
                    .parserSingle("INSERT INTO reagent_batch (batch_no) VALUES ('LOT')", null);
            assertTrue(sql.contains("institution_id"), sql);
            assertTrue(sql.contains("11"), sql);
            return null;
        });
    }

    @Test void explicitInstitutionColumnStillRequiresContext() {
        assertThrows(BizException.class, () -> new InstitutionTenantHandler().ignoreInsert(
                java.util.List.of(new net.sf.jsqlparser.schema.Column("institution_id")), "institution_id"));
    }

    @Test void schemaQualifiedTablesCannotBypassFiltering() {
        assertFalse(new InstitutionTenantHandler().ignoreTable("other_schema.institution"));
        assertFalse(new InstitutionTenantHandler().ignoreTable("reagent_batch"));
    }
    @Test void callerSuppliedInstitutionMustNotBypassInsertIsolation() {
        var scope = new InstitutionContext.Scope(1L, 2L, 3L, "LAB_TECH");
        InstitutionContext.within(scope, () -> {
            assertThrows(BizException.class, () -> new InstitutionTenantHandler().ignoreInsert(
                    java.util.List.of(new net.sf.jsqlparser.schema.Column("institution_id")), "institution_id"));
            return null;
        });
    }
    @Test void businessSelectWithoutScopeIsRejected() {
        var interceptor = new TenantLineInnerInterceptor(new InstitutionTenantHandler());
        assertThrows(BizException.class, () -> interceptor.parserSingle("SELECT id FROM reagent_batch", null));
    }
}
