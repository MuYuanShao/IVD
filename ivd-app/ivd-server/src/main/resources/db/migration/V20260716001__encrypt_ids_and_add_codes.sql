-- ============================================
-- 脚本: V20260716001__encrypt_ids_and_add_codes.sql
-- 说明: 统一 ID 加密策略 — 所有主键从 AUTO_INCREMENT 迁移至 Snowflake (ASSIGN_ID)
--       同时为缺少业务编码的主数据表补齐 code 字段
-- 模块: 全模块
-- 依赖: V20260713003 (sys_dept/sys_user), V20260715001 (type fixes)
-- ============================================

-- ============================================
-- Part 1: 移除 AUTO_INCREMENT，ID 改为应用层 Snowflake 生成
-- MySQL 允许显式插入值到 AUTO_INCREMENT 列，但移除 AUTO_INCREMENT 可避免
-- 数据库和应用层双源生成 ID 的语义歧义
-- ============================================

-- M00 系统管理
ALTER TABLE sys_user    MODIFY COLUMN id BIGINT NOT NULL COMMENT '用户ID（Snowflake）';
ALTER TABLE sys_dept    MODIFY COLUMN id BIGINT NOT NULL COMMENT '科室ID（Snowflake）';

-- M01 试剂目录
ALTER TABLE reagent_catalog    MODIFY COLUMN id BIGINT NOT NULL COMMENT '主键ID（Snowflake）';
ALTER TABLE five_code_mapping  MODIFY COLUMN id BIGINT NOT NULL COMMENT '主键ID（Snowflake）';
ALTER TABLE udi_backlog        MODIFY COLUMN id BIGINT NOT NULL COMMENT '主键ID（Snowflake）';

-- M03 供应商/证照
ALTER TABLE supplier             MODIFY COLUMN id BIGINT NOT NULL COMMENT '主键ID（Snowflake）';
ALTER TABLE reagent_supplier_rel MODIFY COLUMN id BIGINT NOT NULL COMMENT '主键ID（Snowflake）';

-- M06 仓库/设备
ALTER TABLE warehouse    MODIFY COLUMN id BIGINT NOT NULL COMMENT '主键ID（Snowflake）';
ALTER TABLE device       MODIFY COLUMN id BIGINT NOT NULL COMMENT '主键ID（Snowflake）';

-- 预留模块
ALTER TABLE poct_device  MODIFY COLUMN id BIGINT NOT NULL COMMENT '主键ID（Snowflake）';

-- S2 审核规则
ALTER TABLE audit_rule   MODIFY COLUMN id BIGINT NOT NULL COMMENT '主键ID（Snowflake）';

-- 生产厂家 / 配送商
ALTER TABLE manufacturer MODIFY COLUMN id BIGINT NOT NULL COMMENT '主键ID（Snowflake）';
ALTER TABLE distributor  MODIFY COLUMN id BIGINT NOT NULL COMMENT '主键ID（Snowflake）';

-- ============================================
-- Part 2: 补齐缺失的业务编码字段（与 ID 分离）
-- code 用于对外展示/检索，id 仅用于内部关联
-- ============================================

-- 系统用户：新增 user_code（格式 USER-YYYYMMDD-NNN）
ALTER TABLE sys_user
    ADD COLUMN user_code VARCHAR(64) DEFAULT NULL COMMENT '用户编码（业务唯一标识）' AFTER id,
    ADD UNIQUE INDEX uk_user_code (user_code);

-- 供应商：新增 supplier_code（格式 SUP-YYYYMMDD-NNN）
ALTER TABLE supplier
    ADD COLUMN supplier_code VARCHAR(64) DEFAULT NULL COMMENT '供应商编码（业务唯一标识）' AFTER id,
    ADD UNIQUE INDEX uk_supplier_code (supplier_code);

-- 生产厂家：新增 manufacturer_code（格式 MFG-YYYYMMDD-NNN）
ALTER TABLE manufacturer
    ADD COLUMN manufacturer_code VARCHAR(64) DEFAULT NULL COMMENT '厂家编码（业务唯一标识）' AFTER id,
    ADD UNIQUE INDEX uk_manufacturer_code (manufacturer_code);

-- 配送商：新增 distributor_code（格式 DST-YYYYMMDD-NNN）
ALTER TABLE distributor
    ADD COLUMN distributor_code VARCHAR(64) DEFAULT NULL COMMENT '配送商编码（业务唯一标识）' AFTER id,
    ADD UNIQUE INDEX uk_distributor_code (distributor_code);

-- ============================================
-- Part 3: 为已有数据回填业务编码（种子数据兼容）
-- 格式: {PREFIX}-20260716-{自增序号}
-- ============================================

UPDATE sys_user SET user_code = CONCAT('USER-20260716-', LPAD(id, 3, '0')) WHERE user_code IS NULL;
UPDATE supplier SET supplier_code = CONCAT('SUP-20260716-', LPAD(id, 3, '0')) WHERE supplier_code IS NULL;
UPDATE manufacturer SET manufacturer_code = CONCAT('MFG-20260716-', LPAD(id, 3, '0')) WHERE manufacturer_code IS NULL;
UPDATE distributor SET distributor_code = CONCAT('DST-20260716-', LPAD(id, 3, '0')) WHERE distributor_code IS NULL;
