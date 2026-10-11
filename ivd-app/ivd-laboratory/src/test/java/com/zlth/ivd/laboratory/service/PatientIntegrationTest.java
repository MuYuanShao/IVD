package com.zlth.ivd.laboratory.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zlth.ivd.common.audit.OperationAuditLogWriter;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.common.mapper.OperationAuditLogMapper;
import com.zlth.ivd.common.security.*;
import com.zlth.ivd.laboratory.dto.PatientRequest;
import com.zlth.ivd.laboratory.dto.PatientDepartmentRequest;
import com.zlth.ivd.system.mapper.AuthenticationDirectoryMapper;
import com.zlth.ivd.system.service.AuthenticationDirectoryService;
import com.zlth.ivd.system.service.impl.AuthenticationDirectoryServiceImpl;
import com.zlth.ivd.laboratory.mapper.*;
import com.zlth.ivd.laboratory.security.ClinicalCrypto;
import com.zlth.ivd.laboratory.service.impl.PatientServiceImpl;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.util.*;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import static org.junit.jupiter.api.Assertions.*;

class PatientIntegrationTest {
    private AnnotationConfigApplicationContext context;
    private PatientService service;
    private final InstitutionContext.Scope departmentA = new InstitutionContext.Scope(1L,11L,101L,"LAB_TECH");
    @BeforeEach void setup() {
        context=new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",Map.of("ivd.isolation.enabled","true")));
        context.register(Config.class);context.refresh();service=context.getBean(PatientService.class);
    }
    @AfterEach void cleanup(){context.close();assertTrue(InstitutionContext.current().isEmpty());}
    private PatientRequest request(){return new PatientRequest("TEST-P-001","虚构测试患者","FEMALE",LocalDate.of(1990,1,1),null);}
    @Test void authorizedDepartmentCanCreateAndReadWithMaskedList() {
        InstitutionContext.within(departmentA,()->{
            var patient=service.create(request());
            assertEquals("虚构测试患者",service.detail(patient.id()).name());
            var row=service.list(1,20).getRecords().get(0);
            assertNotEquals("虚构测试患者",row.maskedName());
            assertNotEquals("TEST-P-001",row.maskedNumber());
            return null;
        });
    }
    @Test void otherDepartmentInstitutionAndSystemAdminCannotReadClinicalDetails() {
        var patient=InstitutionContext.within(departmentA,()->service.create(request()));
        for(var scope:List.of(new InstitutionContext.Scope(1L,12L,102L,"LAB_TECH"),new InstitutionContext.Scope(2L,21L,201L,"LAB_TECH"))) {
            InstitutionContext.within(scope,()->{assertEquals(0,service.list(1,20).getTotal());assertThrows(BizException.class,()->service.detail(patient.id()));return null;});
        }
        InstitutionContext.within(new InstitutionContext.Scope(1L,11L,99L,"SYSTEM_ADMIN"),()->{
            assertThrows(BizException.class,()->service.detail(patient.id()));return null;
        });
    }
    @Test void updateRequiresCurrentVersionAndRemainsEncrypted() {
        InstitutionContext.within(departmentA,()->{
            var patient=service.create(request());
            var change=new PatientRequest(patient.number(),"虚构修订姓名",patient.sex(),patient.birthDate(),patient.version());
            assertEquals("虚构修订姓名",service.update(patient.id(),change).name());
            assertThrows(BizException.class,()->service.update(patient.id(),change));return null;
        });
    }
    @Test void explicitAdminGrantAllowsOnlySelectedOwnDepartmentAndRevocationRemovesAccess() {
        var patient=InstitutionContext.within(departmentA,()->service.create(request()));
        var admin=new InstitutionContext.Scope(1L,null,99L,"SYSTEM_ADMIN");
        var other=new InstitutionContext.Scope(1L,12L,102L,"LAB_TECH");
        InstitutionContext.within(admin,()->{
            assertThrows(BizException.class,()->service.setDepartmentAccess(patient.id(),new PatientDepartmentRequest(21L,true,"CLINICAL_COLLABORATION")));
            service.setDepartmentAccess(patient.id(),new PatientDepartmentRequest(12L,true,"CLINICAL_COLLABORATION"));return null;
        });
        InstitutionContext.within(other,()->{assertEquals(patient.id(),service.detail(patient.id()).id());return null;});
        InstitutionContext.within(admin,()->{service.setDepartmentAccess(patient.id(),new PatientDepartmentRequest(12L,false,"REVOCATION"));return null;});
        InstitutionContext.within(other,()->{assertThrows(BizException.class,()->service.detail(patient.id()));return null;});
    }
    @Test void rolledBackGrantCannotLeaveASuccessAudit() {
        var patient=InstitutionContext.within(departmentA,()->service.create(request()));
        var admin=new InstitutionContext.Scope(1L,null,99L,"SYSTEM_ADMIN");
        var transaction=new org.springframework.transaction.support.TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        InstitutionContext.within(admin,()->{
            transaction.execute(status->{service.setDepartmentAccess(patient.id(),new PatientDepartmentRequest(12L,true,"CLINICAL_COLLABORATION"));status.setRollbackOnly();return null;});return null;
        });
        InstitutionContext.within(new InstitutionContext.Scope(1L,12L,102L,"LAB_TECH"),()->{
            assertThrows(BizException.class,()->service.detail(patient.id()));return null;
        });
        var jdbc=new JdbcTemplate(context.getBean(DataSource.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM operation_audit_log WHERE action='DEPARTMENT_CLINICAL_COLLABORATION_12'",Integer.class));
    }
    @Test void failedSuccessAuditRollsBackDepartmentGrant() {
        var patient=InstitutionContext.within(departmentA,()->service.create(request()));
        var jdbc=new JdbcTemplate(context.getBean(DataSource.class));
        jdbc.execute("ALTER TABLE operation_audit_log ADD CONSTRAINT reject_department_audit CHECK(action NOT LIKE 'DEPARTMENT_%')");
        InstitutionContext.within(new InstitutionContext.Scope(1L,null,99L,"SYSTEM_ADMIN"),()->{
            assertThrows(org.springframework.dao.DataAccessException.class,()->service.setDepartmentAccess(patient.id(),new PatientDepartmentRequest(12L,true,"CLINICAL_COLLABORATION")));return null;
        });
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM lab_patient_department WHERE patient_id=? AND department_id=12 AND active=TRUE",Integer.class,patient.id()));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM operation_audit_log WHERE action LIKE 'DEPARTMENT_%'",Integer.class));
    }
    @Test void storageContainsOnlyCiphertextAndIndexNotRawDemographics() {
        InstitutionContext.within(departmentA,()->{service.create(request());return null;});
        var row=new JdbcTemplate(context.getBean(DataSource.class)).queryForMap("SELECT encrypted_profile,number_index FROM lab_patient");
        assertTrue(row.get("encrypted_profile").toString().startsWith("v1."));
        for(String raw:List.of(request().number(),request().name(),request().birthDate().toString())) {
            assertFalse(row.toString().contains(raw));
        }
    }
    @Test void substitutedCiphertextCannotReturnAnotherPatientProfile() {
        var first=InstitutionContext.within(departmentA,()->service.create(request()));
        var second=InstitutionContext.within(departmentA,()->service.create(new PatientRequest("TEST-P-002","另一虚构患者","UNKNOWN",null,null)));
        var jdbc=new JdbcTemplate(context.getBean(DataSource.class));
        String replacement=jdbc.queryForObject("SELECT encrypted_profile FROM lab_patient WHERE id=?",String.class,second.id());
        jdbc.update("UPDATE lab_patient SET encrypted_profile=? WHERE id=?",replacement,first.id());
        InstitutionContext.within(departmentA,()->{assertThrows(BizException.class,()->service.detail(first.id()));return null;});
    }
    @Test void sameInstitutionDuplicateNeverAutoSharesOrMergesButOtherInstitutionCanUseSameNumber() {
        InstitutionContext.within(departmentA,()->{service.create(request());return null;});
        InstitutionContext.within(new InstitutionContext.Scope(1L,12L,102L,"LAB_TECH"),()->{
            assertThrows(BizException.class,()->service.create(request()));assertEquals(0,service.list(1,20).getTotal());return null;
        });
        InstitutionContext.within(new InstitutionContext.Scope(2L,21L,201L,"LAB_TECH"),()->{
            assertEquals(request().number(),service.create(request()).number());return null;
        });
    }
    @Test void invalidClinicalPayloadCannotEchoPatientInformation() throws Exception {
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new com.zlth.ivd.laboratory.controller.PatientController(service))
                .setControllerAdvice(new com.zlth.ivd.laboratory.controller.ClinicalExceptionHandler()).build();
        var result=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/lab/patients")
                .contentType("application/json").content("{\"number\":\"TEST\",\"name\":\"DO_NOT_LOG_PATIENT\",\"sex\":\"FEMALE\",\"birthDate\":\"DO_NOT_LOG_PATIENT\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest()).andReturn();
        assertFalse(result.getResponse().getContentAsString().contains("DO_NOT_LOG_PATIENT"));
    }
    @Configuration @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource(){
            var source=new JdbcDataSource();source.setURL("jdbc:h2:mem:patient"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            var jdbc=new JdbcTemplate(source);
            jdbc.execute("CREATE TABLE lab_patient(id BIGINT AUTO_INCREMENT PRIMARY KEY,institution_id BIGINT NOT NULL,number_index VARCHAR(64),encrypted_profile VARCHAR(8192),version INT,created_at TIMESTAMP,updated_at TIMESTAMP,UNIQUE(institution_id,number_index))");
            jdbc.execute("CREATE TABLE lab_patient_department(id BIGINT AUTO_INCREMENT PRIMARY KEY,institution_id BIGINT NOT NULL,patient_id BIGINT,department_id BIGINT,active BOOLEAN,created_at TIMESTAMP,updated_at TIMESTAMP,UNIQUE(institution_id,patient_id,department_id))");
            jdbc.execute("CREATE TABLE sys_dept(id BIGINT PRIMARY KEY,institution_id BIGINT,status INT,is_deleted INT)");
            jdbc.execute("CREATE TABLE lab_sample(id BIGINT AUTO_INCREMENT PRIMARY KEY,institution_id BIGINT NOT NULL,department_id BIGINT,patient_id BIGINT,number_index VARCHAR(64),encrypted_details VARCHAR(8192),status VARCHAR(20),version INT,created_by BIGINT,received_at TIMESTAMP,created_at TIMESTAMP,updated_at TIMESTAMP,UNIQUE(institution_id,number_index))");
            jdbc.execute("CREATE TABLE lab_sample_event(id BIGINT AUTO_INCREMENT PRIMARY KEY,institution_id BIGINT NOT NULL,department_id BIGINT,sample_id BIGINT,from_status VARCHAR(20),to_status VARCHAR(20),encrypted_reason VARCHAR(8192),actor_id BIGINT,occurred_at TIMESTAMP)");
            jdbc.execute("INSERT INTO sys_dept VALUES(11,1,1,0),(12,1,1,0),(21,2,1,0)");
            jdbc.execute("CREATE TABLE lab_result(id BIGINT AUTO_INCREMENT PRIMARY KEY,institution_id BIGINT NOT NULL,department_id BIGINT,sample_id BIGINT,item_id BIGINT,device_id BIGINT,testing_institution_id BIGINT,execution_mode VARCHAR(20),encrypted_snapshot VARCHAR(32000),version INT,created_by BIGINT,updated_by BIGINT,created_at TIMESTAMP,updated_at TIMESTAMP,UNIQUE(institution_id,sample_id,item_id))");
            jdbc.execute("CREATE TABLE lab_result_revision(id BIGINT AUTO_INCREMENT PRIMARY KEY,institution_id BIGINT NOT NULL,department_id BIGINT,result_id BIGINT,version INT,encrypted_snapshot VARCHAR(32000),actor_id BIGINT,occurred_at TIMESTAMP,UNIQUE(institution_id,result_id,version))");
            jdbc.execute("CREATE TABLE lab_result_evidence(id BIGINT AUTO_INCREMENT PRIMARY KEY,institution_id BIGINT,department_id BIGINT,result_id BIGINT,result_version INT,batch_id BIGINT,device_load_id BIGINT,consumption_id BIGINT,evidence_key VARCHAR(64),encrypted_snapshot VARCHAR(32000),actor_id BIGINT,created_at TIMESTAMP,UNIQUE(institution_id,evidence_key))");
            jdbc.execute("CREATE TABLE lab_report(id BIGINT AUTO_INCREMENT PRIMARY KEY,institution_id BIGINT,department_id BIGINT,sample_id BIGINT,patient_id BIGINT,root_id BIGINT,edition INT,number_index VARCHAR(64),status VARCHAR(32),encrypted_snapshot CLOB,version INT,created_by BIGINT,reviewed_by BIGINT,signed_by BIGINT,signed_at TIMESTAMP,pdf_key VARCHAR(256),pdf_digest VARCHAR(64),created_at TIMESTAMP,updated_at TIMESTAMP,UNIQUE(institution_id,number_index,edition))");
            jdbc.execute("CREATE TABLE lab_report_result(id BIGINT AUTO_INCREMENT PRIMARY KEY,institution_id BIGINT,department_id BIGINT,report_id BIGINT,result_id BIGINT,result_version INT,UNIQUE(institution_id,report_id,result_id))");
            jdbc.execute("CREATE TABLE lab_report_event(id BIGINT AUTO_INCREMENT PRIMARY KEY,institution_id BIGINT,department_id BIGINT,report_id BIGINT,action VARCHAR(32),from_status VARCHAR(32),to_status VARCHAR(32),encrypted_snapshot CLOB,actor_id BIGINT,occurred_at TIMESTAMP)");
            jdbc.execute("CREATE TABLE lab_import_batch(id BIGINT AUTO_INCREMENT PRIMARY KEY,institution_id BIGINT,department_id BIGINT,actor_id BIGINT,resource VARCHAR(32),content_index VARCHAR(64),status VARCHAR(32),encrypted_document CLOB,encrypted_outcome CLOB,row_count INT,succeeded INT,execution_key VARCHAR(36),lease_until TIMESTAMP,expires_at TIMESTAMP,created_at TIMESTAMP,updated_at TIMESTAMP,UNIQUE(institution_id,actor_id,content_index))");
            jdbc.execute("CREATE TABLE lab_import_unit(id BIGINT AUTO_INCREMENT PRIMARY KEY,institution_id BIGINT,department_id BIGINT,batch_id BIGINT,unit_key VARCHAR(64),status VARCHAR(32),row_count INT,encrypted_outcome CLOB,created_at TIMESTAMP,UNIQUE(institution_id,batch_id,unit_key))");
            jdbc.execute("CREATE TABLE reagent_batch(id BIGINT PRIMARY KEY,institution_id BIGINT,batch_no VARCHAR(64),reagent_id BIGINT,material_type VARCHAR(20),udi_di VARCHAR(64),udi_pi VARCHAR(64),manufacture_date DATE,expiry_date DATE,open_date DATE,open_expiry_date DATE,stock DECIMAL(10,2),unit VARCHAR(32),unit_price DECIMAL(12,2),avg_cost DECIMAL(12,2),udi_status VARCHAR(30),warehouse_id BIGINT,location_code VARCHAR(64),version INT,created_at TIMESTAMP,updated_at TIMESTAMP)");
            jdbc.execute("ALTER TABLE reagent_batch ADD inventory_generation INT DEFAULT 0");
            jdbc.execute("ALTER TABLE reagent_batch ADD origin_purchase_order_item_id BIGINT");
            jdbc.execute("ALTER TABLE reagent_batch ADD settlement_mode VARCHAR(32)");
            jdbc.execute("INSERT INTO reagent_batch(id,institution_id,batch_no,reagent_id,stock,unit,warehouse_id,version) VALUES(51,1,'FICTION-BATCH',81,10,'盒',102,0),(52,2,'OTHER-BATCH',82,10,'盒',202,0)");
            jdbc.execute("CREATE TABLE device_batch_load(id BIGINT PRIMARY KEY,institution_id BIGINT,device_id BIGINT,batch_id BIGINT,batch_no VARCHAR(64),reagent_id BIGINT,operator_id BIGINT,load_time TIMESTAMP,unload_time TIMESTAMP,created_at TIMESTAMP)");
            jdbc.execute("INSERT INTO device_batch_load(id,institution_id,device_id,batch_id,batch_no,reagent_id,operator_id,load_time) VALUES(61,1,1,51,'FICTION-BATCH',81,101,DATEADD('DAY',-1,CURRENT_TIMESTAMP)),(62,2,2,52,'OTHER-BATCH',82,201,DATEADD('DAY',-1,CURRENT_TIMESTAMP))");
            jdbc.execute("CREATE TABLE consumption_record(id BIGINT PRIMARY KEY,institution_id BIGINT,record_type VARCHAR(20),batch_no VARCHAR(64),batch_id BIGINT,purchase_order_item_id BIGINT,requisition_item_id BIGINT,reagent_id BIGINT,material_type VARCHAR(20),device_id BIGINT,quantity DECIMAL(10,2),unit VARCHAR(32),unit_price DECIMAL(12,2),amount DECIMAL(12,2),purpose VARCHAR(30),warehouse_id BIGINT,requisition_id BIGINT,from_warehouse_id BIGINT,operator_id BIGINT,remark VARCHAR(200),occurred_at TIMESTAMP,created_at TIMESTAMP)");
            jdbc.execute("ALTER TABLE consumption_record ADD settlement_mode VARCHAR(32)");
            jdbc.execute("INSERT INTO consumption_record(id,institution_id,record_type,batch_no,batch_id,reagent_id,device_id,quantity,unit,amount,warehouse_id,operator_id,occurred_at) VALUES(71,1,'CONSUME','FICTION-BATCH',51,81,1,1,'盒',100,102,101,CURRENT_TIMESTAMP),(72,2,'CONSUME','OTHER-BATCH',52,82,2,1,'盒',100,202,201,CURRENT_TIMESTAMP)");
            jdbc.execute("CREATE TABLE lab_test_item(id BIGINT AUTO_INCREMENT PRIMARY KEY,institution_id BIGINT NOT NULL,code VARCHAR(64),name VARCHAR(200),methodology VARCHAR(100),result_type VARCHAR(10),unit VARCHAR(32),active BOOLEAN,version INT,created_at TIMESTAMP,updated_at TIMESTAMP,UNIQUE(institution_id,code))");
            jdbc.execute("INSERT INTO lab_test_item VALUES(1,1,'K','虚构钾项目','酶法','NUMERIC','mmol/L',TRUE,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),(2,2,'OTHER','外机构项目','酶法','NUMERIC','mmol/L',TRUE,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),(3,1,'TEXT','虚构定性项目','手工','TEXT','',TRUE,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            jdbc.execute("ALTER TABLE lab_test_item ADD device_required BOOLEAN DEFAULT FALSE");
            jdbc.execute("CREATE TABLE lab_device_capability(id BIGINT AUTO_INCREMENT PRIMARY KEY,institution_id BIGINT,device_id BIGINT,item_id BIGINT,active BOOLEAN,version INT,created_at TIMESTAMP,updated_at TIMESTAMP,UNIQUE(institution_id,device_id,item_id))");
            jdbc.execute("INSERT INTO lab_device_capability(institution_id,device_id,item_id,active,version,created_at,updated_at) VALUES(1,1,1,TRUE,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            jdbc.execute("CREATE TABLE lab_interval(id BIGINT AUTO_INCREMENT PRIMARY KEY,institution_id BIGINT NOT NULL,item_id BIGINT,device_id BIGINT,kind VARCHAR(20),methodology VARCHAR(100),unit VARCHAR(32),result_type VARCHAR(10),sex VARCHAR(10),min_age_days INT,max_age_days INT,lower_bound DECIMAL(18,6),upper_bound DECIMAL(18,6),normal_text VARCHAR(200),valid_from DATE,valid_to DATE,active BOOLEAN,version INT,created_at TIMESTAMP,updated_at TIMESTAMP)");
            jdbc.execute("CREATE TABLE warehouse(id BIGINT PRIMARY KEY,institution_id BIGINT,parent_id BIGINT,dept_id BIGINT,level INT)");
            jdbc.execute("INSERT INTO warehouse VALUES(101,1,NULL,NULL,1),(102,1,101,11,2),(103,1,101,12,2),(201,2,NULL,NULL,1),(202,2,201,21,2)");
            jdbc.execute("ALTER TABLE warehouse ADD status INT DEFAULT 1");jdbc.execute("ALTER TABLE warehouse ADD is_active BOOLEAN DEFAULT TRUE");
            jdbc.execute("CREATE TABLE device(id BIGINT PRIMARY KEY,institution_id BIGINT,dept_id BIGINT,device_code VARCHAR(64),name VARCHAR(100),location_id BIGINT,is_active BOOLEAN)");
            jdbc.execute("INSERT INTO device VALUES(1,1,11,'OWN','虚构设备',102,TRUE),(2,2,21,'FOREIGN','外机构设备',202,TRUE),(3,1,12,'OTHER-DEPT','其他科室设备',103,TRUE),(4,1,11,'STOPPED','停用设备',102,FALSE)");
            jdbc.execute("ALTER TABLE device ADD version INT DEFAULT 0");jdbc.execute("ALTER TABLE device ADD model VARCHAR(64)");jdbc.execute("ALTER TABLE device ADD manufacturer VARCHAR(128)");jdbc.execute("ALTER TABLE device ADD last_calibration_date DATE");jdbc.execute("ALTER TABLE device ADD next_calibration_date DATE");jdbc.execute("ALTER TABLE device ADD created_at TIMESTAMP");jdbc.execute("ALTER TABLE device ADD updated_at TIMESTAMP");
            jdbc.execute("CREATE TABLE institution(id BIGINT PRIMARY KEY,code VARCHAR(64),name VARCHAR(100),type VARCHAR(20),owner_institution_id BIGINT,contact_person VARCHAR(100),contact_phone VARCHAR(100),address VARCHAR(100),capabilities VARCHAR(100),status INT,created_at TIMESTAMP,updated_at TIMESTAMP)");
            jdbc.execute("INSERT INTO institution(id,code,name,type,owner_institution_id,status) VALUES(1,'OWN','虚构本院','INTERNAL',NULL,1),(2,'OTHER','虚构他院','INTERNAL',NULL,1),(3,'EXTERNAL','虚构外送机构','EXTERNAL',1,1),(4,'OTHER-EXT','外院维护外送机构','EXTERNAL',2,1)");
            jdbc.execute("CREATE TABLE operation_audit_log(id BIGINT AUTO_INCREMENT PRIMARY KEY,institution_id BIGINT,operator_id BIGINT,operator_name VARCHAR(100),module VARCHAR(100),action VARCHAR(100),request_method VARCHAR(20),request_path VARCHAR(500),handler VARCHAR(200),client_ip VARCHAR(100),reason VARCHAR(500),success BOOLEAN,error_message VARCHAR(1000),duration_ms BIGINT,occurred_at TIMESTAMP)");
            return source;
        }
        @Bean SqlSessionFactory factory(DataSource source)throws Exception{
            var config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);
            config.addMapper(LabPatientMapper.class);config.addMapper(LabPatientDepartmentMapper.class);config.addMapper(OperationAuditLogMapper.class);
            config.addMapper(AuthenticationDirectoryMapper.class);
            config.addMapper(LabSampleMapper.class);config.addMapper(LabSampleEventMapper.class);
            config.addMapper(LabResultMapper.class);config.addMapper(LabResultRevisionMapper.class);config.addMapper(LabTestItemMapper.class);config.addMapper(LabIntervalMapper.class);
            config.addMapper(LabDeviceCapabilityMapper.class);
            config.addMapper(LabResultEvidenceMapper.class);config.addMapper(com.zlth.ivd.inventory.mapper.ReagentBatchMapper.class);config.addMapper(com.zlth.ivd.inventory.mapper.DeviceBatchLoadMapper.class);config.addMapper(com.zlth.ivd.consumption.mapper.ConsumptionRecordMapper.class);
            config.addMapper(LabReportMapper.class);config.addMapper(LabReportEventMapper.class);config.addMapper(LabReportResultMapper.class);
            config.addMapper(LabImportBatchMapper.class);config.addMapper(LabImportUnitMapper.class);config.addMapper(com.zlth.ivd.inventory.mapper.WarehouseMapper.class);
            config.addMapper(com.zlth.ivd.inventory.mapper.DeviceRegistryMapper.class);
            config.addMapper(com.zlth.ivd.inventory.mapper.DeviceMapper.class);config.addMapper(com.zlth.ivd.system.mapper.InstitutionMapper.class);
            var plugin=new MybatisPlusInterceptor();plugin.addInnerInterceptor(new InstitutionSqlInterceptor());plugin.addInnerInterceptor(new OptimisticLockerInnerInterceptor());plugin.addInnerInterceptor(new PaginationInnerInterceptor());
            var factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(source);factory.setConfiguration(config);factory.setPlugins(plugin);return factory.getObject();
        }
        @Bean SqlSessionTemplate template(SqlSessionFactory factory){return new SqlSessionTemplate(factory);}
        @Bean LabPatientMapper patients(SqlSessionTemplate template){return template.getMapper(LabPatientMapper.class);}
        @Bean LabPatientDepartmentMapper grants(SqlSessionTemplate template){return template.getMapper(LabPatientDepartmentMapper.class);}
        @Bean LabSampleMapper samples(SqlSessionTemplate template){return template.getMapper(LabSampleMapper.class);}
        @Bean LabSampleEventMapper sampleEvents(SqlSessionTemplate template){return template.getMapper(LabSampleEventMapper.class);}
        @Bean LabResultMapper results(SqlSessionTemplate template){return template.getMapper(LabResultMapper.class);}
        @Bean LabDeviceCapabilityMapper capabilities(SqlSessionTemplate template){return template.getMapper(LabDeviceCapabilityMapper.class);}
        @Bean LabResultEvidenceMapper evidence(SqlSessionTemplate template){return template.getMapper(LabResultEvidenceMapper.class);}
        @Bean LabReportMapper reports(SqlSessionTemplate template){return template.getMapper(LabReportMapper.class);}
        @Bean LabReportResultMapper reportReferences(SqlSessionTemplate template){return template.getMapper(LabReportResultMapper.class);}
        @Bean LabReportEventMapper reportEvents(SqlSessionTemplate template){return template.getMapper(LabReportEventMapper.class);}
        @Bean LabImportBatchMapper importBatches(SqlSessionTemplate template){return template.getMapper(LabImportBatchMapper.class);}
        @Bean LabImportUnitMapper importUnits(SqlSessionTemplate template){return template.getMapper(LabImportUnitMapper.class);}
        @Bean com.zlth.ivd.inventory.service.DeviceRegistryService registry(SqlSessionTemplate template,com.zlth.ivd.inventory.service.DeviceDirectoryService directory,AuthenticationDirectoryService departments,Validator validator,OperationAuditLogWriter audits){return new com.zlth.ivd.inventory.service.impl.DeviceRegistryServiceImpl(template.getMapper(com.zlth.ivd.inventory.mapper.DeviceMapper.class),template.getMapper(com.zlth.ivd.inventory.mapper.DeviceRegistryMapper.class),template.getMapper(com.zlth.ivd.inventory.mapper.WarehouseMapper.class),directory,departments,validator,audits);}
        @Bean com.zlth.ivd.inventory.service.BatchEvidenceDirectoryService batches(SqlSessionTemplate template){return new com.zlth.ivd.inventory.service.impl.BatchEvidenceDirectoryServiceImpl(template.getMapper(com.zlth.ivd.inventory.mapper.ReagentBatchMapper.class),template.getMapper(com.zlth.ivd.inventory.mapper.DeviceBatchLoadMapper.class));}
        @Bean com.zlth.ivd.consumption.service.ConsumptionEvidenceService consumption(SqlSessionTemplate template){return new com.zlth.ivd.consumption.service.impl.ConsumptionEvidenceServiceImpl(template.getMapper(com.zlth.ivd.consumption.mapper.ConsumptionRecordMapper.class));}
        @Bean LabResultRevisionMapper revisions(SqlSessionTemplate template){return template.getMapper(LabResultRevisionMapper.class);}
        @Bean LabCatalogService catalog(SqlSessionTemplate template,Validator validator,com.zlth.ivd.inventory.service.DeviceDirectoryService devices){
            return new com.zlth.ivd.laboratory.service.impl.LabCatalogServiceImpl(template.getMapper(LabTestItemMapper.class),template.getMapper(LabIntervalMapper.class),devices,new IntervalPolicy(),validator);
        }
        @Bean com.zlth.ivd.inventory.service.DeviceDirectoryService devices(SqlSessionTemplate template){return new com.zlth.ivd.inventory.service.impl.DeviceDirectoryServiceImpl(template.getMapper(com.zlth.ivd.inventory.mapper.DeviceMapper.class));}
        @Bean com.zlth.ivd.system.service.InstitutionService institutions(SqlSessionTemplate template,AuthenticationDirectoryService directory){return new com.zlth.ivd.system.service.impl.InstitutionServiceImpl(template.getMapper(com.zlth.ivd.system.mapper.InstitutionMapper.class),directory);}
        @Bean com.zlth.ivd.laboratory.security.ClinicalRecordSupport records(Validator validator,OperationAuditLogWriter audits){
            // Use the actual application JSON customization, not an ISO-only test mapper.
            var builder=new org.springframework.http.converter.json.Jackson2ObjectMapperBuilder();
            new com.zlth.ivd.common.config.JacksonConfig().jsonCustomizer().customize(builder);
            return new com.zlth.ivd.laboratory.security.ClinicalRecordSupport(new ClinicalCrypto(Base64.getEncoder().encodeToString(new byte[32]),"test-jwt-key-not-encryption-key"),builder.build(),validator,audits);
        }
        @Bean OperationAuditLogMapper auditMapper(SqlSessionTemplate template){return template.getMapper(OperationAuditLogMapper.class);}
        @Bean OperationAuditLogWriter audits(ObjectProvider<OperationAuditLogMapper> provider){return new OperationAuditLogWriter(provider);}
        @Bean AuthenticationDirectoryService directory(SqlSessionTemplate template){return new AuthenticationDirectoryServiceImpl(template.getMapper(AuthenticationDirectoryMapper.class));}
        @Bean Validator validator(){return new LocalValidatorFactoryBean();}
        @Bean PlatformTransactionManager transactions(DataSource source){return new DataSourceTransactionManager(source);}
        @Bean PatientService service(LabPatientMapper patients,LabPatientDepartmentMapper grants,Validator validator,OperationAuditLogWriter audits,AuthenticationDirectoryService directory){
            return new PatientServiceImpl(patients,grants,new ClinicalCrypto(Base64.getEncoder().encodeToString(new byte[32]),"test-jwt-key-not-encryption-key"),new ObjectMapper().findAndRegisterModules(),validator,audits,directory);
        }
    }
}
