package com.zlth.ivd.server.service;

import com.zlth.ivd.common.config.GlobalExceptionHandler;
import com.zlth.ivd.common.security.InstitutionContext;
import com.zlth.ivd.consumption.controller.ConsumptionController;
import com.zlth.ivd.consumption.service.ConsumptionService;
import com.zlth.ivd.inventory.service.InstrumentUsageService;
import com.zlth.ivd.procurement.controller.ProcurementController;
import com.zlth.ivd.procurement.service.ProcurementService;
import com.zlth.ivd.settlement.service.SettlementService;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** 真实Controller、事务和SQL，虚构库存与来源。 */
class StockCommandIntegrationTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate db;
    MockMvc mvc;
    static final InstitutionContext.Scope TECH=new InstitutionContext.Scope(1,11L,1001L,"LAB_TECH");
    static final InstitutionContext.Scope KEEPER=new InstitutionContext.Scope(1,null,1002L,"STOREKEEPER");
    static final String RECEIVE="{\"orderId\":6001,\"itemId\":6101,\"qtyReceived\":2,\"batchNo\":\"QA-RECEIPT\",\"expiryDate\":\"2039-01-01\",\"warehouseId\":101}";

    protected AnnotationConfigApplicationContext fixtureContext(){return new AnnotationConfigApplicationContext(ResourceBoundaryIntegrationTest.Config.class);}
    protected boolean migrationApplied(){return false;}
    @BeforeEach void setup(){
        context=fixtureContext();db=new JdbcTemplate(context.getBean(DataSource.class));
        if(!migrationApplied())
            new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(new org.springframework.core.io.ClassPathResource("stock-command-h2.sql")).execute(context.getBean(DataSource.class));
        db.update("INSERT INTO reagent_batch(id,institution_id,reagent_id,batch_no,warehouse_id,stock,unit,unit_price,udi_status,expiry_date,version,settlement_mode) VALUES(5001,1,1,'QA-CONSUME',102,10,'box',10,'IN_STOCK','2039-01-01',0,'消耗结算'),(5003,2,3,'QA-OTHER',202,10,'box',10,'IN_STOCK','2039-01-01',0,'消耗结算')");
        db.update("INSERT INTO purchase_order(id,institution_id,po_no,supplier_id,source_type,settlement_mode,status,received_quantity,total_amount) VALUES(6001,1,'QA-PO',1,'MANUAL','验收结算','DRAFT',0,100)");
        db.update("INSERT INTO purchase_order_item(id,institution_id,po_id,line_no,reagent_id,supplier_id,material_type,order_quantity,received_quantity,unit_price,amount,version) VALUES(6101,1,6001,1,1,1,'REAGENT',10,0,10,100,0)");
        var commands=context.getBean(com.zlth.ivd.common.service.StockCommandService.class);
        mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                new ConsumptionController(context.getBean(ConsumptionService.class),context.getBean(InstrumentUsageService.class),commands),
                new ProcurementController(context.getBean(ProcurementService.class),context.getBean(SettlementService.class),commands))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @AfterEach void close(){if(context!=null)context.close();SecurityContextHolder.clearContext();assertTrue(InstitutionContext.current().isEmpty());}
    MvcResult call(InstitutionContext.Scope scope,MockHttpServletRequestBuilder request,String key,int code){
        if(key!=null)request.header("Idempotency-Key",key);
        var auth=new UsernamePasswordAuthenticationToken(scope.actorId(),"QA",List.of(new SimpleGrantedAuthority("ROLE_"+scope.role())));
        auth.setDetails("QA-user");SecurityContextHolder.getContext().setAuthentication(auth);
        try{return InstitutionContext.within(scope,()->{
            try{return mvc.perform(request).andExpect(jsonPath("$.code").value(code)).andReturn();}
            catch(Exception error){throw new AssertionError(error);}
        });}finally{SecurityContextHolder.clearContext();}
    }
    MockHttpServletRequestBuilder consume(long batch,String qty){return post("/api/v1/consumption/consume").param("batchId",String.valueOf(batch)).param("qty",qty);}
    @Test void historicalAverageCostDoesNotBecomeAnUnverifiedConsumptionPrice() {
        db.update("UPDATE reagent_batch SET unit_price=NULL,avg_cost=12,settlement_mode='验收结算' WHERE id=5001");
        call(TECH,consume(5001,"1"),"qa-historical-price-unknown",40001);
        assertEquals(new BigDecimal("10.00"),stock(5001));
        assertEquals(0,count("consumption_record"));
        assertEquals(0,count("stock_command_receipt"));
        assertEquals(0,count("settlement_record"));
    }
    @Test void quantityReceiptWithoutBatchOrExpiryRemainsUsableAndIdempotent() {
        db.update("UPDATE reagent_catalog SET material_type='CONSUMABLE',udi_di=NULL,enable_unique_code=0,device_bind_mode='FORCE' WHERE id=1");
        String body = """
                {"supplierId":1,"warehouseId":101,"settlementMode":"CONSUMPTION","items":[{"reagentId":1,"qtyReceived":2.5}]}
                """;
        for (int i=0;i<2;i++)
            call(KEEPER, post("/api/v1/procurement/receive/direct").contentType("application/json").content(body),
                    "qa-quantity-receipt",200);
        var batch = db.queryForMap("SELECT * FROM reagent_batch WHERE batch_no LIKE 'BATCH-CONS-%'");
        assertTrue(batch.get("batch_no").toString().matches("BATCH-CONS-[0-9]{8}-[0-9]{3}"));
        assertNull(batch.get("expiry_date"));
        assertEquals(new BigDecimal("2.50"),batch.get("stock"));
        assertEquals(0,count("udi_status_log"));
        Long source = ((Number)batch.get("id")).longValue();
        var inventory = context.getBean(com.zlth.ivd.inventory.service.InventoryService.class);
        var authentication = new UsernamePasswordAuthenticationToken(KEEPER.actorId(),"QA",
                List.of(new SimpleGrantedAuthority("ROLE_STOREKEEPER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            InstitutionContext.within(KEEPER, () -> {
                assertFalse(inventory.getFefoRecommendations(1L,101L).isEmpty());
                inventory.transferBatch(source,102L,new BigDecimal("1.25"),"NORMAL",null);
                return null;
            });
        } finally {SecurityContextHolder.clearContext();}
        Long target = db.queryForObject("SELECT id FROM reagent_batch WHERE batch_no=? AND warehouse_id=102",
                Long.class,batch.get("batch_no"));
        call(TECH,consume(target,"0.25"),"qa-quantity-consume",200);
        assertEquals(new BigDecimal("1.00"),stock(target));
        assertEquals(0,count("device_batch_load"));
        assertEquals(1,count("settlement_record"));
    }
    @Test void flexibleLoadWithoutDeviceIsVisibleIdempotentAndCanUnload() {
        for (int i = 0; i < 2; i++)
            call(TECH, post("/api/v1/consumption/load").param("batchId", "5001"), null, 200);
        assertEquals(1, count("device_batch_load"));
        assertNull(db.queryForObject("SELECT device_id FROM device_batch_load", Long.class));
        assertEquals("IN_USE", db.queryForObject("SELECT udi_status FROM reagent_batch WHERE id=5001", String.class));
        var instrument = context.getBean(InstrumentUsageService.class);
        assertEquals(1, InstitutionContext.within(TECH, instrument::activeLoads).size());
        Long load = db.queryForObject("SELECT id FROM device_batch_load", Long.class);
        var other = new InstitutionContext.Scope(1, 12L, 1005L, "LAB_TECH");
        call(other, post("/api/v1/consumption/unload").param("loadId", load.toString()), null, 40400);
        call(TECH, post("/api/v1/consumption/unload").param("loadId", load.toString()), null, 200);
        assertTrue(InstitutionContext.within(TECH, instrument::activeLoads).isEmpty());
        assertEquals("IN_STOCK", db.queryForObject("SELECT udi_status FROM reagent_batch WHERE id=5001", String.class));
        db.update("UPDATE reagent_catalog SET device_bind_mode='FORCE' WHERE id=1");
        call(TECH, post("/api/v1/consumption/load").param("batchId", "5001"), null, 40001);
        assertEquals(1, count("device_batch_load"));
    }
    @Test void firstLoadStartsOpenStabilityAndReloadDoesNotExtendIt() {
        var today = java.time.LocalDate.now();
        db.update("UPDATE reagent_catalog SET stability_days_after_open=30 WHERE id=1");
        db.update("UPDATE reagent_batch SET expiry_date=? WHERE id=5001", today.plusDays(5));
        call(TECH, post("/api/v1/consumption/load").param("batchId", "5001"), null, 200);
        assertEquals(today, db.queryForObject("SELECT open_date FROM reagent_batch WHERE id=5001", java.sql.Date.class).toLocalDate());
        assertEquals(today.plusDays(5), db.queryForObject("SELECT open_expiry_date FROM reagent_batch WHERE id=5001", java.sql.Date.class).toLocalDate());
        Long firstLoad = db.queryForObject("SELECT id FROM device_batch_load", Long.class);
        call(TECH, post("/api/v1/consumption/unload").param("loadId", firstLoad.toString()), null, 200);
        db.update("UPDATE reagent_catalog SET stability_days_after_open=1 WHERE id=1");
        call(TECH, post("/api/v1/consumption/load").param("batchId", "5001"), null, 200);
        assertEquals(today, db.queryForObject("SELECT open_date FROM reagent_batch WHERE id=5001", java.sql.Date.class).toLocalDate());
        assertEquals(today.plusDays(5), db.queryForObject("SELECT open_expiry_date FROM reagent_batch WHERE id=5001", java.sql.Date.class).toLocalDate());
        db.update("UPDATE reagent_batch SET open_expiry_date=? WHERE id=5001", today.minusDays(1));
        call(TECH, post("/api/v1/consumption/load").param("batchId", "5001"), null, 40001);
        call(TECH, consume(5001, "1"), "qa-open-stability-expired", 40001);
        assertEquals(new BigDecimal("10.00"), stock(5001));
        assertEquals(2, count("device_batch_load"));
    }
    MockHttpServletRequestBuilder receive(String json){return post("/api/v1/procurement/receive").contentType("application/json").content(json);}
    BigDecimal stock(long id){return db.queryForObject("SELECT stock FROM reagent_batch WHERE id=?",BigDecimal.class,id);}
    long count(String table){return db.queryForObject("SELECT COUNT(*) FROM "+table,Long.class);}

    @Test void repeatedConsumptionCommandDeductsAndBillsOnce(){
        call(TECH,consume(5001,"2"),"qa-consume-same",200);
        call(TECH,consume(5001,"2.00"),"qa-consume-same",200);
        assertEquals(new BigDecimal("8.00"),stock(5001));assertEquals(1,count("consumption_record"));assertEquals(1,count("settlement_record"));
    }
    @Test void insufficientUnitCodesRollBackConsumptionAndAllowCorrectedRetry() {
        db.update("UPDATE reagent_catalog SET enable_unique_code=1 WHERE id=1");
        db.update("""
                INSERT INTO udi_unique_code(id,institution_id,unique_code,batch_id,reagent_id,batch_no,status)
                VALUES(9251,1,'QA-CONSUME-CODE-1',5001,1,'QA-CONSUME','IN_STOCK')
                """);
        call(TECH, consume(5001, "2"), "qa-insufficient-codes", 40900);
        assertEquals(new BigDecimal("10.00"), stock(5001));
        assertEquals("IN_STOCK", db.queryForObject("SELECT udi_status FROM reagent_batch WHERE id=5001", String.class));
        assertEquals("IN_STOCK", db.queryForObject("SELECT status FROM udi_unique_code WHERE id=9251", String.class));
        for (String table : List.of("consumption_record", "settlement_record", "settlement_event",
                "udi_status_log", "stock_command_receipt")) assertEquals(0, count(table), table);
        db.update("""
                INSERT INTO udi_unique_code(id,institution_id,unique_code,batch_id,reagent_id,batch_no,status)
                VALUES(9252,1,'QA-CONSUME-CODE-2',5001,1,'QA-CONSUME','IN_STOCK')
                """);
        call(TECH, consume(5001, "2"), "qa-insufficient-codes", 200);
        assertEquals(new BigDecimal("8.00"), stock(5001));
        assertEquals(2L, db.queryForObject("SELECT COUNT(*) FROM udi_unique_code WHERE batch_id=5001 AND status='CONSUMED'", Long.class));
        assertEquals(1, count("consumption_record"));
        assertEquals(1, count("settlement_record"));
    }
    @Test void repeatedPartialReceiptDoesNotReceiveOrChargeTwice(){
        call(KEEPER,receive(RECEIVE),"qa-receive-same",200);call(KEEPER,receive(RECEIVE),"qa-receive-same",200);
        assertEquals(new BigDecimal("2.00"),db.queryForObject("SELECT received_quantity FROM purchase_order_item WHERE id=6101",BigDecimal.class));
        assertEquals(new BigDecimal("2.00"),db.queryForObject("SELECT stock FROM reagent_batch WHERE batch_no='QA-RECEIPT'",BigDecimal.class));
        assertEquals(1,count("consumption_record"));assertEquals(1,count("settlement_record"));
        assertEquals(1L, db.queryForObject("SELECT COUNT(*) FROM udi_status_log l JOIN reagent_batch b ON b.id=l.batch_id AND b.institution_id=l.institution_id WHERE b.batch_no='QA-RECEIPT' AND l.to_status='IN_STOCK'", Long.class));
    }
    @Test void missingCommandKeyCannotMutateStock(){
        call(TECH,consume(5001,"2"),null,40001);assertEquals(new BigDecimal("10.00"),stock(5001));assertEquals(0,count("consumption_record"));
    }
    @Test void sameKeyWithChangedPayloadConflicts(){
        call(TECH,consume(5001,"2"),"qa-consume-conflict",200);call(TECH,consume(5001,"3"),"qa-consume-conflict",40900);
        assertEquals(new BigDecimal("8.00"),stock(5001));assertEquals(1,count("consumption_record"));
    }
    @Test void freshKeysAllowTwoLegitimateIdenticalOperations(){
        call(TECH,consume(5001,"2"),"qa-intent-one",200);call(TECH,consume(5001,"2"),"qa-intent-two",200);
        assertEquals(new BigDecimal("6.00"),stock(5001));assertEquals(2,count("consumption_record"));assertEquals(2,count("settlement_record"));
    }
    @Test void keyCannotReplayAnotherActorsReceipt(){
        call(TECH,consume(5001,"2"),"qa-actor-key",200);
        call(new InstitutionContext.Scope(1,11L,1005L,"LAB_TECH"),consume(5001,"2"),"qa-actor-key",40900);
        assertEquals(new BigDecimal("8.00"),stock(5001));assertEquals(1,count("stock_command_receipt"));
    }
    @Test void sameKeyIsIndependentAcrossInstitutions(){
        call(TECH,consume(5001,"2"),"qa-tenant-key",200);
        call(new InstitutionContext.Scope(2,21L,2001L,"LAB_TECH"),consume(5003,"3"),"qa-tenant-key",200);
        assertEquals(new BigDecimal("8.00"),stock(5001));assertEquals(new BigDecimal("7.00"),stock(5003));assertEquals(2,count("stock_command_receipt"));
    }
    @Test void billingFailureRollsBackReceiptAndAllowsSameKeyRetry(){
        db.update("UPDATE reagent_batch SET unit_price=NULL WHERE id=5001");
        call(TECH,consume(5001,"2"),"qa-failure-retry",40001);
        assertEquals(new BigDecimal("10.00"),stock(5001));assertEquals(0,count("stock_command_receipt"));assertEquals(0,count("consumption_record"));assertEquals(0,count("settlement_record"));
        db.update("UPDATE reagent_batch SET unit_price=10 WHERE id=5001");
        call(TECH,consume(5001,"2"),"qa-failure-retry",200);
        assertEquals(new BigDecimal("8.00"),stock(5001));assertEquals(1,count("stock_command_receipt"));assertEquals(1,count("settlement_record"));
    }
    @Test void concurrentRetriesBothReturnSuccessButWriteOnce() throws Exception {
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            Callable<MvcResult> submit=()->{start.await();return call(TECH,consume(5001,"2"),"qa-concurrent-key",200);};
            var first=pool.submit(submit);var second=pool.submit(submit);start.countDown();
            first.get(20,TimeUnit.SECONDS);second.get(20,TimeUnit.SECONDS);
        }finally{pool.shutdownNow();}
        assertEquals(new BigDecimal("8.00"),stock(5001));assertEquals(1,count("stock_command_receipt"));assertEquals(1,count("consumption_record"));assertEquals(1,count("settlement_record"));
    }
    @Test void directReceiptRetryReturnsTheOriginalOrder(){
        String body="{\"supplierId\":1,\"warehouseId\":101,\"settlementMode\":\"INBOUND\",\"items\":[{\"reagentId\":1,\"qtyReceived\":2,\"batchNo\":\"QA-DIRECT\",\"expiryDate\":\"2039-01-01\"}]}";
        var first=call(KEEPER,post("/api/v1/procurement/receive/direct").contentType("application/json").content(body),"qa-direct-key",200);
        var second=call(KEEPER,post("/api/v1/procurement/receive/direct").contentType("application/json").content(body),"qa-direct-key",200);
        var mapper=context.getBean(com.fasterxml.jackson.databind.ObjectMapper.class);
        assertDoesNotThrow(()->assertEquals(mapper.readTree(first.getResponse().getContentAsByteArray()).path("data").path("poId").asText(),
                mapper.readTree(second.getResponse().getContentAsByteArray()).path("data").path("poId").asText()));
        assertEquals(2,count("purchase_order"));assertEquals(1,count("consumption_record"));assertEquals(1,count("settlement_record"));
    }
    @Test void missingReceiptUdiIsQuarantinedAndDoesNotGenerateUnitCodes() {
        db.update("UPDATE reagent_catalog SET enable_unique_code=1, udi_di=NULL WHERE id=1");
        call(KEEPER, receive(RECEIVE), "qa-udi-quarantine-first", 200);
        call(KEEPER, receive(RECEIVE), "qa-udi-quarantine-second", 200);
        var pending = db.queryForMap("SELECT id,stock,udi_status,udi_di FROM reagent_batch WHERE batch_no='QA-RECEIPT'");
        assertEquals("PENDING_UDI", pending.get("udi_status"));
        assertEquals(new BigDecimal("4.00"), pending.get("stock"));
        assertNull(pending.get("udi_di"));
        assertEquals(0, count("udi_unique_code"));
        var inventory = context.getBean(com.zlth.ivd.inventory.service.ProcurementInventoryService.class);
        assertTrue(InstitutionContext.within(KEEPER, () -> inventory.findInStockBatches(1L, 101L)).isEmpty());
        db.update("UPDATE reagent_catalog SET udi_di='00123456789012' WHERE id=1");
        call(KEEPER, receive(RECEIVE), "qa-udi-ready-next", 200);
        assertEquals(List.of("IN_STOCK", "PENDING_UDI"), db.queryForList(
                "SELECT udi_status FROM reagent_batch WHERE batch_no='QA-RECEIPT' ORDER BY udi_status", String.class));
        assertEquals(2, count("udi_unique_code"));
    }
    @Test void mismatchedReceiptUdiCannotWriteAndCatalogFallbackFeedsBatchAndCodes() {
        db.update("UPDATE reagent_catalog SET enable_unique_code=1,udi_di='00123456789012' WHERE id=1");
        String direct = """
                {"supplierId":1,"warehouseId":101,"settlementMode":"INBOUND","items":[
                {"reagentId":1,"qtyReceived":2,"batchNo":"QA-DI","expiryDate":"2039-01-01","udiDi":"00999999999999"}]}
                """;
        String order = """
                {"orderId":6001,"itemId":6101,"warehouseId":101,"qtyReceived":2,"batchNo":"QA-DI","expiryDate":"2039-01-01","udiDi":"00999999999999"}
                """;
        call(KEEPER, post("/api/v1/procurement/receive/direct").contentType("application/json").content(direct),
                "qa-udi-mismatch-direct", 40001);
        call(KEEPER, post("/api/v1/procurement/receive").contentType("application/json").content(order),
                "qa-udi-mismatch-order", 40001);
        assertEquals(1, count("purchase_order"));
        assertEquals(2, count("reagent_batch"));
        for (String table : List.of("stock_command_receipt","consumption_record","settlement_record","udi_unique_code"))
            assertEquals(0, count(table), table);
        call(KEEPER, post("/api/v1/procurement/receive").contentType("application/json")
                .content(order.replace("00999999999999", "  ")), "qa-udi-mismatch-order", 200);
        assertEquals("00123456789012", db.queryForObject("SELECT udi_di FROM reagent_batch WHERE batch_no='QA-DI'", String.class));
        assertEquals(List.of("00123456789012", "00123456789012"),
                db.queryForList("SELECT udi_di FROM udi_unique_code", String.class));
        db.update("UPDATE reagent_batch SET udi_di='QA-HISTORICAL-MISMATCH' WHERE batch_no='QA-DI'");
        call(KEEPER, post("/api/v1/procurement/receive").contentType("application/json")
                .content(order.replace("00999999999999", "00123456789012")), "qa-udi-historical-conflict", 40900);
        assertEquals(2, db.queryForObject("SELECT stock FROM reagent_batch WHERE batch_no='QA-DI'", Integer.class));
        assertEquals(2, db.queryForObject("SELECT received_quantity FROM purchase_order_item WHERE id=6101", Integer.class));
        assertEquals(2, count("udi_unique_code"));
    }
    @Test void receivingRejectsUnapprovedCatalogWithoutBusinessWrites() {
        for (String status : List.of("DRAFT", "REJECTED")) {
            db.update("UPDATE reagent_catalog SET approval_status=? WHERE id=1", status);
            String direct = """
                    {"supplierId":1,"warehouseId":101,"settlementMode":"INBOUND","items":[
                    {"reagentId":2,"qtyReceived":2,"batchNo":"QA-APPROVED-LINE","expiryDate":"2039-01-01"},
                    {"reagentId":1,"qtyReceived":2,"batchNo":"QA-UNAPPROVED","expiryDate":"2039-01-01"}]}
                    """;
            String order = """
                    {"orderId":6001,"itemId":6101,"warehouseId":101,"qtyReceived":2,"batchNo":"QA-UNAPPROVED","expiryDate":"2039-01-01"}
                    """;
            call(KEEPER, post("/api/v1/procurement/receive/direct").contentType("application/json").content(direct),
                    "qa-unapproved-direct-" + status, 40001);
            call(KEEPER, post("/api/v1/procurement/receive").contentType("application/json").content(order),
                    "qa-unapproved-order-" + status, 40001);
            assertEquals(1, count("purchase_order"));
            assertEquals(1, count("purchase_order_item"));
            assertEquals(2, count("reagent_batch"));
            assertEquals(0, db.queryForObject("SELECT received_quantity FROM purchase_order_item WHERE id=6101", Integer.class));
            for (String table : List.of("stock_command_receipt", "consumption_record", "settlement_record",
                    "settlement_event", "udi_unique_code")) assertEquals(0, count(table), table);
        }
    }
    @Test void batchConsumptionAlsoRequiresStableKey(){
        for(int n=0;n<2;n++)call(TECH,post("/api/v1/consumption/batch-consume").param("batchNo","QA-CONSUME").param("qty","2"),"qa-batch-key",200);
        assertEquals(new BigDecimal("8.00"),stock(5001));assertEquals(1,count("settlement_record"));
    }
    @Test void batchConsumptionRejectsTwoSourcesInTheSameDeviceWarehouse() {
        db.update("UPDATE reagent_batch SET origin_purchase_order_item_id=6101 WHERE id=5001");
        db.update("""
                INSERT INTO reagent_batch(id,institution_id,reagent_id,batch_no,warehouse_id,stock,unit,
                    unit_price,udi_status,expiry_date,version,settlement_mode,origin_purchase_order_item_id,inventory_generation)
                VALUES(5091,1,1,'QA-CONSUME',102,5,'box',20,'IN_STOCK','2039-01-01',0,'消耗结算',6102,1)
                """);
        call(TECH, post("/api/v1/consumption/batch-consume").param("batchNo","QA-CONSUME")
                .param("deviceId","301").param("qty","2"), "qa-ambiguous-consumption", 40900);
        assertEquals(new BigDecimal("10.00"), stock(5001));
        assertEquals(new BigDecimal("5.00"), stock(5091));
        for (String table : List.of("device_batch_load","udi_status_log","consumption_record",
                "settlement_record","settlement_event","stock_command_receipt"))
            assertEquals(0, count(table), table);
    }
    @Test void laterDirectLineFailureRollsBackEverythingAndCorrectedRetryGeneratesExactlyItsUnitCodes(){
        db.update("UPDATE reagent_catalog SET enable_unique_code=1,udi_di='06927634501234' WHERE id=2");
        String body="""
                {"supplierId":1,"warehouseId":101,"settlementMode":"INBOUND","items":[
                {"reagentId":1,"qtyReceived":2,"batchNo":"QA-DR-FIRST","expiryDate":"2039-01-01"},
                {"reagentId":2,"qtyReceived":0.5,"batchNo":"QA-DR-SECOND","expiryDate":"2039-01-01","udiDi":"06927634501234"}]}
                """;
        call(KEEPER,post("/api/v1/procurement/receive/direct").contentType("application/json").content(body),"qa-direct-rollback",40001);
        assertEquals(1,count("purchase_order"));assertEquals(1,count("purchase_order_item"));assertEquals(2,count("reagent_batch"));
        for(String table:List.of("stock_command_receipt","consumption_record","settlement_record","settlement_event","udi_unique_code"))assertEquals(0,count(table),table);
        String corrected=body.replace("\"qtyReceived\":0.5","\"qtyReceived\":3");
        for(int i=0;i<2;i++)call(KEEPER,post("/api/v1/procurement/receive/direct").contentType("application/json").content(corrected),"qa-direct-rollback",200);
        assertEquals(2,count("purchase_order"));assertEquals(2,count("consumption_record"));assertEquals(2,count("settlement_record"));
        assertEquals(1,count("stock_command_receipt"));assertEquals(3,count("udi_unique_code"));
        assertEquals(3L,db.queryForObject("SELECT COUNT(*) FROM udi_unique_code u JOIN reagent_batch b ON b.id=u.batch_id WHERE b.batch_no='QA-DR-SECOND' AND u.reagent_id=2 AND u.status='IN_STOCK' AND u.udi_di='06927634501234'",Long.class));
    }
}
