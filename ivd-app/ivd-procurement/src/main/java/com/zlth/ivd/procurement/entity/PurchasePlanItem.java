package com.zlth.ivd.procurement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("purchase_plan_item")
public class PurchasePlanItem {
    @TableId(type=IdType.AUTO)
    private Long id;
    private Long planId;
    private Integer lineNo;
    private Long reagentId;
    private Integer supplierId;
    private Long suggestionId;
    private String reagentName;
    private String materialType;
    private String acceptanceMethod;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
