package com.zlth.ivd.consumption.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 消耗记录 VO（含关联显示字段）
 *
 * @author IVD Team
 * @since 2026-07-14
 */
@Data
public class ConsumptionRecordVO {
    private Long id;
    private String recordType;
    private String batchNo;
    private Long reagentId;
    private String reagentName;
    private String materialType;
    private Long deviceId;
    private String deviceName;
    private BigDecimal quantity;
    private String unit;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private String purpose;
    private Long warehouseId;
    private Long operatorId;
    private String remark;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
