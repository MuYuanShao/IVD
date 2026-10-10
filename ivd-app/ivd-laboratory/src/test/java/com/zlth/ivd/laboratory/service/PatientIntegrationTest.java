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
            jdbc.execute("INSERT INTO sys_dept VALUES(11,1,1,0),(12,1,1,0),(21,2,1,0)");
            jdbc.execute("CREATE TABLE operation_audit_log(id BIGINT AUTO_INCREMENT PRIMARY KEY,institution_id BIGINT,operator_id BIGINT,operator_name VARCHAR(100),module VARCHAR(100),action VARCHAR(100),request_method VARCHAR(20),request_path VARCHAR(500),handler VARCHAR(200),client_ip VARCHAR(100),reason VARCHAR(500),success BOOLEAN,error_message VARCHAR(1000),duration_ms BIGINT,occurred_at TIMESTAMP)");
            return source;
        }
        @Bean SqlSessionFactory factory(DataSource source)throws Exception{
            var config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);
            config.addMapper(LabPatientMapper.class);config.addMapper(LabPatientDepartmentMapper.class);config.addMapper(OperationAuditLogMapper.class);
            config.addMapper(AuthenticationDirectoryMapper.class);
            var plugin=new MybatisPlusInterceptor();plugin.addInnerInterceptor(new InstitutionSqlInterceptor());plugin.addInnerInterceptor(new OptimisticLockerInnerInterceptor());plugin.addInnerInterceptor(new PaginationInnerInterceptor());
            var factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(source);factory.setConfiguration(config);factory.setPlugins(plugin);return factory.getObject();
        }
        @Bean SqlSessionTemplate template(SqlSessionFactory factory){return new SqlSessionTemplate(factory);}
        @Bean LabPatientMapper patients(SqlSessionTemplate template){return template.getMapper(LabPatientMapper.class);}
        @Bean LabPatientDepartmentMapper grants(SqlSessionTemplate template){return template.getMapper(LabPatientDepartmentMapper.class);}
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
