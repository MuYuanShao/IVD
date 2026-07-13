package com.zlth.ivd.rules.controller;

import com.zlth.ivd.common.dto.Result;
import com.zlth.ivd.rules.entity.AuditRule;
import com.zlth.ivd.rules.service.RulesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/rules")
@RequiredArgsConstructor
public class RulesController {

    private final RulesService rulesService;

    @GetMapping
    public Result<List<AuditRule>> listAll() {
        return Result.success(rulesService.listAll());
    }

    @GetMapping("/layer/{layer}")
    public Result<List<AuditRule>> listByLayer(@PathVariable String layer) {
        return Result.success(rulesService.listByLayer(layer));
    }

    @GetMapping("/{ruleId}")
    public Result<AuditRule> getByRuleId(@PathVariable String ruleId) {
        return Result.success(rulesService.getByRuleId(ruleId));
    }

    @PutMapping("/{ruleId}")
    public Result<String> update(@PathVariable String ruleId, @RequestBody AuditRule rule) {
        rule.setRuleId(ruleId);
        rulesService.updateRule(rule);
        return Result.success("规则更新成功");
    }

    @PutMapping("/{ruleId}/toggle")
    public Result<String> toggle(@PathVariable String ruleId, @RequestParam boolean enabled) {
        rulesService.toggleRule(ruleId, enabled);
        return Result.success(enabled ? "规则已启用" : "规则已禁用");
    }

    @PostMapping
    public Result<AuditRule> create(@RequestBody AuditRule rule) {
        return Result.success(rulesService.createRule(rule));
    }
}
