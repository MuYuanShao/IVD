package com.zlth.ivd.common.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.zlth.ivd.common.config.SafeLongSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Jackson 全局序列化配置
 *
 * @author IVD Team
 * @since 2026-07-01
 */
@Configuration
public class JacksonConfig {

    private static final String DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsonCustomizer() {
        return builder -> {
            // 忽略未知属性
            builder.featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            // 时间戳改为字符串
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            // Java 8 时间模块
            JavaTimeModule module = new JavaTimeModule();
            module.addSerializer(LocalDateTime.class,
                    new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(DATETIME_PATTERN)));
            builder.modules(module);
            builder.simpleDateFormat(DATETIME_PATTERN);
            // Long 安全序列化：超大值（Snowflake ID）→ String 防精度丢失；小数值 → Number
            builder.serializerByType(Long.class, new SafeLongSerializer());
            builder.serializerByType(Long.TYPE, new SafeLongSerializer());
        };
    }
}
