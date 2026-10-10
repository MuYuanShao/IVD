package com.zlth.ivd.procurement.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/**
 * 拣货发货请求 — 将中心库批次按申领单拣货并调拨到科室库
 *
 * @author IVD Team
 * @since 2026-07-22
 */
@Data
public class PickAndShipRequest {

    /** 目标仓库（科室库），默认使用申领单关联的 toWarehouseId */
    private Long targetWarehouseId;

    /** 逐行拣货指令：从哪个批次拣多少货 */
    @jakarta.validation.Valid
    @jakarta.validation.constraints.NotEmpty
    private List<PickItem> items;

    @Data
    public static class PickItem {
        /** 申领明细行 ID */
        @jakarta.validation.constraints.NotNull
        private Long reqItemId;
        /** 源仓库批次 ID（从哪个批次拣货） */
        @jakarta.validation.constraints.NotNull
        private Long sourceBatchId;
        /** 拣货数量 */
        @jakarta.validation.constraints.NotNull
        @jakarta.validation.constraints.DecimalMin(value = "0", inclusive = false)
        private BigDecimal pickQty;
    }
}
