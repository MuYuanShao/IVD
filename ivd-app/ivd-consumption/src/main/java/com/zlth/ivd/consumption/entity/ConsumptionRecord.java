package com.zlth.ivd.consumption.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("consumption_record")
public class ConsumptionRecord {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String recordType;          // INBOUND / CONSUME
    private String batchNo;
    private Long reagentId;
    private String materialType;        // REAGENT/CALIBRATOR/QC_MATERIAL/CONSUMABLE/OTHER
    private Long deviceId;
    private BigDecimal quantity;
    private String unit;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private String purpose;             // ROUTINE/BATCH_TEST/EQUIP_MAINT/DEPT_USE/OTHER
    private Long warehouseId;
    private Integer operatorId;
    private String remark;
    private LocalDateTime occurredAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
