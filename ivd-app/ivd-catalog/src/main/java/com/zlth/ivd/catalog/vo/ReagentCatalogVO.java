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

    /** 是否启用唯一码管控：0=否 1=是 */
    private Integer enableUniqueCode;

    /** 是否带量采购：0=否 1=是 */
    private Integer isVolumeBased;

    /** 带量类型：国采/省采/地市/GPO/其他 */
    private String volumeType;

    /** 是否医保支付：0=否 1=是 */
    private Integer isInsurancePayment;

    /** 医保支付类型：甲类/乙类/丙类/自费 */
    private String insurancePaymentType;

    /** 是否收费：0=否 1=是 */
    private Integer isChargeable;

    /** 收费编码 */
    private String chargeCode;

    /** 嵌套五码映射（可能为 null） */
    private FiveCodeMappingVO fiveCodeMapping;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
