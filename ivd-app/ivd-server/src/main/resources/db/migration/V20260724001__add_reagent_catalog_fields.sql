-- =============================================================================
-- 试剂目录增加采购与医保相关字段（幂等）
-- 是否带量/带量类型/是否医保支付/医保支付类型/是否收费/收费编码
-- =============================================================================

-- 是否带量采购
SET @sql = (SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE reagent_catalog ADD COLUMN is_volume_based TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否带量采购: 0=否 1=是'' AFTER enable_unique_code',
    'SELECT 1'
) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = 'ivd' AND TABLE_NAME = 'reagent_catalog' AND COLUMN_NAME = 'is_volume_based');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 带量类型
SET @sql = (SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE reagent_catalog ADD COLUMN volume_type VARCHAR(32) COMMENT ''带量类型: 国采/省采/地市/GPO/其他'' AFTER is_volume_based',
    'SELECT 1'
) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = 'ivd' AND TABLE_NAME = 'reagent_catalog' AND COLUMN_NAME = 'volume_type');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 是否医保支付
SET @sql = (SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE reagent_catalog ADD COLUMN is_insurance_payment TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否医保支付: 0=否 1=是'' AFTER volume_type',
    'SELECT 1'
) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = 'ivd' AND TABLE_NAME = 'reagent_catalog' AND COLUMN_NAME = 'is_insurance_payment');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 医保支付类型
SET @sql = (SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE reagent_catalog ADD COLUMN insurance_payment_type VARCHAR(32) COMMENT ''医保支付类型: 甲类/乙类/丙类/自费'' AFTER is_insurance_payment',
    'SELECT 1'
) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = 'ivd' AND TABLE_NAME = 'reagent_catalog' AND COLUMN_NAME = 'insurance_payment_type');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 是否收费
SET @sql = (SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE reagent_catalog ADD COLUMN is_chargeable TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否收费: 0=否 1=是'' AFTER insurance_payment_type',
    'SELECT 1'
) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = 'ivd' AND TABLE_NAME = 'reagent_catalog' AND COLUMN_NAME = 'is_chargeable');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 收费编码
SET @sql = (SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE reagent_catalog ADD COLUMN charge_code VARCHAR(50) COMMENT ''收费编码'' AFTER is_chargeable',
    'SELECT 1'
) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = 'ivd' AND TABLE_NAME = 'reagent_catalog' AND COLUMN_NAME = 'charge_code');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
