package com.zlth.ivd.procurement.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zlth.ivd.catalog.entity.ReagentCatalog;
import com.zlth.ivd.catalog.service.CatalogReferenceService;
import com.zlth.ivd.common.enums.ApprovalStatusEnum;
import com.zlth.ivd.common.enums.MaterialTypeEnum;
import com.zlth.ivd.common.enums.PurchaseOrderStatusEnum;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.common.security.InstitutionContext;
import com.zlth.ivd.license.entity.Supplier;
import com.zlth.ivd.license.service.SupplierReferenceService;
import com.zlth.ivd.procurement.dto.CreatePurchasePlanRequest;
import com.zlth.ivd.procurement.entity.*;
import com.zlth.ivd.procurement.mapper.*;
import com.zlth.ivd.procurement.service.ProcurementService;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PurchasePlanServiceImplTest {
    private PurchasePlanMapper plans;
    private PurchasePlanItemMapper items;
    private PurchasePlanEventMapper events;
    private PurchaseOrderMapper orders;
    private SmartReplenishSuggestionMapper suggestions;
    private CatalogReferenceService catalogs;
    private SupplierReferenceService suppliers;
    private ProcurementService procurement;
    private PurchasePlanServiceImpl service;
    private final AtomicReference<PurchasePlan> storedPlan=new AtomicReference<>();
    private final List<PurchasePlanItem> storedItems=new ArrayList<>();
    private final List<PurchaseOrder> storedOrders=new ArrayList<>();
    private ReagentCatalog catalog;
    private SmartReplenishSuggestion suggestion;
    private final InstitutionContext.Scope creator=new InstitutionContext.Scope(1,null,1004L,"PURCHASER");
    private final InstitutionContext.Scope reviewer=new InstitutionContext.Scope(1,null,1006L,"LAB_DIRECTOR");

    @BeforeEach void setup() {
        plans=mock(PurchasePlanMapper.class);items=mock(PurchasePlanItemMapper.class);
        events=mock(PurchasePlanEventMapper.class);orders=mock(PurchaseOrderMapper.class);
        suggestions=mock(SmartReplenishSuggestionMapper.class);catalogs=mock(CatalogReferenceService.class);
        suppliers=mock(SupplierReferenceService.class);procurement=mock(ProcurementService.class);
        service=new PurchasePlanServiceImpl(plans,items,events,orders,mock(ShortageOrderMapper.class),
                mock(ShortageOrderItemMapper.class),suggestions,catalogs,suppliers,procurement,
                new ObjectMapper().findAndRegisterModules());
        when(plans.insert(any(PurchasePlan.class))).thenAnswer(call->{
            PurchasePlan value=call.getArgument(0);value.setId(10L);storedPlan.set(value);return 1;
        });
        when(plans.selectOne(any())).thenAnswer(call->storedPlan.get());
        when(items.insert(any(PurchasePlanItem.class))).thenAnswer(call->{
            PurchasePlanItem value=call.getArgument(0);value.setId((long)storedItems.size()+1);storedItems.add(value);return 1;
        });
        when(items.selectList(any())).thenAnswer(call->new ArrayList<>(storedItems));
        when(orders.selectList(any())).thenAnswer(call->new ArrayList<>(storedOrders));
        when(orders.updateById(any(PurchaseOrder.class))).thenAnswer(call->{
            PurchaseOrder value=call.getArgument(0);
            if(storedOrders.stream().noneMatch(o->o.getId().equals(value.getId())))storedOrders.add(value);return 1;
        });
        catalog=new ReagentCatalog();catalog.setId(3L);catalog.setGenericName("QA reagent");
        catalog.setMaterialType(MaterialTypeEnum.REAGENT);catalog.setApprovalStatus(ApprovalStatusEnum.APPROVED);
        catalog.setPricePerUnit(new BigDecimal("10.00"));catalog.setDistributor("QA supplier");
        when(catalogs.lockForBusiness(3L)).thenAnswer(call->catalog);
        var supplier=new Supplier();supplier.setId(1L);supplier.setName("QA supplier");supplier.setIsActive(true);
        when(suppliers.findById(1L)).thenReturn(supplier);
        when(suppliers.findAll()).thenReturn(List.of(supplier));
        suggestion=new SmartReplenishSuggestion();suggestion.setId(99L);suggestion.setReagentId(3L);suggestion.setStatus("PENDING");
        when(suggestions.selectOne(any())).thenReturn(suggestion);
        when(suggestions.update(any(SmartReplenishSuggestion.class),any())).thenAnswer(call->{
            suggestion.setStatus(((SmartReplenishSuggestion)call.getArgument(0)).getStatus());return 1;
        });
        when(procurement.createManualOrder(any())).thenAnswer(call->{
            var order=new PurchaseOrder();order.setId(101L);order.setPoNo("PO-QA-0001");order.setStatus(PurchaseOrderStatusEnum.DRAFT);
            order.setSupplierId(1);order.setAcceptanceMethod("SCAN");order.setApprovalOrigin("PLAN_REQUIRED");
            return List.of(order);
        });
    }

    @AfterEach void contextIsCleared(){assertTrue(InstitutionContext.current().isEmpty());}

    private CreatePurchasePlanRequest replenishment() {
        var request=new CreatePurchasePlanRequest();request.setSourceType("SMART_REPLENISH");
        request.setSettlementMode("消耗结算");request.setOrderDate(LocalDate.now());
        var row=new CreatePurchasePlanRequest.Suggestion();row.setId(99L);row.setReagentId(3L);
        row.setSupplierId(1);row.setSuggestedQty(new BigDecimal("6.00"));request.setSuggestions(List.of(row));return request;
    }

    @Test void approvedReplenishmentConvertsOnceAndReplayKeepsSameOrderAndSource() {
        var created=InstitutionContext.within(creator,()->service.create(replenishment()));
        assertEquals("DRAFT",created.getStatus());assertEquals(99L,created.getItems().get(0).getSuggestionId());
        InstitutionContext.within(creator,()->service.submit(10L));
        assertEquals("PENDING_REVIEW",storedPlan.get().getStatus());
        InstitutionContext.within(reviewer,()->service.approve(10L,"QA approval"));
        var result=InstitutionContext.within(creator,()->service.convert(10L));
        var replay=InstitutionContext.within(creator,()->service.convert(10L));
        assertEquals("ORDERED",result.getStatus());
        assertEquals("GENERATED",suggestion.getStatus());
        assertEquals(101L,result.getOrders().get(0).getId());
        assertEquals(101L,replay.getOrders().get(0).getId());
        assertEquals(PurchaseOrderStatusEnum.SENT,storedOrders.get(0).getStatus());
        assertEquals("PLAN",storedOrders.get(0).getApprovalOrigin());
        assertEquals(10L,storedOrders.get(0).getPlanId());
        verify(procurement,times(1)).createManualOrder(argThat(request->
                request.getItems().size()==1 && request.getItems().get(0).getQuantity().compareTo(new BigDecimal("6"))==0));
        verify(events,times(4)).insert(any(PurchasePlanEvent.class));
    }

    @Test void unapprovedPlanCannotConvertAndCreatorCannotApproveOwnPlan() {
        InstitutionContext.within(creator,()->service.create(replenishment()));
        assertEquals(40900,InstitutionContext.within(creator,()->assertThrows(BizException.class,()->service.convert(10L))).getErrorCode());
        InstitutionContext.within(creator,()->service.submit(10L));
        var sameCreatorAsDirector=new InstitutionContext.Scope(1,null,creator.actorId(),"LAB_DIRECTOR");
        assertEquals(40300,InstitutionContext.within(sameCreatorAsDirector,
                ()->assertThrows(BizException.class,()->service.approve(10L,null))).getErrorCode());
        verifyNoInteractions(procurement);
    }

    @Test void changedPriceAfterApprovalNeedsNewPlanApprovalRatherThanSilentlyBuyingAtNewPrice() {
        InstitutionContext.within(creator,()->service.create(replenishment()));
        InstitutionContext.within(creator,()->service.submit(10L));
        InstitutionContext.within(reviewer,()->service.approve(10L,null));
        catalog.setPricePerUnit(new BigDecimal("11.00"));
        assertEquals(40900,InstitutionContext.within(creator,()->assertThrows(BizException.class,()->service.convert(10L))).getErrorCode());
        assertEquals("APPROVED",storedPlan.get().getStatus());
        assertEquals("PENDING",suggestion.getStatus());
        verifyNoInteractions(procurement);
    }
}
