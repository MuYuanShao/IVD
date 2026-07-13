package com.zlth.ivd.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * IVD 智慧实验室管理系统 — 一期 MVP 启动入口
 *
 * <p>自动扫描所有业务模块：com.zlth.ivd.*
 * <p>Flyway 在启动时自动执行数据库迁移脚本
 *
 * @author IVD Team
 * @since 2026-07-01
 */
@SpringBootApplication(scanBasePackages = "com.zlth.ivd")
public class IvdApplication {

    public static void main(String[] args) {
        SpringApplication.run(IvdApplication.class, args);
    }
}
