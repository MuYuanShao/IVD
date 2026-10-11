package com.zlth.ivd.common.event;
import java.math.BigDecimal;
/** Inventory publishes its completed movement; consumption owns the movement row. */
public record InventoryTransferRecordedEvent(Long recordId,Long batchId,Long purchaseOrderItemId,String settlementMode,
        String batchNo,Long reagentId,String materialType,BigDecimal quantity,String unit,BigDecimal unitPrice,
        BigDecimal amount,Long targetWarehouseId,Long sourceWarehouseId,String remark) {}
