package com.zlth.ivd.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * 缺货单状态枚举
 *
 * @author IVD Team
 * @since 2026-07-01
 */
@Getter
public enum ShortageStatusEnum {

    PENDING_PROCUREMENT("PENDING_PROCUREMENT", "待采购"),
    PURCHASING("PURCHASING", "采购中"),
    REPLENISHED("REPLENISHED", "已补货"),
    COMPLETED("COMPLETED", "已完成"),
    CANCELLED("CANCELLED", "已取消");

    @EnumValue
    private final String code;
    private final String label;

    ShortageStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
