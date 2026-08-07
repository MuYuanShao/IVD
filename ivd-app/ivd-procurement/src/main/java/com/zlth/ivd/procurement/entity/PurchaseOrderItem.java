package com.zlth.ivd.procurement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("purchase_order_item")
public class PurchaseOrderItem {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long poId;
    private Integer lineNo;
    private Long reagentId;
    private Integer supplierId;
    private String materialType;            // REAGENT/CALIBRATOR/QC_MATERIAL/CONSUMABLE/OTHER
    @TableField("order_quantity")
    private BigDecimal qtyOrdered;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    @TableField("received_quantity")
    private BigDecimal qtyReceived;
    @Version
    private Integer version;
    private LocalDateTime createdAt;
}
