package com.zlth.ivd.rules.service;

import com.zlth.ivd.rules.entity.AuditRule;
import java.util.List;

public interface RulesService {
    List<AuditRule> listAll();
    List<AuditRule> listByLayer(String layer);
    AuditRule getByRuleId(String ruleId);
    void updateRule(AuditRule rule);
    void toggleRule(String ruleId, boolean enabled);
    AuditRule createRule(AuditRule rule);
}
