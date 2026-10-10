package com.zlth.ivd.common.config;

import com.zlth.ivd.common.security.ScopedJdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import javax.sql.DataSource;

@Configuration
public class InstitutionJdbcConfig {
    @Bean @Primary
    public ScopedJdbcTemplate jdbcTemplate(DataSource dataSource,@Value("${ivd.isolation.enabled:false}") boolean enabled) {
        return new ScopedJdbcTemplate(dataSource,enabled);
    }
}
