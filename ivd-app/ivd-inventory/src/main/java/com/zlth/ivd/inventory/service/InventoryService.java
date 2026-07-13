package com.zlth.ivd.inventory.service;

import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.inventory.entity.*;
import com.zlth.ivd.inventory.vo.ReagentBatchVO;
import java.util.List;
import java.util.Map;

public interface InventoryService {
    PageVO<ReagentBatchVO> listBatches(int page, int pageSize, String keyword, String status, Long warehouseId);
    PageVO<Warehouse> listWarehouses(int page, int pageSize);
    List<ReagentBatch> getFefoRecommendations(Long reagentId, Long warehouseId);
    List<ReagentBatch> getExpiryAlerts(int days, Long warehouseId);
    Map<String, Long> getStats(Long warehouseId);
    void transferBatch(Long batchId, Long targetWarehouseId, java.math.BigDecimal qty, String urgency);
    Map<String, Object> traceUdi(String udiDi, String lotNo);
    List<PatientAssociation> getTracePatients(String batchNo);

    /** 科室库库存列表（科室级 warehouse + 下属组库） */
    PageVO<Map<String, Object>> listDeptInventory(int page, int pageSize, Long deptId, String keyword);
}
