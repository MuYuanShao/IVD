package com.zlth.ivd.rules.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.rules.entity.AuditRule;
import com.zlth.ivd.rules.mapper.AuditRuleMapper;
import com.zlth.ivd.rules.service.RulesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class RulesServiceImpl implements RulesService {

    private final AuditRuleMapper ruleMapper;

    @Override
    public List<AuditRule> listAll() {
        return ruleMapper.selectList(
            new LambdaQueryWrapper<AuditRule>().orderByDesc(AuditRule::getPriority)
        );
    }

    @Override
    public List<AuditRule> listByLayer(String layer) {
        return ruleMapper.selectList(
            new LambdaQueryWrapper<AuditRule>()
                .eq(AuditRule::getLayer, layer)
                .eq(AuditRule::getEnabled, 1)
                .orderByDesc(AuditRule::getPriority)
        );
    }

    @Override
    public AuditRule getByRuleId(String ruleId) {
        AuditRule rule = ruleMapper.selectOne(
            new LambdaQueryWrapper<AuditRule>().eq(AuditRule::getRuleId, ruleId)
        );
        if (rule == null) throw new BizException(40400, "规则不存在: " + ruleId);
        return rule;
    }

    @Override
    public void updateRule(AuditRule rule) {
        AuditRule existing = getByRuleId(rule.getRuleId());
        rule.setId(existing.getId());
        ruleMapper.updateById(rule);
        log.info("规则更新: ruleId={}", rule.getRuleId());
    }

    @Override
    public void toggleRule(String ruleId, boolean enabled) {
        AuditRule rule = getByRuleId(ruleId);
        rule.setEnabled(enabled ? 1 : 0);
        ruleMapper.updateById(rule);
        log.info("规则切换: ruleId={}, enabled={}", ruleId, enabled);
    }

    @Override
    public AuditRule createRule(AuditRule rule) {
        ruleMapper.insert(rule);
        log.info("规则创建: ruleId={}", rule.getRuleId());
        return rule;
    }
}
