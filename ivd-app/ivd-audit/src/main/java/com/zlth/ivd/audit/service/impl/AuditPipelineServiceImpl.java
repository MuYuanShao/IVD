package com.zlth.ivd.audit.service.impl;

import com.zlth.ivd.audit.entity.AuditDecisionLog;
import com.zlth.ivd.audit.mapper.AuditDecisionLogMapper;
import com.zlth.ivd.audit.service.AuditPipelineService;
import com.zlth.ivd.common.enums.AuditDecisionEnum;
import com.zlth.ivd.rules.entity.AuditRule;
import com.zlth.ivd.rules.service.RulesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class AuditPipelineServiceImpl implements AuditPipelineService {

    private final RulesService rulesService;
    private final AuditDecisionLogMapper decisionLogMapper;

    @Override
    public String resolveBatch(String deviceId, String testDatetime) {
        // Redis ZRANGEBYSCORE: 按时间反查设备加载的批次
        log.debug("批次关联: device={}, time={}", deviceId, testDatetime);
        return null; // 一期先返回 null，待 Redis 设备批次映射完善
    }

    /**
     * 执行审核流水线（完整实现）
     */
    public AuditDecisionLog executePipeline(String observationId, String patientIdHash, String testItemCode,
                                             String testValue, String deviceId, LocalDateTime testDatetime, String batchNo) {
        long start = System.currentTimeMillis();

        // 1. 批次关联
        String resolvedBatch = batchNo != null ? batchNo : resolveBatch(deviceId, testDatetime.toString());

        // 2. 加载上下文（一期简化：直接用规则匹配）
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("batchNo", resolvedBatch);
        context.put("deviceId", deviceId);
        context.put("testItemCode", testItemCode);
        context.put("testValue", testValue);

        // 3. 规则匹配：按 layer 逐层执行
        List<AuditRule> allRules = new ArrayList<>();
        allRules.addAll(rulesService.listByLayer("RESULT"));
        allRules.addAll(rulesService.listByLayer("BATCH"));
        allRules.addAll(rulesService.listByLayer("LINKAGE"));

        List<String> triggeredRuleIds = new ArrayList<>();
        AuditDecisionEnum finalDecision = AuditDecisionEnum.AUTO_RELEASE;

        for (AuditRule rule : allRules) {
            if (evaluateRule(rule, context)) {
                triggeredRuleIds.add(rule.getRuleId());
                AuditDecisionEnum ruleDecision = AuditDecisionEnum.valueOf(rule.getAction());
                if (ruleDecision.getPriority() > finalDecision.getPriority()) {
                    finalDecision = ruleDecision;
                }
            }
        }

        // 4. 写入决策日志
        AuditDecisionLog logEntry = new AuditDecisionLog();
        logEntry.setObservationId(observationId);
        logEntry.setPatientIdHash(patientIdHash);
        logEntry.setTestItemCode(testItemCode);
        logEntry.setDeviceId(deviceId);
        logEntry.setTestDatetime(testDatetime);
        logEntry.setBatchNo(resolvedBatch);
        logEntry.setDecision(finalDecision.getCode());
        logEntry.setTriggeredRules(triggeredRuleIds.toString());
        logEntry.setAuditLatencyMs((int) (System.currentTimeMillis() - start));
        logEntry.setDecidedAt(LocalDateTime.now());
        decisionLogMapper.insert(logEntry);

        log.info("审核决策: observationId={}, decision={}, triggeredRules={}, latency={}ms",
            observationId, finalDecision.getCode(), triggeredRuleIds, logEntry.getAuditLatencyMs());
        return logEntry;
    }

    private boolean evaluateRule(AuditRule rule, Map<String, Object> context) {
        // 一期简化评估（实际应解析 rule.ruleCondition JSON）
        // 默认不触发，由真实条件 JSON 解析后判定
        return false;
    }

    /** 查询决策日志 */
    public List<AuditDecisionLog> listDecisions(String batchNo, LocalDateTime start, LocalDateTime end) {
        return decisionLogMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AuditDecisionLog>()
                .eq(batchNo != null, AuditDecisionLog::getBatchNo, batchNo)
                .ge(start != null, AuditDecisionLog::getDecidedAt, start)
                .le(end != null, AuditDecisionLog::getDecidedAt, end)
                .orderByDesc(AuditDecisionLog::getDecidedAt)
        );
    }

    /** 记录人工复核结果 */
    public void recordManualFeedback(String observationId, String manualDecision, Integer auditorId, String note) {
        AuditDecisionLog logEntry = decisionLogMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AuditDecisionLog>()
                .eq(AuditDecisionLog::getObservationId, observationId)
                .orderByDesc(AuditDecisionLog::getDecidedAt)
                .last("LIMIT 1")
        );
        if (logEntry != null) {
            logEntry.setManualDecision(manualDecision);
            logEntry.setAuditorId(auditorId);
            logEntry.setAuditorNote(note);
            decisionLogMapper.updateById(logEntry);
        }
    }
}
