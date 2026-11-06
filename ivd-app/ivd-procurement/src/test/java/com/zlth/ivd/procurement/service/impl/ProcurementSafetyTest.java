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
    @Test void replenishmentRequiresApprovedCatalogAndActiveSupplier() {
        var orders = mock(com.zlth.ivd.procurement.mapper.PurchaseOrderMapper.class);
        var suggestions = mock(com.zlth.ivd.procurement.mapper.SmartReplenishSuggestionMapper.class);
        var catalogs = mock(com.zlth.ivd.catalog.service.CatalogReferenceService.class);
        var suppliers = mock(com.zlth.ivd.license.service.SupplierReferenceService.class);
        var suggestion = new com.zlth.ivd.procurement.entity.SmartReplenishSuggestion();
        suggestion.setReagentId(3L);
        suggestion.setStatus("PENDING");
        when(suggestions.selectOne(any())).thenReturn(suggestion);
        var service = new ProcurementServiceImpl(null, null, null, null, null, orders, null, null, catalogs, null, suggestions, null, null, suppliers);
        var row = java.util.Map.<String, Object>of("id", "1", "reagentId", "3", "suggestedQty", "2", "supplierId", "4");
        assertThrows(BizException.class, () -> service.createOrdersFromReplenish(List.of(row)));
        var catalog = new com.zlth.ivd.catalog.entity.ReagentCatalog();
        catalog.setApprovalStatus(com.zlth.ivd.common.enums.ApprovalStatusEnum.APPROVED);
        catalog.setMaterialType(com.zlth.ivd.common.enums.MaterialTypeEnum.REAGENT);
        catalog.setPricePerUnit(BigDecimal.ONE);
        when(catalogs.lockForBusiness(3L)).thenReturn(catalog);
        var supplier = new com.zlth.ivd.license.entity.Supplier();
        supplier.setIsActive(false);
        when(suppliers.findById(4L)).thenReturn(supplier);
        assertThrows(BizException.class, () -> service.createOrdersFromReplenish(List.of(row)));
        verify(suppliers).findById(4L);
        verify(orders, never()).insert(any(com.zlth.ivd.procurement.entity.PurchaseOrder.class));
        verify(orders, never()).updateById(any(com.zlth.ivd.procurement.entity.PurchaseOrder.class));
    }
    @Test void replenishmentRejectsProcessedOrMismatchedSuggestion() {
        var orders = mock(com.zlth.ivd.procurement.mapper.PurchaseOrderMapper.class);
        var suggestions = mock(com.zlth.ivd.procurement.mapper.SmartReplenishSuggestionMapper.class);
        var suggestion = new com.zlth.ivd.procurement.entity.SmartReplenishSuggestion();
        suggestion.setReagentId(9L);
        suggestion.setStatus("GENERATED");
        when(suggestions.selectOne(any())).thenReturn(suggestion);
        var service = new ProcurementServiceImpl(null, null, null, null, null, orders, null, null, null, null, suggestions, null, null, null);
        var row = java.util.Map.<String, Object>of("id", "1", "reagentId", "3", "suggestedQty", "2");
        assertThrows(BizException.class, () -> service.createOrdersFromReplenish(List.of(row)));
        suggestion.setStatus("PENDING");
        assertThrows(BizException.class, () -> service.createOrdersFromReplenish(List.of(row)));
        assertThrows(BizException.class, () -> service.createOrdersFromReplenish(List.of(row, row)));
        verify(orders, never()).insert(any(com.zlth.ivd.procurement.entity.PurchaseOrder.class));
        verify(orders, never()).updateById(any(com.zlth.ivd.procurement.entity.PurchaseOrder.class));
    }
    @Test void replenishmentRejectsNonPositiveQuantityBeforeWritingOrders() {
        var orders = mock(com.zlth.ivd.procurement.mapper.PurchaseOrderMapper.class);
        var service = new ProcurementServiceImpl(null, null, null, null, null, orders, null, null, null, null, null, null, null, null);
        for (String qty : List.of("0", "-1")) {
            assertThrows(BizException.class, () -> service.createOrdersFromReplenish(
                    List.of(java.util.Map.of("id", "1", "reagentId", "3", "suggestedQty", qty))));
        }
        verifyNoInteractions(orders);
    }
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
        when(inventory.lockBatch(4L)).thenReturn(batch);
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
        var req = new PurchaseRequisition(); req.setId(1L); req.setFromDeptId(11L); req.setToWarehouseId(9L); req.setStatus(RequisitionStatusEnum.PARTIAL_FULFILLED);
        when(requisitions.selectById(1L)).thenReturn(req);
        var item = new PurchaseRequisitionItem(); item.setQtyRequested(new BigDecimal("5")); item.setQtyFulfilled(new BigDecimal("3"));
        when(items.selectList(any())).thenReturn(List.of(item));
        var transfer = new com.zlth.ivd.consumption.entity.ConsumptionRecord(); transfer.setId(7L); transfer.setReagentId(3L);
        transfer.setBatchNo("LOT"); transfer.setFromWarehouseId(8L); transfer.setWarehouseId(9L); transfer.setQuantity(new BigDecimal("3"));
        when(movements.findUnreceivedTransferRecords(1L)).thenReturn(List.of(transfer));
        var source = new com.zlth.ivd.inventory.entity.ReagentBatch(); source.setId(4L); source.setReagentId(3L); source.setBatchNo("LOT");
        source.setWarehouseId(8L);
        source.setUdiStatus(com.zlth.ivd.common.enums.UdiStatusEnum.IN_STOCK);
        source.setExpiryDate(java.time.LocalDate.now().plusDays(30));
        var target = new com.zlth.ivd.inventory.entity.ReagentBatch(); target.setId(5L); target.setStock(BigDecimal.ZERO);
        when(inventory.findBatch(3L,"LOT",8L)).thenReturn(source);
        when(inventory.findUniqueLegacyBatch(3L,"LOT",8L)).thenReturn(source);
        when(inventory.lockBatch(4L)).thenReturn(source);
        when(inventory.findBatch(3L,"LOT",9L)).thenReturn(target);
        when(inventory.updateBatch(target)).thenReturn(true);
        when(inventory.receiveIntoWarehouse(source,9L,new BigDecimal("3"))).thenAnswer(call->{target.setStock(new BigDecimal("3"));return target;});
        var catalogs = mock(com.zlth.ivd.catalog.service.CatalogReferenceService.class);
        when(catalogs.findById(3L)).thenReturn(new com.zlth.ivd.catalog.entity.ReagentCatalog());
        var service = spy(new ProcurementServiceImpl(null, requisitions, items, null, null, null, null, inventory, catalogs, movements, null, null, codes, null));
        doReturn(new com.zlth.ivd.procurement.vo.PurchaseRequisitionVO()).when(service).getRequisitionDetail(1L);
        service.receiveRequisition(1L);
        org.junit.jupiter.api.Assertions.assertEquals(RequisitionStatusEnum.PARTIAL_FULFILLED, req.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("3"), target.getStock());
        verify(movements).confirmTransferReceipt(7L,5L);
        when(movements.findUnreceivedTransferRecords(1L)).thenReturn(List.of());
        assertThrows(BizException.class, () -> service.receiveRequisition(1L));
        verify(inventory, times(1)).receiveIntoWarehouse(source,9L,new BigDecimal("3"));
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
