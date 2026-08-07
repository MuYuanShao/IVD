-- ============================================================
-- M00 系统基础数据管理 — 初始化 DDL
-- 创建 sys_user、sys_dept 表，扩展 warehouse 表
-- ============================================================

-- 1. 系统用户表
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '用户ID',
    `username`      VARCHAR(64)  NOT NULL                 COMMENT '用户名／登录账号',
    `real_name`     VARCHAR(64)  NOT NULL                 COMMENT '真实姓名',
    `password`      VARCHAR(256) NOT NULL                 COMMENT '密码（BCrypt）',
    `role`          VARCHAR(32)  NOT NULL                 COMMENT '角色: SYSTEM_ADMIN|PURCHASER|STOREKEEPER|LAB_TECH|LAB_DOCTOR|LAB_DIRECTOR|FINANCE|IT_STAFF',
    `dept_id`       BIGINT       DEFAULT NULL             COMMENT '所属科室ID (FK → sys_dept.id)',
    `phone`         VARCHAR(20)  DEFAULT NULL             COMMENT '手机号',
    `email`         VARCHAR(128) DEFAULT NULL             COMMENT '邮箱',
    `status`        TINYINT      NOT NULL DEFAULT 1       COMMENT '状态: 1启用 | 0停用',
    `is_deleted`    TINYINT      NOT NULL DEFAULT 0       COMMENT '逻辑删除: 0未删除 | 1已删除',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_role` (`role`),
    KEY `idx_dept_id` (`dept_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

-- 2. 系统科室表
CREATE TABLE IF NOT EXISTS `sys_dept` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '科室ID',
    `dept_name`      VARCHAR(128) NOT NULL                 COMMENT '科室名称',
    `dept_code`      VARCHAR(64)  NOT NULL                 COMMENT '科室编码',
    `parent_id`      BIGINT       DEFAULT NULL             COMMENT '上级科室ID (FK → sys_dept.id)',
    `sort_order`     INT          NOT NULL DEFAULT 99      COMMENT '排序 (升序)',
    `contact_person` VARCHAR(64)  DEFAULT NULL             COMMENT '联系人',
    `contact_phone`  VARCHAR(20)  DEFAULT NULL             COMMENT '联系电话',
    `status`         TINYINT      NOT NULL DEFAULT 1       COMMENT '状态: 1启用 | 0停用',
    `remark`         VARCHAR(512) DEFAULT NULL             COMMENT '备注',
    `is_deleted`     TINYINT      NOT NULL DEFAULT 0       COMMENT '逻辑删除: 0未删除 | 1已删除',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dept_code` (`dept_code`),
    KEY `idx_parent_id` (`parent_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统科室表';

-- 3. 仓库表新增 M00 管理字段
ALTER TABLE `warehouse`
    ADD COLUMN `code`            VARCHAR(64)  DEFAULT NULL  COMMENT '库房编码' AFTER `name`,
    ADD COLUMN `dept_id`         BIGINT       DEFAULT NULL  COMMENT '关联科室ID (FK → sys_dept.id)' AFTER `parent_id`,
    ADD COLUMN `location`        VARCHAR(256) DEFAULT NULL  COMMENT '物理位置' AFTER `location_code`,
    ADD COLUMN `contact_person`  VARCHAR(64)  DEFAULT NULL  COMMENT '联系人' AFTER `location`,
    ADD COLUMN `contact_phone`   VARCHAR(20)  DEFAULT NULL  COMMENT '联系电话' AFTER `contact_person`,
    ADD COLUMN `sort_order`      INT          NOT NULL DEFAULT 99 COMMENT '排序 (升序)' AFTER `contact_phone`,
    ADD COLUMN `status`          TINYINT      NOT NULL DEFAULT 1  COMMENT '状态: 1启用 | 0停用' AFTER `sort_order`,
    ADD COLUMN `remark`          VARCHAR(512) DEFAULT NULL  COMMENT '备注' AFTER `status`;

-- 4. 种子数据: 默认系统管理员 (密码: admin123 → BCrypt)
INSERT IGNORE INTO `sys_user` (`username`, `real_name`, `password`, `role`, `status`)
VALUES ('admin', '系统管理员',
        '$2a$10$HsLsPerX7BO.5G/T6WN.9.NAFN9JApD38KOVsojtmjw5S0Z.auxE6',
        'SYSTEM_ADMIN', 1);
