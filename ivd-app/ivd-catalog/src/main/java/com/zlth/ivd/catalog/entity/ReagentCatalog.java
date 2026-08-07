package com.zlth.ivd.catalog.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.zlth.ivd.common.enums.ApprovalStatusEnum;
import com.zlth.ivd.common.enums.DeviceBindModeEnum;
import com.zlth.ivd.common.enums.MaterialTypeEnum;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 试剂目录主表 Entity
 *
 * @author IVD Team
 * @since 2026-07-01
 */
@Data
@TableName("reagent_catalog")
public class ReagentCatalog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 院内编码（IVD-{组别缩写}-{序号}） */
    private String hisCode;

    /** 物料名称 */
    private String genericName;

    /** 物料类型（★ 决定全流程行为） */
    private MaterialTypeEnum materialType;

    /** 生产厂家 */
    private String manufacturer;

    /** 配送商 */
    private String distributor;

    /** 规格 */
    private String specification;

    /** 单位 */
    private String unit;

    /** 储存条件 */
    private String storageCondition;

    /** 适用组别（多选，逗号分隔） */
    private String testGroups;

    /** UDI-DI */
    private String udiDi;

    /** GTIN */
    private String gtin;

    /** 医保编码 */
    private String insuranceCode;

    /** 是否医保支付：0=否 1=是 */
    private Integer isInsurancePayment;

    /** 是否启用唯一码管控：0=否 1=是（开启后验收入库时按数量逐盒生成唯一码） */
    private Integer enableUniqueCode;

    /** NMPA 注册证号 */
    private String registrationCertNo;

    /** 检测方法学 */
    private String detectionMethod;

    /** 标称测试数（仅 REAGENT 必填） */
    private Integer testsPerKit;

    /** 单价（元），含税 */
    private BigDecimal pricePerUnit;

    /** 开瓶稳定天数 */
    private Integer stabilityDaysAfterOpen;

    /** 采购提前期(天) */
    private Integer leadTimeDays;

    /** 设备关联策略 */
    private DeviceBindModeEnum deviceBindMode;

    /** 是否带量采购 */
    private Integer isVolumeBased;

    /** 带量类型: NATIONAL_VOLUME / PROVINCIAL / GPO / NONE */
    private String volumeType;

    /** 医保支付类型: A类 / B类 / C类 / 自费 */
    private String insurancePaymentType;

    /** 是否收费 */
    private Integer isChargeable;

    /** 收费编码 */
    private String chargeCode;

    /** 审批状态 */
    private ApprovalStatusEnum approvalStatus;

    /** 软删除标记 */
    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
