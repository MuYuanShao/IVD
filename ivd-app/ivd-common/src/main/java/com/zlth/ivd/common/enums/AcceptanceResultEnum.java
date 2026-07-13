package com.zlth.ivd.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * 验收结果枚举
 *
 * @author IVD Team
 * @since 2026-07-01
 */
@Getter
public enum AcceptanceResultEnum {

    QUALIFIED("QUALIFIED", "合格"),
    PARTIAL("PARTIAL", "部分合格"),
    UNQUALIFIED("UNQUALIFIED", "不合格");

    @EnumValue
    private final String code;
    private final String label;

    AcceptanceResultEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
