package com.zlth.ivd.procurement.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 人工验收直收请求 DTO — 无采购订单直接验收入库
 *
 * @author IVD Team
 * @since 2026-07-20
 */
@Data
public class DirectReceiveRequest {
    /** 供应商ID（默认 1） */
    private Integer supplierId;
    /** 收货仓库ID（默认 1 = 中心库） */
    private Long warehouseId;
    private String settlementMode;
    /** 验收明细 */
    @jakarta.validation.Valid
    @jakarta.validation.constraints.NotEmpty
    private List<DirectReceiveItem> items;

    @Data
    public static class DirectReceiveItem {
        /** 试剂ID */
        @jakarta.validation.constraints.NotNull
        private Long reagentId;
        /** 收货数量 */
        @jakarta.validation.constraints.NotNull
        @jakarta.validation.constraints.DecimalMin(value="0",inclusive=false)
        private BigDecimal qtyReceived;
        /** 批号 */
        @jakarta.validation.constraints.NotBlank
        @jakarta.validation.constraints.Size(max=64)
        private String batchNo;
        /** 生产日期 */
        private LocalDate productionDate;
        /** 有效期至 */
        @jakarta.validation.constraints.NotNull
        private LocalDate expiryDate;
        /** 单价 */
        private BigDecimal unitPrice;
        /** UDI-DI */
        private String udiDi;
    }
}
