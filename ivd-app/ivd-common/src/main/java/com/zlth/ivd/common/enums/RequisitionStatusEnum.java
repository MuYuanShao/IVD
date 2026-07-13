package com.zlth.ivd.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * 申领单状态枚举
 *
 * @author IVD Team
 * @since 2026-07-01
 */
@Getter
public enum RequisitionStatusEnum {

    DRAFT("DRAFT", "草稿"),
    PENDING_REVIEW("PENDING_REVIEW", "待审核"),
    REVIEWED("REVIEWED", "已审核"),
    PICKING("PICKING", "拣选中"),
    PARTIAL_FULFILLED("PARTIAL_FULFILLED", "部分发货"),
    FULFILLED("FULFILLED", "已完成"),
    SHORTAGE_PENDING("SHORTAGE_PENDING", "缺货待采"),
    CANCELLED("CANCELLED", "已取消");

    @EnumValue
    private final String code;
    private final String label;

    RequisitionStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
