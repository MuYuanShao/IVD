package com.zlth.ivd.server.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.common.security.*;
import com.zlth.ivd.inventory.mapper.RemainingBatchMapper;
import com.zlth.ivd.inventory.service.impl.RemainingBatchServiceImpl;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

/** Real mapper/pagination/tenant rewriting; trigger atomicity is separately tested on MySQL. */
class RemainingBatchIntegrationTest {
    RemainingBatchServiceImpl service;
    @BeforeEach void setup() throws Exception {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+java.util.UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        var jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE warehouse(id BIGINT PRIMARY KEY,institution_id BIGINT,name VARCHAR(50),level INT,dept_id BIGINT,parent_id BIGINT)");
        jdbc.execute("CREATE TABLE reagent_catalog(id BIGINT PRIMARY KEY,institution_id BIGINT,his_code VARCHAR(30),generic_name VARCHAR(50),manufacturer VARCHAR(50),specification VARCHAR(30))");
        jdbc.execute("CREATE TABLE reagent_batch(id BIGINT PRIMARY KEY,institution_id BIGINT,reagent_id BIGINT,warehouse_id BIGINT,batch_no VARCHAR(30),expiry_date DATE,unit VARCHAR(10),unit_price DECIMAL(12,2),location_code VARCHAR(20),udi_status VARCHAR(30))");
        jdbc.execute("CREATE TABLE inventory_remaining_batch(id BIGINT PRIMARY KEY,institution_id BIGINT,batch_id BIGINT,remaining_quantity DECIMAL(10,2),updated_at TIMESTAMP)");
        jdbc.update("INSERT INTO warehouse VALUES(1,1,'central',1,NULL,NULL),(2,1,'dept11',2,11,1),(3,1,'dept12',2,12,1),(4,1,'group11',3,NULL,2),(5,2,'foreign',1,NULL,NULL)");
        jdbc.update("INSERT INTO reagent_catalog VALUES(1,1,'CODE1','Test reagent','Factory','100T'),(2,2,'FOREIGN','Foreign reagent','Factory','100T')");
        jdbc.update("INSERT INTO reagent_batch VALUES(101,1,1,1,'CENTRAL','2027-01-01','box',10,'C1','IN_STOCK'),(102,1,1,2,'OWN','2027-02-01','box',10,'D1','IN_STOCK'),(103,1,1,3,'OTHER','2027-03-01','box',10,'D2','IN_STOCK'),(104,1,1,4,'GROUP','2027-04-01','box',10,'G1','IN_STOCK'),(105,2,2,5,'FOREIGN','2027-01-01','box',10,'F1','IN_STOCK')");
        jdbc.update("INSERT INTO inventory_remaining_batch VALUES(1,1,101,1.25,CURRENT_TIMESTAMP),(2,1,102,2,CURRENT_TIMESTAMP),(3,1,103,3,CURRENT_TIMESTAMP),(4,1,104,4,CURRENT_TIMESTAMP),(5,2,105,5,CURRENT_TIMESTAMP)");
        var configuration=new MybatisConfiguration();configuration.setMapUnderscoreToCamelCase(true);configuration.addMapper(RemainingBatchMapper.class);
        var interceptor=new MybatisPlusInterceptor();interceptor.addInnerInterceptor(new InstitutionSqlInterceptor());interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        var factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(ds);factory.setConfiguration(configuration);factory.setPlugins(interceptor);
        service=new RemainingBatchServiceImpl(new SqlSessionTemplate(factory.getObject()).getMapper(RemainingBatchMapper.class));
    }
    @Test void tenantPaginationAndDecimalIdentityArePreserved(){
        InstitutionContext.within(new InstitutionContext.Scope(1,null,1L,"SYSTEM_ADMIN"),()->{
            var page=service.list(1,2,null,null);assertEquals(4,page.getTotal());assertEquals(2,page.getRecords().size());
            assertEquals("101",page.getRecords().get(0).getId());assertEquals(new java.math.BigDecimal("1.25"),page.getRecords().get(0).getQuantity());
            assertEquals(2,service.list(2,2,null,null).getRecords().size());return null;
        });
        InstitutionContext.within(new InstitutionContext.Scope(2,null,2L,"SYSTEM_ADMIN"),()->{assertEquals(1,service.list(1,20,null,null).getTotal());return null;});
    }
    @Test void departmentAndGroupFilteringIncludesCentralButNotOtherDepartment(){
        for(String role:java.util.List.of("LAB_TECH","LAB_DOCTOR","DEPT_ADMIN"))
            InstitutionContext.within(new InstitutionContext.Scope(1,11L,1L,role),()->{
                assertEquals(3,service.list(1,20,null,null).getTotal());
                assertEquals(0,service.list(1,20,"OTHER",null).getTotal());
                assertEquals(1,service.list(1,20,null,3).getTotal());return null;
            });
    }
    @Test void missingScopeDepartmentAndInvalidPagingFailClosed(){
        assertThrows(BizException.class,()->service.list(1,20,null,null));
        InstitutionContext.within(new InstitutionContext.Scope(1,null,1L,"LAB_TECH"),()->{assertThrows(BizException.class,()->service.list(1,20,null,null));return null;});
        InstitutionContext.within(new InstitutionContext.Scope(1,null,1L,"SYSTEM_ADMIN"),()->{assertThrows(BizException.class,()->service.list(0,20,null,null));assertThrows(BizException.class,()->service.list(1,101,null,null));assertThrows(BizException.class,()->service.list(1,20,null,4));return null;});
    }
}
