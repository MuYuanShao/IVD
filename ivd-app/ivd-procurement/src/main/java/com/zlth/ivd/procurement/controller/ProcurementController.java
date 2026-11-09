package com.zlth.ivd.procurement.controller;

import com.zlth.ivd.common.annotation.AuditLog;
import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.common.dto.Result;
import com.zlth.ivd.common.service.StockCommandService;
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
    private final com.zlth.ivd.common.service.RequisitionReceiptService receiptService;
    private final StockCommandService commands;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private com.zlth.ivd.procurement.service.PurchasePlanService purchasePlans;

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
    @RequireRole({"SYSTEM_ADMIN", "PURCHASER", "LAB_TECH", "LAB_DIRECTOR"})
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
    @RequireRole({"SYSTEM_ADMIN", "PURCHASER", "STOREKEEPER", "LAB_TECH", "LAB_DIRECTOR"})
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
        receiptService.receiveAndSettleRequisition(id);
        return Result.success(procurementService.getRequisitionDetail(id));
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
    @AuditLog(module = "采购管理", action = "从缺货单生成采购计划")
    @RequireRole({"SYSTEM_ADMIN", "PURCHASER"})
    public Result<com.zlth.ivd.procurement.vo.PurchasePlanVO> createFromShortage(@Valid @RequestBody com.zlth.ivd.procurement.dto.CreateOrderFromShortageRequest request) {
        var plan=new com.zlth.ivd.procurement.dto.CreatePurchasePlanRequest();
        plan.setSourceType("SHORTAGE");plan.setShortageId(request.getShortageId());plan.setSupplierId(request.getSupplierId());
        plan.setExpectedDeliveryDate(parseDate(request.getExpectedDeliveryDate()));
        plan.setSettlementMode(request.getSettlementMode());plan.setRemark(request.getRemark());
        return Result.success(plans().create(plan));
    }

    @PostMapping("/orders/manual")
    @AuditLog(module = "采购管理", action = "手动创建采购计划")
    @RequireRole({"SYSTEM_ADMIN", "PURCHASER"})
    public Result<com.zlth.ivd.procurement.vo.PurchasePlanVO> createManualOrder(@RequestBody @Valid com.zlth.ivd.procurement.dto.CreateManualOrderRequest request) {
        var plan=new com.zlth.ivd.procurement.dto.CreatePurchasePlanRequest();
        plan.setSourceType("MANUAL");plan.setSupplierId(request.getSupplierId());plan.setSettlementMode(request.getSettlementMode());
        plan.setRemark(request.getRemark());plan.setOrderDate(parseDate(request.getOrderDate()));
        plan.setExpectedDeliveryDate(parseDate(request.getExpectedDeliveryDate()));
        if(request.getItems()!=null)plan.setItems(request.getItems().stream().map(row->{
            if(row==null)throw new com.zlth.ivd.common.exception.BizException(40001,"采购计划明细不能为空");
            var item=new com.zlth.ivd.procurement.dto.CreatePurchasePlanRequest.Item();
            item.setReagentId(row.getReagentId());item.setSupplierId(row.getSupplierId());item.setQuantity(row.getQuantity());return item;
        }).toList());
        return Result.success(plans().create(plan));
    }

    @GetMapping("/orders/stats")
    public Result<Map<String, Long>> orderStats() {
        return Result.success(procurementService.getOrderStats());
    }

    // ==================== 智能补货生成订单 ====================

    @PostMapping("/orders/from-replenish")
    @AuditLog(module = "采购管理", action = "智能补货生成采购计划")
    @RequireRole({"SYSTEM_ADMIN", "PURCHASER"})
    public Result<com.zlth.ivd.procurement.vo.PurchasePlanVO> createFromReplenish(@RequestBody List<Map<String, Object>> items) {
        if(items==null || items.isEmpty() || items.size()>500)throw new com.zlth.ivd.common.exception.BizException(40001,"请选择1至500条补货建议");
        var plan=new com.zlth.ivd.procurement.dto.CreatePurchasePlanRequest();plan.setSourceType("SMART_REPLENISH");
        try {plan.setSuggestions(items.stream().map(row->{
            if(row==null)throw new IllegalArgumentException("null suggestion");
            var item=new com.zlth.ivd.procurement.dto.CreatePurchasePlanRequest.Suggestion();
            item.setId(Long.valueOf(Objects.toString(row.get("id"),"")));item.setReagentId(Long.valueOf(Objects.toString(row.get("reagentId"),"")));
            item.setSuggestedQty(new java.math.BigDecimal(Objects.toString(row.get("suggestedQty"),"")));
            if(row.get("supplierId")!=null)item.setSupplierId(Integer.valueOf(row.get("supplierId").toString()));return item;
        }).toList());}catch(IllegalArgumentException error){throw new com.zlth.ivd.common.exception.BizException(40001,"补货计划参数格式无效");}
        return Result.success(plans().create(plan));
    }

    // ==================== 验收 ====================

    @PostMapping("/receive")
    @AuditLog(module = "采购管理", action = "验收入库")
    @RequireRole({"SYSTEM_ADMIN", "STOREKEEPER"})
    public Result<String> receiveOrder(@RequestBody @Valid ReceiveItemRequest request,
            @RequestHeader(value=StockCommandService.HEADER,required=false) String requestKey) {
        return Result.success(commands.execute(StockCommandService.Operation.ORDER_RECEIVE,requestKey,request,StockCommandService.TEXT,()->{
            procurementService.receiveOrder(request);
            return "验收成功";
        }));
    }

    @PostMapping("/receive/direct")
    @AuditLog(module = "采购管理", action = "人工验收直收（无采购订单）")
    @RequireRole({"SYSTEM_ADMIN", "STOREKEEPER"})
    public Result<Map<String, Object>> receiveDirect(@RequestBody @Valid com.zlth.ivd.procurement.dto.DirectReceiveRequest request,
            @RequestHeader(value=StockCommandService.HEADER,required=false) String requestKey) {
        return Result.success(commands.execute(StockCommandService.Operation.DIRECT_RECEIVE,requestKey,request,StockCommandService.MAP,
                ()->procurementService.receiveDirect(request)));
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
    @RequireRole({"SYSTEM_ADMIN","PURCHASER","STOREKEEPER","FINANCE"})
    public void exportOrders(@RequestParam(defaultValue = "xlsx") String format,
            @RequestParam(required=false) String status,@RequestParam(required=false) String keyword,HttpServletResponse response) {
        List<PurchaseOrderVO> result=exportOrderRows(status,keyword);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PurchaseOrderVO po : result) {
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

    @GetMapping("/shortages/export")
    @RequireRole({"SYSTEM_ADMIN","PURCHASER","STOREKEEPER","FINANCE"})
    public void exportShortages(@RequestParam(defaultValue="xlsx") String format,
            @RequestParam(required=false) String status,@RequestParam(required=false) String keyword,HttpServletResponse response) {
        var first=procurementService.listShortages(1,1000,status,keyword);
        requireExportSize(first.getTotal());
        List<ShortageOrderVO> values=new ArrayList<>(first.getRecords());
        for(int page=2;values.size()<first.getTotal();page++) {
            var next=procurementService.listShortages(page,1000,status,keyword);
            if(next.getTotal()!=first.getTotal() || next.getRecords().isEmpty())
                throw new com.zlth.ivd.common.exception.BizException(40900,"导出期间数据变化，请重试");
            values.addAll(next.getRecords());
        }
        if(values.stream().map(ShortageOrderVO::getId).distinct().count()!=values.size())
            throw new com.zlth.ivd.common.exception.BizException(40900,"导出期间数据变化，请重试");
        var headers=List.of("缺货单号","来源申领单","科室","缺货数量","预计金额","状态","创建时间");
        List<Map<String,Object>> rows=new ArrayList<>();
        for(var value:values) {
            Map<String,Object> row=new LinkedHashMap<>();
            row.put("缺货单号",value.getShortageNo());row.put("来源申领单",value.getSourceReqNo());
            row.put("科室",value.getDeptName());row.put("缺货数量",value.getShortageQty());
            row.put("预计金额",value.getEstimatedAmount());row.put("状态",value.getStatus()==null?"":value.getStatus().getLabel());
            row.put("创建时间",value.getCreatedAt()==null?"":value.getCreatedAt().toString());rows.add(row);
        }
        if("csv".equalsIgnoreCase(format))ExportImportUtil.exportCsv(response,"缺货单",headers,rows);
        else ExportImportUtil.exportExcel(response,"缺货单",headers,rows);
    }

    private List<PurchaseOrderVO> exportOrderRows(String status,String keyword) {
        var first=procurementService.listOrders(1,1000,status,keyword);requireExportSize(first.getTotal());
        List<PurchaseOrderVO> rows=new ArrayList<>(first.getRecords());
        for(int page=2;rows.size()<first.getTotal();page++) {
            var next=procurementService.listOrders(page,1000,status,keyword);
            if(next.getTotal()!=first.getTotal() || next.getRecords().isEmpty())
                throw new com.zlth.ivd.common.exception.BizException(40900,"导出期间数据变化，请重试");
            rows.addAll(next.getRecords());
        }
        if(rows.stream().map(PurchaseOrderVO::getId).distinct().count()!=rows.size())
            throw new com.zlth.ivd.common.exception.BizException(40900,"导出期间数据变化，请重试");
        return rows;
    }

    private void requireExportSize(long total) {
        if(total>10000)throw new com.zlth.ivd.common.exception.BizException(40001,"导出超过10000条，请缩小筛选范围");
    }

    // ==================== 智能补货 ====================

    @GetMapping("/smart-replenish")
    @RequireRole({"SYSTEM_ADMIN","PURCHASER","STOREKEEPER","LAB_DIRECTOR"})
    public Result<List<Map<String, Object>>> smartReplenish(@RequestParam(defaultValue = "30") int days) {
        return Result.success(procurementService.getSmartReplenishSuggestions(days));
    }
    @PostMapping("/smart-replenish/recalculate")
    @RequireRole({"SYSTEM_ADMIN","PURCHASER","STOREKEEPER"})
    @AuditLog(module="采购计划",action="重算补货建议")
    public Result<List<Map<String,Object>>> recalculate(@RequestParam(defaultValue="30") int days){
        return Result.success(procurementService.recalculateSmartReplenishSuggestions(days));
    }

    private com.zlth.ivd.procurement.service.PurchasePlanService plans(){
        if(purchasePlans==null)throw new com.zlth.ivd.common.exception.BizException(40900,"采购计划服务不可用，不能绕过审批直接建单");
        return purchasePlans;
    }
    private java.time.LocalDate parseDate(String value){
        if(value==null || value.isBlank())return null;
        try{return java.time.LocalDate.parse(value);}
        catch(java.time.format.DateTimeParseException invalid){throw new com.zlth.ivd.common.exception.BizException(40001,"日期格式须为YYYY-MM-DD");}
    }
}
