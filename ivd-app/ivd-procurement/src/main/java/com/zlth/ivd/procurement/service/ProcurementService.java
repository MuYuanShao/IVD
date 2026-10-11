package com.zlth.ivd.procurement.service;

import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.procurement.dto.CreateManualOrderRequest;
import com.zlth.ivd.procurement.dto.DirectReceiveRequest;
import com.zlth.ivd.procurement.dto.PickAndShipRequest;
import com.zlth.ivd.procurement.dto.ReceiveItemRequest;
import com.zlth.ivd.procurement.entity.*;
import com.zlth.ivd.procurement.vo.PurchaseOrderVO;
import com.zlth.ivd.procurement.vo.PurchaseRequisitionVO;
import com.zlth.ivd.procurement.vo.ShortageOrderVO;
import java.util.List;
import java.util.Map;

/**
 * 采购管理服务接口 — 统一管理申领/缺货/采购订单/验收
 *
 * @author IVD Team
 * @since 2026-07-06
 */
public interface ProcurementService {
    PurchaseRequisition lockRequisitionForSettlement(Long id);
    List<PurchaseRequisition> pendingReceipts();
    List<com.zlth.ivd.consumption.entity.ConsumptionRecord> receivedTransfers(Long requisitionId);

    // ── 申领单 ──
    PageVO<PurchaseRequisitionVO> listRequisitions(int page, int pageSize, String status, String keyword);
    PurchaseRequisition createRequisition(PurchaseRequisition req);
    PurchaseRequisitionVO getRequisitionDetail(Long id);
    List<PurchaseRequisitionItem> getRequisitionItems(Long reqId);
    PurchaseRequisitionVO reviewRequisition(Long id, String action, String comment);
    PurchaseRequisitionVO submitRequisition(Long id);
    PurchaseRequisitionVO cancelRequisition(Long id, String reason);

    // ── 拣货发货 ──
    PurchaseRequisitionVO pickAndShip(Long requisitionId, PickAndShipRequest request);

    // ── 科室签收 ──
    PurchaseRequisitionVO receiveRequisition(Long id);

    // ── 缺货单 ──
    PageVO<ShortageOrderVO> listShortages(int page, int pageSize, String status, String keyword);
    ShortageOrderVO getShortageDetail(Long id);
    List<ShortageOrderItem> getShortageItems(Long shortageId);
    ShortageOrder createShortageFromRequisition(Long reqId);

    // ── 采购订单 ──
    PageVO<PurchaseOrderVO> listOrders(int page, int pageSize, String status, String keyword);
    PurchaseOrderVO getOrderDetail(Long id);
    PurchaseOrder createOrderFromShortage(Long shortageId, Integer supplierId, String expectedDeliveryDate, String settlementMode, String remark);
    List<PurchaseOrder> createManualOrder(CreateManualOrderRequest request);

    // ── 验收 ──
    void receiveOrder(ReceiveItemRequest request);

    // ── 统计 ──
    Map<String, Long> getRequisitionStats();
    Map<String, Long> getShortageStats();
    Map<String, Long> getOrderStats();

    // ── 科室库请领记录 ──
    PageVO<Map<String, Object>> listDeptRequisitions(int page, int pageSize, Long deptId);

    // ── 人工验收直收 ──
    Map<String, Object> receiveDirect(DirectReceiveRequest request);

    // ── 智能补货建议 ──
    List<Map<String, Object>> getSmartReplenishSuggestions(int days);
    List<PurchaseOrder> createOrdersFromReplenish(List<Map<String, Object>> items);
}
