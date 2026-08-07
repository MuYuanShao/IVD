package com.zlth.ivd.catalog.dto;

import com.zlth.ivd.common.enums.DeviceBindModeEnum;
import com.zlth.ivd.common.enums.MaterialTypeEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 新增试剂请求 DTO
 * <p>
 * 物料类型相关的条件必填校验（udiDi / registrationCertNo / testsPerKit）在 Service 层完成。
 *
 * @author IVD Team
 * @since 2026-07-03
 */
@Data
public class CreateReagentRequest {

    @NotBlank(message = "院内编码不能为空")
    private String hisCode;

    @NotBlank(message = "物料名称不能为空")
    private String genericName;

    @NotNull(message = "物料类型不能为空")
    private MaterialTypeEnum materialType;

    @NotBlank(message = "生产厂家不能为空")
    private String manufacturer;

    @NotBlank(message = "配送商不能为空")
    private String distributor;

    private String specification;
    private String unit;
    private String storageCondition;

    /** 适用组别（多选，逗号分隔） */
    private String testGroups;

    /** UDI-DI（所有物料类型均为非必填） */
    private String udiDi;

    /** GTIN */
    private String gtin;

    /** 医保编码 */
    private String insuranceCode;

    /** NMPA 注册证号（REAGENT/CALIBRATOR/QC_MATERIAL 必填） */
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
}
