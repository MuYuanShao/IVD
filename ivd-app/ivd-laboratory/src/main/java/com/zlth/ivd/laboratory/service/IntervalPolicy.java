package com.zlth.ivd.laboratory.service;

import java.math.BigDecimal;
import com.zlth.ivd.laboratory.enums.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Matches explicit clinical criteria; missing/ambiguous evidence is never NORMAL. */
@Component
public class IntervalPolicy {
    /** Inclusive ages and validity dates; ANY/device-null overlap more specific configurations. */
    public void requireNoOverlap(Rule candidate, List<Rule> existing) {
        if (!candidate.active()) return;
        boolean conflict = existing.stream().filter(Rule::active)
                .filter(rule -> candidate.id() == null || !Objects.equals(candidate.id(), rule.id()))
                .anyMatch(rule -> Objects.equals(rule.itemId(), candidate.itemId())
                        && rule.kind().equals(candidate.kind())
                        && Objects.equals(rule.methodology(), candidate.methodology())
                        && Objects.equals(rule.unit(), candidate.unit())
                        && (rule.deviceId() == null || candidate.deviceId() == null || Objects.equals(rule.deviceId(), candidate.deviceId()))
                        && (rule.sex()==ReferenceSexEnum.ANY || candidate.sex()==ReferenceSexEnum.ANY || rule.sex().equals(candidate.sex()))
                        && rule.minAgeDays() <= candidate.maxAgeDays() && candidate.minAgeDays() <= rule.maxAgeDays()
                        && !rule.validFrom().isAfter(candidate.validTo()) && !candidate.validFrom().isAfter(rule.validTo()));
        if (conflict) throw new com.zlth.ivd.common.exception.BizException(40900, "区间与有效配置重叠，请调整年龄、性别、设备或有效期");
    }
    public record Rule(Long id, Long itemId, Long deviceId, IntervalKindEnum kind, String methodology, String unit,
                       LabResultTypeEnum resultType, ReferenceSexEnum sex, int minAgeDays, int maxAgeDays,
                       BigDecimal lowerBound, BigDecimal upperBound, String normalText,
                       LocalDate validFrom, LocalDate validTo, boolean active) { }
    public record Input(Long itemId, Long deviceId, String methodology, String unit,
                        String sex, int ageDays, LocalDate sampleDate, String result) { }
    public record Evaluation(String status, Long intervalId, String reason) { }

    public Evaluation evaluate(List<Rule> rules, Input input, IntervalKindEnum kind) {
        if (input == null || input.sampleDate() == null || input.ageDays() < 0 || input.sex() == null)
            return new Evaluation("MISSING", null, "年龄、性别或样本日期缺失，需人工处理");
        var candidates = rules.stream().filter(Rule::active)
                .filter(rule -> Objects.equals(rule.itemId(), input.itemId()) && rule.kind().equals(kind))
                .filter(rule -> rule.deviceId() == null || Objects.equals(rule.deviceId(), input.deviceId()))
                .filter(rule -> Objects.equals(rule.methodology(), input.methodology()))
                .filter(rule -> rule.sex().matches(input.sex()))
                .filter(rule -> input.ageDays() >= rule.minAgeDays() && input.ageDays() <= rule.maxAgeDays())
                .filter(rule -> !input.sampleDate().isBefore(rule.validFrom()) && !input.sampleDate().isAfter(rule.validTo()))
                .toList();
        if (candidates.isEmpty()) return new Evaluation("MISSING", null, "未匹配有效区间，需人工处理");
        var exact = candidates.stream().filter(rule -> Objects.equals(rule.unit(), input.unit())).toList();
        if (exact.isEmpty()) return new Evaluation("UNIT_MISMATCH", null, "单位不一致，不执行自动换算");
        if (exact.size() != 1) return new Evaluation("CONFLICT", null, "存在多个匹配区间，需人工处理");
        var rule = exact.get(0);
        if (input.result() == null || input.result().isBlank()) return new Evaluation("INVALID_RESULT", rule.id(), "检验结果缺失");
        if (rule.resultType()==LabResultTypeEnum.TEXT) {
            if (rule.normalText() == null) return new Evaluation("MISSING", rule.id(), "缺少定性参考结果");
            return new Evaluation(rule.normalText().equals(input.result().trim()) ? "NORMAL" : "ABNORMAL", rule.id(), "定性结果按原值比对，不生成诊断");
        }
        try {
            BigDecimal value = new BigDecimal(input.result().trim());
            if (rule.lowerBound() == null || rule.upperBound() == null) return new Evaluation("MISSING", rule.id(), "缺少定量边界");
            String status = value.compareTo(rule.lowerBound()) < 0 ? "LOW" : value.compareTo(rule.upperBound()) > 0 ? "HIGH" : "NORMAL";
            return new Evaluation(status, rule.id(), "按闭区间判断；仅为风险提示，不代替复核");
        } catch (NumberFormatException e) {
            return new Evaluation("INVALID_RESULT", rule.id(), "定量结果不是有效数值");
        }
    }
}
