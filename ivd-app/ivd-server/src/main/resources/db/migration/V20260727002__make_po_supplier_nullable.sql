-- ============================================
-- 脚本: V20260727002__make_po_supplier_nullable.sql
-- 说明: PO 表头供应商改为可空，供应商职责下沉到明细行
-- 模块: M04 采购管理
-- ============================================

ALTER TABLE purchase_order MODIFY COLUMN supplier_id INT COMMENT '供应商ID（可空，明细行级别供应商优先）';
