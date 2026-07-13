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
    private Integer reagentId;
    private BigDecimal qtyShortage;
    private BigDecimal estUnitPrice;

    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdAt;
}
