package com.zlth.ivd.consumption.controller;

import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.common.dto.Result;
import com.zlth.ivd.common.util.ExportImportUtil;
import com.zlth.ivd.consumption.entity.ConsumptionRecord;
import com.zlth.ivd.consumption.service.ConsumptionService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/consumption")
@RequiredArgsConstructor
public class ConsumptionController {

    private final ConsumptionService consumptionService;

    @GetMapping
    public Result<PageVO<ConsumptionRecord>> list(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String recordType, @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) Long reagentId) {
        return Result.success(consumptionService.listRecords(page, pageSize, recordType, deviceId, reagentId));
    }

    @PostMapping("/consume")
    public Result<String> consume(@RequestParam Long batchId, @RequestParam Long deviceId,
                                  @RequestParam BigDecimal qty, @RequestParam(required = false) String purpose,
                                  @RequestParam(required = false) String remark) {
        consumptionService.recordConsumption(batchId, deviceId, qty, purpose, remark);
        return Result.success("消耗记录成功");
    }

    @PostMapping("/inbound")
    public Result<String> inbound(@RequestParam Long batchId, @RequestParam BigDecimal qty,
                                  @RequestParam Long warehouseId) {
        consumptionService.recordInbound(batchId, qty, warehouseId);
        return Result.success("入库记录成功");
    }

    @PostMapping("/batch-consume")
    public Result<String> batchConsume(@RequestParam String batchNo, @RequestParam Long deviceId,
                                       @RequestParam BigDecimal qty) {
        consumptionService.batchConsume(batchNo, deviceId, qty);
        return Result.success("批量核销成功");
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats(@RequestParam(required = false) Long deviceId) {
        return Result.success(consumptionService.getStats(deviceId));
    }

    // ── 导入导出 ──

    private static final List<String> CONSUMPTION_EXPORT_HEADERS = List.of(
            "类型", "批号", "试剂ID", "设备ID", "数量", "单价", "金额", "用途", "库位ID", "操作人", "发生时间", "创建时间"
    );

    @GetMapping("/export")
    public void export(@RequestParam(defaultValue = "xlsx") String format,
                       @RequestParam(required = false) Long deviceId,
                       HttpServletResponse response) {
        PageVO<ConsumptionRecord> result = consumptionService.listRecords(1, 10000, null, deviceId, null);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ConsumptionRecord r : result.getRecords()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("类型", r.getRecordType() != null ? r.getRecordType() : "");
            row.put("批号", r.getBatchNo() != null ? r.getBatchNo() : "");
            row.put("试剂ID", r.getReagentId() != null ? r.getReagentId().toString() : "");
            row.put("设备ID", r.getDeviceId() != null ? r.getDeviceId().toString() : "");
            row.put("数量", r.getQuantity() != null ? r.getQuantity().toString() : "");
            row.put("单价", r.getUnitPrice() != null ? r.getUnitPrice().toString() : "");
            row.put("金额", r.getAmount() != null ? r.getAmount().toString() : "");
            row.put("用途", r.getPurpose() != null ? r.getPurpose() : "");
            row.put("库位ID", r.getWarehouseId() != null ? r.getWarehouseId().toString() : "");
            row.put("操作人", r.getOperatorId() != null ? r.getOperatorId().toString() : "");
            row.put("发生时间", r.getOccurredAt() != null ? r.getOccurredAt().toString() : "");
            row.put("创建时间", r.getCreatedAt() != null ? r.getCreatedAt().toString() : "");
            rows.add(row);
        }
        if ("csv".equalsIgnoreCase(format)) {
            ExportImportUtil.exportCsv(response, "消耗记录", CONSUMPTION_EXPORT_HEADERS, rows);
        } else {
            ExportImportUtil.exportExcel(response, "消耗记录", CONSUMPTION_EXPORT_HEADERS, rows);
        }
    }
}
