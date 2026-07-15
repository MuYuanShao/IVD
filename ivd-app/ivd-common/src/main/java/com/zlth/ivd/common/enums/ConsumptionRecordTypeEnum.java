package com.zlth.ivd.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * 消耗/入库记录类型枚举
 *
 * @author IVD Team
 * @since 2026-07-14
 */
@Getter
public enum ConsumptionRecordTypeEnum {

    INBOUND("INBOUND", "入库"),
    CONSUME("CONSUME", "消耗");

    @EnumValue
    private final String code;
    private final String label;

    ConsumptionRecordTypeEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
