package com.zlth.ivd.audit.service.impl;

import com.zlth.ivd.audit.entity.AuditDecisionLog;
import com.zlth.ivd.audit.mapper.AuditDecisionLogMapper;
import com.zlth.ivd.rules.entity.AuditRule;
import com.zlth.ivd.rules.service.RulesService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditPipelineServiceImplTest {

    @Test
    void nonnumericValueMustNotBypassNumericRule() {
        var rules = mock(RulesService.class); var logs = mock(AuditDecisionLogMapper.class);
        var rule = new AuditRule(); rule.setRuleId("NUMERIC"); rule.setAction("HOLD_REVIEW");
        rule.setRuleCondition("{\"field\":\"testValue\",\"operator\":\"LT\",\"value\":0}");
        when(rules.listByLayer("RESULT")).thenReturn(List.of(rule));
        when(rules.listByLayer("BATCH")).thenReturn(List.of());
        when(rules.listByLayer("LINKAGE")).thenReturn(List.of());
        var result = new AuditPipelineServiceImpl(rules,logs).executePipeline("OBS-BAD","hash","CRP","N/A","D-1",LocalDateTime.now(),"B-1");
        org.junit.jupiter.api.Assertions.assertTrue(result.getTriggeredRules().contains("NUMERIC:CONFIG_ERROR"));
        assertEquals("HOLD_REVIEW",result.getDecision());
    }

    @Test
    void callerCannotOverrideExpiredServerBatch() {
        RulesService rules = mock(RulesService.class);
        AuditDecisionLogMapper logs = mock(AuditDecisionLogMapper.class);
        AuditRule rule = new AuditRule(); rule.setRuleId("EXPIRED"); rule.setAction("REJECT_RETEST");
        rule.setRuleCondition("{\"field\":\"expiryDays\",\"operator\":\"LT\",\"value\":0}");
        when(rules.listByLayer("RESULT")).thenReturn(List.of());
        when(rules.listByLayer("BATCH")).thenReturn(List.of(rule));
        when(rules.listByLayer("LINKAGE")).thenReturn(List.of());
        var inventory = mock(com.zlth.ivd.inventory.service.AuditInventoryContextService.class);
        LocalDateTime at = LocalDateTime.now();
        when(inventory.resolve("D-1",at,"B-1")).thenReturn(java.util.Map.of(
                "batchNo","B-1","expiryDays",-1,"batchStatus","EXPIRED","openExpiryDays",30,
                "licenseStatus","ACTIVE","inventoryFrozen",false));
        var service = new AuditPipelineServiceImpl(rules,logs); service.setInventoryContextService(inventory);
        var result = service.executePipeline("OBS-FORGED","hash","CRP","1","D-1",at,"B-1",java.util.Map.of("expiryDays",100));
        assertEquals("REJECT_RETEST",result.getDecision());
    }

    @Test
    void missingBatchContextMustNotRelease() {
        RulesService rules = mock(RulesService.class);
        AuditDecisionLogMapper logs = mock(AuditDecisionLogMapper.class);
        AuditRule rule = new AuditRule();
        rule.setRuleId("BATCH-EXPIRED"); rule.setAction("REJECT_RETEST");
        rule.setRuleCondition("{\"field\":\"expiryDays\",\"operator\":\"LT\",\"value\":0}");
        when(rules.listByLayer("RESULT")).thenReturn(List.of());
        when(rules.listByLayer("BATCH")).thenReturn(List.of(rule));
        when(rules.listByLayer("LINKAGE")).thenReturn(List.of());
        AuditDecisionLog result = new AuditPipelineServiceImpl(rules, logs).executePipeline(
                "OBS-MISSING", "hash", "CRP", "1", "D-1", LocalDateTime.now(), "B-1");
        assertEquals("HOLD_REVIEW", result.getDecision());
    }

    @Test
    void matchingJsonConditionMustTriggerConfiguredDecision() {
        RulesService rules = mock(RulesService.class);
        AuditDecisionLogMapper logs = mock(AuditDecisionLogMapper.class);
        AuditRule rule = new AuditRule();
        rule.setRuleId("TEST-001");
        rule.setAction("HOLD_REVIEW");
        rule.setRuleCondition("{\"field\":\"testValue\",\"operator\":\"GT\",\"value\":100}");
        when(rules.listByLayer("RESULT")).thenReturn(List.of(rule));
        when(rules.listByLayer("BATCH")).thenReturn(List.of());
        when(rules.listByLayer("LINKAGE")).thenReturn(List.of());
        when(logs.insert(any(AuditDecisionLog.class))).thenReturn(1);

        AuditPipelineServiceImpl service = new AuditPipelineServiceImpl(rules, logs);
        AuditDecisionLog result = service.executePipeline(
                "OBS-1", "hash", "CRP", "101", "D-1", LocalDateTime.now(), "B-1");

        assertEquals("HOLD_REVIEW", result.getDecision());
    }
}
