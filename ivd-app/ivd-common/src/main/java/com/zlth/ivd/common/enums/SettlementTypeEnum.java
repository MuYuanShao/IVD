package com.zlth.ivd.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * 结算类型枚举
 *
 * @author IVD Team
 * @since 2026-07-01
 */
@Getter
public enum SettlementTypeEnum {

    REQUISITION("REQUISITION", "申领结算"),
    CONSUMPTION("CONSUMPTION", "领用结算"),
    TRANSFER("TRANSFER", "调拨结算"),
    INBOUND("INBOUND", "验收结算");

    @EnumValue
    private final String code;
    private final String label;

    SettlementTypeEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
