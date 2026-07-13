package com.zlth.ivd.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * 审批状态枚举（目录变更审批）
 *
 * @author IVD Team
 * @since 2026-07-01
 */
@Getter
public enum ApprovalStatusEnum {

    DRAFT("DRAFT", "草稿"),
    PENDING("PENDING", "待审批"),
    APPROVED("APPROVED", "已批准"),
    REJECTED("REJECTED", "已驳回");

    @EnumValue
    private final String code;
    private final String label;

    ApprovalStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
