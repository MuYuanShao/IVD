package com.zlth.ivd.server.config;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.*;
import javax.sql.DataSource;
import java.sql.DatabaseMetaData;
import java.util.*;

@Configuration(proxyBeanMethods=false)
public class StockCommandSchemaConfig {
    private static final String TABLE="stock_command_receipt";
    private static final List<String> KEY=List.of("institution_id","operation_code","request_key");

    @Bean SmartInitializingSingleton stockCommandSchemaGuard(DataSource source){return ()->{
        try(var connection=source.getConnection()){
            var meta=connection.getMetaData();String catalog=connection.getCatalog(),schema=null,table=null;
            var fields=new HashSet<String>();var required=new HashSet<String>();
            try(var columns=meta.getColumns(catalog,null,"%","%")){
                while(columns.next())if(TABLE.equalsIgnoreCase(columns.getString("TABLE_NAME"))){
                    table=columns.getString("TABLE_NAME");schema=columns.getString("TABLE_SCHEM");
                    String name=columns.getString("COLUMN_NAME").toLowerCase(Locale.ROOT);fields.add(name);
                    if(columns.getInt("NULLABLE")==DatabaseMetaData.columnNoNulls)required.add(name);
                }
            }
            if(!fields.containsAll(Set.of("id","institution_id","operation_code","request_key","actor_id","request_hash","response_json","created_at","updated_at","completed_at"))
                    || !required.containsAll(List.of("institution_id","operation_code","request_key","actor_id","request_hash")))throw missing();
            var keys=new HashMap<String,TreeMap<Integer,String>>();
            try(var indexes=meta.getIndexInfo(catalog,schema,table,true,false)){
                while(indexes.next()){
                    String name=indexes.getString("INDEX_NAME"),column=indexes.getString("COLUMN_NAME");
                    if(name!=null && column!=null && !indexes.getBoolean("NON_UNIQUE"))
                        keys.computeIfAbsent(name,ignored->new TreeMap<>()).put(indexes.getInt("ORDINAL_POSITION"),column.toLowerCase(Locale.ROOT));
                }
            }
            if(keys.values().stream().noneMatch(index->new ArrayList<>(index.values()).equals(KEY)))throw missing();
            if("MySQL".equalsIgnoreCase(meta.getDatabaseProductName())){
                if(catalog==null)throw missing();
                var names=new HashSet<String>();
                try(var query=connection.prepareStatement("SELECT TRIGGER_NAME FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=? AND EVENT_OBJECT_TABLE=? AND ACTION_TIMING='BEFORE'")){
                    query.setString(1,catalog);query.setString(2,table);try(var rows=query.executeQuery()){while(rows.next())names.add(rows.getString(1));}
                }
                if(!names.containsAll(Set.of("stock_command_receipt_no_rewrite","stock_command_receipt_no_delete")))throw missing();
                try(var query=connection.prepareStatement("SELECT COLLATION_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=? AND TABLE_NAME=? AND COLUMN_NAME='request_key'")){
                    query.setString(1,catalog);query.setString(2,table);
                    try(var rows=query.executeQuery()){if(!rows.next() || !"ascii_bin".equals(rows.getString(1)))throw missing();}
                }
            }
        }catch(java.sql.SQLException error){throw new IllegalStateException("库存请求回执结构校验失败，拒绝启动",error);}
    };}

    private IllegalStateException missing(){return new IllegalStateException("库存请求回执或保护约束缺失，请执行V20261104001后启动");}
}
