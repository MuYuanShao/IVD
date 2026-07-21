-- ============================================================
-- 科室申领试剂范围 — dept_reagent 关联表
-- 用于科室管理中引用/取消试剂目录，制定各科室可申领的试剂范围
-- ============================================================

CREATE TABLE IF NOT EXISTS `dept_reagent` (
    `id`          BIGINT   NOT NULL COMMENT '主键（Snowflake）',
    `dept_id`     BIGINT   NOT NULL COMMENT '科室ID (FK → sys_dept.id)',
    `reagent_id`  BIGINT   NOT NULL COMMENT '试剂ID (FK → reagent_catalog.id)',
    `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dept_reagent` (`dept_id`, `reagent_id`),
    KEY `idx_dept_id` (`dept_id`),
    KEY `idx_reagent_id` (`reagent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='科室申领试剂范围';
