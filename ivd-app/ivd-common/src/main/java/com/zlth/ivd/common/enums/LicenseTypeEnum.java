package com.zlth.ivd.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * 证照类型枚举
 *
 * @author IVD Team
 * @since 2026-07-01
 */
@Getter
public enum LicenseTypeEnum {

    PRODUCT_REG("PRODUCT_REG", "产品注册证"),
    PRODUCTION("PRODUCTION", "生产许可证"),
    OPERATION("OPERATION", "经营许可证"),
    GMP("GMP", "GMP证书"),
    GSP("GSP", "GSP证书"),
    OTHER("OTHER", "其他");

    @EnumValue
    private final String code;
    private final String label;

    LicenseTypeEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
