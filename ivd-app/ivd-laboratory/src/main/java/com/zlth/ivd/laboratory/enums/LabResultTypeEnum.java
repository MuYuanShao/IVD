package com.zlth.ivd.laboratory.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.zlth.ivd.common.exception.BizException;

public enum LabResultTypeEnum {
    NUMERIC("NUMERIC"), TEXT("TEXT");

    @EnumValue
    private final String code;
    LabResultTypeEnum(String code) { this.code = code; }
    @JsonValue public String getCode() { return code; }
    public static LabResultTypeEnum fromCode(String code) {
        for (var value : values()) if (value.code.equals(code)) return value;
        throw new BizException(40001, "检验枚举值无效");
    }
}
