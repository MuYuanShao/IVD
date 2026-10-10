package com.zlth.ivd.server.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zlth.ivd.common.enums.RequisitionStatusEnum;
import com.zlth.ivd.procurement.entity.PurchaseRequisition;
import com.zlth.ivd.procurement.mapper.PurchaseRequisitionMapper;
import com.zlth.ivd.settlement.service.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时自动签收调度器
 * <p>
 * 定期扫描超过设定时限仍未签收的已发货申领单，自动执行签收+结算。
 * 默认关闭，需通过 {@code ivd.auto-receive.enabled} 手动开启。
 *
 * @author IVD Team
 * @since 2026-07-23
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoReceiveScheduler {

    private final SettlementService settlementService;
    private final PurchaseRequisitionMapper reqMapper;
    private final com.zlth.ivd.system.service.AuthenticationDirectoryService institutions;
    @Value("${ivd.isolation.enabled:false}")
    private boolean isolationEnabled;

    @Value("${ivd.auto-receive.enabled:false}")
    private boolean enabled;

    @Value("${ivd.auto-receive.delay-hours:24}")
    private int delayHours;

    /**
     * 每小时扫描一次：签收所有超过 delayHours 小时仍未处理的已发货申领单
     */
    @Scheduled(cron = "${ivd.auto-receive.cron:0 0 * * * ?}")
    public void autoReceive() {
        if (!enabled) {
            log.debug("自动签收已禁用，跳过 (ivd.auto-receive.enabled=false)");
            return;
        }

        if(!isolationEnabled) throw new IllegalStateException("自动签收必须启用机构隔离");
        for(Long institutionId:institutions.activeInstitutionIds()) {
            try {
                com.zlth.ivd.common.security.InstitutionContext.within(
                        com.zlth.ivd.common.security.InstitutionContext.jobScope(institutionId),()->{ receiveInInstitution(); return null; });
            } catch(Exception error) { log.error("机构自动签收失败: institutionId={}",institutionId,error); }
        }
    }

    private void receiveInInstitution() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(delayHours);
        List<PurchaseRequisition> expired = reqMapper.selectList(
                new LambdaQueryWrapper<PurchaseRequisition>()
                        .in(PurchaseRequisition::getStatus,
                                RequisitionStatusEnum.SHIPPED,
                                RequisitionStatusEnum.PARTIAL_FULFILLED)
                        .le(PurchaseRequisition::getUpdatedAt, threshold));

        if (expired.isEmpty()) {
            log.debug("自动签收扫描完成: 无超时待签收申领单 (threshold={})", threshold);
            return;
        }

        int success = 0;
        int failed = 0;
        for (PurchaseRequisition req : expired) {
            try {
                settlementService.receiveAndSettleRequisition(req.getId());
                success++;
                log.info("自动签收成功: reqNo={}, id={}", req.getReqNo(), req.getId());
            } catch (Exception e) {
                failed++;
                log.error("自动签收失败: reqNo={}, id={}, error={}",
                        req.getReqNo(), req.getId(), e.getMessage());
            }
        }
        log.info("自动签收完成: 共{}单, 成功{}单, 失败{}单, threshold={}",
                expired.size(), success, failed, threshold);
    }
}
