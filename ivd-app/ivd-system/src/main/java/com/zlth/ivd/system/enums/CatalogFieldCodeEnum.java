package com.zlth.ivd.system.enums;

import com.zlth.ivd.common.exception.BizException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CatalogFieldCodeEnum {
    TEST_GROUP("适用组别"),
    DETECTION_METHOD("检测方法学"),
    VOLUME_TYPE("带量类型"),
    INSURANCE_PAYMENT_TYPE("医保支付类型");

    private final String label;

    public static CatalogFieldCodeEnum fromCode(String code) {
        if (code != null) {
            for (CatalogFieldCodeEnum item : values()) {
                if (item.name().equals(code)) return item;
            }
        }
        throw new BizException(40001, "不支持的试剂目录字段分类: " + code);
    }
}
