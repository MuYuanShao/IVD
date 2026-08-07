package com.zlth.ivd.settlement.controller;

import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.common.dto.Result;
import com.zlth.ivd.common.util.ExportImportUtil;
import com.zlth.ivd.common.annotation.RequireRole;
import com.zlth.ivd.settlement.entity.SettlementRecord;
import com.zlth.ivd.settlement.service.SettlementService;
import com.zlth.ivd.settlement.vo.ConsumptionSettlementVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/settlement")
@RequiredArgsConstructor
@RequireRole({"SYSTEM_ADMIN", "FINANCE"})
public class SettlementController {

    private final SettlementService settlementService;

    @GetMapping
    public Result<PageVO<SettlementRecord>> list(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String settlementType, @RequestParam(required = false) String status) {
        return Result.success(settlementService.listRecords(page, pageSize, settlementType, status));
    }

    /** 科室签收 + 自动结算（科室人员 / 库管员操作） */
    @PostMapping("/receive")
    @RequireRole({"SYSTEM_ADMIN", "STOREKEEPER", "DEPT_ADMIN"})
    public Result<String> receiveAndSettle(@RequestParam Long requisitionId) {
        settlementService.receiveAndSettleRequisition(requisitionId);
        return Result.success("签收完成，结算记录已自动生成");
    }

    /** 一键批量签收：签收所有待签收申领单 */
    @PostMapping("/receive/batch")
    @RequireRole({"SYSTEM_ADMIN", "STOREKEEPER", "DEPT_ADMIN"})
    public Result<Map<String, Object>> batchReceive() {
        Map<String, Object> result = settlementService.batchReceiveAndSettle();
        return Result.success(result);
    }

    @PostMapping("/requisition")
    public Result<String> settleRequisition(@RequestParam Long requisitionId) {
        settlementService.settleRequisition(requisitionId);
        return Result.success("申领结算成功");
    }

    @PostMapping("/transfer")
    public Result<String> settleTransfer(@RequestParam Long transferRecordId) {
        settlementService.settleTransfer(transferRecordId);
        return Result.success("调拨结算成功");
    }

    @PostMapping("/consumption")
    public Result<String> settleConsumption(@RequestParam Long consumptionRecordId) {
        settlementService.settleConsumption(consumptionRecordId);
        return Result.success("消耗结算成功");
    }

    @PostMapping("/reverse")
    public Result<String> reverse(@RequestParam Long settlementId,
                                  @RequestParam(required = false) String reason) {
        settlementService.reverseSettlement(settlementId, reason);
        return Result.success("冲销成功");
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.success(settlementService.getStats());
    }

    /** 消耗结算明细：消耗数据全量字段展示 */
    @GetMapping("/consumption-details")
    public Result<PageVO<ConsumptionSettlementVO>> consumptionDetails(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long reagentId,
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) Long warehouseId) {
        return Result.success(settlementService.getConsumptionDetails(page, pageSize, reagentId, deviceId, warehouseId));
    }

    // ── 导入导出 ──

    private static final List<String> SETTLEMENT_EXPORT_HEADERS = List.of(
            "结算单号", "结算类型", "来源单号", "批号", "试剂ID", "数量", "单价", "金额", "状态", "结算人", "结算时间", "创建时间"
    );

    @GetMapping("/export")
    public void export(@RequestParam(defaultValue = "xlsx") String format, HttpServletResponse response) {
        PageVO<SettlementRecord> result = settlementService.listRecords(1, 10000, null, null);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SettlementRecord r : result.getRecords()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("结算单号", r.getSettlementNo() != null ? r.getSettlementNo() : "");
            row.put("结算类型", r.getSettlementType() != null ? r.getSettlementType() : "");
            row.put("来源单号", r.getSourceNo() != null ? r.getSourceNo() : "");
            row.put("批号", r.getBatchNo() != null ? r.getBatchNo() : "");
            row.put("试剂ID", r.getReagentId() != null ? r.getReagentId().toString() : "");
            row.put("数量", r.getQuantity() != null ? r.getQuantity().toString() : "");
            row.put("单价", r.getUnitPrice() != null ? r.getUnitPrice().toString() : "");
            row.put("金额", r.getAmount() != null ? r.getAmount().toString() : "");
            row.put("状态", r.getStatus() != null ? r.getStatus() : "");
            row.put("结算人", r.getSettledBy() != null ? r.getSettledBy().toString() : "");
            row.put("结算时间", r.getSettledAt() != null ? r.getSettledAt().toString() : "");
            row.put("创建时间", r.getCreatedAt() != null ? r.getCreatedAt().toString() : "");
            rows.add(row);
        }
        if ("csv".equalsIgnoreCase(format)) {
            ExportImportUtil.exportCsv(response, "结算记录", SETTLEMENT_EXPORT_HEADERS, rows);
        } else {
            ExportImportUtil.exportExcel(response, "结算记录", SETTLEMENT_EXPORT_HEADERS, rows);
        }
    }
}
