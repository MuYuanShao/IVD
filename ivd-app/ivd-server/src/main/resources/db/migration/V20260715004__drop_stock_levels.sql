-- ============================================================
-- 移除最低/最高库存字段（当前无业务逻辑关联，二期重新设计）
-- ============================================================
ALTER TABLE `reagent_catalog` DROP COLUMN IF EXISTS `min_stock_level`;
ALTER TABLE `reagent_catalog` DROP COLUMN IF EXISTS `max_stock_level`;
