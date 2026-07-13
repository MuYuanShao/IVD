package com.zlth.ivd.catalog.dto;

import com.zlth.ivd.common.enums.DeviceBindModeEnum;
import com.zlth.ivd.common.enums.MaterialTypeEnum;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 编辑试剂请求 DTO（全部字段可选，仅更新非 null 字段）
 *
 * @author IVD Team
 * @since 2026-07-03
 */
@Data
public class UpdateReagentRequest {

    private String genericName;
    private MaterialTypeEnum materialType;
    private String manufacturer;
    private String distributor;
    private String specification;
    private String unit;
    private String storageCondition;
    private String testGroups;
    private String udiDi;
    private String gtin;
    private String insuranceCode;
    private String registrationCertNo;
    private String detectionMethod;
    private Integer testsPerKit;
    private BigDecimal pricePerUnit;
    private Integer stabilityDaysAfterOpen;
    private BigDecimal minStockLevel;
    private BigDecimal maxStockLevel;
    private Integer leadTimeDays;
    private DeviceBindModeEnum deviceBindMode;
}
