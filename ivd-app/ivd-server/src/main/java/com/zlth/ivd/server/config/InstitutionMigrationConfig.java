package com.zlth.ivd.server.config;

import java.sql.SQLException;
import javax.sql.DataSource;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Prevents a new JAR from silently migrating ownership while isolation remains disabled. */
@Configuration
public class InstitutionMigrationConfig {
    private static final MigrationVersion OWNERSHIP = MigrationVersion.fromVersion("20261010005");

    public FlywayMigrationStrategy checkedInstitutionMigration(@Value("${ivd.isolation.enabled:false}") boolean enabled) {
        return flyway -> {
            if (!enabled) {
                for (var migration : flyway.info().pending()) {
                    if (migration.getVersion() != null && migration.getVersion().compareTo(OWNERSHIP) >= 0)
                        throw new IllegalStateException("机构归属迁移需要先暂停写入、备份并显式启用 ivd.isolation.enabled；未执行迁移");
                }
            }
            flyway.migrate(); // Preserve normal checksum validation; never repair executed migrations here.
        };
    }

    @Bean
    public FlywayMigrationStrategy checkedClinicalInstitutionMigration(
            @Value("${ivd.isolation.enabled:false}") boolean enabled,
            @Value("${ivd.clinical.enabled:false}") boolean clinicalEnabled,
            com.zlth.ivd.laboratory.security.ClinicalCrypto crypto) {
        if(clinicalEnabled && (!enabled || !crypto.available()))
            throw new IllegalStateException("临床业务缺少机构隔离或独立密钥；未执行迁移");
        return checkedInstitutionMigration(enabled);
    }

    @Bean
    public SmartInitializingSingleton institutionSchemaGuard(DataSource source,
            @Value("${ivd.isolation.enabled:false}") boolean enabled) {
        return () -> {
            try (var connection = source.getConnection()) {
                boolean ownerSchema = false;
                try (var columns = connection.getMetaData().getColumns(connection.getCatalog(), null, "%", "%")) {
                    while (columns.next()) {
                        if ("sys_user".equalsIgnoreCase(columns.getString("TABLE_NAME")) && "institution_id".equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                            ownerSchema = true; break;
                        }
                    }
                }
                if (ownerSchema != enabled)
                    throw new IllegalStateException("机构隔离开关与数据库归属结构不一致，已拒绝启动；请按部署步骤完成迁移和开关配置");
                if (enabled) {
                    java.util.Set<String> tables = new java.util.HashSet<>();
                    try (var rows = connection.getMetaData().getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
                        while (rows.next()) tables.add(rows.getString("TABLE_NAME").toLowerCase(java.util.Locale.ROOT));
                    }
                    if (!tables.containsAll(java.util.Set.of("lab_test_item", "lab_interval")))
                        throw new IllegalStateException("检验项目与区间迁移尚未完成，已拒绝启用检验业务");
                    boolean deviceRequirement=false;
                    try(var columns=connection.getMetaData().getColumns(connection.getCatalog(),null,"%","%")){
                        while(columns.next())if("lab_test_item".equalsIgnoreCase(columns.getString("TABLE_NAME")) && "device_required".equalsIgnoreCase(columns.getString("COLUMN_NAME")))deviceRequirement=true;
                    }
                    if(!deviceRequirement)throw new IllegalStateException("项目设备要求迁移尚未完成，拒绝以旧结构启用新检验目录");
                }
            } catch (SQLException error) {
                throw new IllegalStateException("机构归属结构校验失败，已拒绝启动", error);
            }
        };
    }
}
