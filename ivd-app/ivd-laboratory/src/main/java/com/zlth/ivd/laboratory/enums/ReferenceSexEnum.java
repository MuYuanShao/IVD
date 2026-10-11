package com.zlth.ivd.laboratory.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.zlth.ivd.common.exception.BizException;

public enum ReferenceSexEnum {
    ANY("ANY"), MALE("MALE"), FEMALE("FEMALE");

    @EnumValue
    private final String code;
    ReferenceSexEnum(String code) { this.code = code; }
    @JsonValue public String getCode() { return code; }
    public static ReferenceSexEnum fromCode(String code) {
        for (var value : values()) if (value.code.equals(code)) return value;
        throw new BizException(40001, "检验枚举值无效");
    }

    public boolean matches(String sex) { return this == ANY || code.equals(sex); }
}
