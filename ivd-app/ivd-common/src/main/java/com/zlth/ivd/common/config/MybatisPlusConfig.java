package com.zlth.ivd.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 全局配置
 * <p>
 * - 分页插件
 * - 枚举 TypeHandler 自动扫描（通过 application.yml 配置）
 *
 * @author IVD Team
 * @since 2026-07-01
 */
@Configuration
@MapperScan("com.zlth.ivd.**.mapper")
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 乐观锁插件（配合 @Version 注解）
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        // 分页插件（MySQL 方言）
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(500L); // 单次最大 500 条
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
