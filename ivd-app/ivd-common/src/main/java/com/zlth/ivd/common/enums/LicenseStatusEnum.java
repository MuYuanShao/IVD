package com.zlth.ivd.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * 证照状态枚举
 *
 * @author IVD Team
 * @since 2026-07-01
 */
@Getter
public enum LicenseStatusEnum {

    ACTIVE("ACTIVE", "有效"),
    EXPIRING("EXPIRING", "即将到期"),
    EXPIRED("EXPIRED", "已过期"),
    FROZEN("FROZEN", "已冻结");

    @EnumValue
    private final String code;
    private final String label;

    LicenseStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
