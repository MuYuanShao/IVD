-- ============================================
-- 脚本: V2026070902__create_distributor.sql
-- 说明: 配送商主数据表（与 supplier 证照管理表独立）
-- 模块: M03 证照管理（扩展）
-- 依赖: 无
-- ============================================

CREATE TABLE distributor (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    name VARCHAR(200) NOT NULL COMMENT '配送商名称',
    short_name VARCHAR(64) COMMENT '简称',
    contact_person VARCHAR(64) COMMENT '联系人',
    contact_phone VARCHAR(20) COMMENT '联系电话',
    contact_email VARCHAR(128) COMMENT '联系邮箱',
    address VARCHAR(256) COMMENT '地址',
    is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否活跃',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE INDEX idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配送商主表';
