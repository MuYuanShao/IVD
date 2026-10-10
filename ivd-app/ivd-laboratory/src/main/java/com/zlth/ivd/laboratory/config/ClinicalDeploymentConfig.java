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
            if(!tables.containsAll(java.util.Set.of("lab_patient","lab_patient_department"))) throw new IllegalStateException("患者归属迁移尚未完成，拒绝启用临床业务");
        };
    }
}
