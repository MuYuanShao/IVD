-- ============================================
-- 脚本: V20260722002__fix_reagent_id_bigint.sql
-- 说明: 修复采购模块 3 张表中 reagent_id 类型 INT → BIGINT
--       与 reagent_catalog.id (BIGINT) 保持一致
-- 模块: M04 采购管理
-- 依赖: V20260701003 (init_procurement)
-- ============================================

-- purchase_requisition_item: 申领明细表
ALTER TABLE purchase_requisition_item
    MODIFY COLUMN reagent_id BIGINT NOT NULL COMMENT '试剂ID (FK → reagent_catalog.id)';

-- purchase_order_item: 采购订单明细表
ALTER TABLE purchase_order_item
    MODIFY COLUMN reagent_id BIGINT NOT NULL COMMENT '试剂ID (FK → reagent_catalog.id)';

-- shortage_order_item: 缺货单明细表
ALTER TABLE shortage_order_item
    MODIFY COLUMN reagent_id BIGINT NOT NULL COMMENT '试剂ID (FK → reagent_catalog.id)';
