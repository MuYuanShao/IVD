package com.zlth.ivd.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.zlth.ivd.common.enums.MaterialTypeEnum;
import com.zlth.ivd.common.enums.UdiStatusEnum;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("reagent_batch")
public class ReagentBatch {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String batchNo;
    private Long reagentId;
    private MaterialTypeEnum materialType;
    private String udiDi;
    private String udiPi;
    private LocalDate manufactureDate;
    private LocalDate expiryDate;
    private LocalDate openDate;
    private LocalDate openExpiryDate;
    private BigDecimal stock;
    private String unit;
    private BigDecimal unitPrice;
    private BigDecimal avgCost;
    private UdiStatusEnum udiStatus;
    private Long warehouseId;
    private String locationCode;
    @Version private Integer version;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updatedAt;
}
