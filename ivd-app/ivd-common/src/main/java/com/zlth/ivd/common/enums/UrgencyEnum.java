package com.zlth.ivd.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * 库存/申领紧急程度枚举
 *
 * @author IVD Team
 * @since 2026-07-01
 */
@Getter
public enum UrgencyEnum {

    NORMAL("NORMAL", "正常"),
    URGENT("URGENT", "紧急"),
    CRITICAL("CRITICAL", "严重");

    @EnumValue
    private final String code;
    private final String label;

    UrgencyEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
