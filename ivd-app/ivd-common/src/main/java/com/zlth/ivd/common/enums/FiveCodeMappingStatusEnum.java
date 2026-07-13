package com.zlth.ivd.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * 五码映射状态枚举
 *
 * @author IVD Team
 * @since 2026-07-01
 */
@Getter
public enum FiveCodeMappingStatusEnum {

    MAPPED("MAPPED", "已映射"),
    PENDING_MAPPING("PENDING_MAPPING", "待映射"),
    INSURANCE_PENDING("INSURANCE_PENDING", "医保待补全");

    @EnumValue
    private final String code;
    private final String label;

    FiveCodeMappingStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
