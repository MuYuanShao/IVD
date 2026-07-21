-- =============================================================================
-- 智能补货建议表 — 持久化计算后的补货建议，支持数量修改后批量生成采购单
-- =============================================================================
CREATE TABLE smart_replenish_suggestion (
    id              BIGINT          NOT NULL COMMENT 'PK, Snowflake',
    reagent_id      BIGINT          NOT NULL COMMENT '试剂ID → reagent_catalog.id',
    reagent_name    VARCHAR(200)    DEFAULT '' COMMENT '试剂名称（快照）',
    specification   VARCHAR(200)    DEFAULT '' COMMENT '规格型号（快照）',
    manufacturer    VARCHAR(120)    DEFAULT '' COMMENT '厂家（快照）',
    current_stock   DECIMAL(10,2)   NOT NULL DEFAULT 0.00 COMMENT '当前库存',
    daily_consumption DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '日均消耗',
    safety_stock    DECIMAL(10,2)   NOT NULL DEFAULT 0.00 COMMENT '安全库存',
    suggested_qty   DECIMAL(10,2)   NOT NULL DEFAULT 0.00 COMMENT '建议补货量',
    unit            VARCHAR(20)     DEFAULT '' COMMENT '单位',
    supplier        VARCHAR(200)    DEFAULT '' COMMENT '推荐供应商',
    analysis_days   INT             NOT NULL DEFAULT 30 COMMENT '分析周期（天）',
    batch_no        VARCHAR(64)     DEFAULT NULL COMMENT '计算批次号，同一批计算共享',
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING / GENERATED / CANCELLED',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    INDEX idx_batch_no (batch_no),
    INDEX idx_reagent_id (reagent_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='智能补货建议';
