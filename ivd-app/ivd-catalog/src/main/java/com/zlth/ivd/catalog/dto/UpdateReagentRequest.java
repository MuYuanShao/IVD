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
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
public class UpdateReagentRequest {
    @com.fasterxml.jackson.annotation.JsonIgnore
    private boolean stabilityDaysPresent;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private boolean leadTimePresent;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private boolean pricePresent;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private boolean testsPresent;

    @com.fasterxml.jackson.annotation.JsonSetter("leadTimeDays")
    public void setLeadTimeDays(Integer value) { leadTimePresent = true; leadTimeDays = value; }
    @com.fasterxml.jackson.annotation.JsonSetter("pricePerUnit")
    public void setPricePerUnit(BigDecimal value) { pricePresent = true; pricePerUnit = value; }
    @com.fasterxml.jackson.annotation.JsonSetter("testsPerKit")
    public void setTestsPerKit(Integer value) { testsPresent = true; testsPerKit = value; }

    @com.fasterxml.jackson.annotation.JsonSetter("stabilityDaysAfterOpen")
    public void setStabilityDaysAfterOpen(Integer value) {
        stabilityDaysPresent = true;
        stabilityDaysAfterOpen = value;
    }

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
    @jakarta.validation.constraints.Positive
    private Integer testsPerKit;
    @jakarta.validation.constraints.DecimalMin("0")
    private BigDecimal pricePerUnit;
    @jakarta.validation.constraints.PositiveOrZero
    private Integer stabilityDaysAfterOpen;
    @jakarta.validation.constraints.PositiveOrZero
    private Integer leadTimeDays;
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
