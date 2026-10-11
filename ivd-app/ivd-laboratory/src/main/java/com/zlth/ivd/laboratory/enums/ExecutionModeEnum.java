package com.zlth.ivd.laboratory.enums;
import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.zlth.ivd.common.exception.BizException;
public enum ExecutionModeEnum {
    INSTRUMENT, MANUAL, EXTERNAL;
    @EnumValue private final String code=name();
    @JsonValue public String getCode(){return code;}
    public static ExecutionModeEnum fromCode(String code){
        for(var mode:values())if(mode.code.equals(code))return mode;
        throw new BizException(40001,"检测方式无效");
    }
}
