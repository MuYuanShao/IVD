package com.zlth.ivd.inventory.service;

import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.inventory.entity.*;
import com.zlth.ivd.inventory.vo.ReagentBatchVO;
import java.util.List;
import java.util.Map;

public interface InventoryService {
    PageVO<ReagentBatchVO> listBatches(int page, int pageSize, String keyword, String status, Long warehouseId,
                                      String hisCode, String reagentName, String distributor, String manufacturer, String registrationCertNo,
                                      Integer level);
    PageVO<Warehouse> listWarehouses(int page, int pageSize);
    List<Map<String, Object>> getFefoRecommendations(Long reagentId, Long warehouseId);
    List<Map<String, Object>> getExpiryAlerts(int days, Long warehouseId);
    Map<String, Long> getStats(Long warehouseId, Integer level);
    void transferBatch(Long batchId, Long targetWarehouseId, java.math.BigDecimal qty, String urgency);
    Map<String, Object> traceUdi(String udiDi, String lotNo);
    List<PatientAssociation> getTracePatients(String batchNo);

    /** 查询试剂的所有 UDI 数据：试剂信息 + 全部批次的 UDI-DI / UDI-PI / 批号 */
    Map<String, Object> getReagentUdiData(Long reagentId);

    /** 查询单个唯一码的追溯信息 */
    Map<String, Object> getUniqueCodeTrace(String uniqueCode);

    /** 科室库库存列表（科室级 warehouse + 下属组库） */
    PageVO<Map<String, Object>> listDeptInventory(int page, int pageSize, Long deptId, String keyword,
                                                   String hisCode, String reagentName, String distributor, String manufacturer, String registrationCertNo);
}
