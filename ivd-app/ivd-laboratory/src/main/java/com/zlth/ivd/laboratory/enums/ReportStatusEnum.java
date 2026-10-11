package com.zlth.ivd.laboratory.enums;
import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.zlth.ivd.common.exception.BizException;
public enum ReportStatusEnum {
    DRAFT,PENDING_REVIEW,REVIEWED,RETURNED,SIGNED,REVOKED,SUPERSEDED;
    @EnumValue private final String code=name();
    @JsonValue public String getCode(){return code;}
    public void validateTransition(ReportStatusEnum target){
        boolean allowed=switch(this){
            case DRAFT,RETURNED -> target==PENDING_REVIEW;
            case PENDING_REVIEW -> target==REVIEWED || target==RETURNED;
            case REVIEWED -> target==SIGNED || target==RETURNED;
            case SIGNED -> target==REVOKED || target==SUPERSEDED;
            default -> false;
        };
        if(!allowed)throw new BizException(40900,"报告状态不允许该操作");
    }
}
