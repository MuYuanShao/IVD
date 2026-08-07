-- =============================================================================
-- udi_unique_code: 唯一码表（每盒/每单位一个唯一码）
-- 格式: yyyyMMdd + 6位自增序号，如 20260723000001
-- 自验收入库时批量生成
-- =============================================================================

-- 唯一码日序列号表（原子自增，日级重置）
CREATE TABLE IF NOT EXISTS udi_unique_code_seq (
    date_key VARCHAR(8) NOT NULL PRIMARY KEY COMMENT '日期 yyyyMMdd',
    current_seq BIGINT NOT NULL DEFAULT 0 COMMENT '当前日最大已分配序号'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='唯一码日序列';

-- 唯一码主表
CREATE TABLE IF NOT EXISTS udi_unique_code (
    id BIGINT NOT NULL PRIMARY KEY COMMENT 'Snowflake PK',
    unique_code VARCHAR(14) NOT NULL COMMENT '唯一码 yyyyMMdd + 6位序号',
    batch_id BIGINT COMMENT '关联批次ID reagent_batch.id',
    reagent_id BIGINT COMMENT '试剂ID reagent_catalog.id',
    batch_no VARCHAR(64) COMMENT '批号（冗余，便于查询）',
    udi_di VARCHAR(128) COMMENT 'UDI-DI（冗余，便于查询）',
    status VARCHAR(32) NOT NULL DEFAULT 'IN_STOCK' COMMENT '状态: IN_STOCK, TRANSFERRED, CONSUMED, EXPIRED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_unique_code (unique_code),
    INDEX idx_batch_id (batch_id),
    INDEX idx_reagent_id (reagent_id),
    INDEX idx_batch_no (batch_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='UDI唯一码';
