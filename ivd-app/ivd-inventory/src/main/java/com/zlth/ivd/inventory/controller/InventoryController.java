package com.zlth.ivd.inventory.controller;

import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.common.dto.Result;
import com.zlth.ivd.common.util.ExportImportUtil;
import com.zlth.ivd.inventory.entity.*;
import com.zlth.ivd.inventory.vo.ReagentBatchVO;
import com.zlth.ivd.inventory.service.InventoryService;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private static final Logger log = LoggerFactory.getLogger(InventoryController.class);

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public Result<PageVO<ReagentBatchVO>> listBatches(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) String status,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String hisCode, @RequestParam(required = false) String reagentName,
            @RequestParam(required = false) String distributor, @RequestParam(required = false) String manufacturer,
            @RequestParam(required = false) String registrationCertNo) {
        return Result.success(inventoryService.listBatches(page, pageSize, keyword, status, warehouseId,
                hisCode, reagentName, distributor, manufacturer, registrationCertNo));
    }

    @GetMapping("/warehouses")
    public Result<PageVO<Warehouse>> listWarehouses(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "50") int pageSize) {
        return Result.success(inventoryService.listWarehouses(page, pageSize));
    }

    @GetMapping("/fefo")
    public Result<List<ReagentBatch>> fefoRecommendations(@RequestParam(required = false) Long reagentId, @RequestParam(required = false) Long warehouseId) {
        return Result.success(inventoryService.getFefoRecommendations(reagentId, warehouseId));
    }

    @GetMapping("/expiry-alerts")
    public Result<List<ReagentBatch>> expiryAlerts(@RequestParam(defaultValue = "90") int days,
                                                    @RequestParam(required = false) Long warehouseId) {
        return Result.success(inventoryService.getExpiryAlerts(days, warehouseId));
    }

    @GetMapping("/stats")
    public Result<Map<String, Long>> stats(@RequestParam(required = false) Long warehouseId) {
        return Result.success(inventoryService.getStats(warehouseId));
    }

    @PostMapping("/transfer")
    public Result<String> transfer(@RequestParam Long batchId, @RequestParam Long targetWarehouseId,
                                   @RequestParam BigDecimal qty, @RequestParam(defaultValue = "NORMAL") String urgency) {
        inventoryService.transferBatch(batchId, targetWarehouseId, qty, urgency);
        return Result.success("调拨成功");
    }

    @GetMapping("/trace")
    public Result<Map<String, Object>> trace(@RequestParam String udiDi, @RequestParam(required = false) String lotNo) {
        return Result.success(inventoryService.traceUdi(udiDi, lotNo));
    }

    /** 患者关联追溯 */
    @GetMapping("/trace/patients/{batchNo}")
    public Result<List<PatientAssociation>> tracePatients(@PathVariable String batchNo) {
        return Result.success(inventoryService.getTracePatients(batchNo));
    }

    /** 科室库库存列表（科室级 warehouse + 下属组库） */
    @GetMapping("/dept")
    public Result<PageVO<Map<String, Object>>> listDeptInventory(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long deptId, @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String hisCode, @RequestParam(required = false) String reagentName,
            @RequestParam(required = false) String distributor, @RequestParam(required = false) String manufacturer,
            @RequestParam(required = false) String registrationCertNo) {
        return Result.success(inventoryService.listDeptInventory(page, pageSize, deptId, keyword,
                hisCode, reagentName, distributor, manufacturer, registrationCertNo));
    }

    /** 科室库请领记录（一期占位，后续接入科室二级库） */
    @GetMapping("/dept/requisitions")
    public Result<PageVO<Map<String, Object>>> listDeptRequisitions(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long deptId) {
        return Result.success(PageVO.empty(page, pageSize));
    }

    // ── 导入导出 ──

    private static final List<String> BATCH_EXPORT_HEADERS = List.of(
            "试剂ID", "批号", "UDI-DI", "库存量", "效期", "库位ID", "状态", "创建时间"
    );

    @GetMapping("/export")
    public void export(@RequestParam(defaultValue = "xlsx") String format,
                       @RequestParam(required = false) Long warehouseId,
                       HttpServletResponse response) {
        PageVO<ReagentBatchVO> result = inventoryService.listBatches(1, 10000, null, null, warehouseId, null, null, null, null, null);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ReagentBatchVO batch : result.getRecords()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("试剂ID", batch.getReagentId() != null ? batch.getReagentId().toString() : "");
            row.put("批号", batch.getBatchNo() != null ? batch.getBatchNo() : "");
            row.put("UDI-DI", batch.getUdiDi() != null ? batch.getUdiDi() : "");
            row.put("库存量", batch.getStock() != null ? batch.getStock().toString() : "");
            row.put("效期", batch.getExpiryDate() != null ? batch.getExpiryDate().toString() : "");
            row.put("库位ID", batch.getWarehouseId() != null ? batch.getWarehouseId().toString() : "");
            row.put("状态", batch.getUdiStatus() != null ? batch.getUdiStatus().getLabel() : "");
            row.put("创建时间", batch.getCreatedAt() != null ? batch.getCreatedAt().toString() : "");
            rows.add(row);
        }
        if ("csv".equalsIgnoreCase(format)) {
            ExportImportUtil.exportCsv(response, "库存批次", BATCH_EXPORT_HEADERS, rows);
        } else {
            ExportImportUtil.exportExcel(response, "库存批次", BATCH_EXPORT_HEADERS, rows);
        }
    }
}
