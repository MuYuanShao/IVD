package com.zlth.ivd.server.config;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.*;
import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.*;

/** Never run the new mutation path on a database missing its transactional evidence store. */
@Configuration
public class CatalogHistorySchemaConfig {
    @Bean
    public SmartInitializingSingleton catalogHistorySchemaGuard(DataSource source) {
        return () -> {
            try (var connection = source.getConnection()) {
                Set<String> tables = new HashSet<>();
                try (var rows = connection.getMetaData().getTables(connection.getCatalog(),null,"%",new String[]{"TABLE"})) {
                    while(rows.next()) tables.add(rows.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
                }
                if(!tables.containsAll(Set.of("catalog_draft","catalog_change_log")))
                    throw new IllegalStateException("目录草稿及变更记录迁移尚未完成，已拒绝启动；请先执行 V20261011005");
                if(connection.getMetaData().getDatabaseProductName().equalsIgnoreCase("MySQL")) {
                    Set<String> triggers = new HashSet<>();
                    try(var statement=connection.prepareStatement("SELECT TRIGGER_NAME FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=? AND EVENT_OBJECT_TABLE='catalog_change_log'")) {
                        statement.setString(1,connection.getCatalog());
                        try(var rows=statement.executeQuery()) { while(rows.next())triggers.add(rows.getString(1)); }
                    }
                    if(!triggers.containsAll(Set.of("catalog_change_log_no_update","catalog_change_log_no_delete")))
                        throw new IllegalStateException("目录变更历史不可变保护缺失，已拒绝启动");
                }
            } catch(SQLException error) { throw new IllegalStateException("目录变更结构校验失败，已拒绝启动",error); }
        };
    }
}
