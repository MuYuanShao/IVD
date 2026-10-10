package com.zlth.ivd.audit.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class AuditPipelineServiceImpl implements AuditPipelineService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final RulesService rulesService;
    private final AuditDecisionLogMapper decisionLogMapper;
    @org.springframework.beans.factory.annotation.Autowired
    public void setInventoryContextService(com.zlth.ivd.inventory.service.AuditInventoryContextService service) {
        this.inventoryContextService = service;
    }
    private com.zlth.ivd.inventory.service.AuditInventoryContextService inventoryContextService;

    @Override
    public String resolveBatch(String deviceId, String testDatetime) {
        // Redis ZRANGEBYSCORE: 按时间反查设备加载的批次
        log.debug("批次关联: device={}, time={}", deviceId, testDatetime);
        if (inventoryContextService == null) return null;
        Object batch = inventoryContextService.resolve(deviceId, LocalDateTime.parse(testDatetime), null).get("batchNo");
        return batch == null ? null : batch.toString();
    }

    /**
     * 执行审核流水线（完整实现）
     */
    public AuditDecisionLog executePipeline(String observationId, String patientIdHash, String testItemCode,
                                             String testValue, String deviceId, LocalDateTime testDatetime, String batchNo) {
        return executePipeline(observationId, patientIdHash, testItemCode, testValue, deviceId,
                testDatetime, batchNo, Collections.emptyMap());
    }

    public AuditDecisionLog executePipeline(String observationId, String patientIdHash, String testItemCode,
                                             String testValue, String deviceId, LocalDateTime testDatetime,
                                             String batchNo, Map<String, Object> ruleContext) {
        long start = System.currentTimeMillis();

        // 1. 批次关联
        String resolvedBatch = batchNo != null ? batchNo : resolveBatch(deviceId, testDatetime.toString());

        // 2. 加载上下文（一期简化：直接用规则匹配）
        Map<String, Object> context = new LinkedHashMap<>();
        if (ruleContext != null) context.putAll(ruleContext);
        for (String field : List.of("batchStatus", "expiryDays", "openExpiryDays", "deviceCalibrationDays",
                "licenseStatus", "inventoryFrozen", "deviceBindMode")) context.remove(field);
        if (inventoryContextService != null)
            context.putAll(inventoryContextService.resolve(deviceId, testDatetime, resolvedBatch));
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
        boolean missingFacts = resolvedBatch == null || List.of("batchStatus", "expiryDays", "openExpiryDays", "licenseStatus", "inventoryFrozen")
                .stream().anyMatch(field -> context.get(field) == null);
        AuditDecisionEnum finalDecision = missingFacts || allRules.isEmpty()
                ? AuditDecisionEnum.HOLD_REVIEW : AuditDecisionEnum.AUTO_RELEASE;
        if (missingFacts) triggeredRuleIds.add("CONTEXT_INCOMPLETE");
        if (allRules.isEmpty()) triggeredRuleIds.add("NO_ACTIVE_RULES");

        for (AuditRule rule : allRules) {
            if ("R-BATCH-004".equals(rule.getRuleId()) && "FLEXIBLE".equals(context.get("deviceBindMode"))) continue;
            try {
                if (evaluateRule(rule, context)) {
                    triggeredRuleIds.add(rule.getRuleId());
                    AuditDecisionEnum ruleDecision = AuditDecisionEnum.valueOf(rule.getAction());
                    if (ruleDecision.getPriority() > finalDecision.getPriority()) {
                        finalDecision = ruleDecision;
                    }
                }
            } catch (IllegalArgumentException e) {
                // 医疗审核采用 fail-closed：启用规则配置损坏时不得静默自动放行。
                triggeredRuleIds.add(rule.getRuleId() + ":CONFIG_ERROR");
                if (AuditDecisionEnum.HOLD_REVIEW.getPriority() > finalDecision.getPriority()) {
                    finalDecision = AuditDecisionEnum.HOLD_REVIEW;
                }
                log.error("审核规则配置无效，转人工复核: ruleId={}, error={}", rule.getRuleId(), e.getMessage());
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
        logEntry.setTriggeredRules(toJson(triggeredRuleIds));
        logEntry.setAuditLatencyMs((int) (System.currentTimeMillis() - start));
        logEntry.setDecidedAt(LocalDateTime.now());
        decisionLogMapper.insert(logEntry);

        log.info("审核决策: observationId={}, decision={}, triggeredRules={}, latency={}ms",
            observationId, finalDecision.getCode(), triggeredRuleIds, logEntry.getAuditLatencyMs());
        return logEntry;
    }

    private boolean evaluateRule(AuditRule rule, Map<String, Object> context) {
        String condition = rule.getRuleCondition();
        if (condition == null || condition.isBlank()) {
            throw new IllegalArgumentException("ruleCondition 不能为空");
        }
        try {
            return evaluateCondition(JSON.readTree(condition), context);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("ruleCondition 不是有效 JSON", e);
        }
    }

    private boolean evaluateCondition(JsonNode node, Map<String, Object> context) {
        if (node.has("all")) {
            for (JsonNode child : node.get("all")) {
                if (!evaluateCondition(child, context)) return false;
            }
            return true;
        }
        if (node.has("any")) {
            for (JsonNode child : node.get("any")) {
                if (evaluateCondition(child, context)) return true;
            }
            return false;
        }

        String field = text(node, "field");
        if (field == null) field = typeToField(text(node, "type"));
        String operator = Optional.ofNullable(text(node, "operator")).orElse("EQ").toUpperCase(Locale.ROOT);
        if (field == null) throw new IllegalArgumentException("条件缺少 field/type");
        Object actual = context.get(field);
        JsonNode expected = node.get("value");
        if (actual == null && !"EXISTS".equals(operator) && !"NOT_EXISTS".equals(operator)) {
            throw new IllegalArgumentException("审核上下文缺失: " + field);
        }

        return switch (operator) {
            case "EXISTS" -> actual != null;
            case "NOT_EXISTS" -> actual == null;
            case "EQ" -> compare(actual, expected) == 0;
            case "NE" -> compare(actual, expected) != 0;
            case "GT" -> compare(actual, expected) > 0;
            case "GTE", "GE" -> compare(actual, expected) >= 0;
            case "LT" -> compare(actual, expected) < 0;
            case "LTE", "LE" -> compare(actual, expected) <= 0;
            case "IN", "NOT_IN" -> {
                if (expected == null || !expected.isArray()) {
                    throw new IllegalArgumentException("IN/NOT_IN 的 value 必须为数组");
                }
                boolean found = false;
                for (JsonNode value : expected) {
                    if (compare(actual, value) == 0) {
                        found = true;
                        break;
                    }
                }
                yield "IN".equals(operator) ? found : !found;
            }
            case "BETWEEN" -> {
                if (expected == null || !expected.isArray() || expected.size() != 2) {
                    throw new IllegalArgumentException("BETWEEN 的 value 必须包含两个边界");
                }
                yield compare(actual, expected.get(0)) >= 0 && compare(actual, expected.get(1)) <= 0;
            }
            default -> throw new IllegalArgumentException("不支持的 operator: " + operator);
        };
    }

    private int compare(Object actual, JsonNode expected) {
        if (actual == null || expected == null || expected.isNull()) {
            return actual == null && (expected == null || expected.isNull()) ? 0 : -1;
        }
        String actualText = String.valueOf(actual);
        String expectedText = expected.isTextual() ? expected.asText() : expected.toString();
        if (expected.isNumber()) {
            try {
                return new BigDecimal(actualText).compareTo(expected.decimalValue());
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException("数值审核字段不是有效数字", error);
            }
        }
        if (expected.isBoolean() && !"true".equalsIgnoreCase(actualText) && !"false".equalsIgnoreCase(actualText))
            throw new IllegalArgumentException("布尔审核字段必须为true或false");
        try {
            return new BigDecimal(actualText).compareTo(new BigDecimal(expectedText));
        } catch (NumberFormatException ignored) {
            return actualText.compareToIgnoreCase(expectedText);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String typeToField(String type) {
        if (type == null) return null;
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "TEST_VALUE", "RESULT_VALUE" -> "testValue";
            case "BATCH_STATUS" -> "batchStatus";
            case "EXPIRY_DAYS" -> "expiryDays";
            case "OPEN_EXPIRY_DAYS" -> "openExpiryDays";
            case "DEVICE_CALIBRATION_DAYS" -> "deviceCalibrationDays";
            case "LICENSE_STATUS" -> "licenseStatus";
            case "INVENTORY_FROZEN" -> "inventoryFrozen";
            default -> type;
        };
    }

    private String toJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("审核结果 JSON 序列化失败", e);
        }
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
    public void recordManualFeedback(String observationId, String manualDecision, Long auditorId, String note) {
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
