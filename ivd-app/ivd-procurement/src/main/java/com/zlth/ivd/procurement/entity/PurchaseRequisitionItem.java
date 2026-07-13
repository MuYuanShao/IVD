package com.zlth.ivd.procurement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("purchase_requisition_item")
public class PurchaseRequisitionItem {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long reqId;
    private Integer lineNo;
    private Integer reagentId;
    private String specification;
    private BigDecimal qtyRequested;
    private BigDecimal stockOnHand;
    private BigDecimal qtyFulfilled;
    private BigDecimal qtyShortage;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private Integer groupId;
    private Long shortageOrderId;

    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdAt;
}
