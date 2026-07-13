package com.zlth.ivd.settlement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("settlement_record")
public class SettlementRecord {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String settlementNo;        // STL-YYYYMMDD-NNNN
    private String settlementType;      // REQUISITION / CONSUMPTION
    private Long sourceId;
    private String sourceNo;
    private String batchNo;
    private Long reagentId;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private String status;              // ACTIVE / REVERSED
    private Integer fromDeptId;
    private Integer settledBy;
    private LocalDateTime settledAt;
    private LocalDateTime reversedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
