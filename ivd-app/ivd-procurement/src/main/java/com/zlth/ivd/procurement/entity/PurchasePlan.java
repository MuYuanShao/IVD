package com.zlth.ivd.procurement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("purchase_plan")
public class PurchasePlan {
    @TableId(type=IdType.AUTO)
    private Long id;
    private String planNo;
    private String sourceType;
    private Long shortageOrderId;
    private String status;
    private LocalDate orderDate;
    private LocalDate expectedDeliveryDate;
    private String settlementMode;
    private String remark;
    private Long createdBy;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private String reviewComment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableField(exist=false)
    private List<PurchasePlanItem> items;
}
