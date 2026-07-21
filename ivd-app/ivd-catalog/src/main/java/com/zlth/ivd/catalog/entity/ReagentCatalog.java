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
