package com.zlth.ivd.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * 采购订单状态枚举
 *
 * @author IVD Team
 * @since 2026-07-01
 */
@Getter
public enum PurchaseOrderStatusEnum {

    DRAFT("DRAFT", "草稿"),
    SENT("SENT", "已发送"),
    PARTIAL_RECEIVED("PARTIAL_RECEIVED", "部分收货"),
    COMPLETED("COMPLETED", "已完成"),
    CANCELLED("CANCELLED", "已取消");

    @EnumValue
    private final String code;
    private final String label;

    PurchaseOrderStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
