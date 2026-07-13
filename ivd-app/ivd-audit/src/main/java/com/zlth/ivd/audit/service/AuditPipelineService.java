package com.zlth.ivd.audit.service;

import com.zlth.ivd.audit.entity.AuditDecisionLog;

import java.time.LocalDateTime;
import java.util.List;

/**
 * S3 审核决策流水线
 * <p>
 * 三层规则架构：
 *   L1 结果规则（传统，LIS也能做）→ L2 批次规则（★ 护城河）→ L3 联动规则（跨域触发）
 * <p>
 * 性能要求：P95 < 500ms（LIS 同步等待）
 * 兜底策略：审核服务不可用时默认 AUTO_RELEASE
 *
 * @author IVD Team
 * @since 2026-07-01
 */
public interface AuditPipelineService {

    String resolveBatch(String deviceId, String testDatetime);

    AuditDecisionLog executePipeline(String observationId, String patientIdHash, String testItemCode,
                                     String testValue, String deviceId, LocalDateTime testDatetime, String batchNo);

    List<AuditDecisionLog> listDecisions(String batchNo, LocalDateTime start, LocalDateTime end);

    void recordManualFeedback(String observationId, String manualDecision, Integer auditorId, String note);
}
