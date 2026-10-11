package com.zlth.ivd.laboratory.config;

import com.zlth.ivd.laboratory.security.ClinicalCrypto;
import javax.sql.DataSource;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

@Configuration
@ConditionalOnProperty(name="ivd.clinical.enabled",havingValue="true")
public class ClinicalDeploymentConfig {
    @Bean public SmartInitializingSingleton clinicalDeploymentGuard(DataSource source,ClinicalCrypto crypto,
            @Value("${ivd.isolation.enabled:false}") boolean isolationEnabled) {
        return () -> {
            if(!isolationEnabled || !crypto.available()) throw new IllegalStateException("启用临床业务必须完成机构隔离并配置独立临床加密密钥");
            java.util.Set<String> tables=new java.util.HashSet<>();
            try(var connection=source.getConnection();var rows=connection.getMetaData().getTables(connection.getCatalog(),null,"%",new String[]{"TABLE"})) {
                while(rows.next()) tables.add(rows.getString("TABLE_NAME").toLowerCase(java.util.Locale.ROOT));
            }catch(java.sql.SQLException error){throw new IllegalStateException("临床表结构校验失败");}
            if(!tables.containsAll(java.util.Set.of("lab_patient","lab_patient_department","lab_sample","lab_sample_event","lab_result","lab_result_revision","lab_device_capability","lab_result_evidence","lab_report","lab_report_result","lab_report_event","lab_import_batch","lab_import_unit"))) throw new IllegalStateException("临床归属迁移尚未完成，拒绝启用临床业务");
            if(getClass().getResource("/fonts/NotoSansSC-Regular.ttf")==null)throw new IllegalStateException("中文报告字体未打包，拒绝启用报告签发");
            try(var connection=source.getConnection()){
                if(!connection.getMetaData().getDatabaseProductName().toLowerCase(java.util.Locale.ROOT).contains("mysql"))throw new IllegalStateException("部署临床模块需要MySQL不可变证据保护，拒绝替代数据库上线");
                var triggers=new java.util.HashSet<String>();
                try(var statement=connection.prepareStatement("SELECT TRIGGER_NAME FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=?")){
                    statement.setString(1,connection.getCatalog());try(var rows=statement.executeQuery()){while(rows.next())triggers.add(rows.getString(1));}
                }
                var required=java.util.Set.of("trg_lab_sample_event_no_update","trg_lab_sample_event_no_delete","trg_lab_result_revision_no_update","trg_lab_result_revision_no_delete","trg_lab_result_evidence_no_update","trg_lab_result_evidence_no_delete","trg_lab_report_event_no_update","trg_lab_report_event_no_delete","trg_lab_import_unit_no_update","trg_lab_import_unit_no_delete");
                if(!triggers.containsAll(required))throw new IllegalStateException("临床不可变事件、证据或导入回执保护尚未安装，拒绝启用");
            }catch(java.sql.SQLException error){throw new IllegalStateException("临床证据保护校验失败，拒绝启用");}
        };
    }
}
