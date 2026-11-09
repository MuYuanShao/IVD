package com.zlth.ivd.procurement.service.impl;

import com.zlth.ivd.catalog.entity.ReagentCatalog;
import com.zlth.ivd.catalog.service.CatalogReferenceService;
import com.zlth.ivd.common.enums.ApprovalStatusEnum;
import com.zlth.ivd.common.enums.MaterialTypeEnum;
import com.zlth.ivd.common.enums.UdiStatusEnum;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.common.security.InstitutionContext;
import com.zlth.ivd.consumption.entity.ConsumptionRecord;
import com.zlth.ivd.consumption.service.ProcurementConsumptionService;
import com.zlth.ivd.inventory.entity.ReagentBatch;
import com.zlth.ivd.inventory.entity.Warehouse;
import com.zlth.ivd.inventory.service.ProcurementInventoryService;
import com.zlth.ivd.procurement.entity.SmartReplenishRun;
import com.zlth.ivd.procurement.entity.SmartReplenishSuggestion;
import com.zlth.ivd.procurement.mapper.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SmartReplenishCalculationTest {
    @Test void selectedWindowCentralUsableStockAndOutstandingProcurementDetermineNetDemand() {
        var inventory=mock(ProcurementInventoryService.class);
        var catalogs=mock(CatalogReferenceService.class);
        var consumption=mock(ProcurementConsumptionService.class);
        var orders=mock(PurchaseOrderMapper.class);
        var suggestions=mock(SmartReplenishSuggestionMapper.class);
        var plans=mock(PurchasePlanMapper.class);
        var runs=mock(SmartReplenishRunMapper.class);
        var service=new ProcurementServiceImpl(null,null,null,null,null,orders,null,inventory,catalogs,consumption,suggestions,null,null,null);
        ReflectionTestUtils.setField(service,"replenishRuns",runs);
        ReflectionTestUtils.setField(service,"purchasePlanMapper",plans);
        var central=new Warehouse();central.setId(1L);when(inventory.findActiveCentralWarehouse()).thenReturn(central);
        var usable=batch(1L,10,UdiStatusEnum.IN_STOCK,LocalDate.now().plusDays(10));
        var expired=batch(1L,500,UdiStatusEnum.IN_STOCK,LocalDate.now().minusDays(1));
        var recalled=batch(1L,500,UdiStatusEnum.RECALLED,LocalDate.now().plusDays(10));
        var departmental=batch(2L,500,UdiStatusEnum.IN_STOCK,LocalDate.now().plusDays(10));
        when(inventory.listAllBatches()).thenReturn(List.of(usable,expired,recalled,departmental));
        when(consumption.findConsumptionRecords()).thenReturn(List.of(
                movement("CONSUME",30,1),movement("CONSUME",9000,100),movement("INBOUND",5000,1)));
        when(orders.outstandingQuantities()).thenReturn(List.of(Map.of("reagentId",3L,"quantity",new BigDecimal("8"))));
        when(plans.committedQuantities()).thenReturn(List.of(Map.of("reagentId",3L,"quantity",new BigDecimal("2"))));
        var catalog=new ReagentCatalog();catalog.setId(3L);catalog.setGenericName("QA reagent");
        catalog.setMaterialType(MaterialTypeEnum.REAGENT);catalog.setApprovalStatus(ApprovalStatusEnum.APPROVED);
        when(catalogs.findById(3L)).thenReturn(catalog);
        var saved=new ArrayList<SmartReplenishSuggestion>();
        var savedRuns=new ArrayList<SmartReplenishRun>();
        when(runs.insert(any(SmartReplenishRun.class))).thenAnswer(call->{
            SmartReplenishRun run=call.getArgument(0);run.setId(7L);savedRuns.add(run);return 1;
        });
        when(runs.selectList(any())).thenAnswer(call->savedRuns);
        when(suggestions.insert(any(SmartReplenishSuggestion.class))).thenAnswer(call->{
            SmartReplenishSuggestion value=call.getArgument(0);value.setId(99L);saved.add(value);return 1;
        });
        when(suggestions.selectList(any())).thenAnswer(call->saved);
        var actor=new InstitutionContext.Scope(1,null,1004L,"PURCHASER");
        var computed=InstitutionContext.within(actor,()->service.recalculateSmartReplenishSuggestions(30));
        assertEquals(1,computed.size());
        assertEquals(0,new BigDecimal("10").compareTo((BigDecimal)computed.get(0).get("suggestedQty")));
        assertEquals(0,new BigDecimal("1").compareTo((BigDecimal)computed.get(0).get("dailyConsumption")));
        assertEquals(0,new BigDecimal("10").compareTo((BigDecimal)computed.get(0).get("currentStock")));
        InstitutionContext.within(actor,()->service.getSmartReplenishSuggestions(30));
        InstitutionContext.within(actor,()->service.getSmartReplenishSuggestions(30));
        verify(runs,times(1)).insert(any(SmartReplenishRun.class));
        verify(suggestions,times(1)).insert(any(SmartReplenishSuggestion.class));
        verify(suggestions,never()).delete(any());
        assertTrue(InstitutionContext.current().isEmpty());
    }

    @Test void invalidAnalysisPeriodsAreRejectedBeforeAnyDataReadOrMutation() {
        var inventory=mock(ProcurementInventoryService.class);
        var suggestions=mock(SmartReplenishSuggestionMapper.class);
        var service=new ProcurementServiceImpl(null,null,null,null,null,null,null,inventory,null,null,suggestions,null,null,null);
        for(int days:List.of(-1,0,366,Integer.MAX_VALUE)) {
            assertEquals(40001,assertThrows(BizException.class,()->service.getSmartReplenishSuggestions(days)).getErrorCode());
            assertEquals(40001,assertThrows(BizException.class,()->service.recalculateSmartReplenishSuggestions(days)).getErrorCode());
        }
        verifyNoInteractions(inventory,suggestions);
    }

    private ReagentBatch batch(Long warehouse,int quantity,UdiStatusEnum status,LocalDate expiry) {
        var batch=new ReagentBatch();batch.setReagentId(3L);batch.setWarehouseId(warehouse);
        batch.setStock(BigDecimal.valueOf(quantity));batch.setUdiStatus(status);batch.setExpiryDate(expiry);
        batch.setMaterialType(MaterialTypeEnum.REAGENT);return batch;
    }
    private ConsumptionRecord movement(String type,int quantity,int daysAgo) {
        var row=new ConsumptionRecord();row.setRecordType(type);row.setReagentId(3L);
        row.setQuantity(BigDecimal.valueOf(quantity));row.setOccurredAt(LocalDateTime.now().minusDays(daysAgo));return row;
    }
}
