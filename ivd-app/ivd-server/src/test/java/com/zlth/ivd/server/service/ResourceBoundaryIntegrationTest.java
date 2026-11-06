package com.zlth.ivd.server.service;

import com.zlth.ivd.common.config.MinioProperties;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.common.security.InstitutionContext;
import com.zlth.ivd.inventory.service.InventoryService;
import com.zlth.ivd.inventory.service.InstrumentUsageService;
import com.zlth.ivd.license.service.LicenseService;
import com.zlth.ivd.procurement.service.ProcurementService;
import com.zlth.ivd.audit.mapper.AuditDecisionLogMapper;
import io.minio.*;
import org.junit.jupiter.api.*;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 仅使用虚构数据，验证真实机构/科室SQL及Service事务代理，不连接外部服务。 */
class ResourceBoundaryIntegrationTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate bootstrap;
    InventoryService inventory;
    ProcurementService procurement;
    InstrumentUsageService instruments;
    LicenseService licenses;
    MinioClient minio;
    final InstitutionContext.Scope tech = new InstitutionContext.Scope(1,11L,1001L,"LAB_TECH");
    final InstitutionContext.Scope admin = new InstitutionContext.Scope(1,null,1000L,"SYSTEM_ADMIN");

    @BeforeEach void setup() {
        context=new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("boundary-test",Map.of("ivd.clinical.enabled","true")));
        context.register(Config.class);context.refresh();
        bootstrap=new JdbcTemplate(context.getBean(DataSource.class));
        inventory=context.getBean(InventoryService.class);procurement=context.getBean(ProcurementService.class);
        instruments=context.getBean(InstrumentUsageService.class);licenses=context.getBean(LicenseService.class);minio=context.getBean(MinioClient.class);
        bootstrap.execute("CREATE TABLE patient_association(id BIGINT PRIMARY KEY,institution_id BIGINT,department_id BIGINT,batch_no VARCHAR(64),patient_id_hash VARCHAR(100),test_item_code VARCHAR(64),test_value VARCHAR(64),test_time TIMESTAMP,audit_status VARCHAR(32),created_at TIMESTAMP)");
        bootstrap.execute("CREATE TABLE supplier_license(id BIGINT PRIMARY KEY,institution_id BIGINT,supplier_id BIGINT,license_type VARCHAR(32),cert_no VARCHAR(64),issuing_authority VARCHAR(100),issue_date DATE,expire_date DATE,status VARCHAR(32),attachments VARCHAR(4000),created_at TIMESTAMP,updated_at TIMESTAMP)");
        bootstrap.execute("CREATE TABLE audit_decision_log(id BIGINT PRIMARY KEY,institution_id BIGINT,department_id BIGINT,observation_id VARCHAR(64),patient_id_hash VARCHAR(100),test_item_code VARCHAR(64),test_value DECIMAL(12,2),device_id VARCHAR(64),test_datetime TIMESTAMP,batch_no VARCHAR(64),decision VARCHAR(32),triggered_rules VARCHAR(4000),audit_latency_ms INT,manual_decision VARCHAR(32),auditor_id BIGINT,auditor_note VARCHAR(1000),recheck_value DECIMAL(12,2),decided_at TIMESTAMP,created_at TIMESTAMP)");
        bootstrap.execute("ALTER TABLE audit_decision_log ADD COLUMN test_value_text VARCHAR(1000)");
        bootstrap.update("INSERT INTO reagent_batch(id,institution_id,reagent_id,batch_no,warehouse_id,stock,unit,unit_price,udi_status,expiry_date,version) VALUES(5001,1,1,'QA-SHARED',102,3,'box',10,'IN_USE','2039-01-01',0),(5002,1,1,'QA-SHARED',103,3,'box',10,'IN_USE','2039-01-01',0),(5003,2,3,'QA-SHARED',202,3,'box',10,'IN_USE','2039-01-01',0),(5004,1,1,'QA-SHARED',101,3,'box',10,'IN_STOCK','2039-01-01',0)");
        bootstrap.update("INSERT INTO device_batch_load(id,institution_id,device_id,batch_id,reagent_id,batch_no,operator_id,load_time) VALUES(8001,1,301,5001,1,'QA-SHARED',1001,CURRENT_TIMESTAMP),(8002,1,302,5002,1,'QA-SHARED',1002,CURRENT_TIMESTAMP),(8003,2,401,5003,3,'QA-SHARED',2001,CURRENT_TIMESTAMP)");
        bootstrap.update("INSERT INTO udi_unique_code(id,institution_id,unique_code,batch_id,reagent_id,batch_no,status) VALUES(7001,1,'QA-OWN',5001,1,'QA-SHARED','IN_STOCK'),(7002,1,'QA-OTHER-DEPT',5002,1,'QA-SHARED','IN_STOCK'),(7003,2,'QA-FOREIGN',5003,3,'QA-SHARED','IN_STOCK'),(7004,1,'QA-CENTRAL',5004,1,'QA-SHARED','IN_STOCK')");
        bootstrap.update("INSERT INTO patient_association(id,institution_id,department_id,batch_no,patient_id_hash,test_item_code,test_value) VALUES(9001,1,11,'QA-SHARED','QA-HASH-1','QA-T','1'),(9002,1,12,'QA-SHARED','QA-HASH-2','QA-T','2'),(9003,2,21,'QA-SHARED','QA-HASH-3','QA-T','3')");
        bootstrap.update("INSERT INTO purchase_requisition(id,institution_id,req_no,from_dept_id,to_warehouse_id,status) VALUES(6001,1,'QA-R1',11,102,'DRAFT'),(6002,1,'QA-R2',12,103,'DRAFT'),(6003,2,'QA-R3',21,202,'DRAFT')");
        bootstrap.update("INSERT INTO purchase_requisition_item(id,institution_id,req_id,line_no,reagent_id,qty_requested) VALUES(6101,1,6001,1,1,2),(6102,1,6002,1,1,2),(6103,2,6003,1,3,2)");
        String attachments="[{\"name\":\"fixture.pdf\",\"objectKey\":\"known.pdf\",\"size\":7,\"type\":\"pdf\"}]";
        bootstrap.update("INSERT INTO supplier_license(id,institution_id,supplier_id,license_type,cert_no,issue_date,expire_date,status,attachments) VALUES(501,1,1,'PRODUCT_REG','QA-CERT','2026-01-01','2039-01-01','ACTIVE',?),(502,2,2,'PRODUCT_REG','QA-OTHER','2026-01-01','2039-01-01','ACTIVE',?)",attachments,attachments);
    }
    @AfterEach void close(){if(context!=null)context.close();SecurityContextHolder.clearContext();assertTrue(InstitutionContext.current().isEmpty());}
    <T>T as(InstitutionContext.Scope scope,Supplier<T> operation){
        var auth=new UsernamePasswordAuthenticationToken(scope.actorId(),"QA",List.of(new SimpleGrantedAuthority("ROLE_"+scope.role())));
        auth.setDetails(scope.role().equals("SYSTEM_ADMIN")?"admin":"QA-user");SecurityContextHolder.getContext().setAuthentication(auth);
        try{return InstitutionContext.within(scope,operation);}finally{SecurityContextHolder.clearContext();}
    }
    void denied(int code,Runnable operation){assertEquals(code,assertThrows(BizException.class,operation::run).getErrorCode());}

    @Test void foreignLicenseIsRejectedBeforeObjectRead(){as(tech,()->{denied(40400,()->licenses.downloadAttachment(502L,"known.pdf",new MockHttpServletResponse()));return null;});verifyNoInteractions(minio);}
    @Test void activeDocumentAttachmentsAreDownloadOnlyAndNotSniffed() throws Exception {
        bootstrap.update("UPDATE supplier_license SET attachments=? WHERE id=501",
                "[{\"name\":\"untrusted.svg\",\"objectKey\":\"known.pdf\"}]");
        when(minio.getObject(any(GetObjectArgs.class))).thenAnswer(invocation -> new GetObjectResponse(
                new okhttp3.Headers.Builder().build(),"qa-cert","qa","fixture",new ByteArrayInputStream("<svg/>".getBytes())));
        var response = new MockHttpServletResponse();
        as(tech, () -> {licenses.downloadAttachment(501L,"known.pdf",response);return null;});
        assertEquals("application/octet-stream",response.getContentType());
        assertTrue(response.getHeader("Content-Disposition").startsWith("attachment;"));
        assertEquals("nosniff",response.getHeader("X-Content-Type-Options"));
        assertTrue(response.getHeader("Content-Security-Policy").contains("sandbox"));
    }
    @Test void catalogDeletionGuardRejectsStockAndOpenOrdersInCurrentInstitution() {
        var guard = new CatalogDeletionGuardImpl(context.getBean(JdbcTemplate.class));
        as(admin, () -> {
            denied(40900, () -> guard.requireDeletable(1L));
            return null;
        });
        bootstrap.update("UPDATE reagent_batch SET stock=0 WHERE institution_id=1");
        bootstrap.update("INSERT INTO purchase_order(id,institution_id,po_no,status) VALUES(9691,1,'QA-DELETE-OPEN','SENT')");
        bootstrap.update("INSERT INTO purchase_order_item(id,institution_id,po_id,reagent_id) VALUES(9692,1,9691,1)");
        as(admin, () -> {
            denied(40900, () -> guard.requireDeletable(1L));
            return null;
        });
        bootstrap.update("UPDATE purchase_order SET status='COMPLETED' WHERE id=9691");
        as(admin, () -> {
            guard.requireDeletable(1L);
            guard.requireDeletable(3L);
            return null;
        });
    }
    @Test void expiryCatchupIncludesMissedDaysAndTheExactExpiryDay() {
        var today = java.time.LocalDate.now();
        bootstrap.update("UPDATE supplier_license SET status='ACTIVE',expire_date=? WHERE id=501", today.minusDays(8));
        bootstrap.update("""
                INSERT INTO supplier_license(id,institution_id,supplier_id,license_type,cert_no,status,expire_date)
                VALUES(505,1,1,'PRODUCT_REG','QA-TODAY','ACTIVE',?),
                      (506,1,1,'PRODUCT_REG','QA-FUTURE','ACTIVE',?),
                      (507,1,1,'PRODUCT_REG','QA-NEAR','ACTIVE',?)
                """, today, today.plusDays(100), today.plusDays(7));
        InstitutionContext.within(InstitutionContext.jobScope(1), () -> {
            licenses.processExpiryCheck();
            licenses.processExpiryCheck();
            return null;
        });
        assertEquals(List.of("EXPIRED", "EXPIRED", "ACTIVE", "EXPIRING"),
                bootstrap.queryForList("SELECT status FROM supplier_license WHERE institution_id=1 ORDER BY id", String.class));
        assertEquals("ACTIVE", bootstrap.queryForObject("SELECT status FROM supplier_license WHERE id=502", String.class));
        assertTrue(InstitutionContext.current().isEmpty());
    }
    @Test void auditDecisionPreservesExactResultInputWithoutNumericRounding() {
        var audit = context.getBean(com.zlth.ivd.audit.service.AuditPipelineService.class);
        var mapper = context.getBean(AuditDecisionLogMapper.class);
        for (String input : List.of("1.2345", "阳性", " 12.30 ")) {
            Long id = as(tech, () -> audit.executePipeline("QA-EVIDENCE", "QA-HASH", "QA-T",
                    input, null, LocalDateTime.now(), "QA-SHARED").getId());
            as(tech, () -> {
                var stored = mapper.selectById(id);
                assertEquals(input, stored.getTestValueText());
                if (" 12.30 ".equals(input))
                    assertEquals(0, new java.math.BigDecimal("12.30").compareTo(stored.getTestValue()));
                else assertNull(stored.getTestValue());
                return null;
            });
            as(new InstitutionContext.Scope(1,12L,1005L,"LAB_TECH"), () -> {
                assertNull(mapper.selectById(id));
                return null;
            });
        }
        assertEquals(3L, bootstrap.queryForObject("SELECT COUNT(*) FROM audit_decision_log", Long.class));
    }
    @Test void dashboardMoneyDistinguishesEmptyKnownAndUnpricedEvidence() {
        var mapper = context.getBean(com.zlth.ivd.analytics.mapper.DashboardMapper.class);
        bootstrap.update("UPDATE reagent_batch SET stock=0");
        as(admin, () -> {
            assertEquals(0, mapper.getTodayConsumptionCost().signum());
            assertEquals(0, mapper.getCurrentStockAmount().signum());
            return null;
        });
        bootstrap.update("UPDATE reagent_batch SET stock=2,unit_price=12.34 WHERE id=5001");
        bootstrap.update("UPDATE reagent_batch SET stock=3,unit_price=NULL,avg_cost=1.25 WHERE id=5002");
        bootstrap.update("UPDATE reagent_batch SET stock=3,unit_price=NULL,avg_cost=NULL WHERE id=5003");
        bootstrap.update("""
                INSERT INTO consumption_record(id,institution_id,record_type,amount,occurred_at)
                VALUES(9401,1,'CONSUME',12.34,CURRENT_TIMESTAMP),
                      (9402,1,'CONSUME',0.01,CURRENT_TIMESTAMP),
                      (9403,1,'INBOUND',NULL,CURRENT_TIMESTAMP),
                      (9404,2,'CONSUME',NULL,CURRENT_TIMESTAMP)
                """);
        as(admin, () -> {
            assertEquals(0, new java.math.BigDecimal("12.35").compareTo(mapper.getTodayConsumptionCost()));
            assertEquals(0, new java.math.BigDecimal("28.43").compareTo(mapper.getCurrentStockAmount()));
            return null;
        });
        bootstrap.update("UPDATE consumption_record SET amount=NULL WHERE id=9402");
        bootstrap.update("UPDATE reagent_batch SET avg_cost=NULL WHERE id=5002");
        as(admin, () -> {
            assertNull(mapper.getTodayConsumptionCost());
            assertNull(mapper.getCurrentStockAmount());
            return null;
        });
    }
    @Test void verifiedAdminCanReadOnlySelectedInstitutionWorkbenchWithoutLocalAccountCopy() {
        bootstrap.update("INSERT INTO sys_user(id,institution_id,username,role,status) VALUES(1000,1,'admin','SYSTEM_ADMIN',1)");
        var workbench = context.getBean(com.zlth.ivd.analytics.service.WorkbenchService.class);
        var switched = new InstitutionContext.Scope(2, null, 1000L, "SYSTEM_ADMIN");
        as(switched, () -> {
            var products = workbench.products(1000L, 1, 20, "QA-SHARED");
            assertEquals(1, products.getTotal());
            assertEquals("5003", products.getRecords().get(0).getId());
            denied(40300, () -> workbench.products(1001L, 1, 20, "QA-SHARED"));
            return null;
        });
        as(admin, () -> {
            var products = workbench.products(1000L, 1, 20, "QA-SHARED");
            assertEquals(3, products.getTotal());
            assertFalse(products.getRecords().stream().anyMatch(item -> "5003".equals(item.getId())));
            return null;
        });
    }
    @Test void userWritesRejectUnusableDepartmentsAndKeepValidAssignments() {
        bootstrap.update("UPDATE sys_dept SET status=0 WHERE id=12");
        var users = context.getBean(com.zlth.ivd.system.service.UserService.class);
        as(admin, () -> {
            for (Long dept : java.util.Arrays.asList(null, 12L, 21L, 999L)) {
                denied(40001, () -> users.create("qa-invalid-dept", "QA", "LAB_TECH", dept, null, null));
                if (dept != null) denied(40001, () -> users.update(1001L, "QA changed", null, dept, null, null));
            }
            denied(40001, () -> users.update(1004L, null, "LAB_TECH", null, null, null));
            assertEquals(11L, users.update(1001L, "QA valid", null, null, null, null).getDeptId());
            assertEquals(11L, users.create("qa-valid-dept", "QA", "LAB_TECH", 11L, null, null).getDeptId());
            assertNull(users.create("qa-finance", "QA", "FINANCE", null, null, null).getDeptId());
            return null;
        });
        assertEquals(0L, bootstrap.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username='qa-invalid-dept'", Long.class));
        assertEquals("PURCHASER", bootstrap.queryForObject("SELECT role FROM sys_user WHERE id=1004", String.class));
        assertEquals(11L, bootstrap.queryForObject("SELECT dept_id FROM sys_user WHERE id=1001", Long.class));
    }
    @Test void attachmentUploadWritesBusinessChangeAndCleansObjectOnRollback() throws Exception {
        bootstrap.execute("CREATE TABLE license_change_log(id BIGINT PRIMARY KEY,institution_id BIGINT,license_id BIGINT,change_type VARCHAR(32),old_expire_date DATE,new_expire_date DATE,operator_id BIGINT,operator_name VARCHAR(64),note VARCHAR(2000),created_at TIMESTAMP)");
        var file = new org.springframework.mock.web.MockMultipartFile("file", "evidence.pdf",
                "application/pdf", new byte[]{1, 2, 3});
        String key = as(admin, () -> licenses.uploadAttachment(501L, file, 1000L));
        var change = bootstrap.queryForMap("SELECT institution_id, license_id, change_type, operator_id, note FROM license_change_log");
        assertEquals(1L, change.get("institution_id"));
        assertEquals(501L, change.get("license_id"));
        assertEquals("ADD_ATTACHMENT", change.get("change_type"));
        assertEquals(1000L, change.get("operator_id"));
        assertEquals(key, new com.fasterxml.jackson.databind.ObjectMapper().readTree((String) change.get("note")).get("objectKey").asText());
        String before = bootstrap.queryForObject("SELECT attachments FROM supplier_license WHERE id=501", String.class);
        assertTrue(before.contains(key));
        bootstrap.execute("ALTER TABLE license_change_log ADD CONSTRAINT qa_reject_upload CHECK (change_type <> 'ADD_ATTACHMENT' OR license_id <> 501 OR operator_id <> 1001)");
        as(admin, () -> assertThrows(RuntimeException.class, () -> licenses.uploadAttachment(501L, file, 1001L)));
        assertEquals(before, bootstrap.queryForObject("SELECT attachments FROM supplier_license WHERE id=501", String.class));
        assertEquals(1L, bootstrap.queryForObject("SELECT COUNT(*) FROM license_change_log", Long.class));
        verify(minio, times(2)).putObject(any(PutObjectArgs.class));
        verify(minio).removeObject(argThat(args -> args.object().startsWith("license/501/") && !args.object().endsWith(key)));
    }
    @Test void attachmentDeleteAndReplaceKeepOldObjectUntilCommit() throws Exception {
        bootstrap.execute("CREATE TABLE license_change_log(id BIGINT PRIMARY KEY,institution_id BIGINT,license_id BIGINT,change_type VARCHAR(32),old_expire_date DATE,new_expire_date DATE,operator_id BIGINT,operator_name VARCHAR(64),note VARCHAR(2000),created_at TIMESTAMP)");
        bootstrap.execute("ALTER TABLE license_change_log ADD CONSTRAINT qa_deny_attachment_change CHECK (change_type='CREATE')");
        var file = new org.springframework.mock.web.MockMultipartFile("file", "new.pdf", "application/pdf", new byte[]{1,2,3});
        String before = bootstrap.queryForObject("SELECT attachments FROM supplier_license WHERE id=501", String.class);
        as(admin, () -> {
            assertThrows(RuntimeException.class, () -> licenses.deleteAttachment(501L, "known.pdf", 1000L));
            assertThrows(RuntimeException.class, () -> licenses.replaceAttachment(501L, "known.pdf", file, 1000L));
            return null;
        });
        assertEquals(before, bootstrap.queryForObject("SELECT attachments FROM supplier_license WHERE id=501", String.class));
        verify(minio, never()).removeObject(argThat(args -> args.object().equals("license/501/known.pdf")));
        verify(minio).removeObject(argThat(args -> !args.object().equals("license/501/known.pdf")));

        bootstrap.execute("ALTER TABLE license_change_log DROP CONSTRAINT qa_deny_attachment_change");
        String replacement = as(admin, () -> licenses.replaceAttachment(501L, "known.pdf", file, 1000L));
        verify(minio).removeObject(argThat(args -> args.object().equals("license/501/known.pdf")));
        assertTrue(bootstrap.queryForObject("SELECT attachments FROM supplier_license WHERE id=501", String.class).contains(replacement));
        as(admin, () -> { licenses.deleteAttachment(501L, replacement, 1000L); return null; });
        verify(minio).removeObject(argThat(args -> args.object().equals("license/501/" + replacement)));
        assertEquals("[]", bootstrap.queryForObject("SELECT attachments FROM supplier_license WHERE id=501", String.class));
    }

    @Test void concurrentAttachmentUploadsPreserveBothEntries() throws Exception {
        bootstrap.execute("CREATE TABLE license_change_log(id BIGINT PRIMARY KEY,institution_id BIGINT,license_id BIGINT,change_type VARCHAR(32),old_expire_date DATE,new_expire_date DATE,operator_id BIGINT,operator_name VARCHAR(64),note VARCHAR(2000),created_at TIMESTAMP)");
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.concurrent.Callable<String> upload = () -> {
                start.await();
                var file = new org.springframework.mock.web.MockMultipartFile("file", "same.pdf", "application/pdf", new byte[]{1});
                return as(admin, () -> licenses.uploadAttachment(501L, file, 1000L));
            };
            var a = pool.submit(upload);
            var b = pool.submit(upload);
            start.countDown();
            String first = a.get(10, java.util.concurrent.TimeUnit.SECONDS);
            String second = b.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertNotEquals(first, second);
            String attachments = bootstrap.queryForObject("SELECT attachments FROM supplier_license WHERE id=501", String.class);
            assertTrue(attachments.contains(first) && attachments.contains(second) && attachments.contains("known.pdf"));
            assertEquals(2L, bootstrap.queryForObject("SELECT COUNT(*) FROM license_change_log", Long.class));
        } finally {
            pool.shutdownNow();
        }
    }
    @Test void licenseSearchMatchesLiteralCertificateOrOwnedSupplierAndKeepsFilters() {
        createSupplierTable();
        bootstrap.update("INSERT INTO supplier(id,institution_id,name) VALUES(1,1,'QA 本院供货'),(2,2,'QA 他院供货')");
        bootstrap.update("UPDATE supplier_license SET cert_no='QA_100%' WHERE id=501");
        as(admin, () -> {
            var query = new com.zlth.ivd.license.dto.LicenseQuery();
            query.setKeyword("  本院供货  ");
            assertEquals(List.of(501L), licenses.listLicenses(query).getRecords().stream().map(v -> v.getId()).toList());
            query.setKeyword("_100%");
            assertEquals(1, licenses.listLicenses(query).getRecords().size());
            query.setKeyword("他院供货");
            assertTrue(licenses.listLicenses(query).getRecords().isEmpty());
            query.setKeyword("QA-OTHER");
            assertTrue(licenses.listLicenses(query).getRecords().isEmpty());
            query.setKeyword("QA");
            query.setStatus("FROZEN");
            assertTrue(licenses.listLicenses(query).getRecords().isEmpty());
            query.setStatus(null);
            query.setKeyword("%_");
            assertTrue(licenses.listLicenses(query).getRecords().isEmpty());
            return null;
        });
    }
    private void createSupplierTable() {
        bootstrap.execute("CREATE TABLE supplier(id BIGINT PRIMARY KEY,institution_id BIGINT,supplier_code VARCHAR(64),name VARCHAR(100),short_name VARCHAR(100),contact_person VARCHAR(100),contact_phone VARCHAR(100),contact_email VARCHAR(100),address VARCHAR(100),is_active BOOLEAN,created_at TIMESTAMP,updated_at TIMESTAMP)");
    }

    @Test void certificateEditPersistsOwnedSupplierAndRejectsForeignOrInvalidDates() {
        createSupplierTable();
        bootstrap.execute("CREATE TABLE license_change_log(id BIGINT PRIMARY KEY,institution_id BIGINT,license_id BIGINT,change_type VARCHAR(32),old_expire_date DATE,new_expire_date DATE,operator_id BIGINT,operator_name VARCHAR(64),note VARCHAR(2000),created_at TIMESTAMP)");
        bootstrap.update("INSERT INTO supplier(id,institution_id,name) VALUES(1,1,'QA original'),(2,2,'QA foreign'),(3,1,'QA replacement')");
        as(admin, () -> {
            var request = new com.zlth.ivd.license.dto.UpdateLicenseRequest();
            request.setOperatorId(1000L);
            request.setSupplierId(3L);
            assertEquals(3L, licenses.update(501L, request).getSupplierId());
            request.setSupplierId(2L);
            denied(40400, () -> licenses.update(501L, request));
            request.setSupplierId(null);
            request.setIssueDate(java.time.LocalDate.of(2040, 1, 1));
            denied(40001, () -> licenses.update(501L, request));
            var create = new com.zlth.ivd.license.dto.CreateLicenseRequest();
            create.setIssueDate(java.time.LocalDate.of(2040, 1, 1));
            create.setExpireDate(java.time.LocalDate.of(2039, 1, 1));
            denied(40001, () -> licenses.create(create));
            return null;
        });
        assertEquals(3L, bootstrap.queryForObject("SELECT supplier_id FROM supplier_license WHERE id=501", Long.class));
        assertEquals(1L, bootstrap.queryForObject("SELECT COUNT(*) FROM license_change_log", Long.class));
        assertEquals(2L, bootstrap.queryForObject("SELECT COUNT(*) FROM supplier_license", Long.class));
    }

    @Test void ordinaryLicenseEditCannotThawFrozenCertificate() {
        createSupplierTable();
        bootstrap.execute("CREATE TABLE license_change_log(id BIGINT PRIMARY KEY,institution_id BIGINT,license_id BIGINT,change_type VARCHAR(32),old_expire_date DATE,new_expire_date DATE,operator_id BIGINT,operator_name VARCHAR(64),note VARCHAR(2000),created_at TIMESTAMP)");
        bootstrap.update("UPDATE supplier_license SET status='FROZEN' WHERE id=501");
        as(admin, () -> {
            var request = new com.zlth.ivd.license.dto.UpdateLicenseRequest();
            request.setOperatorId(1000L);
            request.setExpireDate(java.time.LocalDate.now().plusYears(1));
            request.setIssuingAuthority("QA");
            assertEquals("FROZEN", licenses.update(501L, request).getStatus());
            request.setStatus("ACTIVE");
            denied(40900, () -> licenses.update(501L, request));
            return null;
        });
        assertEquals("FROZEN", bootstrap.queryForObject("SELECT status FROM supplier_license WHERE id=501", String.class));
        assertEquals(1L, bootstrap.queryForObject("SELECT COUNT(*) FROM license_change_log", Long.class));
    }

    @Test void licenseCsvRoundTripMapsSupplierTypeAndDatesWithoutImportingInvalidRows() {
        createSupplierTable();
        bootstrap.execute("CREATE TABLE license_change_log(id BIGINT PRIMARY KEY,institution_id BIGINT,license_id BIGINT,change_type VARCHAR(32),old_expire_date DATE,new_expire_date DATE,operator_id BIGINT,operator_name VARCHAR(64),note VARCHAR(2000),created_at TIMESTAMP)");
        bootstrap.update("INSERT INTO supplier(id,institution_id,name) VALUES(1,1,'QA owned'),(2,2,'QA foreign')");
        var controller = new com.zlth.ivd.license.controller.LicenseController(licenses);
        var response = new MockHttpServletResponse();
        as(admin, () -> {
            controller.export("csv", response);
            String csv = new String(response.getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8)
                    + "QA foreign,PRODUCT_REG,QA-SKIP-FOREIGN,,2026-01-01,2039-01-01,,\n"
                    + "QA owned,产品注册证,QA-SKIP-DATE,,2040-01-01,2039-01-01,,\n"
                    + "QA owned,产品注册证,QA-SKIP-FROZEN,,2026-01-01,2039-01-01,已冻结,\n"
                    + "QA owned,OPERATIon,QA-CODE-TYPE,,2026-01-01,2039-01-01,,\n";
            var file = new org.springframework.mock.web.MockMultipartFile("file","license.csv","text/csv",
                    csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var result = controller.importFile(file).getData();
            assertEquals(5, result.get("total"));
            assertEquals(2, result.get("success"));
            assertEquals(3, result.get("skipped"));
            return null;
        });
        assertEquals(2L, bootstrap.queryForObject("SELECT COUNT(*) FROM supplier_license WHERE institution_id=1 AND supplier_id=1 AND cert_no='QA-CERT' AND license_type='PRODUCT_REG' AND issue_date='2026-01-01' AND expire_date='2039-01-01'", Long.class));
        assertEquals("OPERATION", bootstrap.queryForObject("SELECT license_type FROM supplier_license WHERE cert_no='QA-CODE-TYPE'", String.class));
        assertEquals(2L, bootstrap.queryForObject("SELECT COUNT(*) FROM license_change_log WHERE operator_id=1000", Long.class));
    }

    @Test void certificateHistoryIgnoresClientSuppliedOperator() {
        createSupplierTable();
        bootstrap.execute("CREATE TABLE license_change_log(id BIGINT PRIMARY KEY,institution_id BIGINT,license_id BIGINT,change_type VARCHAR(32),old_expire_date DATE,new_expire_date DATE,operator_id BIGINT,operator_name VARCHAR(64),note VARCHAR(2000),created_at TIMESTAMP)");
        as(admin, () -> {
            var create = new com.zlth.ivd.license.dto.CreateLicenseRequest();
            create.setSupplierId(1L);
            create.setLicenseType("PRODUCT_REG");
            create.setCertNo("QA-ACTOR");
            create.setIssueDate(java.time.LocalDate.now());
            create.setExpireDate(java.time.LocalDate.now().plusYears(1));
            create.setOperatorId(99999L);
            create.setOperatorName("forged-create");
            Long id = licenses.create(create).getId();
            var update = new com.zlth.ivd.license.dto.UpdateLicenseRequest();
            update.setIssuingAuthority("QA");
            update.setOperatorId(99998L);
            update.setOperatorName("forged-update");
            licenses.update(id, update);
            return null;
        });
        assertEquals(List.of(1000L, 1000L), bootstrap.queryForList("SELECT operator_id FROM license_change_log", Long.class));
        assertEquals(List.of("admin", "admin"), bootstrap.queryForList("SELECT operator_name FROM license_change_log", String.class));
    }

    @Test void expiryJobUpdatesOverdueActiveRowsButNotFrozenOrForeignRows() {
        bootstrap.update("UPDATE supplier_license SET status='FROZEN',expire_date=? WHERE id=501", java.time.LocalDate.now().minusDays(1));
        bootstrap.update("UPDATE supplier_license SET expire_date=? WHERE id=502", java.time.LocalDate.now().minusDays(1));
        bootstrap.update("INSERT INTO supplier_license(id,institution_id,supplier_id,license_type,status,expire_date) VALUES(503,1,1,'PRODUCT_REG','ACTIVE',?)", java.time.LocalDate.now().minusDays(1));
        InstitutionContext.within(InstitutionContext.jobScope(1), () -> {
            licenses.processExpiryCheck();
            return null;
        });
        assertEquals(List.of("FROZEN","ACTIVE","EXPIRED"), bootstrap.queryForList("SELECT status FROM supplier_license ORDER BY id", String.class));
    }
    @Test void unlistedObjectIsRejectedBeforeObjectRead(){as(tech,()->{denied(40400,()->licenses.downloadAttachment(501L,"orphan.pdf",new MockHttpServletResponse()));return null;});verifyNoInteractions(minio);}
    @Test void ownedListedAttachmentRemainsReadableWithoutSharedCaching() throws Exception {
        when(minio.getObject(any(GetObjectArgs.class))).thenAnswer(inv->new GetObjectResponse(new okhttp3.Headers.Builder().build(),"qa-cert","qa","fixture",new ByteArrayInputStream("fixture".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        var response=new MockHttpServletResponse();as(tech,()->{licenses.downloadAttachment(501L,"known.pdf",response);return null;});
        assertEquals("fixture",response.getContentAsString());assertTrue(Objects.requireNonNull(response.getHeader("Cache-Control")).contains("no-store"));
    }
    @Test void requisitionItemsRequireOwnedVisibleParent(){
        as(tech,()->{assertEquals(1,procurement.getRequisitionItems(6001L).size());denied(40400,()->procurement.getRequisitionItems(6002L));denied(40400,()->procurement.getRequisitionItems(6003L));return null;});
        as(admin,()->{assertEquals(1,procurement.getRequisitionItems(6002L).size());return null;});
    }
    @Test void uniqueCodesCannotExposeAnotherDepartmentOrInstitution(){as(tech,()->{
        assertEquals("QA-OWN",inventory.getUniqueCodeTrace("QA-OWN").get("uniqueCode"));
        assertEquals("QA-CENTRAL",inventory.getUniqueCodeTrace("QA-CENTRAL").get("uniqueCode"));
        denied(40400,()->inventory.getUniqueCodeTrace("QA-OTHER-DEPT"));denied(40400,()->inventory.getUniqueCodeTrace("QA-FOREIGN"));return null;
    });}
    @Test void unknownCodeBatchFailsClosedForClinicalUser(){
        bootstrap.update("UPDATE udi_unique_code SET batch_id=NULL WHERE id=7001");
        as(tech,()->{denied(40400,()->inventory.getUniqueCodeTrace("QA-OWN"));return null;});
    }
    @Test void foreignDepartmentLoadCannotBeUnloaded(){
        as(tech,()->{denied(40400,()->instruments.unload(8002L));denied(40400,()->instruments.unload(8003L));return null;});
        assertNull(bootstrap.queryForObject("SELECT unload_time FROM device_batch_load WHERE id=8002",java.sql.Timestamp.class));
    }
    @Test void ownDepartmentLoadCanStillBeUnloaded(){as(tech,()->{instruments.unload(8001L);return null;});assertNotNull(bootstrap.queryForObject("SELECT unload_time FROM device_batch_load WHERE id=8001",java.sql.Timestamp.class));}
    @Test void traceCannotExpandToForeignDepartmentLoadEvents(){as(tech,()->{
        var result=inventory.traceUdi("QA-SHARED",null);
        @SuppressWarnings("unchecked") var timeline=(List<Map<String,Object>>)result.get("timeline");
        assertTrue(timeline.stream().anyMatch(e->"8001".equals(e.get("sourceId"))));
        assertFalse(timeline.stream().anyMatch(e->Set.of("8002","8003").contains(e.get("sourceId"))));return null;
    });}
    @Test void traceShowsExactBatchStatusEventsAndOmitsUnattributedHistory() {
        bootstrap.update("""
                INSERT INTO udi_status_log(id,institution_id,batch_id,batch_no,from_status,to_status,operator_id,occurred_at)
                VALUES (9101,1,5001,'QA-SHARED','IN_STOCK','RECALLED',1001,CURRENT_TIMESTAMP),
                       (9102,1,5002,'QA-SHARED','IN_STOCK','SCRAPPED',1002,CURRENT_TIMESTAMP),
                       (9103,2,5003,'QA-SHARED','IN_STOCK','DESTROYED',2001,CURRENT_TIMESTAMP),
                       (9104,1,NULL,'QA-SHARED','IN_STOCK','EXPIRED',1001,CURRENT_TIMESTAMP)
                """);
        as(tech, () -> {
            var events = (List<?>) inventory.traceUdi("QA-SHARED", null).get("timeline");
            var status = events.stream().map(row -> (Map<?, ?>) row)
                    .filter(row -> "udi_status_log".equals(row.get("source"))).toList();
            assertEquals(1, status.size());
            assertEquals("9101", status.get(0).get("sourceId"));
            assertEquals("1001", status.get(0).get("operator"));
            assertTrue(status.get(0).get("detail").toString().contains("RECALLED"));
            return null;
        });
    }
    @Test void traceReturnsOwnedCatalogCodesWithoutDroppingLeadingZero() {
        bootstrap.update("UPDATE reagent_catalog SET gtin='00123456789012',registration_cert_no='QA-NMPA',insurance_code='QA-INS' WHERE id=1");
        bootstrap.update("UPDATE reagent_catalog SET gtin='00999999999999' WHERE id=3");
        bootstrap.update("UPDATE reagent_batch SET udi_di='QA-DI' WHERE id IN (5001,5004)");
        as(tech, () -> {
            var result = inventory.traceUdi("QA-SHARED", null);
            assertEquals("00123456789012", result.get("gtin"));
            assertEquals("QA-NMPA", result.get("registrationCertNo"));
            assertEquals("QA-NMPA", result.get("nmpaCert"));
            assertEquals("QA-INS", result.get("insuranceCode"));
            assertEquals("QA-FLEX", result.get("hisCode"));
            assertEquals("QA-DI", result.get("udiDi"));
            var batches = (List<?>) result.get("batches");
            assertEquals(2, batches.size());
            assertTrue(batches.stream().allMatch(row -> "00123456789012".equals(((Map<?, ?>) row).get("gtin"))));
            return null;
        });
    }
    @Test void tracePdfContainsOnlyAuthorizedBusinessEvidence() throws Exception {
        bootstrap.update("UPDATE reagent_catalog SET gtin='00123456789012',registration_cert_no='QA-NMPA' WHERE id=1");
        bootstrap.update("UPDATE reagent_catalog SET generic_name=? WHERE id=1", "虚构检验试剂");
        bootstrap.update("UPDATE warehouse SET name=? WHERE id=101", "虚构中心库");
        bootstrap.update("UPDATE warehouse SET name=? WHERE id=102", "虚构科室库");
        bootstrap.update("UPDATE device SET name=? WHERE id=301", "虚构检验仪");
        bootstrap.update("UPDATE reagent_batch SET udi_di='QA-FOREIGN-DI' WHERE id=5003");
        var controller = new com.zlth.ivd.server.controller.TracePdfController(inventory);
        var response = as(tech, () -> {
            assertEquals(40400, assertThrows(BizException.class,
                    () -> controller.export("QA-FOREIGN-DI", null)).getErrorCode());
            try { return controller.export("QA-SHARED", null); }
            catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
        });
        assertEquals("no-store", response.getHeaders().getCacheControl());
        assertEquals(org.springframework.http.MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        assertTrue(response.getHeaders().getContentDisposition().isAttachment());
        byte[] pdf = Objects.requireNonNull(response.getBody());
        try (var document = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            String text = new org.apache.pdfbox.text.PDFTextStripper().getText(document);
            assertTrue(text.contains("追溯报告"));
            assertTrue(text.contains("虚构检验试剂"));
            assertTrue(text.contains("虚构检验仪"));
            assertTrue(text.contains("00123456789012"));
            assertTrue(text.contains("QA-NMPA"));
            assertFalse(text.contains("QA-FOREIGN-DI"));
            assertFalse(text.contains("QA-HASH"));
        }
        var output = java.nio.file.Path.of("../../output/pdf").toAbsolutePath().normalize();
        java.nio.file.Files.createDirectories(output);
        java.nio.file.Files.write(output.resolve("synthetic-udi-trace.pdf"), pdf);
    }
    @Test void nonclinicalRolesCannotReadLegacyPatientResults(){
        for(String role:List.of("FINANCE","STOREKEEPER","PURCHASER","IT_STAFF"))as(new InstitutionContext.Scope(1,null,1002L,role),()->{denied(40300,()->inventory.getTracePatients("QA-SHARED"));return null;});
    }
    @Test void legacyPatientHttpRouteEnforcesRoleAndKeepsFixedAdminException(){
        var factory = new org.springframework.aop.aspectj.annotation.AspectJProxyFactory(
                new com.zlth.ivd.inventory.controller.InventoryController(inventory));
        factory.addAspect(new com.zlth.ivd.common.aspect.RoleAspect());
        Object controller = factory.getProxy();
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.zlth.ivd.common.config.GlobalExceptionHandler()).build();
        as(new InstitutionContext.Scope(1,null,1003L,"FINANCE"),()->{
            try {
                mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/inventory/trace/patients/QA-SHARED"))
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(40300));
            } catch(Exception error){throw new AssertionError(error);}return null;
        });
        as(admin,()->{
            try {
                mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/inventory/trace/patients/QA-SHARED"))
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(200))
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.length()").value(2));
            } catch(Exception error){throw new AssertionError(error);}return null;
        });
    }
    @Test void ordinaryAdministratorDoesNotInheritFixedAdminClinicalException(){
        as(admin,()->{
            ((org.springframework.security.authentication.AbstractAuthenticationToken)SecurityContextHolder.getContext().getAuthentication()).setDetails("other-admin");
            denied(40300,()->inventory.getTracePatients("QA-SHARED"));return null;
        });
    }
    @Test void allClinicalRolesRespectResourceDepartmentBoundary(){
        for(String role:List.of("LAB_TECH","LAB_DOCTOR","LAB_DIRECTOR"))as(new InstitutionContext.Scope(1,11L,1001L,role),()->{
            denied(40400,()->inventory.getUniqueCodeTrace("QA-OTHER-DEPT"));
            denied(40400,()->instruments.unload(8002L));
            assertEquals(1,inventory.getTracePatients("QA-SHARED").size());return null;
        });
    }
    @Test void missingScopeOrClinicalDepartmentFailsClosed(){
        denied(40300,()->inventory.getTracePatients("QA-SHARED"));
        as(new InstitutionContext.Scope(1,null,1001L,"LAB_TECH"),()->{
            denied(40300,()->inventory.getTracePatients("QA-SHARED"));
            denied(40300,()->inventory.getUniqueCodeTrace("QA-OWN"));return null;
        });
    }
    @Test void clinicalUserSeesOwnLegacyResultsOnlyAndAdminRetainsExplicitAccess(){
        as(tech,()->{var rows=inventory.getTracePatients("QA-SHARED");assertEquals(1,rows.size());assertEquals("QA-HASH-1",rows.get(0).getPatientIdHash());return null;});
        as(admin,()->{assertEquals(2,inventory.getTracePatients("QA-SHARED").size());return null;});
    }
    @Test void clinicalFeatureDisabledDeniesEvenAdminLegacyRead(){
        Object target = AopTestUtils.getUltimateTargetObject(inventory);
        ReflectionTestUtils.setField(target,"clinicalEnabled",false);
        as(admin,()->{denied(40300,()->inventory.getTracePatients("QA-SHARED"));return null;});
    }
    @Test void legacyAmbiguousLotIsNeverAssignedToAnArbitraryReagent(){
        bootstrap.update("INSERT INTO reagent_batch(id,institution_id,reagent_id,batch_no,warehouse_id,stock,udi_status,expiry_date,version) VALUES(5005,1,2,'QA-SHARED',102,1,'IN_STOCK','2039-01-01',0)");
        as(tech,()->{denied(40900,()->inventory.getTracePatients("QA-SHARED"));return null;});
    }
    @Test void newClinicalDecisionIsVisibleInItsDepartmentButNotAnother(){
        var audit=context.getBean(com.zlth.ivd.audit.service.AuditPipelineService.class);var mapper=context.getBean(AuditDecisionLogMapper.class);
        Long id=as(tech,()->{
            var result=audit.executePipeline("QA-OBS","QA-HASH","QA-T","1",null,LocalDateTime.now(),"QA-SHARED");
            assertNotNull(mapper.selectById(result.getId()),"new decision must remain visible to its owning department");return result.getId();
        });
        assertEquals(11L,bootstrap.queryForObject("SELECT department_id FROM audit_decision_log WHERE id=?",Long.class,id));
        as(new InstitutionContext.Scope(1,12L,1002L,"LAB_TECH"),()->{assertNull(mapper.selectById(id));return null;});
    }

    @Configuration
    @Import({SupplyFlowIntegrationTest.Config.class,com.zlth.ivd.audit.service.impl.AuditPipelineServiceImpl.class,
            com.zlth.ivd.system.service.impl.UserServiceImpl.class,
            com.zlth.ivd.analytics.service.impl.WorkbenchServiceImpl.class,
            com.zlth.ivd.finance.service.impl.FinanceServiceImpl.class})
    @MapperScan({"com.zlth.ivd.license.mapper","com.zlth.ivd.audit.mapper","com.zlth.ivd.analytics.mapper","com.zlth.ivd.finance.mapper"})
    static class Config {
        @Bean com.zlth.ivd.rules.service.RulesService rules(){return mock(com.zlth.ivd.rules.service.RulesService.class);}
        @Bean com.zlth.ivd.inventory.service.AuditInventoryContextService auditContext(){return mock(com.zlth.ivd.inventory.service.AuditInventoryContextService.class);}
        @Bean com.zlth.ivd.catalog.service.ReagentCatalogService catalogs(){return mock(com.zlth.ivd.catalog.service.ReagentCatalogService.class);}
        @Bean MinioClient minio(){return mock(MinioClient.class);}
        @Bean MinioProperties minioProperties(){var props=new MinioProperties();props.setBucketCert("qa-cert");return props;}
        @Bean LicenseService licenses(com.zlth.ivd.license.mapper.SupplierLicenseMapper licenses,com.zlth.ivd.license.mapper.SupplierMapper suppliers,
                com.zlth.ivd.license.mapper.LicenseChangeLogMapper changes,com.zlth.ivd.license.mapper.ReagentSupplierRelMapper relations,
                com.zlth.ivd.catalog.service.ReagentCatalogService catalogs,MinioClient minio,MinioProperties props){
            return new com.zlth.ivd.license.service.impl.LicenseServiceImpl(licenses,suppliers,changes,relations,catalogs,minio,props);
        }
        @Bean InventoryService inventory(com.zlth.ivd.inventory.mapper.ReagentBatchMapper batches,com.zlth.ivd.inventory.mapper.WarehouseMapper warehouses,
                com.zlth.ivd.inventory.mapper.DeviceMapper devices,com.zlth.ivd.inventory.mapper.DeviceBatchLoadMapper loads,
                com.zlth.ivd.inventory.mapper.PatientAssociationMapper patients,com.zlth.ivd.catalog.mapper.ReagentCatalogMapper catalogs,
                com.zlth.ivd.udi.mapper.UdiUniqueCodeMapper codes,com.zlth.ivd.udi.service.UniqueCodeService codeService,
                com.zlth.ivd.inventory.service.ProcurementInventoryService stock,
                ApplicationEventPublisher events,JdbcTemplate jdbc,com.zlth.ivd.catalog.service.CatalogReferenceService references){
            return new com.zlth.ivd.inventory.service.impl.InventoryServiceImpl(batches,warehouses,devices,loads,patients,catalogs,codes,codeService,stock,events,jdbc,references);
        }
    }
}
