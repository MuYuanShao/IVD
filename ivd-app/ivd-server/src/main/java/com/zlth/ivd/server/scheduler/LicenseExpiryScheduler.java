package com.zlth.ivd.server.scheduler;

import com.xxl.job.core.handler.annotation.XxlJob;
import com.zlth.ivd.common.security.InstitutionContext;
import com.zlth.ivd.license.service.LicenseService;
import com.zlth.ivd.system.service.AuthenticationDirectoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Explicitly opt-in expiry refresh. The authenticated reminder API remains available while this is disabled.
 * Enable ivd.license-expiry.enabled only after deployment verification; isolation is mandatory.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LicenseExpiryScheduler {
    private final LicenseService licenses;
    private final AuthenticationDirectoryService institutions;

    @Value("${ivd.license-expiry.enabled:false}")
    private boolean enabled;
    @Value("${ivd.isolation.enabled:false}")
    private boolean isolationEnabled;

    @Scheduled(cron = "${ivd.license-expiry.cron:0 0 8 * * ?}", zone = "Asia/Shanghai")
    public void scheduledCheck() {
        executeIfEnabled();
    }

    @XxlJob("licenseExpireCheck")
    public void licenseExpireCheck() {
        executeIfEnabled();
    }

    private void executeIfEnabled() {
        if (!enabled) return;
        if (!isolationEnabled) throw new IllegalStateException("证照效期任务必须启用机构隔离");
        int failed = 0;
        for (Long institution : institutions.activeInstitutionIds()) {
            try {
                if (institution == null || institution <= 0) throw new IllegalArgumentException("机构编号无效");
                InstitutionContext.within(InstitutionContext.jobScope(institution), () -> {
                    licenses.processExpiryCheck();
                    return null;
                });
            } catch (Exception error) {
                failed++;
                log.error("机构证照效期任务失败: institutionId={}", institution, error);
            }
        }
        if (failed > 0) throw new IllegalStateException("证照效期任务有 " + failed + " 个机构执行失败");
    }
}
