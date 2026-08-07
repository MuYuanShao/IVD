package com.zlth.ivd.settlement.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 消耗结算明细 VO — 消耗数据 + 试剂目录 + 设备 + 批次 + 科室 + 库房 全量字段
 */
@Data
public class ConsumptionSettlementVO {
    private String settlementNo;            // 结算单号
    private Long settlementId;              // 结算ID
    private String deptName;                 // 结算科室
    private String warehouseName;            // 结算库房
    private String reagentName;             // 试剂名称
    private String genericName;             // 通用名称
    private String specification;           // 规格型号
    private String manufacturer;            // 厂家
    private String distributor;             // 结算配送商
    private BigDecimal unitPrice;           // 单价
    private BigDecimal batchUnitPrice;      // 批次单价
    private BigDecimal quantity;            // 结算数量
    private BigDecimal amount;              // 结算金额
    private String consumerName;            // 消耗人
    private String isVolumeBased;           // 是否带量
    private String registrationCertNo;      // 注册证号
    private String isInsurancePayment;      // 是否医保支付
    private String volumeType;              // 带量类型
    private String insurancePaymentType;    // 医保支付类型
    private String isChargeable;            // 是否收费
    private String deviceCode;              // 设备号
    private String deviceName;              // 设备名称
    private LocalDateTime occurredAt;       // 消耗时间
}
