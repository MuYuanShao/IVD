package com.zlth.ivd.procurement.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.zlth.ivd.common.enums.PurchaseOrderStatusEnum;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("purchase_order")
public class PurchaseOrder {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String poNo;
    private Integer supplierId;
    private String sourceType;
    @TableField("shortage_order_id")
    private Long shortageOrderId;
    @TableField("req_id")
    private Long reqId;
    @TableField(exist = false)
    private Long sourceId;
    private String acceptanceMethod;
    private String settlementMode;
    private BigDecimal totalAmount;
    private PurchaseOrderStatusEnum status;
    private LocalDate orderDate;
    @TableField("expected_arrival")
    private LocalDate expectedDeliveryDate;
    private BigDecimal receivedQuantity;
    @TableField(exist = false)
    private Integer createdBy;
    @TableField(exist = false)
    private String remark;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 订单明细（非数据库字段，查询时手动填充） */
    @TableField(exist = false)
    private List<PurchaseOrderItem> items;
}
