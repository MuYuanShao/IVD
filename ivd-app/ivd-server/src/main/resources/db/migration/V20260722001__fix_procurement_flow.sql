-- ============================================
-- 脚本: V20260722001__fix_procurement_flow.sql
-- 说明: 修复申领→调拨→结算闭环链路缺陷（幂等版）
-- 模块: M03/M06/M08/X2
-- ============================================

-- 1. consumption_record.record_type 新增 TRANSFER（幂等：MODIFY 重复执行安全）
ALTER TABLE consumption_record
  MODIFY COLUMN record_type ENUM('INBOUND','CONSUME','TRANSFER') NOT NULL COMMENT '记录类型';

-- 2. settlement_record.settlement_type 新增 TRANSFER（幂等）
ALTER TABLE settlement_record
  MODIFY COLUMN settlement_type ENUM('REQUISITION','CONSUMPTION','TRANSFER') NOT NULL COMMENT '结算类型';

-- 3. purchase_order 补充 settlement_mode 列（仅在列不存在时添加）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_order' AND COLUMN_NAME = 'settlement_mode');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE purchase_order ADD COLUMN settlement_mode VARCHAR(32) DEFAULT ''调拨结算'' COMMENT ''结算方式'' AFTER acceptance_method',
  'SELECT ''settlement_mode already exists, skipping'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4. purchase_order_item 新增乐观锁版本号
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_order_item' AND COLUMN_NAME = 'version');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE purchase_order_item ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT ''乐观锁版本号'' AFTER received_quantity',
  'SELECT ''version already exists, skipping'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5. consumption_record 补充调拨审计字段
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'consumption_record' AND COLUMN_NAME = 'requisition_id');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE consumption_record ADD COLUMN requisition_id BIGINT COMMENT ''关联申领单ID'' AFTER warehouse_id',
  'SELECT ''requisition_id already exists, skipping'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'consumption_record' AND COLUMN_NAME = 'from_warehouse_id');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE consumption_record ADD COLUMN from_warehouse_id BIGINT COMMENT ''调出仓库ID'' AFTER requisition_id',
  'SELECT ''from_warehouse_id already exists, skipping'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
