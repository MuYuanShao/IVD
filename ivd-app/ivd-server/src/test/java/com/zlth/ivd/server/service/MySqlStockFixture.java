package com.zlth.ivd.server.service;

import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import javax.sql.DataSource;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** 仅创建并清理本用例自有随机库，不接受外部业务库名。 */
final class MySqlStockFixture implements AutoCloseable {
    private static final String SERVER="jdbc:mysql://127.0.0.1:3306/";
    private static final String OPTIONS="?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
    private final String database="ivd_stockqa_"+UUID.randomUUID().toString().replace("-","");
    private JdbcTemplate administrator;
    private boolean created;

    AnnotationConfigApplicationContext open(boolean withCommandMigration){
        String password=System.getenv("IVD_STOCK_MYSQL_PASSWORD");
        if(password==null || password.isEmpty())throw new IllegalStateException("需要显式配置IVD_STOCK_MYSQL_PASSWORD");
        String user=System.getenv().getOrDefault("IVD_STOCK_MYSQL_USER","root");
        administrator=new JdbcTemplate(new DriverManagerDataSource(SERVER+OPTIONS,user,password));
        assertEquals("InnoDB",administrator.queryForObject("SELECT @@default_storage_engine",String.class));
        checkTarget();
        administrator.execute("CREATE DATABASE `"+database+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        created=true;
        DataSource source=new DriverManagerDataSource(SERVER+database+OPTIONS,user,password);
        assertEquals(database,new JdbcTemplate(source).queryForObject("SELECT DATABASE()",String.class));
        new ResourceDatabasePopulator(new ClassPathResource("supply-flow-schema.sql")).execute(source);
        if(withCommandMigration){
            var fixtureDb = new JdbcTemplate(source);
            fixtureDb.execute("DROP TABLE purchase_order_number_sequence");
            fixtureDb.execute("DROP TABLE receipt_virtual_batch_sequence");
            fixtureDb.execute("ALTER TABLE udi_status_log DROP COLUMN batch_id");
            fixtureDb.execute("ALTER TABLE device_batch_load MODIFY COLUMN device_id BIGINT NOT NULL");
            fixtureDb.execute("CREATE TABLE license_change_log(change_type ENUM('CREATE','RENEW','UPDATE','FREEZE','UNFREEZE','DELETE_ATTACHMENT','REPLACE_ATTACHMENT') NOT NULL)");
            fixtureDb.execute("CREATE TABLE audit_decision_log(id BIGINT PRIMARY KEY)");
            fixtureDb.update("INSERT INTO purchase_order(id,institution_id,po_no,order_date) VALUES "
                    + "(99001,1,'PO-20261031-0007','2026-10-30'),"
                    + "(99002,1,'PO-20261031-0012','2026-10-31'),"
                    + "(99003,2,'PO-20261031-0003','2026-10-31')");
            var flyway=Flyway.configure().dataSource(source).locations("classpath:db/migration")
                    .baselineOnMigrate(true).baselineVersion("20261011007").validateOnMigrate(true).cleanDisabled(true).load();
            assertEquals(8,flyway.migrate().migrationsExecuted);assertEquals("20261105002",flyway.info().current().getVersion().getVersion());
            flyway.validate();
            assertEquals(12, fixtureDb.queryForObject("SELECT last_number FROM purchase_order_number_sequence "
                    + "WHERE institution_id=1 AND order_day='20261031'", Integer.class));
            assertEquals(3, fixtureDb.queryForObject("SELECT last_number FROM purchase_order_number_sequence "
                    + "WHERE institution_id=2 AND order_day='20261031'", Integer.class));
            assertEquals(3, fixtureDb.update("DELETE FROM purchase_order WHERE id IN (99001,99002,99003)"));
        }
        var context=new AnnotationConfigApplicationContext();
        context.register(ResourceBoundaryIntegrationTest.Config.class);
        if(withCommandMigration)context.register(com.zlth.ivd.server.config.StockCommandSchemaConfig.class);
        context.addBeanFactoryPostProcessor(factory->factory.registerSingleton("source",source));
        try{
            context.refresh();assertSame(source,context.getBean(DataSource.class),"禁止退回H2而虚报MySQL验证通过");return context;
        }catch(RuntimeException | Error failure){context.close();throw failure;}
    }
    @Override public void close(){
        if(created){checkTarget();administrator.execute("DROP DATABASE `"+database+"`");created=false;}
    }
    private void checkTarget(){
        if(!database.matches("ivd_stockqa_[a-f0-9]{32}"))throw new IllegalStateException("拒绝操作非本测试随机临时库");
    }
}
