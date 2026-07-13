package com.zlth.ivd.procurement.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 验收入库请求 DTO
 *
 * @author IVD Team
 * @since 2026-07-09
 */
@Data
public class ReceiveItemRequest {
    /** 采购订单ID */
    private Long orderId;
    /** 订单明细ID */
    private Long itemId;
    /** 收货数量 */
    private BigDecimal qtyReceived;
    /** UDI-DI（产品标识） */
    private String udiDi;
    /** 批号 */
    private String batchNo;
    /** 生产日期 */
    private LocalDate productionDate;
    /** 有效期至 */
    private LocalDate expiryDate;
    /** 收货仓库ID（默认中心库=1） */
    private Long warehouseId;
}
