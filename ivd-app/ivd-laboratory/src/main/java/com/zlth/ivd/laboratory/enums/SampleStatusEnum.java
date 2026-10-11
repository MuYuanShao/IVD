package com.zlth.ivd.laboratory.enums;
import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.zlth.ivd.common.exception.BizException;
public enum SampleStatusEnum {
    COLLECTED, RECEIVED, REJECTED;
    @EnumValue private final String code=name();
    @JsonValue public String getCode(){return code;}
    public void validateTransition(SampleStatusEnum target){
        if(this!=COLLECTED || (target!=RECEIVED && target!=REJECTED))
            throw new BizException(40900,"样本状态不允许该转移，请刷新后核对");
    }
}
