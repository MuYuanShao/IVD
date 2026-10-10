package com.zlth.ivd.inventory.service;

import com.zlth.ivd.inventory.entity.ReagentBatch;
import com.zlth.ivd.inventory.entity.Warehouse;
import java.util.List;

/** 采购模块可使用的库存领域契约。 */
public interface ProcurementInventoryService {
    Warehouse findActiveDeptWarehouse(Long deptId);
    ReagentBatch findBatch(Long reagentId, String batchNo, Long warehouseId);
    ReagentBatch getBatch(Long batchId);
    boolean updateBatch(ReagentBatch batch);
    void createBatch(ReagentBatch batch);
    List<ReagentBatch> findInStockBatches(Long reagentId, Long warehouseId);
    List<ReagentBatch> listAllBatches();
}
