package com.zlth.ivd.procurement.service.impl;

import com.zlth.ivd.common.enums.RequisitionStatusEnum;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.procurement.dto.PickAndShipRequest;
import com.zlth.ivd.procurement.dto.ReceiveItemRequest;
import com.zlth.ivd.procurement.entity.PurchaseRequisition;
import com.zlth.ivd.procurement.entity.PurchaseRequisitionItem;
import com.zlth.ivd.procurement.mapper.PurchaseRequisitionMapper;
import com.zlth.ivd.procurement.mapper.PurchaseRequisitionItemMapper;
import com.zlth.ivd.inventory.service.ProcurementInventoryService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class ProcurementSafetyTest {
    @Test void wrongReagentBatchCannotBeDeducted() {
        var requisitions = mock(PurchaseRequisitionMapper.class);
        var items = mock(PurchaseRequisitionItemMapper.class);
        var inventory = mock(ProcurementInventoryService.class);
        var req = new PurchaseRequisition(); req.setId(1L); req.setToWarehouseId(9L); req.setStatus(RequisitionStatusEnum.REVIEWED);
        when(requisitions.selectById(1L)).thenReturn(req);
        var item = new PurchaseRequisitionItem(); item.setId(2L); item.setReagentId(3L); item.setQtyRequested(BigDecimal.TEN);
        when(items.selectList(any())).thenReturn(List.of(item));
        var batch = new com.zlth.ivd.inventory.entity.ReagentBatch(); batch.setId(4L); batch.setReagentId(99L); batch.setStock(BigDecimal.TEN);
        when(inventory.getBatch(4L)).thenReturn(batch);
        var service = new ProcurementServiceImpl(null, requisitions, items, null, null, null, null, inventory, null, null, null, null, null, null);
        var request = new PickAndShipRequest(); var pick = new PickAndShipRequest.PickItem();
        pick.setReqItemId(2L); pick.setSourceBatchId(4L); pick.setPickQty(BigDecimal.ONE); request.setItems(List.of(pick));
        assertThrows(BizException.class, () -> service.pickAndShip(1L, request));
        verify(inventory, never()).updateBatch(any());
        org.junit.jupiter.api.Assertions.assertEquals(BigDecimal.TEN, batch.getStock());
    }

    @Test void partialReceiptKeepsRemainderOpenAndMarksOnlyNewShipment() {
        var requisitions = mock(PurchaseRequisitionMapper.class);
        var items = mock(PurchaseRequisitionItemMapper.class);
        var inventory = mock(ProcurementInventoryService.class);
        var movements = mock(com.zlth.ivd.consumption.service.ProcurementConsumptionService.class);
        var codes = mock(com.zlth.ivd.udi.service.UniqueCodeService.class);
        var req = new PurchaseRequisition(); req.setId(1L); req.setToWarehouseId(9L); req.setStatus(RequisitionStatusEnum.PARTIAL_FULFILLED);
        when(requisitions.selectById(1L)).thenReturn(req);
        var item = new PurchaseRequisitionItem(); item.setQtyRequested(new BigDecimal("5")); item.setQtyFulfilled(new BigDecimal("3"));
        when(items.selectList(any())).thenReturn(List.of(item));
        var transfer = new com.zlth.ivd.consumption.entity.ConsumptionRecord(); transfer.setId(7L); transfer.setReagentId(3L);
        transfer.setBatchNo("LOT"); transfer.setFromWarehouseId(8L); transfer.setWarehouseId(9L); transfer.setQuantity(new BigDecimal("3"));
        when(movements.findUnreceivedTransferRecords(1L)).thenReturn(List.of(transfer));
        var source = new com.zlth.ivd.inventory.entity.ReagentBatch(); source.setId(4L); source.setReagentId(3L); source.setBatchNo("LOT");
        var target = new com.zlth.ivd.inventory.entity.ReagentBatch(); target.setId(5L); target.setStock(BigDecimal.ZERO);
        when(inventory.findBatch(3L,"LOT",8L)).thenReturn(source);
        when(inventory.findBatch(3L,"LOT",9L)).thenReturn(target);
        when(inventory.updateBatch(target)).thenReturn(true);
        var service = spy(new ProcurementServiceImpl(null, requisitions, items, null, null, null, null, inventory, null, movements, null, null, codes, null));
        doReturn(new com.zlth.ivd.procurement.vo.PurchaseRequisitionVO()).when(service).getRequisitionDetail(1L);
        service.receiveRequisition(1L);
        org.junit.jupiter.api.Assertions.assertEquals(RequisitionStatusEnum.PARTIAL_FULFILLED, req.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("3"), target.getStock());
        verify(movements).confirmTransferReceipt(7L,5L);
        when(movements.findUnreceivedTransferRecords(1L)).thenReturn(List.of());
        assertThrows(BizException.class, () -> service.receiveRequisition(1L));
        verify(inventory, times(1)).updateBatch(target);
    }
    @Test void negativePickCannotReachInventory() {
        var requisitions = mock(PurchaseRequisitionMapper.class);
        var items = mock(PurchaseRequisitionItemMapper.class);
        var inventory = mock(ProcurementInventoryService.class);
        var req = new PurchaseRequisition(); req.setId(1L); req.setStatus(RequisitionStatusEnum.REVIEWED);
        when(requisitions.selectById(1L)).thenReturn(req);
        var item = new PurchaseRequisitionItem(); item.setId(2L); item.setReagentId(3L); item.setQtyRequested(BigDecimal.TEN);
        when(items.selectList(any())).thenReturn(List.of(item));
        var service = new ProcurementServiceImpl(null, requisitions, items, null, null, null, null, inventory, null, null, null, null, null, null);
        var request = new PickAndShipRequest(); var pick = new PickAndShipRequest.PickItem();
        pick.setReqItemId(2L); pick.setSourceBatchId(4L); pick.setPickQty(BigDecimal.ONE.negate()); request.setItems(List.of(pick));
        assertThrows(BizException.class, () -> service.pickAndShip(1L, request));
        verifyNoInteractions(inventory);
    }

    @Test void zeroReceiptIsRejectedBeforeLookingUpOrder() {
        var service = new ProcurementServiceImpl(null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        var request = new ReceiveItemRequest(); request.setQtyReceived(BigDecimal.ZERO);
        assertThrows(BizException.class, () -> service.receiveOrder(request));
    }
}
