package com.zlth.ivd.server.scheduler;

import com.zlth.ivd.common.security.InstitutionContext;
import com.zlth.ivd.license.service.LicenseService;
import com.zlth.ivd.system.service.AuthenticationDirectoryService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LicenseExpirySchedulerTest {
    @Test void disabledIsNoOpAndEnabledRunsAllInstitutionsWithCleanup() {
        var licenses=mock(LicenseService.class);var directory=mock(AuthenticationDirectoryService.class);
        var job=new LicenseExpiryScheduler(licenses,directory);
        job.scheduledCheck();verifyNoInteractions(licenses,directory);
        ReflectionTestUtils.setField(job,"enabled",true);
        assertThrows(IllegalStateException.class,job::scheduledCheck);
        ReflectionTestUtils.setField(job,"isolationEnabled",true);
        when(directory.activeInstitutionIds()).thenReturn(List.of(1L,2L));
        var visited=new ArrayList<Long>();
        doAnswer(call->{
            var scope=InstitutionContext.require();assertEquals("SYSTEM_JOB",scope.role());assertNull(scope.actorId());
            visited.add(scope.institutionId());if(scope.institutionId()==1)throw new IllegalStateException("QA failure");
            return null;
        }).when(licenses).processExpiryCheck();
        assertThrows(IllegalStateException.class,job::licenseExpireCheck);
        assertEquals(List.of(1L,2L),visited);assertTrue(InstitutionContext.current().isEmpty());
    }
}
