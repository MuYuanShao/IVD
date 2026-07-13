package com.zlth.ivd.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * 消耗用途枚举
 *
 * @author IVD Team
 * @since 2026-07-01
 */
@Getter
public enum ConsumptionPurposeEnum {

    ROUTINE("ROUTINE", "日常消耗"),
    BATCH_TEST("BATCH_TEST", "批量检测"),
    EQUIP_MAINT("EQUIP_MAINT", "设备维护"),
    DEPT_USE("DEPT_USE", "科内领用"),
    OTHER("OTHER", "其他");

    @EnumValue
    private final String code;
    private final String label;

    ConsumptionPurposeEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
