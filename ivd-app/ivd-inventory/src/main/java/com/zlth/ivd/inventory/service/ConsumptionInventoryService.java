package com.zlth.ivd.inventory.service;
import com.zlth.ivd.inventory.entity.*;
import java.math.BigDecimal;
import java.util.*;
/** Inventory-owned operations for the consumption module. */
public interface ConsumptionInventoryService {
    ReagentBatch getBatch(Long id);
    ReagentBatch consumeStock(Long id,BigDecimal quantity);
    ReagentBatch addManualStock(Long id,BigDecimal quantity,Long warehouseId);
    Device getDevice(Long id);
    List<Device> getDevices(Collection<Long> ids);
    List<ReagentBatch> availableByBatchNo(String batchNo);
    List<ReagentBatch> availableForDevice(String batchNo,Long warehouseId,BigDecimal quantity);
    long activeDeviceCount();
    long openLoadCount();
}
