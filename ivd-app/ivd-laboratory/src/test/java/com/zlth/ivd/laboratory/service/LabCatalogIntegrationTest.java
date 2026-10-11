package com.zlth.ivd.laboratory.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.common.security.*;
import com.zlth.ivd.inventory.service.DeviceDirectoryService;
import com.zlth.ivd.laboratory.dto.*;
import com.zlth.ivd.laboratory.mapper.*;
import com.zlth.ivd.laboratory.service.impl.LabCatalogServiceImpl;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import static org.junit.jupiter.api.Assertions.*;

/** Real MyBatis + transactions + isolated DB, calls only the public business service. */
class LabCatalogIntegrationTest {
    private AnnotationConfigApplicationContext context;
    private LabCatalogService service;
    private final InstitutionContext.Scope first=new InstitutionContext.Scope(1L,11L,101L,"SYSTEM_ADMIN");
    private final InstitutionContext.Scope second=new InstitutionContext.Scope(2L,22L,202L,"SYSTEM_ADMIN");
    @BeforeEach void setUp(){context=new AnnotationConfigApplicationContext(Config.class);service=context.getBean(LabCatalogService.class);}
    @AfterEach void tearDown(){context.close();}
    private TestItemRequest itemRequest(){return new TestItemRequest("K","血钾","酶法","NUMERIC","mmol/L",true,null);}
    private IntervalRequest intervalRequest(Long itemId){return new IntervalRequest(itemId,null,"REFERENCE","ANY",0,73000,new BigDecimal("3.5"),new BigDecimal("5.5"),null,LocalDate.of(2026,1,1),LocalDate.of(2026,12,31),true,null);}

    @Test void ownInstitutionCanCreateAndReadItemsAndIntervalsWithoutCrossIdAccess(){
        var item=InstitutionContext.within(first,()->service.createItem(itemRequest()));
        var interval=InstitutionContext.within(first,()->service.createInterval(intervalRequest(item.id())));
        InstitutionContext.within(first,()->{
            assertEquals("血钾",service.item(item.id()).name());
            assertEquals(interval.id(),service.intervals(1,20,item.id(),"REFERENCE").getRecords().get(0).id());
            assertEquals(1,service.items(1,20,null).getTotal());return null;
        });
        InstitutionContext.within(second,()->{
            assertEquals(0,service.items(1,20,null).getTotal());
            assertThrows(BizException.class,()->service.item(item.id()));
            assertThrows(BizException.class,()->service.interval(interval.id()));
            assertThrows(BizException.class,()->service.createInterval(intervalRequest(item.id())));
            // Same code is independently valid in a different institution.
            assertNotEquals(item.id(),service.createItem(itemRequest()).id());return null;
        });
    }

    @Test void concurrentOverlappingIntervalsOnlyCreateOneConfiguration() throws Exception {
        var item=InstitutionContext.within(first,()->service.createItem(itemRequest()));
        var start=new java.util.concurrent.CountDownLatch(1);
        var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.Callable<Boolean> operation=()->{
            start.await();
            try { return InstitutionContext.within(first,()->{service.createInterval(intervalRequest(item.id()));return true;}); }
            catch(BizException conflict){assertEquals(40900,conflict.getErrorCode());return false;}
        };
        try {
            var one=executor.submit(operation); var two=executor.submit(operation); start.countDown();
            assertNotEquals(one.get(10,java.util.concurrent.TimeUnit.SECONDS),two.get(10,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1,InstitutionContext.within(first,()->service.intervals(1,20,item.id(),null).getTotal()));
        } finally { executor.shutdownNow(); }
    }

    @Test void clinicalReaderCannotWriteAndStaleVersionsCannotOverwriteMasterData(){
        var item=InstitutionContext.within(first,()->service.createItem(itemRequest()));
        var clinical=new InstitutionContext.Scope(1L,11L,102L,"LAB_TECH");
        InstitutionContext.within(clinical,()->{
            assertEquals(item.id(),service.item(item.id()).id());
            assertThrows(BizException.class,()->service.createItem(itemRequest())); return null;
        });
        InstitutionContext.within(first,()->{
            var update=new TestItemRequest("K","血钾修订","酶法","NUMERIC","mmol/L",true,item.version());
            assertEquals("血钾修订",service.updateItem(item.id(),update).name());
            assertThrows(BizException.class,()->service.updateItem(item.id(),update)); return null;
        });
        assertThrows(BizException.class,()->service.items(1,20,null));
    }

    @Test void ownActiveDeviceSupportsMeasurementButForeignAndInactiveDevicesAreRejected(){
        var item=InstitutionContext.within(first,()->service.createItem(itemRequest()));
        InstitutionContext.within(first,()->{
            var saved=service.createInterval(measurement(item.id(),1L,true));
            assertEquals("MEASUREMENT",service.interval(saved.id()).kind());
            assertThrows(BizException.class,()->service.createInterval(measurement(item.id(),2L,true)));
            assertThrows(BizException.class,()->service.createInterval(measurement(item.id(),4L,true)));
            var historical=service.createInterval(measurement(item.id(),4L,false));
            assertFalse(service.interval(historical.id()).active());return null;
        });
        InstitutionContext.within(new InstitutionContext.Scope(1L,11L,103L,"LAB_TECH"),()->{
            assertEquals("NORMAL",service.evaluate(new IntervalEvaluationRequest(item.id(),1L,"酶法","mmol/L","MALE",100,LocalDate.of(2026,10,10),"4","MEASUREMENT")).status());return null;
        });
    }
    @Test void actualDeviceOptionsEnforceDepartmentWarehouseAndInstitutionOwnership(){
        InstitutionContext.within(new InstitutionContext.Scope(1L,11L,103L,"LAB_TECH"),()->{
            var ids=service.deviceOptions(null).stream().map(com.zlth.ivd.laboratory.vo.DeviceOptionVO::id).toList();
            assertEquals(java.util.Set.of(1L,6L),new java.util.HashSet<>(ids));return null;
        });
        var item=InstitutionContext.within(first,()->service.createItem(itemRequest()));
        InstitutionContext.within(new InstitutionContext.Scope(1L,11L,104L,"LAB_DIRECTOR"),()->{
            assertThrows(BizException.class,()->service.createInterval(measurement(item.id(),3L,true)));
            assertThrows(BizException.class,()->service.createInterval(measurement(item.id(),5L,true)));
            assertThrows(BizException.class,()->service.createInterval(measurement(item.id(),7L,true)));return null;
        });
        InstitutionContext.within(new InstitutionContext.Scope(2L,22L,203L,"LAB_TECH"),()->{
            assertEquals(java.util.List.of(2L),service.deviceOptions(null).stream().map(com.zlth.ivd.laboratory.vo.DeviceOptionVO::id).toList());return null;
        });
    }
    private IntervalRequest measurement(Long itemId,Long deviceId,boolean active){return new IntervalRequest(itemId,deviceId,"MEASUREMENT","ANY",0,73000,new BigDecimal("3.5"),new BigDecimal("5.5"),null,LocalDate.of(2026,1,1),LocalDate.of(2026,12,31),active,null);}

    @Configuration @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource(){
            var source=new JdbcDataSource();source.setURL("jdbc:h2:mem:lab"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
            var jdbc=new JdbcTemplate(source);
            jdbc.execute("CREATE TABLE lab_test_item(id BIGINT AUTO_INCREMENT PRIMARY KEY,institution_id BIGINT NOT NULL,code VARCHAR(64),name VARCHAR(200),methodology VARCHAR(100),result_type VARCHAR(10),unit VARCHAR(32),active BOOLEAN,version INT,created_at TIMESTAMP,updated_at TIMESTAMP,UNIQUE(institution_id,code))");
            jdbc.execute("ALTER TABLE lab_test_item ADD device_required BOOLEAN DEFAULT FALSE");
            jdbc.execute("CREATE TABLE lab_interval(id BIGINT AUTO_INCREMENT PRIMARY KEY,institution_id BIGINT NOT NULL,item_id BIGINT,device_id BIGINT,kind VARCHAR(20),methodology VARCHAR(100),unit VARCHAR(32),result_type VARCHAR(10),sex VARCHAR(10),min_age_days INT,max_age_days INT,lower_bound DECIMAL(18,6),upper_bound DECIMAL(18,6),normal_text VARCHAR(200),valid_from DATE,valid_to DATE,active BOOLEAN,version INT,created_at TIMESTAMP,updated_at TIMESTAMP)");
            jdbc.execute("CREATE TABLE warehouse(id BIGINT PRIMARY KEY,institution_id BIGINT,parent_id BIGINT,dept_id BIGINT,level INT)");
            jdbc.execute("INSERT INTO warehouse VALUES(101,1,NULL,NULL,1),(102,1,101,11,2),(103,1,101,12,2),(201,2,NULL,NULL,1),(202,2,201,22,2)");
            jdbc.execute("CREATE TABLE device(id BIGINT PRIMARY KEY,institution_id BIGINT,dept_id BIGINT,device_code VARCHAR(64),name VARCHAR(100),location_id BIGINT,is_active BOOLEAN)");
            jdbc.execute("INSERT INTO device VALUES(1,1,11,'OWN','本组设备',102,TRUE),(2,2,22,'FOREIGN','外机构设备',202,TRUE),(3,1,12,'OTHER-DEPT','其他科室',103,TRUE),(4,1,11,'INACTIVE','停用设备',102,FALSE),(5,1,11,'INCONSISTENT','归属不一致设备',103,TRUE),(6,1,NULL,'SHARED','中心共享设备',101,TRUE),(7,1,12,'ASSIGNED-CENTRAL','中央位置其他科室设备',101,TRUE)");
            return source;
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source)throws Exception{
            var config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);config.addMapper(LabTestItemMapper.class);config.addMapper(LabIntervalMapper.class);
            config.addMapper(com.zlth.ivd.inventory.mapper.DeviceMapper.class);
            var plugin=new MybatisPlusInterceptor();plugin.addInnerInterceptor(new InstitutionSqlInterceptor());plugin.addInnerInterceptor(new OptimisticLockerInnerInterceptor());plugin.addInnerInterceptor(new PaginationInnerInterceptor());
            var factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(source);factory.setConfiguration(config);factory.setPlugins(plugin);return factory.getObject();
        }
        @Bean SqlSessionTemplate template(SqlSessionFactory factory){return new SqlSessionTemplate(factory);}
        @Bean LabTestItemMapper items(SqlSessionTemplate template){return template.getMapper(LabTestItemMapper.class);}
        @Bean LabIntervalMapper intervals(SqlSessionTemplate template){return template.getMapper(LabIntervalMapper.class);}
        @Bean Validator validator(){return new LocalValidatorFactoryBean();}
        @Bean PlatformTransactionManager transactions(DataSource source){return new DataSourceTransactionManager(source);}
        @Bean DeviceDirectoryService devices(SqlSessionTemplate template){return new com.zlth.ivd.inventory.service.impl.DeviceDirectoryServiceImpl(template.getMapper(com.zlth.ivd.inventory.mapper.DeviceMapper.class));}
        @Bean LabCatalogService service(LabTestItemMapper items,LabIntervalMapper intervals,Validator validator,DeviceDirectoryService devices){
            return new LabCatalogServiceImpl(items,intervals,devices,new IntervalPolicy(),validator);
        }
    }
}
