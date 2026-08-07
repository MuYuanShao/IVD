package com.zlth.ivd.procurement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("shortage_order_item")
public class ShortageOrderItem {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long shortageOrderId;
    private Integer lineNo;
    private Long reqItemId;
    private Long reagentId;
    private BigDecimal qtyShortage;
    private BigDecimal estUnitPrice;

    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdAt;

    // ── 以下为展示用非数据库字段 ──
    @TableField(exist = false)
    private String reagentName;
    @TableField(exist = false)
    private String genericName;
    @TableField(exist = false)
    private String specification;
    @TableField(exist = false)
    private String manufacturer;
    @TableField(exist = false)
    private String unit;
    @TableField(exist = false)
    private BigDecimal estAmount;
}
