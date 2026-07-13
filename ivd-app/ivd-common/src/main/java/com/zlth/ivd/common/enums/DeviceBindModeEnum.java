package com.zlth.ivd.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * 设备关联策略枚举
 *
 * @author IVD Team
 * @since 2026-07-01
 */
@Getter
public enum DeviceBindModeEnum {

    FORCE("FORCE", "强制关联"),
    FLEXIBLE("FLEXIBLE", "柔性关联");

    @EnumValue
    private final String code;
    private final String label;

    DeviceBindModeEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
