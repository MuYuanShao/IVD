package com.zlth.ivd.server.config;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zlth.ivd.common.audit.OperationAuditLogWriter;
import com.zlth.ivd.common.mapper.OperationAuditLogMapper;
import com.zlth.ivd.common.security.InstitutionContext;
import com.zlth.ivd.common.utils.JwtTokenProvider;
import com.zlth.ivd.system.mapper.AuthenticationDirectoryMapper;
import com.zlth.ivd.system.service.AuthenticationDirectoryService;
import com.zlth.ivd.system.service.impl.AuthenticationDirectoryServiceImpl;
import java.util.*;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real signed JWT, security filter chain and authentication-directory queries against an isolated DB. */
class InstitutionRequestIntegrationTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private JwtTokenProvider tokens;
    @BeforeEach void setup(){
        context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,"ivd.isolation.enabled=true");
        context.register(Config.class);context.refresh();tokens=context.getBean(JwtTokenProvider.class);
        mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
    }
    @AfterEach void cleanup(){context.close();assertTrue(InstitutionContext.current().isEmpty());}
    private String bearer(Long uid,String role,Long owner){return "Bearer "+tokens.generateToken(uid,"test",role,owner,11L);}

    @Test void verifiedAccountDeterminesDepartmentAndContextIsCleared()throws Exception{
        mvc.perform(get("/api/v1/institution-probe").header("Authorization",bearer(101L,"LAB_TECH",1L)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.institutionId").value(1)).andExpect(jsonPath("$.departmentId").value(11));
        assertTrue(InstitutionContext.current().isEmpty());
    }
    @Test void ordinaryUserCannotSwitchEvenWithValidToken()throws Exception{
        mvc.perform(get("/api/v1/institution-probe").header("Authorization",bearer(101L,"LAB_TECH",1L)).header("X-Institution-Id","2"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40300));
    }
    @Test void oldAndRefreshTokensAreRejected()throws Exception{
        for(String token:List.of(tokens.generateToken(101L,"test","LAB_TECH"),tokens.generateRefreshToken(101L,1L))){
            mvc.perform(get("/api/v1/institution-probe").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
        }
    }
    @Test void changedRoleAndInactiveInstitutionInvalidateContext()throws Exception{
        mvc.perform(get("/api/v1/institution-probe").header("Authorization",bearer(101L,"SYSTEM_ADMIN",1L))).andExpect(status().isUnauthorized());
        context.getBean(JdbcTemplate.class).update("UPDATE institution SET status=0 WHERE id=1");
        mvc.perform(get("/api/v1/institution-probe").header("Authorization",bearer(101L,"LAB_TECH",1L))).andExpect(status().isForbidden());
    }
    @Test void adminSelectionClearsOldDepartmentAndWritesSelectionAudit()throws Exception{
        mvc.perform(get("/api/v1/institution-probe").header("Authorization",bearer(102L,"SYSTEM_ADMIN",1L)).header("X-Institution-Id","2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.institutionId").value(2)).andExpect(jsonPath("$.departmentId").doesNotExist());
        var rows=context.getBean(JdbcTemplate.class).queryForList("SELECT institution_id,action FROM operation_audit_log");
        assertEquals(1,rows.size());assertEquals(2L,((Number)rows.get(0).get("institution_id")).longValue());assertEquals("SELECT_INSTITUTION",rows.get(0).get("action"));
    }
    @Test void foreignDepartmentIsRejectedAndControllerExceptionClearsScope()throws Exception{
        context.getBean(JdbcTemplate.class).update("UPDATE sys_user SET dept_id=22 WHERE id=101");
        mvc.perform(get("/api/v1/institution-probe").header("Authorization",bearer(101L,"LAB_TECH",1L))).andExpect(status().isForbidden());
        assertThrows(jakarta.servlet.ServletException.class,()->mvc.perform(get("/api/v1/institution-probe/failure").header("Authorization",bearer(102L,"SYSTEM_ADMIN",1L))));
        assertTrue(InstitutionContext.current().isEmpty());
    }

    @Configuration @EnableWebMvc @Import(SecurityConfig.class)
    static class Config {
        @Bean ObjectMapper objectMapper(){return new ObjectMapper().findAndRegisterModules();}
        @Bean JwtTokenProvider tokens(){return new JwtTokenProvider("0123456789abcdef0123456789abcdef",60000,120000);}
        @Bean DataSource dataSource(){
            var source=new JdbcDataSource();source.setURL("jdbc:h2:mem:request"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");var jdbc=new JdbcTemplate(source);
            jdbc.execute("CREATE TABLE institution(id BIGINT PRIMARY KEY,name VARCHAR(100),type VARCHAR(20),status INT)");
            jdbc.execute("INSERT INTO institution VALUES(1,'Test A','INTERNAL',1),(2,'Test B','INTERNAL',1)");
            jdbc.execute("CREATE TABLE sys_dept(id BIGINT PRIMARY KEY,institution_id BIGINT,status INT,is_deleted INT)");
            jdbc.execute("INSERT INTO sys_dept VALUES(11,1,1,0),(22,2,1,0)");
            jdbc.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY,institution_id BIGINT,dept_id BIGINT,username VARCHAR(100),role VARCHAR(30),status INT,is_deleted INT)");
            jdbc.execute("INSERT INTO sys_user VALUES(101,1,11,'test-tech','LAB_TECH',1,0),(102,1,11,'test-admin','SYSTEM_ADMIN',1,0)");
            jdbc.execute("CREATE TABLE operation_audit_log(id BIGINT AUTO_INCREMENT PRIMARY KEY,institution_id BIGINT,operator_id BIGINT,operator_name VARCHAR(100),module VARCHAR(100),action VARCHAR(100),request_method VARCHAR(20),request_path VARCHAR(500),handler VARCHAR(200),client_ip VARCHAR(100),reason VARCHAR(500),success BOOLEAN,error_message VARCHAR(1000),duration_ms BIGINT,occurred_at TIMESTAMP)");
            return source;
        }
        @Bean JdbcTemplate jdbc(DataSource source){return new JdbcTemplate(source);}
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source)throws Exception{
            var config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);config.addMapper(AuthenticationDirectoryMapper.class);config.addMapper(OperationAuditLogMapper.class);
            var factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(source);factory.setConfiguration(config);return factory.getObject();
        }
        @Bean SqlSessionTemplate template(SqlSessionFactory factory){return new SqlSessionTemplate(factory);}
        @Bean AuthenticationDirectoryService directory(SqlSessionTemplate template){return new AuthenticationDirectoryServiceImpl(template.getMapper(AuthenticationDirectoryMapper.class));}
        @Bean OperationAuditLogMapper auditMapper(SqlSessionTemplate template){return template.getMapper(OperationAuditLogMapper.class);}
        @Bean OperationAuditLogWriter audits(ObjectProvider<OperationAuditLogMapper> provider){return new OperationAuditLogWriter(provider);}
        @Bean Probe probe(){return new Probe();}
    }
    @RestController static class Probe {
        @GetMapping("/api/v1/institution-probe") public Map<String,Object> scope(){
            var scope=InstitutionContext.require();Map<String,Object> response=new LinkedHashMap<>();response.put("institutionId",scope.institutionId());response.put("departmentId",scope.departmentId());return response;
        }
        @GetMapping("/api/v1/institution-probe/failure") public String failure(){InstitutionContext.require();throw new IllegalStateException("test failure");}
    }
}
