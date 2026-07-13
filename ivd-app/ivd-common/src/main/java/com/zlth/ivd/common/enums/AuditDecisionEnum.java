package com.zlth.ivd.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * 审核决策动作枚举（4 种，优先级从低到高）
 *
 * @author IVD Team
 * @since 2026-07-01
 */
@Getter
public enum AuditDecisionEnum {

    AUTO_RELEASE("AUTO_RELEASE", "自动放行", 1),
    WARN_RELEASE("WARN_RELEASE", "放行带警告", 2),
    HOLD_REVIEW("HOLD_REVIEW", "转人工审核", 3),
    REJECT_RETEST("REJECT_RETEST", "拒绝需复测", 4);

    @EnumValue
    private final String code;
    private final String label;
    /** 优先级：数值越大越优先，REJECT_RETEST 为最高 */
    private final int priority;

    AuditDecisionEnum(String code, String label, int priority) {
        this.code = code;
        this.label = label;
        this.priority = priority;
    }
}
