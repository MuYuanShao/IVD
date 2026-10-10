package com.zlth.ivd.common.security;

import com.zlth.ivd.common.exception.BizException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InstitutionContextTest {
    @Test void jobScopeDoesNotImpersonateAClinicalUser() {
        InstitutionContext.within(InstitutionContext.jobScope(11L), () -> {
            assertEquals(11L, InstitutionContext.require().institutionId());
            assertNull(InstitutionContext.require().actorId());
            assertThrows(BizException.class, InstitutionContext::requireUserActor);
            return null;
        });
        assertThrows(BizException.class, InstitutionContext::require);
    }
    @Test void workerThreadDoesNotInheritRequestScope() {
        var scope = new InstitutionContext.Scope(1L, 2L, 3L, "LAB_TECH");
        InstitutionContext.within(scope, () -> {
            java.util.concurrent.CompletableFuture.runAsync(() ->
                    assertThrows(BizException.class, InstitutionContext::require)).join();
            return null;
        });
    }
    @Test void clinicalResourceRequiresMatchingDepartmentEvenForSystemAdmin() {
        var scope = new InstitutionContext.Scope(1L, 2L, 3L, "SYSTEM_ADMIN");
        InstitutionContext.within(scope, () -> {
            assertThrows(BizException.class, () -> InstitutionAccess.requireOwned(1L, 8L, true));
            InstitutionAccess.requireOwned(1L, 2L, true);
            return null;
        });
    }

    @Test void institutionSharedResourceDoesNotRequireAClinicalDepartment() {
        var scope = new InstitutionContext.Scope(1L, null, 3L, "SYSTEM_ADMIN");
        InstitutionContext.within(scope, () -> {
            InstitutionAccess.requireOwned(1L, null, false);
            assertThrows(BizException.class, () -> InstitutionAccess.requireOwned(1L, null, true));
            return null;
        });
    }

    @Test void adminSwitchDiscardsTheOldInstitutionsDepartment() {
        var scope = new InstitutionContext.Scope(1L, 2L, 3L, "SYSTEM_ADMIN");
        InstitutionContext.within(scope, () -> {
            var selected = InstitutionContext.selectInstitution(9L);
            assertEquals(9L, selected.institutionId());
            assertNull(selected.departmentId());
            assertEquals(scope, InstitutionContext.require());
            return null;
        });
    }

    @Test void nestedExecutionRestoresOuterScope() {
        var outer = new InstitutionContext.Scope(1L, 2L, 3L, "SYSTEM_ADMIN");
        var inner = new InstitutionContext.Scope(9L, null, 3L, "SYSTEM_ADMIN");
        InstitutionContext.within(outer, () -> {
            InstitutionContext.within(inner, () -> { assertEquals(inner, InstitutionContext.require()); return null; });
            assertEquals(outer, InstitutionContext.require());
            return null;
        });
        assertThrows(BizException.class, InstitutionContext::require);
    }
    @Test void sharedCatalogStillCannotCrossInstitutions() {
        var scope = new InstitutionContext.Scope(1L, 2L, 3L, "LAB_TECH");
        InstitutionContext.within(scope, () -> {
            assertThrows(BizException.class, () -> InstitutionAccess.requireOwned(9L, null, false));
            return null;
        });
    }
    @Test void exceptionDoesNotLeakInstitutionToNextRequest() {
        var scope = new InstitutionContext.Scope(1L, 2L, 3L, "LAB_TECH");
        assertThrows(IllegalStateException.class, () -> InstitutionContext.within(scope, () -> {
            throw new IllegalStateException("test failure");
        }));
        assertThrows(BizException.class, InstitutionContext::require);
    }
    @Test void ordinaryUserCannotSelectAnotherInstitution() {
        var scope = new InstitutionContext.Scope(1L, 2L, 3L, "LAB_TECH");
        InstitutionContext.within(scope, () -> {
            assertThrows(BizException.class, () -> InstitutionContext.selectInstitution(9L));
            return null;
        });
    }
    @Test void absentContextNeverFallsBackToTheHomeInstitution() {
        assertThrows(BizException.class, InstitutionContext::require);
    }
}
