package com.zlth.ivd.procurement.vo;

import com.zlth.ivd.common.enums.PurchaseOrderStatusEnum;
import com.zlth.ivd.procurement.entity.PurchaseOrderItem;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 采购订单 VO（含关联显示字段）
 *
 * @author IVD Team
 * @since 2026-07-13
 */
@Data
public class PurchaseOrderVO {
    private Long id;
    private String poNo;
    private Integer supplierId;
    private String supplierName;
    private String sourceType;
    private Long shortageOrderId;
    private Long reqId;
    private String sourceReqNo;
    private String reagentName;
    private String specification;
    private BigDecimal orderQuantity;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private PurchaseOrderStatusEnum status;
    private LocalDate orderDate;
    private LocalDate expectedDeliveryDate;
    private BigDecimal receivedQuantity;
    private String acceptanceMethod;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 订单明细 */
    private List<PurchaseOrderItem> items;
}
