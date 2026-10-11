package com.zlth.ivd.server.config;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.*;
import javax.sql.DataSource;
import java.util.*;
@Configuration
public class SupplyFlowSchemaConfig {
    @Bean public SmartInitializingSingleton supplyFlowSchemaGuard(DataSource source){return ()->{
        try(var connection=source.getConnection()){
            var fields=new HashSet<String>();
            try(var columns=connection.getMetaData().getColumns(connection.getCatalog(),null,"%","%")){
                while(columns.next())fields.add(columns.getString("TABLE_NAME").toLowerCase(Locale.ROOT)+"."+columns.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
            if(!fields.containsAll(Set.of("reagent_batch.inventory_generation","reagent_batch.origin_purchase_order_item_id",
                    "reagent_batch.settlement_mode","consumption_record.settlement_mode","settlement_event.settlement_id")))
                throw new IllegalStateException("进销存来源与结算审计迁移 V20261011006 未完成，已拒绝启动");
            if(connection.getMetaData().getDatabaseProductName().equalsIgnoreCase("MySQL")){
                var triggers=new HashSet<String>();
                try(var statement=connection.prepareStatement("SELECT TRIGGER_NAME FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=? AND EVENT_OBJECT_TABLE='settlement_event'")){
                    statement.setString(1,connection.getCatalog());try(var rows=statement.executeQuery()){while(rows.next())triggers.add(rows.getString(1));}
                }
                if(!triggers.containsAll(Set.of("settlement_event_no_update","settlement_event_no_delete")))
                    throw new IllegalStateException("结算不可变业务事件保护缺失，拒绝启动");
            }
        }catch(java.sql.SQLException failure){throw new IllegalStateException("进销存闭环结构校验失败，拒绝启动",failure);}
    };}
}
