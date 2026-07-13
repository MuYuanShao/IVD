package com.zlth.ivd.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * X2 结算状态枚举
 *
 * @author IVD Team
 * @since 2026-07-01
 */
@Getter
public enum SettlementStatusEnum {

    PENDING("PENDING", "待结算"),
    SETTLED("SETTLED", "已结算"),
    REVERSED("REVERSED", "已冲销");

    @EnumValue
    private final String code;
    private final String label;

    SettlementStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
