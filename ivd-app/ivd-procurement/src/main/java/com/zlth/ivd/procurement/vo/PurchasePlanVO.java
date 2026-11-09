package com.zlth.ivd.procurement.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PurchasePlanVO {
    private Long id;
    private String planNo;
    private String sourceType;
    private Long shortageId;
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
    private BigDecimal totalAmount;
    private List<Item> items;
    private List<Order> orders;
    @Data public static class Item {
        private Long id;
        private Long reagentId;
        private Integer supplierId;
        private Long suggestionId;
        private String reagentName;
        private String materialType;
        private String acceptanceMethod;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal amount;
    }
    @Data public static class Order {
        private Long id;
        private String poNo;
        private Integer supplierId;
        private String acceptanceMethod;
        private String status;
    }
}
