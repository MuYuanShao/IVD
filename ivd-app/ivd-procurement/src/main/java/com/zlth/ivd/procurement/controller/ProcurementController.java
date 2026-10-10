package com.zlth.ivd.procurement.controller;

import com.zlth.ivd.common.annotation.AuditLog;
import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.common.dto.Result;
import com.zlth.ivd.common.util.ExportImportUtil;
import com.zlth.ivd.procurement.dto.PickAndShipRequest;
import com.zlth.ivd.procurement.dto.ReceiveItemRequest;
import com.zlth.ivd.procurement.entity.*;
import com.zlth.ivd.procurement.vo.PurchaseOrderVO;
import com.zlth.ivd.procurement.vo.PurchaseRequisitionVO;
import com.zlth.ivd.procurement.vo.ShortageOrderVO;
import com.zlth.ivd.procurement.service.ProcurementService;
import com.zlth.ivd.common.annotation.RequireRole;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 采购管理 Controller — 申领/缺货/采购订单/验收 统一入口
 *
 * @author IVD Team
 * @since 2026-07-06
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/procurement")
@RequiredArgsConstructor
public class ProcurementController {

    private final ProcurementService procurementService;

    // ==================== 申领单 ====================

    @GetMapping("/requisitions")
    public Result<PageVO<PurchaseRequisitionVO>> listRequisitions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return Result.success(procurementService.listRequisitions(page, pageSize, status, keyword));
    }

    @GetMapping("/requisitions/{id}")
    public Result<PurchaseRequisitionVO> requisitionDetail(@PathVariable Long id) {
        return Result.success(procurementService.getRequisitionDetail(id));
    }

    /** 申领明细（仅查 purchase_requisition_item 表，不混入总表数据） */
    @GetMapping("/requisitions/{reqId}/items")
    public Result<List<PurchaseRequisitionItem>> requisitionItems(@PathVariable Long reqId) {
        return Result.success(procurementService.getRequisitionItems(reqId));
    }

    @PostMapping("/requisitions")
    @AuditLog(module = "采购管理", action = "创建申领单")
    @RequireRole({"SYSTEM_ADMIN", "PURCHASER"})
    public Result<PurchaseRequisition> createRequisition(@RequestBody PurchaseRequisition req) {
        return Result.success(procurementService.createRequisition(req));
    }

    @GetMapping("/requisitions/stats")
    public Result<Map<String, Long>> requisitionStats() {
        return Result.success(procurementService.getRequisitionStats());
    }

    @PutMapping("/requisitions/{id}/review")
    @AuditLog(module = "采购管理", action = "审核申领单")
    @RequireRole({"SYSTEM_ADMIN", "STOREKEEPER"})
    public Result<PurchaseRequisitionVO> reviewRequisition(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String action = body.getOrDefault("action", "APPROVE");
        String comment = body.getOrDefault("comment", "");
        return Result.success(procurementService.reviewRequisition(id, action, comment));
    }

    @PutMapping("/requisitions/{id}/submit")
    @AuditLog(module = "采购管理", action = "提交申领审批")
    @RequireRole({"SYSTEM_ADMIN", "PURCHASER", "STOREKEEPER"})
    public Result<PurchaseRequisitionVO> submitRequisition(@PathVariable Long id) {
        return Result.success(procurementService.submitRequisition(id));
    }

    @PutMapping("/requisitions/{id}/cancel")
    @AuditLog(module = "采购管理", action = "作废申领单")
    @RequireRole({"SYSTEM_ADMIN", "PURCHASER"})
    public Result<PurchaseRequisitionVO> cancelRequisition(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.getOrDefault("reason", "用户主动作废") : "用户主动作废";
        return Result.success(procurementService.cancelRequisition(id, reason));
    }

    @PostMapping("/requisitions/{id}/pick-and-ship")
    @AuditLog(module = "采购管理", action = "拣货发货")
    @RequireRole({"SYSTEM_ADMIN", "STOREKEEPER"})
    public Result<PurchaseRequisitionVO> pickAndShip(@PathVariable Long id,
                                                     @RequestBody @Valid PickAndShipRequest request) {
        return Result.success(procurementService.pickAndShip(id, request));
    }

    @PutMapping("/requisitions/{id}/receive")
    @AuditLog(module = "采购管理", action = "科室签收")
    @RequireRole({"SYSTEM_ADMIN", "STOREKEEPER"})
    public Result<PurchaseRequisitionVO> receiveRequisition(@PathVariable Long id) {
        return Result.success(procurementService.receiveRequisition(id));
    }

    // ==================== 缺货单 ====================

    @GetMapping("/shortages")
    public Result<PageVO<ShortageOrderVO>> listShortages(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return Result.success(procurementService.listShortages(page, pageSize, status, keyword));
    }

    @GetMapping("/shortages/{id}")
    public Result<ShortageOrderVO> shortageDetail(@PathVariable Long id) {
        return Result.success(procurementService.getShortageDetail(id));
    }

    /** 缺货明细（仅查 shortage_order_item 表） */
    @GetMapping("/shortages/{id}/items")
    public Result<List<ShortageOrderItem>> shortageItems(@PathVariable Long id) {
        return Result.success(procurementService.getShortageItems(id));
    }

    @PostMapping("/shortages")
    @AuditLog(module = "采购管理", action = "生成缺货单")
    @RequireRole({"SYSTEM_ADMIN", "PURCHASER"})
    public Result<ShortageOrder> createShortage(@RequestParam Long reqId) {
        return Result.success(procurementService.createShortageFromRequisition(reqId));
    }

    @GetMapping("/shortages/stats")
    public Result<Map<String, Long>> shortageStats() {
        return Result.success(procurementService.getShortageStats());
    }

    @PostMapping("/shortages/stats")
    public Result<Map<String, Long>> shortageStatsPost() {
        return Result.success(procurementService.getShortageStats());
    }

    // ==================== 采购订单 ====================

    @GetMapping("/orders")
    public Result<PageVO<PurchaseOrderVO>> listOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return Result.success(procurementService.listOrders(page, pageSize, status, keyword));
    }

    @GetMapping("/orders/{id}")
    public Result<PurchaseOrderVO> orderDetail(@PathVariable Long id) {
        return Result.success(procurementService.getOrderDetail(id));
    }

    @PostMapping("/orders/from-shortage")
    @AuditLog(module = "采购管理", action = "从缺货单生成采购订单")
    @RequireRole({"SYSTEM_ADMIN", "PURCHASER"})
    public Result<PurchaseOrder> createFromShortage(@RequestBody com.zlth.ivd.procurement.dto.CreateOrderFromShortageRequest request) {
        return Result.success(procurementService.createOrderFromShortage(
                request.getShortageId(), request.getSupplierId(),
                request.getExpectedDeliveryDate(), request.getSettlementMode(), request.getRemark()));
    }

    @PostMapping("/orders/manual")
    @AuditLog(module = "采购管理", action = "手动创建采购订单")
    @RequireRole({"SYSTEM_ADMIN", "PURCHASER"})
    public Result<List<PurchaseOrder>> createManualOrder(@RequestBody @Valid com.zlth.ivd.procurement.dto.CreateManualOrderRequest request) {
        return Result.success(procurementService.createManualOrder(request));
    }

    @GetMapping("/orders/stats")
    public Result<Map<String, Long>> orderStats() {
        return Result.success(procurementService.getOrderStats());
    }

    // ==================== 智能补货生成订单 ====================

    @PostMapping("/orders/from-replenish")
    @AuditLog(module = "采购管理", action = "智能补货生成采购订单")
    @RequireRole({"SYSTEM_ADMIN", "PURCHASER"})
    public Result<List<PurchaseOrder>> createFromReplenish(@RequestBody List<Map<String, Object>> items) {
        return Result.success(procurementService.createOrdersFromReplenish(items));
    }

    // ==================== 验收 ====================

    @PostMapping("/receive")
    @AuditLog(module = "采购管理", action = "验收入库")
    @RequireRole({"SYSTEM_ADMIN", "STOREKEEPER"})
    public Result<String> receiveOrder(@RequestBody @Valid ReceiveItemRequest request) {
        procurementService.receiveOrder(request);
        return Result.success("验收成功");
    }

    @PostMapping("/receive/direct")
    @AuditLog(module = "采购管理", action = "人工验收直收（无采购订单）")
    @RequireRole({"SYSTEM_ADMIN", "STOREKEEPER"})
    public Result<Map<String, Object>> receiveDirect(@RequestBody com.zlth.ivd.procurement.dto.DirectReceiveRequest request) {
        return Result.success(procurementService.receiveDirect(request));
    }

    // ==================== 科室库请领记录 ====================

    @GetMapping("/dept/requisitions")
    public Result<PageVO<Map<String, Object>>> listDeptRequisitions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long deptId) {
        return Result.success(procurementService.listDeptRequisitions(page, pageSize, deptId));
    }

    // ==================== 导入导出 ====================

    private static final List<String> ORDER_EXPORT_HEADERS = List.of(
            "订单编号", "供应商ID", "来源类型", "总金额", "状态", "下单日期", "预计到货", "创建时间"
    );

    @GetMapping("/orders/export")
    public void exportOrders(@RequestParam(defaultValue = "xlsx") String format, HttpServletResponse response) {
        PageVO<PurchaseOrderVO> result = procurementService.listOrders(1, 10000, null, null);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PurchaseOrderVO po : result.getRecords()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("订单编号", po.getPoNo() != null ? po.getPoNo() : "");
            row.put("供应商ID", po.getSupplierId() != null ? po.getSupplierId().toString() : "");
            row.put("来源类型", po.getSourceType() != null ? po.getSourceType() : "");
            row.put("总金额", po.getTotalAmount() != null ? po.getTotalAmount().toString() : "");
            row.put("状态", po.getStatus() != null ? po.getStatus().getLabel() : "");
            row.put("下单日期", po.getOrderDate() != null ? po.getOrderDate().toString() : "");
            row.put("预计到货", po.getExpectedDeliveryDate() != null ? po.getExpectedDeliveryDate().toString() : "");
            row.put("创建时间", po.getCreatedAt() != null ? po.getCreatedAt().toString() : "");
            rows.add(row);
        }
        if ("csv".equalsIgnoreCase(format)) {
            ExportImportUtil.exportCsv(response, "采购订单", ORDER_EXPORT_HEADERS, rows);
        } else {
            ExportImportUtil.exportExcel(response, "采购订单", ORDER_EXPORT_HEADERS, rows);
        }
    }

    // ==================== 智能补货 ====================

    @GetMapping("/smart-replenish")
    public Result<List<Map<String, Object>>> smartReplenish(@RequestParam(defaultValue = "30") int days) {
        return Result.success(procurementService.getSmartReplenishSuggestions(days));
    }
}
