package com.zlth.ivd.catalog.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 试剂目录响应 VO（含枚举 label 及嵌套五码映射）
 *
 * @author IVD Team
 * @since 2026-07-03
 */
@Data
public class ReagentCatalogVO {

    private Long id;
    private String hisCode;
    private String genericName;

    /** 物料类型 code */
    private String materialType;
    /** 物料类型中文标签 */
    private String materialTypeLabel;

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
    private Integer leadTimeDays;

    /** 设备关联策略 code */
    private String deviceBindMode;
    /** 设备关联策略中文标签 */
    private String deviceBindModeLabel;

    /** 审批状态 code */
    private String approvalStatus;
    /** 审批状态中文标签 */
    private String approvalStatusLabel;

    /** 嵌套五码映射（可能为 null） */
    private FiveCodeMappingVO fiveCodeMapping;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
