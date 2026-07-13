package com.zlth.ivd.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("daily_inventory_snapshot")
public class DailyInventorySnapshot {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private LocalDate snapshotDate;
    private Long reagentId;
    private Long warehouseId;
    private BigDecimal opening;
    private BigDecimal inbound;
    private BigDecimal outbound;
    private BigDecimal adjustment;
    private BigDecimal closing;
    private BigDecimal difference;
    private BigDecimal differenceRatio;
    private Integer isBalanced;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
