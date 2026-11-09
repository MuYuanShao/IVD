package com.zlth.ivd.server.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

@EnabledIfEnvironmentVariable(named="IVD_STOCK_MYSQL_TEST",matches="true")
class MySqlPurchasePlanFlowIntegrationTest extends PurchasePlanFlowIntegrationTest {
    private final MySqlStockFixture mysql=new MySqlStockFixture();

    @Override protected AnnotationConfigApplicationContext fixtureContext(){return mysql.open(true);}

    @Override @AfterEach void close(){
        try{super.close();}
        finally{mysql.close();}
    }
}
