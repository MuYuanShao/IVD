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

    @Configuration @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource(){
            var source=new JdbcDataSource();source.setURL("jdbc:h2:mem:lab"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
            var jdbc=new JdbcTemplate(source);
            jdbc.execute("CREATE TABLE lab_test_item(id BIGINT AUTO_INCREMENT PRIMARY KEY,institution_id BIGINT NOT NULL,code VARCHAR(64),name VARCHAR(200),methodology VARCHAR(100),result_type VARCHAR(10),unit VARCHAR(32),active BOOLEAN,version INT,created_at TIMESTAMP,updated_at TIMESTAMP,UNIQUE(institution_id,code))");
            jdbc.execute("CREATE TABLE lab_interval(id BIGINT AUTO_INCREMENT PRIMARY KEY,institution_id BIGINT NOT NULL,item_id BIGINT,device_id BIGINT,kind VARCHAR(20),methodology VARCHAR(100),unit VARCHAR(32),result_type VARCHAR(10),sex VARCHAR(10),min_age_days INT,max_age_days INT,lower_bound DECIMAL(18,6),upper_bound DECIMAL(18,6),normal_text VARCHAR(200),valid_from DATE,valid_to DATE,active BOOLEAN,version INT,created_at TIMESTAMP,updated_at TIMESTAMP)");
            return source;
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source)throws Exception{
            var config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);config.addMapper(LabTestItemMapper.class);config.addMapper(LabIntervalMapper.class);
            var plugin=new MybatisPlusInterceptor();plugin.addInnerInterceptor(new InstitutionSqlInterceptor());plugin.addInnerInterceptor(new OptimisticLockerInnerInterceptor());plugin.addInnerInterceptor(new PaginationInnerInterceptor());
            var factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(source);factory.setConfiguration(config);factory.setPlugins(plugin);return factory.getObject();
        }
        @Bean SqlSessionTemplate template(SqlSessionFactory factory){return new SqlSessionTemplate(factory);}
        @Bean LabTestItemMapper items(SqlSessionTemplate template){return template.getMapper(LabTestItemMapper.class);}
        @Bean LabIntervalMapper intervals(SqlSessionTemplate template){return template.getMapper(LabIntervalMapper.class);}
        @Bean Validator validator(){return new LocalValidatorFactoryBean();}
        @Bean PlatformTransactionManager transactions(DataSource source){return new DataSourceTransactionManager(source);}
        @Bean LabCatalogService service(LabTestItemMapper items,LabIntervalMapper intervals,Validator validator){
            DeviceDirectoryService noDevices=new DeviceDirectoryService(){
                public DeviceReference requireActiveOwned(Long id){throw new BizException(40400,"测试库没有设备");}
                public DeviceReference requireOwned(Long id){throw new BizException(40400,"测试库没有设备");}
                public java.util.List<DeviceReference> activeOptions(String keyword){return java.util.List.of();}
            };
            return new LabCatalogServiceImpl(items,intervals,noDevices,new IntervalPolicy(),validator);
        }
    }
}
