package com.zlth.ivd.server.service;

import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.common.security.InstitutionContext;
import com.zlth.ivd.procurement.dto.*;
import com.zlth.ivd.procurement.service.ProcurementService;
import com.zlth.ivd.inventory.service.InventoryService;
import com.zlth.ivd.settlement.service.SettlementService;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

/** 虚构数据下的真实事务、行锁、流水、唯一码及结算守恒回归。 */
class StockFlowSafetyIntegrationTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate db;
    ProcurementService procurement;
    InventoryService inventory;
    SettlementService billing;
    final InstitutionContext.Scope keeper=new InstitutionContext.Scope(1,null,1002L,"STOREKEEPER");
    final InstitutionContext.Scope tech=new InstitutionContext.Scope(1,11L,1001L,"LAB_TECH");
    final InstitutionContext.Scope otherTech=new InstitutionContext.Scope(1,12L,1005L,"LAB_TECH");
    protected AnnotationConfigApplicationContext fixtureContext(){
        return new AnnotationConfigApplicationContext(ResourceBoundaryIntegrationTest.Config.class);
    }
    @BeforeEach void setup(){
        context=fixtureContext();
        db=new JdbcTemplate(context.getBean(DataSource.class));procurement=context.getBean(ProcurementService.class);
        inventory=context.getBean(InventoryService.class);billing=context.getBean(SettlementService.class);
        db.update("INSERT INTO reagent_batch(id,institution_id,reagent_id,batch_no,warehouse_id,stock,unit,unit_price,udi_status,expiry_date,version,settlement_mode) VALUES(5001,1,1,'QA-LOT',101,10,'box',10,'IN_STOCK','2039-01-01',0,'领用结算')");
        db.update("INSERT INTO purchase_requisition(id,institution_id,req_no,from_dept_id,to_warehouse_id,status,urgency) VALUES(6001,1,'QA-SHIP-1',11,102,'REVIEWED','NORMAL'),(6002,1,'QA-SHIP-2',12,103,'REVIEWED','NORMAL')");
        db.update("INSERT INTO purchase_requisition_item(id,institution_id,req_id,line_no,reagent_id,qty_requested,qty_fulfilled,unit_price,amount) VALUES(6101,1,6001,1,1,2,0,10,20),(6102,1,6002,1,1,2,0,10,20)");
        db.execute("CREATE TABLE shortage_order(id BIGINT PRIMARY KEY,institution_id BIGINT,shortage_no VARCHAR(64),req_id BIGINT,from_dept_id BIGINT,status VARCHAR(32),urgency VARCHAR(32),suggested_supplier_id BIGINT,po_id BIGINT,created_by BIGINT,created_at TIMESTAMP,updated_at TIMESTAMP)");
        db.execute("CREATE TABLE shortage_order_item(id BIGINT PRIMARY KEY,institution_id BIGINT,shortage_order_id BIGINT,line_no INT,req_item_id BIGINT,reagent_id BIGINT,qty_shortage DECIMAL(10,2),est_unit_price DECIMAL(12,2),created_at TIMESTAMP)");
        db.update("INSERT INTO shortage_order(id,institution_id,shortage_no,req_id,from_dept_id,status,urgency) VALUES(7001,1,'QA-SHORT',6001,11,'PENDING_PROCUREMENT','NORMAL'),(7002,2,'QA-FOREIGN',6003,21,'PENDING_PROCUREMENT','NORMAL')");
        db.update("INSERT INTO shortage_order_item(id,institution_id,shortage_order_id,line_no,req_item_id,reagent_id,qty_shortage,est_unit_price) VALUES(7101,1,7001,1,6101,1,2,10)");
    }
    @AfterEach void close(){if(context!=null)context.close();SecurityContextHolder.clearContext();assertTrue(InstitutionContext.current().isEmpty());}
    <T>T as(InstitutionContext.Scope scope,Supplier<T> operation){
        var auth=new UsernamePasswordAuthenticationToken(scope.actorId(),"QA",List.of(new SimpleGrantedAuthority("ROLE_"+scope.role())));
        auth.setDetails("QA-operator");SecurityContextHolder.getContext().setAuthentication(auth);
        try{return InstitutionContext.within(scope,operation);}finally{SecurityContextHolder.clearContext();}
    }
    void run(InstitutionContext.Scope scope,Runnable operation){as(scope,()->{operation.run();return null;});}
    long count(String table){return db.queryForObject("SELECT COUNT(*) FROM "+table,Long.class);}
    BigDecimal stock(){return db.queryForObject("SELECT stock FROM reagent_batch WHERE id=5001",BigDecimal.class);}
    void managedCodes(int number){
        db.update("UPDATE reagent_catalog SET enable_unique_code=1 WHERE id=1");
        for(int i=0;i<number;i++)db.update("INSERT INTO udi_unique_code(id,institution_id,unique_code,batch_id,reagent_id,batch_no,status) VALUES(?,1,?,5001,1,'QA-LOT','IN_STOCK')",8000+i,"QA-CODE-"+i);
    }
    void ship(long req,long item,String amount){
        ship(req,item,5001L,amount);
    }
    void ship(long req,long item,long source,String amount){
        var command=new PickAndShipRequest();var line=new PickAndShipRequest.PickItem();line.setReqItemId(item);line.setSourceBatchId(source);line.setPickQty(new BigDecimal(amount));command.setItems(List.of(line));
        run(keeper,()->procurement.pickAndShip(req,command));
    }
    void receipt(){run(tech,()->billing.receiveAndSettleRequisition(6001L));}
    @Test void catalogDeletionAndNewPurchaseShareTheCatalogRowLock() throws Exception {
        var catalogs = context.getBean(com.zlth.ivd.catalog.service.CatalogReferenceService.class);
        var guard = new CatalogDeletionGuardImpl(context.getBean(org.springframework.jdbc.core.JdbcTemplate.class));
        var tx = new org.springframework.transaction.support.TransactionTemplate(
                context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        var pool = Executors.newSingleThreadExecutor();
        var started = new CountDownLatch(1);
        var future = new java.util.concurrent.atomic.AtomicReference<Future<Boolean>>();
        try {
            as(keeper, () -> tx.execute(status -> {
                assertNotNull(catalogs.lockForBusiness(2L));
                guard.requireDeletable(2L);
                future.set(pool.submit(() -> {
                    started.countDown();
                    var request = manualOrder();
                    request.getItems().get(0).setReagentId(2L);
                    try { as(keeper, () -> procurement.createManualOrder(request)); return true; }
                    catch (BizException refused) { return false; }
                }));
                try {
                    assertTrue(started.await(5,TimeUnit.SECONDS));
                    assertThrows(TimeoutException.class, () -> future.get().get(300,TimeUnit.MILLISECONDS));
                } catch (InterruptedException e) { throw new RuntimeException(e); }
                context.getBean(com.zlth.ivd.catalog.mapper.ReagentCatalogMapper.class).deleteById(2L);
                return null;
            }));
            assertFalse(future.get().get(10,TimeUnit.SECONDS));
            assertEquals(0,count("purchase_order"));
            assertEquals(0,count("purchase_order_item"));
        } finally { pool.shutdownNow(); }
    }

    @Test void deletionCurrentReadSeesPurchaseCommittedWhileWaitingForCatalogLock() throws Exception {
        var catalogs = context.getBean(com.zlth.ivd.catalog.service.CatalogReferenceService.class);
        var guard = new CatalogDeletionGuardImpl(context.getBean(org.springframework.jdbc.core.JdbcTemplate.class));
        var tx = new org.springframework.transaction.support.TransactionTemplate(
                context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        var pool = Executors.newSingleThreadExecutor();
        var future = new java.util.concurrent.atomic.AtomicReference<Future<Boolean>>();
        try {
            as(keeper, () -> tx.execute(status -> {
                var request = manualOrder();
                request.getItems().get(0).setReagentId(2L);
                procurement.createManualOrder(request);
                future.set(pool.submit(() -> as(keeper, () -> tx.execute(inner -> {
                    assertNotNull(catalogs.lockForBusiness(2L));
                    assertThrows(BizException.class, () -> guard.requireDeletable(2L));
                    return true;
                }))));
                assertThrows(TimeoutException.class, () -> future.get().get(300,TimeUnit.MILLISECONDS));
                return null;
            }));
            assertTrue(future.get().get(10,TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }
    @Test void allProcurementEntrypointsEnforceLicenseEligibilityAndExplicitRenewalRestoresIt() throws Exception {
        db.execute("CREATE TABLE supplier(id BIGINT PRIMARY KEY,institution_id BIGINT,supplier_code VARCHAR(64),name VARCHAR(100),short_name VARCHAR(100),contact_person VARCHAR(100),contact_phone VARCHAR(100),contact_email VARCHAR(100),address VARCHAR(100),is_active BOOLEAN,created_at TIMESTAMP,updated_at TIMESTAMP)");
        db.update("INSERT INTO supplier(id,institution_id,name,is_active) VALUES(1,1,'QA supplier',TRUE)");
        db.execute("CREATE TABLE supplier_license(id BIGINT PRIMARY KEY,institution_id BIGINT,supplier_id BIGINT,license_type VARCHAR(32),cert_no VARCHAR(128),issuing_authority VARCHAR(256),issue_date DATE,expire_date DATE,status VARCHAR(32),attachments VARCHAR(4000),created_at TIMESTAMP,updated_at TIMESTAMP)");
        db.execute("CREATE TABLE license_change_log(id BIGINT PRIMARY KEY,institution_id BIGINT,license_id BIGINT,change_type VARCHAR(32),old_expire_date DATE,new_expire_date DATE,operator_id BIGINT,operator_name VARCHAR(64),note VARCHAR(4000),created_at TIMESTAMP)");
        db.execute("CREATE TABLE smart_replenish_suggestion(id BIGINT PRIMARY KEY,institution_id BIGINT,reagent_id BIGINT,reagent_name VARCHAR(200),specification VARCHAR(128),manufacturer VARCHAR(128),current_stock DECIMAL(10,2),daily_consumption DECIMAL(10,2),safety_stock DECIMAL(10,2),suggested_qty DECIMAL(10,2),unit VARCHAR(32),supplier VARCHAR(100),analysis_days INT,batch_no VARCHAR(64),status VARCHAR(32),created_at TIMESTAMP,updated_at TIMESTAMP)");
        db.update("INSERT INTO smart_replenish_suggestion(id,institution_id,reagent_id,status) VALUES(7991,1,1,'PENDING')");
        var realEligibility = new com.zlth.ivd.license.service.impl.SupplierReferenceServiceImpl(
                context.getBean(com.zlth.ivd.license.mapper.SupplierMapper.class),
                context.getBean(com.zlth.ivd.license.mapper.SupplierLicenseMapper.class));
        var supplierApi = context.getBean(com.zlth.ivd.license.service.SupplierReferenceService.class);
        org.mockito.Mockito.doAnswer(call -> {
            realEligibility.requireProcurementEligible(call.getArgument(0));
            return null;
        }).when(supplierApi).requireProcurementEligible(org.mockito.ArgumentMatchers.anyLong());
        var direct = new DirectReceiveRequest();
        direct.setSupplierId(1); direct.setWarehouseId(101L);
        var line = new DirectReceiveRequest.DirectReceiveItem();
        line.setReagentId(1L);line.setQtyReceived(BigDecimal.ONE);line.setBatchNo("QA-ELIGIBILITY");
        line.setExpiryDate(LocalDate.now().plusYears(1));direct.setItems(List.of(line));
        var replenishment = List.of(Map.<String,Object>of("id","7991","reagentId","1","supplierId",1,"suggestedQty",1));
        for (String status : List.of("MISSING", "ACTIVE", "FROZEN")) {
            if (!status.equals("MISSING")) {
                if (status.equals("ACTIVE")) db.update("INSERT INTO supplier_license(id,institution_id,supplier_id,license_type,cert_no,issue_date,expire_date,status,attachments) VALUES(9791,1,1,'OPERATION','QA-OLD',?,?,?,'[{\"name\":\"new.pdf\",\"objectKey\":\"known.pdf\"}]')",
                        LocalDate.now().minusYears(1), LocalDate.now(), status);
                else db.update("UPDATE supplier_license SET status='FROZEN',expire_date=? WHERE id=9791", LocalDate.now().plusYears(1));
            }
            run(keeper, () -> {
                assertThrows(BizException.class, () -> procurement.createManualOrder(manualOrder()));
                assertThrows(BizException.class, () -> procurement.createOrderFromShortage(7001L,1,null,"领用结算",null));
                assertThrows(BizException.class, () -> procurement.receiveDirect(direct));
                assertThrows(BizException.class, () -> procurement.createOrdersFromReplenish(replenishment));
            });
            assertEquals(0,count("purchase_order"));
            assertEquals("PENDING",db.queryForObject("SELECT status FROM smart_replenish_suggestion WHERE id=7991",String.class));
            assertEquals(new BigDecimal("10.00"),stock());
        }
        var licenses = context.getBean(com.zlth.ivd.license.service.LicenseService.class);
        var request = new com.zlth.ivd.license.dto.ApproveLicenseRenewalRequest();
        request.setCertNo("QA-NEW");request.setIssueDate(LocalDate.now());request.setExpireDate(LocalDate.now().plusYears(1));
        request.setAttachmentKey("known.pdf");request.setReason("QA renewal approval");
        run(keeper, () -> assertThrows(BizException.class, () -> licenses.approveRenewal(9791L, request)));
        var admin = new InstitutionContext.Scope(1,null,1000L,"SYSTEM_ADMIN");
        as(admin, () -> licenses.approveRenewal(9791L, request));
        as(admin, () -> licenses.approveRenewal(9791L, request));
        assertEquals(1,count("license_change_log"));
        assertEquals(1000L,db.queryForObject("SELECT operator_id FROM license_change_log",Long.class));
        as(keeper, () -> procurement.createManualOrder(manualOrder()));
        assertEquals(1,count("purchase_order"));
    }
    @Test void requisitionSummaryPreservesFractionalQuantitiesInListAndDetail() {
        db.update("UPDATE purchase_requisition_item SET qty_requested=0.75,qty_fulfilled=0.25,qty_shortage=0.50 WHERE id=6101");
        db.update("INSERT INTO purchase_requisition_item(id,institution_id,req_id,line_no,reagent_id,qty_requested,qty_fulfilled,qty_shortage,unit_price,amount) VALUES(6181,1,6001,2,2,0.75,0.25,0.50,20,15)");
        as(keeper, () -> {
            var detail = procurement.getRequisitionDetail(6001L);
            var listed = procurement.listRequisitions(1, 20, null, "QA-SHIP-1").getRecords()
                    .stream().filter(row -> row.getId().equals(6001L)).findFirst().orElseThrow();
            for (var row : List.of(detail, listed)) {
                assertEquals(0, new BigDecimal("1.50").compareTo(row.getTotalQty()));
                assertEquals(0, new BigDecimal("0.50").compareTo(row.getShippedQty()));
                assertEquals(0, new BigDecimal("1.00").compareTo(row.getShortageQty()));
            }
            return null;
        });
    }
    @Test void fefoOnlyRecommendsBatchesStillWithinBothExpiryDates() {
        var today = LocalDate.now();
        db.update("""
                INSERT INTO reagent_batch(id,institution_id,reagent_id,batch_no,warehouse_id,stock,udi_status,expiry_date,open_expiry_date)
                VALUES(5021,1,1,'QA-PAST',101,2,'IN_STOCK',?,NULL),
                      (5022,1,1,'QA-NO-EXPIRY',101,2,'IN_STOCK',NULL,NULL),
                      (5023,1,1,'QA-OPEN-PAST',101,2,'IN_STOCK',?,?),
                      (5024,1,1,'QA-TODAY',101,2,'IN_STOCK',?,?),
                      (5025,2,3,'QA-OTHER-OWNER',201,2,'IN_STOCK',?,NULL)
                """, today.minusDays(1), today.plusDays(1), today.minusDays(1), today, today, today);
        var rows = as(keeper, () -> inventory.getFefoRecommendations(null, null));
        assertEquals(List.of("QA-TODAY", "QA-LOT"), rows.stream().map(row -> row.get("batchNo")).toList());
    }
    @Test void automaticReceiptRunsAndSettlesEachInstitutionWithoutLeakingContext() {
        ship(6001L, 6101L, "2");
        db.update("INSERT INTO reagent_batch(id,institution_id,reagent_id,batch_no,warehouse_id,stock,unit,unit_price,udi_status,expiry_date,settlement_mode) VALUES(5099,2,3,'QA-LOT',201,8,'box',15,'IN_STOCK','2039-01-01','领用结算')");
        db.update("INSERT INTO purchase_requisition(id,institution_id,req_no,from_dept_id,to_warehouse_id,status,urgency) VALUES(6099,2,'QA-AUTO-2',21,202,'SHIPPED','NORMAL')");
        db.update("INSERT INTO purchase_requisition_item(id,institution_id,req_id,line_no,reagent_id,qty_requested,qty_fulfilled,unit_price,amount) VALUES(6199,2,6099,1,3,2,2,15,30)");
        db.update("""
                INSERT INTO consumption_record(id,institution_id,record_type,batch_no,batch_id,requisition_id,
                  requisition_item_id,reagent_id,warehouse_id,from_warehouse_id,quantity,unit,unit_price,amount,settlement_mode,occurred_at)
                VALUES(9399,2,'TRANSFER','QA-LOT',5099,6099,6199,3,202,201,2,'box',15,30,'领用结算',CURRENT_TIMESTAMP)
                """);
        db.update("UPDATE purchase_requisition SET updated_at=? WHERE id IN (6001,6099)", java.time.LocalDateTime.now().minusHours(48));
        var directory = new com.zlth.ivd.system.service.impl.AuthenticationDirectoryServiceImpl(
                context.getBean(com.zlth.ivd.system.mapper.AuthenticationDirectoryMapper.class));
        var scheduler = new com.zlth.ivd.server.scheduler.AutoReceiveScheduler(billing,
                context.getBean(com.zlth.ivd.procurement.mapper.PurchaseRequisitionMapper.class), directory);
        org.springframework.test.util.ReflectionTestUtils.setField(scheduler, "enabled", true);
        org.springframework.test.util.ReflectionTestUtils.setField(scheduler, "isolationEnabled", true);
        org.springframework.test.util.ReflectionTestUtils.setField(scheduler, "delayHours", 24);
        scheduler.autoReceive();
        assertTrue(InstitutionContext.current().isEmpty());
        assertEquals(List.of("FULFILLED","FULFILLED"),
                db.queryForList("SELECT status FROM purchase_requisition WHERE id IN (6001,6099) ORDER BY id", String.class));
        assertEquals(List.of(1L, 2L), db.queryForList(
                "SELECT institution_id FROM requisition_transfer_receipt ORDER BY institution_id", Long.class));
        assertEquals(List.of(new BigDecimal("20.00"),new BigDecimal("30.00")), db.queryForList(
                "SELECT amount FROM settlement_record ORDER BY institution_id", BigDecimal.class));
        assertEquals(2L, db.queryForObject("SELECT COUNT(*) FROM settlement_record WHERE settled_by IS NULL", Long.class));
        scheduler.autoReceive();
        assertTrue(InstitutionContext.current().isEmpty());
        assertEquals(2, count("requisition_transfer_receipt"));
        assertEquals(2, count("settlement_record"));
    }
    @Test void transferredOpenBatchKeepsItsExpiryAndDoesNotMergeWithUnopenedStock() {
        var today = LocalDate.now();
        run(keeper, () -> inventory.transferBatch(5001L, 102L, BigDecimal.ONE, "NORMAL", null));
        db.update("UPDATE reagent_batch SET open_date=?,open_expiry_date=? WHERE id=5001",
                today.minusDays(1), today.plusDays(2));
        run(keeper, () -> inventory.transferBatch(5001L, 102L, BigDecimal.ONE, "NORMAL", null));
        run(keeper, () -> inventory.transferBatch(5001L, 102L, BigDecimal.ONE, "NORMAL", null));
        assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM reagent_batch WHERE warehouse_id=102 AND batch_no='QA-LOT'", Integer.class));
        assertEquals(today.minusDays(1), db.queryForObject(
                "SELECT open_date FROM reagent_batch WHERE warehouse_id=102 AND open_date IS NOT NULL", java.sql.Date.class).toLocalDate());
        assertEquals(today.plusDays(2), db.queryForObject(
                "SELECT open_expiry_date FROM reagent_batch WHERE warehouse_id=102 AND open_date IS NOT NULL", java.sql.Date.class).toLocalDate());
        assertEquals(new BigDecimal("2.00"), db.queryForObject(
                "SELECT stock FROM reagent_batch WHERE warehouse_id=102 AND open_date IS NOT NULL", BigDecimal.class));
        db.update("UPDATE reagent_batch SET open_expiry_date=? WHERE id=5001", today.minusDays(1));
        run(keeper, () -> assertThrows(BizException.class,
                () -> inventory.transferBatch(5001L, 102L, BigDecimal.ONE, "NORMAL", null)));
        assertEquals(new BigDecimal("7.00"), stock());
    }
    @Test void requisitionReceiptInheritsTheActualShippedBatchOpenDates() {
        var today = LocalDate.now();
        db.update("UPDATE reagent_batch SET open_date=?,open_expiry_date=? WHERE id=5001",
                today.minusDays(3), today.plusDays(1));
        ship(6001L, 6101L, "2");
        receipt();
        assertEquals(today.minusDays(3), db.queryForObject(
                "SELECT open_date FROM reagent_batch WHERE warehouse_id=102 AND batch_no='QA-LOT'", java.sql.Date.class).toLocalDate());
        assertEquals(today.plusDays(1), db.queryForObject(
                "SELECT open_expiry_date FROM reagent_batch WHERE warehouse_id=102 AND batch_no='QA-LOT'", java.sql.Date.class).toLocalDate());
    }
    @Test void concurrentManualAndShortageOrdersShareNumberSequence() throws Exception {
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var manual = pool.submit(() -> {
                start.await();
                return as(keeper, () -> procurement.createManualOrder(manualOrder()).get(0).getPoNo());
            });
            var shortage = pool.submit(() -> {
                start.await();
                return as(keeper, () -> procurement.createOrderFromShortage(7001L, 1, null, "领用结算", null).getPoNo());
            });
            start.countDown();
            String prefix = "PO-" + LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE) + "-";
            assertEquals(Set.of(prefix + "0001", prefix + "0002"),
                    Set.of(manual.get(15, TimeUnit.SECONDS), shortage.get(15, TimeUnit.SECONDS)));
            assertEquals(2, count("purchase_order"));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test void exhaustedOrderSequenceDoesNotWrapOrWriteOrders() {
        String day = LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        db.update("INSERT INTO purchase_order_number_sequence VALUES(1,?,9999)", day);
        run(keeper, () -> assertEquals(40900,
                assertThrows(BizException.class, () -> procurement.createManualOrder(manualOrder())).getErrorCode()));
        assertEquals(0, count("purchase_order"));
        assertEquals(9999, db.queryForObject(
                "SELECT last_number FROM purchase_order_number_sequence WHERE institution_id=1 AND order_day=?",
                Integer.class, day));
    }

    private CreateManualOrderRequest manualOrder() {
        var request = new CreateManualOrderRequest();
        request.setSupplierId(1);
        request.setSettlementMode("领用结算");
        var item = new CreateManualOrderRequest.ManualOrderItem();
        item.setReagentId(1L);
        item.setQuantity(BigDecimal.ONE);
        request.setItems(List.of(item));
        return request;
    }
    @Test void submitAndCancelCannotBothWinFromDraft() throws Exception {
        db.update("UPDATE purchase_requisition SET status='DRAFT' WHERE id=6001");
        var purchaser = new InstitutionContext.Scope(1, null, 1004L, "PURCHASER");
        boolean submitted = assertSingleTransition(
                () -> procurement.submitRequisition(6001L),
                () -> procurement.cancelRequisition(6001L, "QA cancel"), purchaser);
        assertEquals(submitted ? "PENDING_REVIEW" : "CANCELLED", requisitionStatus());
        assertEquals(new BigDecimal("10.00"), stock());
        assertEquals(0, count("consumption_record"));
    }

    @Test void conflictingReviewsCannotBothWinFromPending() throws Exception {
        db.update("UPDATE purchase_requisition SET status='PENDING_REVIEW' WHERE id=6001");
        boolean approved = assertSingleTransition(
                () -> procurement.reviewRequisition(6001L, "APPROVE", "QA approve"),
                () -> procurement.reviewRequisition(6001L, "REJECT", "QA reject"), keeper);
        assertEquals(approved ? "REVIEWED" : "DRAFT", requisitionStatus());
        if (approved) {
            ship(6001L, 6101L, "2");
            run(keeper, () -> assertThrows(BizException.class,
                    () -> procurement.reviewRequisition(6001L, "REJECT", "stale")));
            assertEquals("SHIPPED", requisitionStatus());
            assertEquals(new BigDecimal("8.00"), stock());
        }
    }

    private String requisitionStatus() {
        return db.queryForObject("SELECT status FROM purchase_requisition WHERE id=6001", String.class);
    }

    private boolean assertSingleTransition(Runnable first, Runnable second, InstitutionContext.Scope actor) throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var a = pool.submit(() -> tryTransition(first, actor, ready, start));
            var b = pool.submit(() -> tryTransition(second, actor, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            boolean firstWon = a.get(15, TimeUnit.SECONDS);
            assertNotEquals(firstWon, b.get(15, TimeUnit.SECONDS));
            return firstWon;
        } finally {
            pool.shutdownNow();
        }
    }

    private boolean tryTransition(Runnable action, InstitutionContext.Scope actor, CountDownLatch ready,
                                  CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            run(actor, action);
            return true;
        } catch (BizException rejected) {
            assertEquals(40000, rejected.getErrorCode());
            return false;
        }
    }
    @Test void fractionalCentTransferCannotCreateStockThroughIndependentRounding(){
        run(keeper,()->assertThrows(BizException.class,()->inventory.transferBatch(5001L,102L,new BigDecimal("0.005"),"NORMAL",null)));
        assertEquals(new BigDecimal("10.00"),stock());assertEquals(1,count("reagent_batch"));assertEquals(0,count("consumption_record"));
    }
    @Test void expiredGenericTransferIsRejectedWithoutSideEffects(){
        db.update("UPDATE reagent_batch SET expiry_date=? WHERE id=5001",LocalDate.now().minusDays(1));
        run(keeper,()->assertThrows(BizException.class,()->inventory.transferBatch(5001L,102L,BigDecimal.ONE,"NORMAL",null)));
        assertEquals(new BigDecimal("10.00"),stock());assertEquals(1,count("reagent_batch"));assertEquals(0,count("consumption_record"));
    }
    @Test void normalGenericTransferConservesQuantityAndWritesOneMovement(){
        run(keeper,()->inventory.transferBatch(5001L,102L,new BigDecimal("1.25"),"NORMAL","QA"));
        assertEquals(new BigDecimal("8.75"),stock());assertEquals(new BigDecimal("10.00"),db.queryForObject("SELECT SUM(stock) FROM reagent_batch",BigDecimal.class));
        assertEquals(1,count("consumption_record"));
    }
    @Test void nonmedicalTransferDoesNotCreateUdiStateEvents(){
        db.update("UPDATE reagent_catalog SET material_type='CONSUMABLE' WHERE id=1");
        db.update("UPDATE reagent_batch SET material_type='CONSUMABLE',stock=1 WHERE id=5001");
        run(keeper,()->inventory.transferBatch(5001L,102L,BigDecimal.ONE,"NORMAL",null));
        assertEquals(0,count("udi_status_log"));assertEquals(new BigDecimal("1.00"),db.queryForObject("SELECT SUM(stock) FROM reagent_batch",BigDecimal.class));
    }
    @Test void managedMaterialCannotBePickedInFractionalUnits(){
        managedCodes(10);
        assertThrows(BizException.class,()->ship(6001,6101,"0.5"));
        assertEquals(new BigDecimal("10.00"),stock());assertEquals(0,count("consumption_record"));
    }
    @Test void managedGenericTransferRequiresEnoughCodesAndRollsEverythingBack(){
        managedCodes(1);
        run(keeper,()->assertThrows(BizException.class,()->inventory.transferBatch(5001L,102L,new BigDecimal("2"),"NORMAL",null)));
        assertEquals(new BigDecimal("10.00"),stock());assertEquals(1,count("reagent_batch"));assertEquals(0,count("consumption_record"));
        assertEquals(1L,db.queryForObject("SELECT COUNT(*) FROM udi_unique_code WHERE batch_id=5001",Long.class));
    }
    @Test void transferLedgerFailureRollsBackStockStatesAndCodes(){
        managedCodes(10);
        db.execute("ALTER TABLE consumption_record ADD CONSTRAINT qa_reject_transfer CHECK(record_type<>'TRANSFER')");
        run(keeper,()->assertThrows(RuntimeException.class,()->inventory.transferBatch(5001L,102L,BigDecimal.TEN,"NORMAL",null)));
        assertEquals(new BigDecimal("10.00"),stock());assertEquals(1,count("reagent_batch"));assertEquals(0,count("consumption_record"));
        assertEquals("IN_STOCK",db.queryForObject("SELECT udi_status FROM reagent_batch WHERE id=5001",String.class));
        assertEquals(0,count("udi_status_log"));assertEquals(10L,db.queryForObject("SELECT COUNT(*) FROM udi_unique_code WHERE batch_id=5001",Long.class));
    }
    @Test void codeShortageOnReceiptRollsBackTargetReceiptAndBill(){
        managedCodes(1);ship(6001,6101,"2");assertThrows(BizException.class,this::receipt);
        assertEquals(new BigDecimal("8.00"),stock());assertEquals(1,count("reagent_batch"));assertEquals(0,count("requisition_transfer_receipt"));assertEquals(0,count("settlement_record"));
        assertEquals(1L,db.queryForObject("SELECT COUNT(*) FROM udi_unique_code WHERE batch_id=5001",Long.class));
    }
    @Test void recalledSourceCannotBeRevivedThroughReceipt(){
        ship(6001,6101,"2");
        run(keeper,()->context.getBean(com.zlth.ivd.udi.service.UdiStateMachine.class).transition(5001L,"QA-LOT",com.zlth.ivd.common.enums.UdiStatusEnum.IN_STOCK,com.zlth.ivd.common.enums.UdiStatusEnum.RECALLED,"1002"));
        assertThrows(BizException.class,this::receipt);assertEquals(1,count("reagent_batch"));assertEquals(0,count("requisition_transfer_receipt"));
    }
    @Test void expiryDuringTransitStopsReceipt(){
        ship(6001,6101,"2");db.update("UPDATE reagent_batch SET expiry_date=? WHERE id=5001",LocalDate.now().minusDays(1));
        assertThrows(BizException.class,this::receipt);assertEquals(1,count("reagent_batch"));assertEquals(0,count("requisition_transfer_receipt"));
    }
    @Test void receivedManagedStockHasExactlyItsCodes(){
        managedCodes(3);db.update("UPDATE reagent_batch SET stock=3 WHERE id=5001");
        Object untouchedAt=db.queryForObject("SELECT updated_at FROM udi_unique_code WHERE unique_code='QA-CODE-2'",java.sql.Timestamp.class);
        ship(6001,6101,"2");
        assertEquals(3L,db.queryForObject("SELECT COUNT(*) FROM udi_unique_code WHERE batch_id=5001",Long.class));
        receipt();
        long target=db.queryForObject("SELECT target_batch_id FROM requisition_transfer_receipt",Long.class);
        assertEquals(2L,db.queryForObject("SELECT COUNT(*) FROM udi_unique_code WHERE batch_id=?",Long.class,target));
        assertEquals(1L,db.queryForObject("SELECT COUNT(*) FROM udi_unique_code WHERE batch_id=5001",Long.class));assertEquals(1,count("settlement_record"));
        var codes=db.queryForList("SELECT unique_code,batch_id,status,updated_at FROM udi_unique_code ORDER BY unique_code");
        assertEquals(target,((Number)codes.get(0).get("batch_id")).longValue());
        assertEquals(target,((Number)codes.get(1).get("batch_id")).longValue());
        assertEquals(5001L,((Number)codes.get(2).get("batch_id")).longValue());
        assertTrue(codes.stream().allMatch(code->"IN_STOCK".equals(code.get("status"))));
        assertEquals(untouchedAt,codes.get(2).get("updated_at"));
    }
    @Test void simultaneousDepartmentReceiptsNeverStealEachOthersCodes() throws Exception {
        managedCodes(10);ship(6001,6101,"2");ship(6002,6102,"2");
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(()->{start.await();run(tech,()->billing.receiveAndSettleRequisition(6001L));return null;});
            var b=pool.submit(()->{start.await();run(otherTech,()->billing.receiveAndSettleRequisition(6002L));return null;});start.countDown();
            a.get(15,TimeUnit.SECONDS);b.get(15,TimeUnit.SECONDS);
        }finally{pool.shutdownNow();}
        var targets=db.queryForList("SELECT target_batch_id FROM requisition_transfer_receipt",Long.class);assertEquals(2,targets.size());
        for(Long target:targets)assertEquals(2L,db.queryForObject("SELECT COUNT(*) FROM udi_unique_code WHERE batch_id=?",Long.class,target));
        assertEquals(6L,db.queryForObject("SELECT COUNT(*) FROM udi_unique_code WHERE batch_id=5001",Long.class));
        assertEquals(new BigDecimal("10.00"),db.queryForObject("SELECT SUM(stock) FROM reagent_batch",BigDecimal.class));
    }
    @Test void simultaneousPicksFromDifferentBatchesCannotOversupplyOneRequisition() throws Exception {
        db.update("INSERT INTO reagent_batch(id,institution_id,reagent_id,batch_no,warehouse_id,stock,unit,unit_price,udi_status,expiry_date,version,settlement_mode) VALUES(5002,1,1,'QA-LOT-2',101,10,'box',10,'IN_STOCK','2039-01-01',0,'领用结算')");
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> first=()->{start.await();try{ship(6001,6101,5001,"2");return true;}catch(BizException denied){return false;}};
            Callable<Boolean> second=()->{start.await();try{ship(6001,6101,5002,"2");return true;}catch(BizException denied){return false;}};
            var a=pool.submit(first);var b=pool.submit(second);start.countDown();
            assertNotEquals(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));
        }finally{pool.shutdownNow();}
        assertEquals(new BigDecimal("18.00"),db.queryForObject("SELECT SUM(stock) FROM reagent_batch",BigDecimal.class));
        assertEquals(new BigDecimal("2.00"),db.queryForObject("SELECT qty_fulfilled FROM purchase_requisition_item WHERE id=6101",BigDecimal.class));
        assertEquals(1,count("consumption_record"));
    }
    @Test void legacyReceiptEndpointCannotBypassBilling(){
        ship(6001,6101,"2");var controller=new com.zlth.ivd.procurement.controller.ProcurementController(procurement,billing,context.getBean(com.zlth.ivd.common.service.StockCommandService.class));
        run(keeper,()->controller.receiveRequisition(6001L));assertEquals(1,count("settlement_record"));
    }
    @Test void unknownShortageCannotCreateOrphanOrder(){
        run(keeper,()->assertThrows(BizException.class,()->procurement.createOrderFromShortage(99999L,1,null,"领用结算",null)));
        run(keeper,()->assertThrows(BizException.class,()->procurement.createOrderFromShortage(7002L,1,null,"领用结算",null)));
        assertEquals(0,count("purchase_order"));
    }
    @Test void centralAvailabilityNotEmptyDestinationDeterminesWhetherProcurementIsNeeded(){
        long previous=count("shortage_order");
        // 中心库有10，目标科室库为空，需求2不应被算成缺货。
        run(keeper,()->assertThrows(BizException.class,()->procurement.createShortageFromRequisition(6001L)));
        assertEquals(previous,count("shortage_order"));
    }
    @Test void shortageConversionIsSingleUseAndPersistsSourceIdentity(){
        var po=as(keeper,()->procurement.createOrderFromShortage(7001L,1,null,"领用结算",null));
        run(keeper,()->assertThrows(BizException.class,()->procurement.createOrderFromShortage(7001L,1,null,"领用结算",null)));
        assertEquals(1,count("purchase_order"));assertEquals(7001L,db.queryForObject("SELECT shortage_order_id FROM purchase_order WHERE id=?",Long.class,po.getId()));
        assertEquals(6001L,db.queryForObject("SELECT req_id FROM purchase_order WHERE id=?",Long.class,po.getId()));
    }
    @Test void simultaneousShortageConversionsCreateOnlyOnePurchaseObligation() throws Exception {
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> convert=()->{
                start.await();
                try {as(new InstitutionContext.Scope(1,null,1004L,"PURCHASER"),()->procurement.createOrderFromShortage(7001L,1,null,"领用结算",null));return true;}
                catch(BizException denied){assertEquals(40900,denied.getErrorCode());return false;}
            };
            var a=pool.submit(convert);var b=pool.submit(convert);start.countDown();
            assertNotEquals(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));
        }finally{pool.shutdownNow();}
        assertEquals(1,count("purchase_order"));assertEquals(1,count("purchase_order_item"));
        assertNotNull(db.queryForObject("SELECT po_id FROM shortage_order WHERE id=7001",Long.class));
    }
    @Test void shortageAuditFailureRollsBackOrderAndSourceState(){
        db.execute("ALTER TABLE operation_audit_log ADD CONSTRAINT qa_reject_conversion CHECK(action<>'SHORTAGE_CONVERTED')");
        run(keeper,()->assertThrows(RuntimeException.class,()->procurement.createOrderFromShortage(7001L,1,null,"领用结算",null)));
        assertEquals(0,count("purchase_order"));assertEquals(0,count("purchase_order_item"));assertEquals(0,count("operation_audit_log"));
        assertEquals("PENDING_PROCUREMENT",db.queryForObject("SELECT status FROM shortage_order WHERE id=7001",String.class));
        assertNull(db.queryForObject("SELECT po_id FROM shortage_order WHERE id=7001",Long.class));
    }
    @Test void completedShortagePurchaseClosesItsSource(){
        var po=as(keeper,()->procurement.createOrderFromShortage(7001L,1,null,"领用结算",null));
        long item=db.queryForObject("SELECT id FROM purchase_order_item WHERE po_id=?",Long.class,po.getId());
        var receipt=new ReceiveItemRequest();receipt.setOrderId(po.getId());receipt.setItemId(item);receipt.setQtyReceived(BigDecimal.ONE);receipt.setBatchNo("QA-PURCHASE");receipt.setExpiryDate(LocalDate.now().plusYears(1));receipt.setWarehouseId(101L);
        run(keeper,()->procurement.receiveOrder(receipt));assertEquals("PURCHASING",db.queryForObject("SELECT status FROM shortage_order WHERE id=7001",String.class));
        run(keeper,()->procurement.receiveOrder(receipt));assertEquals("COMPLETED",db.queryForObject("SELECT status FROM shortage_order WHERE id=7001",String.class));
        assertEquals(List.of("SHORTAGE_CONVERTED","SHORTAGE_COMPLETED"),db.queryForList("SELECT action FROM operation_audit_log ORDER BY id",String.class));
    }
}
