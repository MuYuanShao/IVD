-- ============================================
-- 脚本: V20260727001__add_supplier_id_to_po_item.sql
-- 说明: 采购订单明细表增加供应商字段，支持一单多供应商
-- 模块: M04 采购管理
-- ============================================

ALTER TABLE purchase_order_item ADD COLUMN supplier_id INT COMMENT '供应商ID' AFTER reagent_id;
