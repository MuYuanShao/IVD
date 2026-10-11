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
    private Long batchId;
    private Long purchaseOrderItemId;
    private String settlementMode;
    private Long requisitionItemId;
    private Long reagentId;
    private String materialType;        // REAGENT/CALIBRATOR/QC_MATERIAL/CONSUMABLE/OTHER
    private Long deviceId;
    private BigDecimal quantity;
    private String unit;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private String purpose;             // ROUTINE/BATCH_TEST/EQUIP_MAINT/DEPT_USE/OTHER
    private Long warehouseId;
    private Long requisitionId;         // 关联申领单ID（拣货发货时写入）
    private Long fromWarehouseId;       // 调出仓库ID（调拨源仓库）
    private Long operatorId;
    private String remark;
    private LocalDateTime occurredAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
