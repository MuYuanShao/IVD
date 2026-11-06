package com.zlth.ivd.server.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.*;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.zlth.ivd.common.security.*;
import com.zlth.ivd.common.enums.*;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.procurement.dto.*;
import com.zlth.ivd.procurement.entity.*;
import com.zlth.ivd.procurement.service.*;
import com.zlth.ivd.consumption.service.*;
import com.zlth.ivd.inventory.entity.*;
import com.zlth.ivd.inventory.service.*;
import com.zlth.ivd.settlement.service.*;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Fictional stock only: real MyBatis, transaction proxies and synchronous billing listeners. */
class SupplyFlowIntegrationTest {
    AnnotationConfigApplicationContext context;
    ProcurementService procurement;ConsumptionService consumption;SettlementService settlements;JdbcTemplate jdbc;
    final InstitutionContext.Scope purchaser=new InstitutionContext.Scope(1L,null,1004L,"PURCHASER");
    final InstitutionContext.Scope keeper=new InstitutionContext.Scope(1L,null,1002L,"STOREKEEPER");
    final InstitutionContext.Scope tech=new InstitutionContext.Scope(1L,11L,1001L,"LAB_TECH");
    final InstitutionContext.Scope finance=new InstitutionContext.Scope(1L,null,1003L,"FINANCE");
    @BeforeEach void setup(){context=new AnnotationConfigApplicationContext(Config.class);procurement=context.getBean(ProcurementService.class);
        consumption=context.getBean(ConsumptionService.class);settlements=context.getBean(SettlementService.class);jdbc=context.getBean(JdbcTemplate.class);}
    @AfterEach void close(){context.close();SecurityContextHolder.clearContext();assertTrue(InstitutionContext.current().isEmpty());}
    <T>T as(InstitutionContext.Scope scope,Supplier<T> action){
        var authentication=new UsernamePasswordAuthenticationToken(scope.actorId(),"QA",List.of(new SimpleGrantedAuthority("ROLE_"+scope.role())));
        authentication.setDetails("虚构验证用户");SecurityContextHolder.getContext().setAuthentication(authentication);
        try{return InstitutionContext.within(scope,action);}finally{SecurityContextHolder.clearContext();}
    }
    void run(InstitutionContext.Scope scope,Runnable action){as(scope,()->{action.run();return null;});}
    long count(String table){return as(finance,()->jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class));}
    ReagentBatch batch(long id){return as(keeper,()->context.getBean(ProcurementInventoryService.class).getBatch(id));}
    record Received(Long orderId,Long itemId,Long batchId){}
    @Test void quantityOnlyCatalogCanFlowThroughPurchaseReceiptRequisitionAndConsumption() {
        var raw = new JdbcTemplate(context.getBean(DataSource.class));
        raw.update("UPDATE reagent_catalog SET material_type='CONSUMABLE',udi_di=NULL WHERE id=2");
        var order = ordered("消耗结算", 2L, 3);
        var receipt = new ReceiveItemRequest();
        receipt.setOrderId(order.orderId());
        receipt.setItemId(order.itemId());
        receipt.setQtyReceived(new BigDecimal("2.50"));
        receipt.setWarehouseId(101L);
        run(keeper, () -> procurement.receiveOrder(receipt));
        var source = raw.queryForMap("SELECT id,batch_no,expiry_date FROM reagent_batch WHERE reagent_id=2 AND warehouse_id=101");
        assertNull(source.get("expiry_date"));
        assertTrue(source.get("batch_no").toString().startsWith("BATCH-CONS-"));
        var req = as(tech, () -> {
            var request = new PurchaseRequisition();request.setFromDeptId(11L);request.setUrgency(UrgencyEnum.NORMAL);
            var item = new PurchaseRequisitionItem();item.setReagentId(2L);item.setQtyRequested(new BigDecimal("1.25"));
            request.setItems(List.of(item));return procurement.createRequisition(request);
        });
        run(tech, () -> procurement.submitRequisition(req.getId()));
        run(keeper, () -> procurement.reviewRequisition(req.getId(),"APPROVE",null));
        var reqItem = as(keeper, () -> procurement.getRequisitionItems(req.getId()).get(0));
        var request = new PickAndShipRequest();
        var pick = new PickAndShipRequest.PickItem();
        pick.setReqItemId(reqItem.getId());pick.setSourceBatchId(((Number)source.get("id")).longValue());
        pick.setPickQty(new BigDecimal("1.25"));request.setItems(List.of(pick));
        run(keeper, () -> procurement.pickAndShip(req.getId(),request));
        run(tech, () -> settlements.receiveAndSettleRequisition(req.getId()));
        Long target = raw.queryForObject("SELECT id FROM reagent_batch WHERE warehouse_id=102 AND reagent_id=2",Long.class);
        run(tech, () -> consumption.recordConsumption(target,null,new BigDecimal("0.25"),"ROUTINE",null));
        assertEquals(new BigDecimal("1.00"),batch(target).getStock());
        assertNull(batch(target).getExpiryDate());
        assertEquals(0,count("device_batch_load"));
        assertEquals(0,count("udi_status_log"));
        assertEquals(1,count("settlement_record"));
    }
    @Test void consumablesIgnoreForcedInstrumentPolicyAndNeverCreateLoad() {
        var raw = new JdbcTemplate(context.getBean(DataSource.class));
        raw.update("UPDATE reagent_catalog SET material_type='CONSUMABLE' WHERE id=2");
        raw.update("""
                INSERT INTO reagent_batch(id,institution_id,reagent_id,batch_no,warehouse_id,stock,unit,unit_price,udi_status,expiry_date,material_type,settlement_mode)
                VALUES(9191,1,2,'QA-DIRECT-USE',102,2,'支',10,'IN_STOCK','2039-01-01','CONSUMABLE','消耗结算')
                """);
        var instruments = context.getBean(com.zlth.ivd.inventory.service.InstrumentUsageService.class);
        run(tech, () -> {
            assertThrows(BizException.class, () -> consumption.recordConsumption(9191L, 301L, BigDecimal.ONE, "ROUTINE", null));
            assertThrows(BizException.class, () -> instruments.bind(9191L, 301L));
            consumption.recordConsumption(9191L, null, BigDecimal.ONE, "ROUTINE", null);
        });
        assertEquals(0, count("device_batch_load"));
        assertEquals(0, count("udi_status_log"));
        assertNull(raw.queryForObject("SELECT device_id FROM consumption_record WHERE batch_id=9191", Long.class));
        assertEquals("CONSUMABLE", raw.queryForObject("SELECT material_type FROM consumption_record WHERE batch_id=9191", String.class));
        assertEquals(1, count("settlement_record"));
    }
    @Test void consumptionDetailsFilterByDayAndReagentBeforePaging() {
        var raw = new JdbcTemplate(context.getBean(DataSource.class));
        raw.update("UPDATE reagent_catalog SET generic_name='QA literal % reagent' WHERE id=1");
        raw.update("""
                INSERT INTO consumption_record(id,institution_id,record_type,batch_no,reagent_id,warehouse_id,quantity,occurred_at)
                VALUES(9201,1,'CONSUME','QA-D1',1,102,1,'2026-11-04 00:00:00'),
                      (9202,1,'CONSUME','QA-D2',1,102,1,'2026-11-04 23:59:59'),
                      (9203,1,'CONSUME','QA-NEXT',1,102,1,'2026-11-05 00:00:00'),
                      (9204,1,'CONSUME','QA-OTHER',2,102,1,'2026-11-04 12:00:00'),
                      (9205,2,'CONSUME','QA-FOREIGN',3,202,1,'2026-11-04 12:00:00'),
                      (9206,1,'CONSUME','QA-OTHER-WH',1,103,1,'2026-11-04 12:00:00')
                """);
        var day = LocalDate.of(2026, 11, 4);
        run(finance, () -> {
            var first = settlements.getConsumptionDetails(1, 1, null, null, 102L, day, "  QA-FLEX ");
            var second = settlements.getConsumptionDetails(2, 1, null, null, 102L, day, "%");
            assertEquals(2, first.getTotal());
            assertEquals("QA-D2", first.getRecords().get(0).getBatchNo());
            assertEquals(2, second.getTotal());
            assertEquals("QA-D1", second.getRecords().get(0).getBatchNo());
            assertEquals(0, settlements.getConsumptionDetails(1, 20, null, null, null, day, "QA-FOREIGN").getTotal());
        });
    }
    @Test void consumptionBillingUsesWarehouseDepartmentForAdminAndManualSettlement() {
        var received = receive("消耗结算", 1L, "QA-BILL-DEPT", 2);
        long batchId = sign(ship(received.batchId(), 1L, 2), 1L, "QA-BILL-DEPT");
        var raw = new JdbcTemplate(context.getBean(DataSource.class));
        raw.update("INSERT INTO warehouse(id,institution_id,name,level,parent_id,status,is_active) VALUES(104,1,'QA group',3,102,1,TRUE)");
        raw.update("UPDATE reagent_batch SET warehouse_id=104 WHERE id=?", batchId);
        var admin = new InstitutionContext.Scope(1, null, 1000L, "SYSTEM_ADMIN");
        run(admin, () -> consumption.recordConsumption(batchId, null, BigDecimal.ONE, "ROUTINE", null));
        assertEquals(11L, raw.queryForObject("SELECT from_dept_id FROM settlement_record WHERE settlement_type='CONSUMPTION'", Long.class));

        raw.update("""
                INSERT INTO consumption_record(id,institution_id,record_type,batch_no,batch_id,reagent_id,quantity,unit_price,amount,warehouse_id,settlement_mode)
                VALUES(9181,1,'CONSUME','QA-BILL-DEPT',?,1,1,10,10,104,'消耗结算')
                """, batchId);
        run(finance, () -> settlements.settleConsumption(9181L));
        assertEquals(11L, raw.queryForObject("SELECT from_dept_id FROM settlement_record WHERE source_id=9181", Long.class));
    }
    @Test void traceLinksPostedInboundSettlementToItsReceipt() {
        String lot = "QA-TRACE-INBOUND";
        receive("验收结算", 1L, lot, 2);
        run(keeper, () -> {
            Long settlementId = jdbc.queryForObject(
                    "SELECT id FROM settlement_record WHERE settlement_type='INBOUND' AND batch_no=?",
                    Long.class, lot);
            var result = new com.zlth.ivd.inventory.service.impl.TraceQueryService(jdbc).trace(lot, null);
            var events = (List<?>) result.get("timeline");
            assertTrue(events.stream().map(row -> (Map<?, ?>) row)
                    .anyMatch(row -> "consumption_record".equals(row.get("source")) && "验收入库".equals(row.get("node"))));
            var linked = events.stream().map(row -> (Map<?, ?>) row)
                    .filter(row -> "settlement_record".equals(row.get("source"))).toList();
            assertEquals(1, linked.size());
            assertEquals(settlementId.toString(), linked.get(0).get("sourceId"));
            assertTrue(linked.get(0).get("detail").toString().contains("20.00"));
        });
    }
    @Test void manualPurchaseRejectsUnapprovedCatalogBeforeAllocatingOrWriting() {
        long orders = count("purchase_order");
        long lines = count("purchase_order_item");
        long sequences = count("purchase_order_number_sequence");
        for (String status : List.of("DRAFT", "PENDING", "REJECTED")) {
            run(purchaser, () -> {
                jdbc.update("UPDATE reagent_catalog SET approval_status=? WHERE id=?", status, 2L);
                var request = new CreateManualOrderRequest();
                request.setSupplierId(1);
                request.setSettlementMode("验收结算");
                var approved = new CreateManualOrderRequest.ManualOrderItem();
                approved.setReagentId(1L);
                approved.setQuantity(BigDecimal.ONE);
                var unapproved = new CreateManualOrderRequest.ManualOrderItem();
                unapproved.setReagentId(2L);
                unapproved.setQuantity(BigDecimal.ONE);
                request.setItems(List.of(approved, unapproved));
                var error = assertThrows(BizException.class, () -> procurement.createManualOrder(request));
                assertEquals(40001, error.getErrorCode());
                assertTrue(error.getMessage().contains("已批准"));
            });
            assertEquals(orders, count("purchase_order"));
            assertEquals(lines, count("purchase_order_item"));
            assertEquals(sequences, count("purchase_order_number_sequence"));
        }
    }
    @Test void manualPurchaseRejectsUnavailableSupplierBeforeWriting() {
        long orders = count("purchase_order");
        long lines = count("purchase_order_item");
        run(purchaser, () -> {
            var request = new CreateManualOrderRequest();
            request.setSupplierId(99);
            request.setSettlementMode("验收结算");
            var item = new CreateManualOrderRequest.ManualOrderItem();
            item.setReagentId(1L);
            item.setQuantity(BigDecimal.ONE);
            request.setItems(List.of(item));
            assertEquals(40300, assertThrows(BizException.class, () -> procurement.createManualOrder(request)).getErrorCode());
        });
        assertEquals(orders, count("purchase_order"));
        assertEquals(lines, count("purchase_order_item"));
    }
    @Test void directReceivingRejectsInactiveSupplierBeforeCreatingOrderOrStock() {
        long orders = count("purchase_order");
        long batches = count("reagent_batch");
        run(keeper, () -> {
            var request = new com.zlth.ivd.procurement.dto.DirectReceiveRequest();
            request.setSupplierId(2);
            request.setWarehouseId(101L);
            var line = new com.zlth.ivd.procurement.dto.DirectReceiveRequest.DirectReceiveItem();
            line.setReagentId(1L);
            line.setQtyReceived(BigDecimal.ONE);
            line.setBatchNo("QA-INACTIVE-SUPPLIER");
            line.setExpiryDate(LocalDate.now().plusYears(1));
            request.setItems(List.of(line));
            assertEquals("供应商已停用", assertThrows(BizException.class, () -> procurement.receiveDirect(request)).getMessage());
        });
        assertEquals(orders, count("purchase_order"));
        assertEquals(batches, count("reagent_batch"));
    }
    Received ordered(String mode,long reagent,int quantity){
        var order=as(purchaser,()->{
            var request=new CreateManualOrderRequest();request.setSettlementMode(mode);request.setSupplierId(1);
            var item=new CreateManualOrderRequest.ManualOrderItem();item.setReagentId(reagent);item.setQuantity(BigDecimal.valueOf(quantity));
            request.setItems(List.of(item));return procurement.createManualOrder(request).get(0);
        });
        var item=as(keeper,()->procurement.getOrderDetail(order.getId()).getItems().get(0));
        return new Received(order.getId(),item.getId(),null);
    }
    Received receive(String mode,long reagent,String lot,int quantity){
        var order=ordered(mode,reagent,quantity);
        var receipt=new ReceiveItemRequest();receipt.setOrderId(order.orderId());receipt.setItemId(order.itemId());receipt.setQtyReceived(BigDecimal.valueOf(quantity));
        receipt.setBatchNo(lot);receipt.setExpiryDate(LocalDate.now().plusYears(1));receipt.setWarehouseId(101L);
        run(keeper,()->procurement.receiveOrder(receipt));
        var stock=as(keeper,()->context.getBean(ProcurementInventoryService.class).findBatch(reagent,lot,101L));
        return new Received(order.orderId(),order.itemId(),stock.getId());
    }
    Long ship(Long batchId,long reagent,int quantity){
        var req=as(tech,()->{
            var request=new PurchaseRequisition();request.setFromDeptId(11L);request.setUrgency(UrgencyEnum.NORMAL);
            var item=new PurchaseRequisitionItem();item.setReagentId(reagent);item.setQtyRequested(BigDecimal.valueOf(quantity));
            request.setItems(List.of(item));return procurement.createRequisition(request);
        });
        run(tech,()->procurement.submitRequisition(req.getId()));run(keeper,()->procurement.reviewRequisition(req.getId(),"APPROVE",null));
        var item=as(keeper,()->procurement.getRequisitionItems(req.getId()).get(0));
        var request=new PickAndShipRequest();request.setTargetWarehouseId(102L);var line=new PickAndShipRequest.PickItem();
        line.setReqItemId(item.getId());line.setSourceBatchId(batchId);line.setPickQty(BigDecimal.valueOf(quantity));request.setItems(List.of(line));
        run(keeper,()->procurement.pickAndShip(req.getId(),request));return req.getId();
    }
    Long sign(Long req,long reagent,String lot){
        run(tech,()->settlements.receiveAndSettleRequisition(req));
        return as(tech,()->context.getBean(ProcurementInventoryService.class).findBatch(reagent,lot,102L).getId());
    }
    @Test void flexiblePurchaseToConsumeCreatesOneBillAndExhaustedLotCanReceiveAgain(){
        var received=receive("消耗结算",1,"QA-LOT",4);var first=sign(ship(received.batchId(),1,2),1,"QA-LOT");
        assertEquals(0,count("settlement_record"));run(tech,()->consumption.recordConsumption(first,null,new BigDecimal("2"),"ROUTINE",null));
        assertEquals(1,count("settlement_record"));assertEquals(UdiStatusEnum.CONSUMED,batch(first).getUdiStatus());
        var second=sign(ship(received.batchId(),1,2),1,"QA-LOT");assertNotEquals(first,second);
        assertEquals(UdiStatusEnum.IN_STOCK,batch(second).getUdiStatus());assertEquals(UdiStatusEnum.CONSUMED,batch(first).getUdiStatus());
        run(tech,()->consumption.recordConsumption(second,null,new BigDecimal("2"),"ROUTINE",null));
        assertEquals(2,count("settlement_record"));assertEquals(new BigDecimal("40.00"),as(finance,()->jdbc.queryForObject("SELECT SUM(amount) FROM settlement_record",BigDecimal.class)));
        assertEquals(0,batch(received.batchId()).getStock().signum());assertEquals(0,batch(second).getStock().signum());
    }
    @Test void allFourPoliciesChargeOnlyAtTheirChosenStage(){
        for(String mode:List.of("验收结算","领用结算","调拨结算","消耗结算")){
            long before=count("settlement_record");String lot="QA-"+UUID.randomUUID().toString().substring(0,8);
            var received=receive(mode,1,lot,1);assertEquals(before+(mode.equals("验收结算")?1:0),count("settlement_record"));
            var target=sign(ship(received.batchId(),1,1),1,lot);
            assertEquals(before+(mode.equals("消耗结算")?0:1),count("settlement_record"));
            run(tech,()->consumption.recordConsumption(target,null,BigDecimal.ONE,"ROUTINE",null));
            assertEquals(before+1,count("settlement_record"));
        }
        assertEquals(4,count("settlement_record"));assertEquals(4,count("settlement_event"));
    }
    @Test void forcedInstrumentMustBeOwnedAndInTheSameDepartmentWarehouse(){
        var received=receive("消耗结算",2,"QA-INSTRUMENT",2);var target=sign(ship(received.batchId(),2,2),2,"QA-INSTRUMENT");
        run(tech,()->assertThrows(BizException.class,()->consumption.recordConsumption(target,null,BigDecimal.ONE,"ROUTINE",null)));
        run(tech,()->assertThrows(BizException.class,()->consumption.recordConsumption(target,302L,BigDecimal.ONE,"ROUTINE",null)));
        assertEquals(new BigDecimal("2.00"),batch(target).getStock());assertEquals(0,count("settlement_record"));
        run(tech,()->consumption.recordConsumption(target,301L,BigDecimal.ONE,"ROUTINE",null));
        assertEquals(1,count("settlement_record"));assertEquals(1,count("device_batch_load"));
        assertEquals(UdiStatusEnum.IN_USE,batch(target).getUdiStatus());
    }
    @Test void mixedPoliciesForSamePhysicalLotKeepDistinctOrigins(){
        var first=receive("消耗结算",1,"QA-MIX",1);var second=receive("验收结算",1,"QA-MIX",1);
        assertNotEquals(first.batchId(),second.batchId());assertEquals(1,count("settlement_record"));
        var firstTarget=sign(ship(first.batchId(),1,1),1,"QA-MIX");
        var secondTarget=sign(ship(second.batchId(),1,1),1,"QA-MIX");assertNotEquals(firstTarget,secondTarget);
        run(tech,()->consumption.recordConsumption(firstTarget,null,BigDecimal.ONE,"ROUTINE",null));
        run(tech,()->consumption.recordConsumption(secondTarget,null,BigDecimal.ONE,"ROUTINE",null));
        assertEquals(2,count("settlement_record"));assertEquals(new BigDecimal("20.00"),as(finance,()->jdbc.queryForObject("SELECT SUM(amount) FROM settlement_record",BigDecimal.class)));
    }
    @Test void sameConsumptionEventCannotBillTwiceAndManualRetryIsRejected(){
        var received=receive("消耗结算",1,"QA-IDEMPOTENT",2);var target=sign(ship(received.batchId(),1,2),1,"QA-IDEMPOTENT");
        run(tech,()->consumption.recordConsumption(target,null,BigDecimal.ONE,"ROUTINE",null));
        Long source=as(finance,()->jdbc.queryForObject("SELECT id FROM consumption_record WHERE record_type='CONSUME'",Long.class));
        run(tech,()->new org.springframework.transaction.support.TransactionTemplate(context.getBean(org.springframework.transaction.PlatformTransactionManager.class))
                .execute(status->{context.publishEvent(new com.zlth.ivd.common.event.StockBusinessRecordedEvent(source,"CONSUME"));return null;}));
        run(finance,()->assertThrows(BizException.class,()->settlements.settleConsumption(source)));
        assertEquals(1,count("settlement_record"));assertEquals(1,count("settlement_event"));assertEquals(new BigDecimal("1.00"),batch(target).getStock());
    }
    @Test void billingEvidenceFailureRollsBackConsumptionStockAndState(){
        var received=receive("消耗结算",1,"QA-ROLLBACK",2);var target=sign(ship(received.batchId(),1,2),1,"QA-ROLLBACK");
        new JdbcTemplate(context.getBean(DataSource.class)).execute("ALTER TABLE settlement_event ALTER COLUMN reason VARCHAR(1)");
        run(tech,()->assertThrows(RuntimeException.class,()->consumption.recordConsumption(target,null,new BigDecimal("2"),"ROUTINE",null)));
        assertEquals(new BigDecimal("2.00"),batch(target).getStock());assertEquals(UdiStatusEnum.IN_STOCK,batch(target).getUdiStatus());
        assertEquals(0,count("settlement_record"));assertEquals(0,count("settlement_event"));
        assertEquals(0,as(finance,()->jdbc.queryForObject("SELECT COUNT(*) FROM consumption_record WHERE record_type='CONSUME'",Long.class)));
    }
    @Test void unknownHistoricalPolicyIsNotGuessedOrAutoCharged(){
        var received=receive("消耗结算",1,"QA-LEGACY",1);
        run(keeper,()->jdbc.update("UPDATE reagent_batch SET settlement_mode=NULL WHERE id=?",received.batchId()));
        var target=sign(ship(received.batchId(),1,1),1,"QA-LEGACY");
        run(tech,()->consumption.recordConsumption(target,null,BigDecimal.ONE,"ROUTINE",null));
        assertEquals(0,count("settlement_record"));
        Long source=as(finance,()->jdbc.queryForObject("SELECT id FROM consumption_record WHERE record_type='CONSUME'",Long.class));
        run(finance,()->assertThrows(BizException.class,()->settlements.settleConsumption(source)));
    }
    @Test void reversalHasReasonAndImmutableBusinessEventWithoutRestocking(){
        var received=receive("消耗结算",1,"QA-REVERSE",2);var target=sign(ship(received.batchId(),1,2),1,"QA-REVERSE");
        run(tech,()->consumption.recordConsumption(target,null,BigDecimal.ONE,"ROUTINE",null));
        Long id=as(finance,()->jdbc.queryForObject("SELECT id FROM settlement_record",Long.class));
        run(finance,()->assertThrows(BizException.class,()->settlements.reverseSettlement(id," ")));
        run(finance,()->settlements.reverseSettlement(id,"虚构核对冲销原因"));
        var history=as(finance,()->settlements.history(id));assertEquals(2,history.size());assertEquals("REVERSED",history.get(1).toStatus());
        assertEquals("虚构核对冲销原因",history.get(1).reason());assertEquals(new BigDecimal("1.00"),batch(target).getStock());
        run(finance,()->assertThrows(BizException.class,()->settlements.reverseSettlement(id,"重复")));assertEquals(2,count("settlement_event"));
    }
    @Test void oneFailedBatchReceiptDoesNotRollBackAnotherSuccessfulReceipt(){
        var received=receive("消耗结算",1,"QA-BATCH",3);
        var good=ship(received.batchId(),1,1);var bad=ship(received.batchId(),1,1);
        run(keeper,()->jdbc.update("UPDATE consumption_record SET quantity=0 WHERE requisition_id=? AND record_type='TRANSFER'",bad));
        var result=as(tech,()->settlements.batchReceiveAndSettle());assertEquals(1,result.get("success"));assertEquals(1,result.get("failed"));
        assertEquals(RequisitionStatusEnum.FULFILLED,as(keeper,()->procurement.getRequisitionDetail(good).getStatus()));
        assertEquals(RequisitionStatusEnum.SHIPPED,as(keeper,()->procurement.getRequisitionDetail(bad).getStatus()));
        assertEquals(1,count("requisition_transfer_receipt"));
    }
    @Test void wrongDepartmentAndInstitutionCannotSignOrConsume(){
        var received=receive("消耗结算",1,"QA-ISOLATION",2);var req=ship(received.batchId(),1,1);
        var otherDept=new InstitutionContext.Scope(1L,12L,1001L,"LAB_TECH");var otherOwner=new InstitutionContext.Scope(2L,21L,1001L,"LAB_TECH");
        for(var scope:List.of(otherDept,otherOwner))run(scope,()->assertThrows(BizException.class,()->settlements.receiveAndSettleRequisition(req)));
        var target=sign(req,1,"QA-ISOLATION");
        for(var scope:List.of(otherDept,otherOwner))run(scope,()->assertThrows(BizException.class,()->consumption.recordConsumption(target,null,BigDecimal.ONE,"ROUTINE",null)));
        assertEquals(new BigDecimal("1.00"),batch(target).getStock());assertEquals(0,count("settlement_record"));
    }
    @Test void missingBatchExpiryOrOverReceiptCannotMarkOrderReceived(){
        var order=ordered("消耗结算",1,2);var request=new ReceiveItemRequest();request.setItemId(order.itemId());request.setQtyReceived(BigDecimal.ONE);
        request.setExpiryDate(LocalDate.now().plusYears(1));request.setWarehouseId(101L);
        run(keeper,()->assertThrows(BizException.class,()->procurement.receiveOrder(request)));
        request.setBatchNo("QA-INVALID");request.setExpiryDate(null);
        run(keeper,()->assertThrows(BizException.class,()->procurement.receiveOrder(request)));
        request.setExpiryDate(LocalDate.now().plusYears(1));request.setQtyReceived(new BigDecimal("3"));
        run(keeper,()->assertThrows(BizException.class,()->procurement.receiveOrder(request)));
        assertEquals(0,count("reagent_batch"));assertEquals(0,count("consumption_record"));
        assertEquals(0,as(keeper,()->procurement.getOrderDetail(order.orderId()).getReceivedQuantity()).signum());
    }
    @Test void failedInboundBillingRollsBackOrderQuantityAndStock(){
        var order=ordered("验收结算",1,1);
        new JdbcTemplate(context.getBean(DataSource.class)).execute("ALTER TABLE settlement_event ALTER COLUMN reason VARCHAR(1)");
        var request=new ReceiveItemRequest();request.setItemId(order.itemId());request.setQtyReceived(BigDecimal.ONE);request.setBatchNo("QA-INBOUND-ROLLBACK");
        request.setExpiryDate(LocalDate.now().plusYears(1));request.setWarehouseId(101L);
        run(keeper,()->assertThrows(RuntimeException.class,()->procurement.receiveOrder(request)));
        assertEquals(0,count("reagent_batch"));assertEquals(0,count("consumption_record"));assertEquals(0,count("settlement_record"));
        assertEquals(0,as(keeper,()->procurement.getOrderDetail(order.orderId()).getItems().get(0).getQtyReceived()).signum());
    }
    @Test void reversalEvidenceFailureDoesNotChangeFinancialState(){
        var received=receive("消耗结算",1,"QA-REVERSE-ROLLBACK",1);var target=sign(ship(received.batchId(),1,1),1,"QA-REVERSE-ROLLBACK");
        run(tech,()->consumption.recordConsumption(target,null,BigDecimal.ONE,"ROUTINE",null));
        Long id=as(finance,()->jdbc.queryForObject("SELECT id FROM settlement_record",Long.class));
        new JdbcTemplate(context.getBean(DataSource.class)).execute("ALTER TABLE settlement_event ADD CONSTRAINT reject_reverse CHECK(to_status<>'REVERSED')");
        run(finance,()->assertThrows(RuntimeException.class,()->settlements.reverseSettlement(id,"虚构失败回滚")));
        assertEquals("ACTIVE",as(finance,()->jdbc.queryForObject("SELECT status FROM settlement_record WHERE id=?",String.class,id)));
        assertEquals(1,count("settlement_event"));
    }
    @Test void repeatedReceiptDoesNotIncreaseDepartmentStockOrBillTwice(){
        var received=receive("领用结算",1,"QA-RECEIPT-ONCE",1);var req=ship(received.batchId(),1,1);var target=sign(req,1,"QA-RECEIPT-ONCE");
        run(tech,()->assertThrows(BizException.class,()->settlements.receiveAndSettleRequisition(req)));
        assertEquals(new BigDecimal("1.00"),batch(target).getStock());assertEquals(1,count("settlement_record"));assertEquals(1,count("requisition_transfer_receipt"));
    }
    @Test void legacyShipmentWithoutExactStockIdCannotGuessAcrossMultipleOrigins(){
        var first=receive("消耗结算",1,"QA-AMBIGUOUS",1);receive("消耗结算",1,"QA-AMBIGUOUS",1);var req=ship(first.batchId(),1,1);
        run(keeper,()->jdbc.update("UPDATE consumption_record SET batch_id=NULL WHERE requisition_id=?",req));
        run(tech,()->assertThrows(BizException.class,()->settlements.receiveAndSettleRequisition(req)));
        assertEquals(0,count("requisition_transfer_receipt"));assertEquals(0,count("settlement_record"));
    }
    @Test void concurrentConsumersCannotOversellOrBillBeyondActualStock()throws Exception{
        var received=receive("消耗结算",1,"QA-CONCURRENT-CONSUME",1);var target=sign(ship(received.batchId(),1,1),1,"QA-CONCURRENT-CONSUME");
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var barrier=new java.util.concurrent.CyclicBarrier(2);
        try{
            var action=(java.util.concurrent.Callable<Integer>)()->{barrier.await();try{run(tech,()->consumption.recordConsumption(target,null,BigDecimal.ONE,"ROUTINE",null));return 1;}catch(BizException expected){return 0;}};
            var one=pool.submit(action);var two=pool.submit(action);
            assertEquals(1,one.get(15,java.util.concurrent.TimeUnit.SECONDS)+two.get(15,java.util.concurrent.TimeUnit.SECONDS));
        }finally{pool.shutdownNow();}
        assertEquals(0,batch(target).getStock().signum());assertEquals(1,count("settlement_record"));assertEquals(1,count("settlement_event"));
    }
    @Test void concurrentPartialReceiptsRespectOrderedQuantityAndRetainOneOrigin()throws Exception{
        var order=ordered("验收结算",1,2);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var barrier=new java.util.concurrent.CyclicBarrier(2);
        try{
            var action=(java.util.concurrent.Callable<Void>)()->{barrier.await();var request=new ReceiveItemRequest();request.setItemId(order.itemId());
                request.setOrderId(order.orderId());request.setWarehouseId(101L);request.setQtyReceived(BigDecimal.ONE);request.setBatchNo("QA-CONCURRENT-INBOUND");
                request.setExpiryDate(LocalDate.now().plusYears(1));run(keeper,()->procurement.receiveOrder(request));return null;};
            var one=pool.submit(action);var two=pool.submit(action);one.get(15,java.util.concurrent.TimeUnit.SECONDS);two.get(15,java.util.concurrent.TimeUnit.SECONDS);
        }finally{pool.shutdownNow();}
        assertEquals(1,count("reagent_batch"));assertEquals(2,count("settlement_record"));
        assertEquals(new BigDecimal("2.00"),as(keeper,()->procurement.getOrderDetail(order.orderId()).getReceivedQuantity()));
        assertEquals(PurchaseOrderStatusEnum.COMPLETED,as(keeper,()->procurement.getOrderDetail(order.orderId()).getStatus()));
    }
    @Configuration @EnableTransactionManagement(proxyTargetClass=true)
    @MapperScan({"com.zlth.ivd.catalog.mapper","com.zlth.ivd.procurement.mapper","com.zlth.ivd.inventory.mapper",
            "com.zlth.ivd.consumption.mapper","com.zlth.ivd.settlement.mapper","com.zlth.ivd.udi.mapper","com.zlth.ivd.system.mapper","com.zlth.ivd.common.mapper"})
    @Import({com.zlth.ivd.common.service.StockCommandService.class,com.zlth.ivd.common.audit.OperationAuditLogWriter.class,com.zlth.ivd.catalog.service.impl.CatalogReferenceServiceImpl.class,
            com.zlth.ivd.inventory.service.impl.ProcurementInventoryServiceImpl.class,com.zlth.ivd.inventory.service.impl.ConsumptionInventoryServiceImpl.class,
            com.zlth.ivd.inventory.service.impl.InstrumentUsageServiceImpl.class,com.zlth.ivd.inventory.service.impl.UdiBatchStateStoreImpl.class,
            com.zlth.ivd.system.service.impl.ProcurementOrgServiceImpl.class,
            com.zlth.ivd.udi.service.UdiStateMachine.class,com.zlth.ivd.udi.service.UniqueCodeService.class,
            com.zlth.ivd.procurement.service.impl.ProcurementServiceImpl.class,
            com.zlth.ivd.consumption.service.impl.ProcurementConsumptionServiceImpl.class,com.zlth.ivd.consumption.service.impl.ConsumptionServiceImpl.class,
            com.zlth.ivd.settlement.service.impl.SettlementServiceImpl.class,com.zlth.ivd.settlement.service.impl.StockBillingStatusServiceImpl.class})
    static class Config {
        @Bean com.fasterxml.jackson.databind.ObjectMapper commandJson(){return new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();}
        @Bean DataSource source(){var source=new DriverManagerDataSource("jdbc:h2:mem:flow"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
            new ResourceDatabasePopulator(new ClassPathResource("supply-flow-schema.sql")).execute(source);return source;}
        @Bean JdbcTemplate jdbc(DataSource source){return new ScopedJdbcTemplate(source,true);}
        @Bean org.springframework.transaction.PlatformTransactionManager manager(DataSource source){return new DataSourceTransactionManager(source);}
        @Bean SqlSessionFactory factory(DataSource source)throws Exception{
            var configuration=new MybatisConfiguration();configuration.setMapUnderscoreToCamelCase(true);
            var plugin=new MybatisPlusInterceptor();plugin.addInnerInterceptor(new InstitutionSqlInterceptor());plugin.addInnerInterceptor(new OptimisticLockerInnerInterceptor());plugin.addInnerInterceptor(new PaginationInnerInterceptor());
            var factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(source);factory.setConfiguration(configuration);factory.setPlugins(plugin);
            var globals=new com.baomidou.mybatisplus.core.config.GlobalConfig();globals.setMetaObjectHandler(new com.zlth.ivd.common.config.MybatisPlusConfig().metaObjectHandler());factory.setGlobalConfig(globals);
            return factory.getObject();
        }
        @Bean SqlSessionTemplate template(SqlSessionFactory factory){return new SqlSessionTemplate(factory);}
        @Bean com.zlth.ivd.system.service.ResearchProjectService projects(){return mock(com.zlth.ivd.system.service.ResearchProjectService.class);}
        @Bean com.zlth.ivd.license.service.SupplierReferenceService suppliers(){
            var service=mock(com.zlth.ivd.license.service.SupplierReferenceService.class);var supplier=new com.zlth.ivd.license.entity.Supplier();supplier.setId(1L);supplier.setName("QA供应商");
            supplier.setIsActive(true);
            when(service.findById(1L)).thenReturn(supplier);
            var inactive = new com.zlth.ivd.license.entity.Supplier();
            inactive.setId(2L);
            inactive.setIsActive(false);
            when(service.findById(2L)).thenReturn(inactive);
            when(service.findAll()).thenReturn(List.of(supplier));return service;
        }
    }
}
