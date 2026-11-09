package com.zlth.ivd.server.service;

import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.common.security.InstitutionContext;
import com.zlth.ivd.procurement.dto.CreateManualOrderRequest;
import com.zlth.ivd.procurement.dto.CreatePurchasePlanRequest;
import com.zlth.ivd.procurement.dto.ReceiveItemRequest;
import com.zlth.ivd.procurement.service.ProcurementService;
import com.zlth.ivd.procurement.service.PurchasePlanService;
import com.zlth.ivd.procurement.vo.PurchasePlanVO;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

/** Real MyBatis and transaction proxies; only fictional task-owned databases. */
class PurchasePlanFlowIntegrationTest {
    private StockFlowSafetyIntegrationTest fixture;
    AnnotationConfigApplicationContext context;
    JdbcTemplate db;
    PurchasePlanService plans;
    ProcurementService procurement;
    final InstitutionContext.Scope creator=new InstitutionContext.Scope(1,null,1004L,"PURCHASER");
    final InstitutionContext.Scope reviewer=new InstitutionContext.Scope(1,null,1006L,"LAB_DIRECTOR");

    protected AnnotationConfigApplicationContext fixtureContext() {
        return new AnnotationConfigApplicationContext(ResourceBoundaryIntegrationTest.Config.class);
    }

    @BeforeEach void setup() {
        fixture=new StockFlowSafetyIntegrationTest() {
            @Override protected AnnotationConfigApplicationContext fixtureContext() {
                return PurchasePlanFlowIntegrationTest.this.fixtureContext();
            }
        };
        fixture.setup();
        context=fixture.context;db=fixture.db;procurement=fixture.procurement;
        plans=context.getBean(PurchasePlanService.class);
    }

    @AfterEach void close() {
        if(fixture!=null)fixture.close();
    }

    <T>T as(InstitutionContext.Scope scope,Supplier<T> work){return fixture.as(scope,work);}
    long count(String table){return db.queryForObject("SELECT COUNT(*) FROM "+table,Long.class);}

    @Test void manualPlanNeedsDifferentApproverAndConvertsBySupplierAndAcceptanceMethodOnlyOnce() {
        db.update("UPDATE reagent_catalog SET material_type='CONSUMABLE' WHERE id=2");
        var request=manual(1L,new BigDecimal("2"));request.getItems().add(line(2L,new BigDecimal("3")));
        request.setOrderDate(LocalDate.now().minusDays(1));request.setExpectedDeliveryDate(LocalDate.now().plusDays(5));
        var created=as(creator,()->plans.create(request));
        assertEquals("DRAFT",created.getStatus());assertEquals(0,count("purchase_order"));
        as(creator,()->plans.submit(created.getId()));
        var self=new InstitutionContext.Scope(1,null,creator.actorId(),"LAB_DIRECTOR");
        assertEquals(40300,as(self,()->assertThrows(BizException.class,()->plans.approve(created.getId(),"self"))).getErrorCode());
        as(reviewer,()->plans.approve(created.getId(),"QA independent approval"));
        var ordered=as(creator,()->plans.convert(created.getId()));
        var replay=as(creator,()->plans.convert(created.getId()));
        assertEquals("ORDERED",ordered.getStatus());assertEquals(2,ordered.getOrders().size());
        assertEquals(ordered.getOrders().stream().map(PurchasePlanVO.Order::getId).toList(),
                replay.getOrders().stream().map(PurchasePlanVO.Order::getId).toList());
        assertEquals(List.of("MANUAL","SCAN"),db.queryForList("SELECT acceptance_method FROM purchase_order ORDER BY acceptance_method",String.class));
        assertEquals(2,count("purchase_order"));assertEquals(2,count("purchase_order_item"));
        assertEquals(2L,db.queryForObject("SELECT COUNT(*) FROM purchase_order WHERE approval_origin='PLAN' AND status='SENT' AND plan_id=?",Long.class,created.getId()));
        assertEquals(2L,db.queryForObject("SELECT COUNT(*) FROM purchase_order WHERE order_date=? AND expected_arrival=?",Long.class,
                request.getOrderDate(),request.getExpectedDeliveryDate()));
        assertEquals(4L,db.queryForObject("SELECT COUNT(*) FROM purchase_plan_event WHERE plan_id=?",Long.class,created.getId()));
    }

    @Test void newInternalDraftOrderCannotBypassPlanApprovalAtReceipt() {
        var request=new CreateManualOrderRequest();request.setSupplierId(1);request.setSettlementMode("消耗结算");
        var item=new CreateManualOrderRequest.ManualOrderItem();item.setReagentId(1L);item.setQuantity(BigDecimal.ONE);
        request.setItems(List.of(item));
        var order=as(creator,()->procurement.createManualOrder(request).get(0));
        assertEquals("PLAN_REQUIRED",order.getApprovalOrigin());
        Long itemId=db.queryForObject("SELECT id FROM purchase_order_item WHERE po_id=?",Long.class,order.getId());
        var receipt=new ReceiveItemRequest();receipt.setOrderId(order.getId());receipt.setItemId(itemId);
        receipt.setBatchNo("QA-NOT-APPROVED");receipt.setQtyReceived(BigDecimal.ONE);receipt.setExpiryDate(LocalDate.now().plusYears(1));
        assertEquals(40900,as(creator,()->assertThrows(BizException.class,()->procurement.receiveOrder(receipt))).getErrorCode());
        assertEquals(0,db.queryForObject("SELECT received_quantity FROM purchase_order_item WHERE id=?",Integer.class,itemId));
        assertEquals(0,count("consumption_record"));assertEquals(0,count("settlement_record"));
    }

    @Test void receivedPlanOrderTraceIncludesActualPlanApprovalEvidence() {
        db.update("UPDATE reagent_catalog SET material_type='CONSUMABLE' WHERE id=2");
        var plan=approved(manual(2L,new BigDecimal("2")));
        var other=approved(manual(2L,BigDecimal.ONE));
        var order=as(creator,()->plans.convert(plan.getId())).getOrders().get(0);
        Long itemId=db.queryForObject("SELECT id FROM purchase_order_item WHERE po_id=?",Long.class,order.getId());
        var receipt=new ReceiveItemRequest();
        receipt.setOrderId(order.getId());receipt.setItemId(itemId);receipt.setWarehouseId(101L);
        receipt.setBatchNo("QA-PLAN-TRACE");receipt.setQtyReceived(BigDecimal.ONE);
        var keeper=new InstitutionContext.Scope(1,null,1002L,"STOREKEEPER");
        as(keeper,()->{procurement.receiveOrder(receipt);return null;});
        var trace=as(keeper,()->fixture.inventory.traceUdi("QA-PLAN-TRACE",null));
        @SuppressWarnings("unchecked")
        var nodes=(List<java.util.Map<String,Object>>)trace.get("timeline");
        assertEquals(1,nodes.stream().filter(n->"采购计划".equals(n.get("node")) && String.valueOf(plan.getId()).equals(n.get("sourceId"))).count());
        var approval=nodes.stream().filter(n->"采购计划审批通过".equals(n.get("node"))).findFirst().orElseThrow();
        Long approvalId=db.queryForObject("SELECT id FROM purchase_plan_event WHERE plan_id=? AND to_status='APPROVED'",Long.class,plan.getId());
        assertEquals(String.valueOf(approvalId),approval.get("sourceId"));
        assertEquals("1006",approval.get("operator"));
        assertTrue(approval.get("detail").toString().contains(plan.getPlanNo()));
        assertFalse(nodes.stream().anyMatch(n->n.get("detail").toString().contains(other.getPlanNo())));
    }

    @Test void successfulSuggestionPlanMarksGeneratedAndCannotCreateAnotherObligation() {
        createSuggestions();
        var request=replenishment(7991L);
        var plan=approved(request);
        as(creator,()->plans.convert(plan.getId()));
        as(creator,()->plans.convert(plan.getId()));
        assertEquals("GENERATED",db.queryForObject("SELECT status FROM smart_replenish_suggestion WHERE id=7991",String.class));
        assertEquals(40900,as(creator,()->assertThrows(BizException.class,()->plans.create(request))).getErrorCode());
        assertEquals(1,count("purchase_plan"));assertEquals(1,count("purchase_order"));assertEquals(1,count("purchase_order_item"));
        assertEquals("ORDERED",db.queryForObject("SELECT status FROM purchase_plan WHERE id=?",String.class,plan.getId()));
    }

    @Test void suggestionStateWriteFailureRollsBackAllOrdersAndRetainsApprovedPlan() {
        createSuggestions();
        var plan=approved(replenishment(7991L));
        db.execute("ALTER TABLE smart_replenish_suggestion ADD CONSTRAINT qa_deny_generated CHECK(status<>'GENERATED')");
        as(creator,()->assertThrows(RuntimeException.class,()->plans.convert(plan.getId())));
        assertConversionRolledBack(plan.getId());
        dropCheck("smart_replenish_suggestion","qa_deny_generated");
        as(creator,()->plans.convert(plan.getId()));
        assertEquals(1,count("purchase_order"));
        assertEquals("GENERATED",db.queryForObject("SELECT status FROM smart_replenish_suggestion WHERE id=7991",String.class));
    }

    @Test void immutablePlanEventFailureRollsBackOrdersPlanAndSuggestionState() {
        createSuggestions();
        var plan=approved(replenishment(7991L));
        db.execute("ALTER TABLE purchase_plan_event ADD CONSTRAINT qa_deny_ordered_event CHECK(to_status<>'ORDERED')");
        as(creator,()->assertThrows(RuntimeException.class,()->plans.convert(plan.getId())));
        assertConversionRolledBack(plan.getId());
        dropCheck("purchase_plan_event","qa_deny_ordered_event");
        as(creator,()->plans.convert(plan.getId()));
        assertEquals(1,count("purchase_order"));
        assertEquals("ORDERED",db.queryForObject("SELECT status FROM purchase_plan WHERE id=?",String.class,plan.getId()));
    }

    @Test void latestSuggestionReadsDoNotDeleteOrRegenerateAndInvalidDaysFailBeforeWrites() {
        createSuggestions();
        db.update("INSERT INTO smart_replenish_run(id,institution_id,analysis_days,batch_no,created_at,updated_at) VALUES(1,1,30,'QA-CURRENT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        db.update("UPDATE smart_replenish_suggestion SET batch_no='QA-CURRENT',analysis_days=30 WHERE id=7991");
        db.update("INSERT INTO smart_replenish_suggestion(id,institution_id,reagent_id,suggested_qty,batch_no,analysis_days,status) VALUES(7992,1,2,4,'QA-OLD',30,'PENDING')");
        for(int run=0;run<2;run++) {
            var rows=as(creator,()->procurement.getSmartReplenishSuggestions(30));
            assertEquals(1,rows.size());assertEquals("7991",rows.get(0).get("id"));
        }
        for(int days:List.of(0,-1,366))as(creator,()->{
            assertEquals(40001,assertThrows(BizException.class,()->procurement.getSmartReplenishSuggestions(days)).getErrorCode());
            assertEquals(40001,assertThrows(BizException.class,()->procurement.recalculateSmartReplenishSuggestions(days)).getErrorCode());return null;
        });
        assertEquals(2,count("smart_replenish_suggestion"));assertEquals(1,count("smart_replenish_run"));
        assertEquals(2L,db.queryForObject("SELECT COUNT(*) FROM smart_replenish_suggestion WHERE status='PENDING'",Long.class));
    }

    @Test void recalculationUsesRecentConsumptionAndNetCentralSupplyAndRemembersEmptyRun() {
        createSuggestions();
        db.execute("DELETE FROM smart_replenish_suggestion WHERE id=7991");
        var heldPlan=approved(manual(1L,new BigDecimal("2")));
        db.update("INSERT INTO reagent_batch(id,institution_id,reagent_id,batch_no,warehouse_id,stock,unit_price,udi_status,expiry_date) VALUES(5101,1,1,'QA-EXPIRED',101,500,10,'IN_STOCK',?),(5102,1,1,'QA-DEPT',102,500,10,'IN_STOCK','2039-01-01')",LocalDate.now().minusDays(1));
        db.update("INSERT INTO consumption_record(id,institution_id,record_type,reagent_id,quantity,warehouse_id,occurred_at) VALUES(9601,1,'CONSUME',1,30,102,?),(9602,1,'CONSUME',1,9000,102,?),(9603,1,'INBOUND',1,5000,101,?)",
                LocalDateTime.now().minusDays(1),LocalDateTime.now().minusDays(100),LocalDateTime.now().minusDays(1));
        db.update("INSERT INTO purchase_order(id,institution_id,po_no,source_type,approval_origin,status) VALUES(9691,1,'QA-IN-PROCUREMENT','MANUAL','LEGACY','SENT')");
        db.update("INSERT INTO purchase_order_item(id,institution_id,po_id,reagent_id,order_quantity,received_quantity) VALUES(9692,1,9691,1,8,0)");
        var computed=as(creator,()->procurement.recalculateSmartReplenishSuggestions(30));
        assertEquals(1,computed.size());
        assertEquals(0,new BigDecimal("10").compareTo((BigDecimal)computed.get(0).get("suggestedQty")));
        assertEquals(0,BigDecimal.ONE.compareTo((BigDecimal)computed.get(0).get("dailyConsumption")));
        assertEquals(0,new BigDecimal("10").compareTo((BigDecimal)computed.get(0).get("currentStock")));
        db.update("UPDATE reagent_batch SET stock=100 WHERE id=5001");
        assertTrue(as(creator,()->procurement.recalculateSmartReplenishSuggestions(30)).isEmpty());
        assertTrue(as(creator,()->procurement.getSmartReplenishSuggestions(30)).isEmpty());
        assertEquals(1,count("smart_replenish_suggestion"));assertEquals(2,count("smart_replenish_run"));
        assertEquals("APPROVED",db.queryForObject("SELECT status FROM purchase_plan WHERE id=?",String.class,heldPlan.getId()));
    }

    private PurchasePlanVO approved(CreatePurchasePlanRequest request) {
        var created=as(creator,()->plans.create(request));
        as(creator,()->plans.submit(created.getId()));
        return as(reviewer,()->plans.approve(created.getId(),"QA approval"));
    }

    private CreatePurchasePlanRequest manual(Long reagent,BigDecimal quantity) {
        var request=new CreatePurchasePlanRequest();request.setSourceType("MANUAL");
        request.setSupplierId(1);request.setSettlementMode("消耗结算");
        request.setItems(new java.util.ArrayList<>(List.of(line(reagent,quantity))));return request;
    }

    private CreatePurchasePlanRequest.Item line(Long reagent,BigDecimal quantity) {
        var item=new CreatePurchasePlanRequest.Item();item.setReagentId(reagent);item.setSupplierId(1);item.setQuantity(quantity);return item;
    }

    private CreatePurchasePlanRequest replenishment(Long suggestionId) {
        var request=new CreatePurchasePlanRequest();request.setSourceType("SMART_REPLENISH");request.setSettlementMode("消耗结算");
        var source=new CreatePurchasePlanRequest.Suggestion();source.setId(suggestionId);source.setReagentId(1L);source.setSupplierId(1);source.setSuggestedQty(new BigDecimal("2"));
        request.setSuggestions(List.of(source));return request;
    }

    private void createSuggestions() {
        db.execute("CREATE TABLE smart_replenish_suggestion(id BIGINT PRIMARY KEY,institution_id BIGINT,reagent_id BIGINT,reagent_name VARCHAR(200),specification VARCHAR(128),manufacturer VARCHAR(128),current_stock DECIMAL(10,2),daily_consumption DECIMAL(10,2),safety_stock DECIMAL(10,2),suggested_qty DECIMAL(10,2),unit VARCHAR(32),supplier VARCHAR(100),analysis_days INT,batch_no VARCHAR(64),status VARCHAR(32),created_at TIMESTAMP,updated_at TIMESTAMP)");
        db.update("INSERT INTO smart_replenish_suggestion(id,institution_id,reagent_id,suggested_qty,status) VALUES(7991,1,1,2,'PENDING')");
    }

    private void assertConversionRolledBack(Long id) {
        assertEquals(0,count("purchase_order"));assertEquals(0,count("purchase_order_item"));
        assertEquals("APPROVED",db.queryForObject("SELECT status FROM purchase_plan WHERE id=?",String.class,id));
        assertEquals("PENDING",db.queryForObject("SELECT status FROM smart_replenish_suggestion WHERE id=7991",String.class));
        assertEquals(3L,db.queryForObject("SELECT COUNT(*) FROM purchase_plan_event WHERE plan_id=?",Long.class,id));
    }

    private void dropCheck(String table,String name) {
        String product;
        try(var connection=db.getDataSource().getConnection()){product=connection.getMetaData().getDatabaseProductName();}
        catch(java.sql.SQLException error){throw new IllegalStateException(error);}
        db.execute("ALTER TABLE "+table+(product.contains("MySQL")?" DROP CHECK ":" DROP CONSTRAINT ")+name);
    }
}
