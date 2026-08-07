-- V20260727004: 修复 NULL-safe 的历史数据 + 智能补货 PO 的明细金额
-- V20260727003 的 WHERE 使用 != 无法匹配 NULL 值（如智能补货生成的 PO 和 replenish source 的 PO）
-- 同时修复 purchase_order_item.amount 为 NULL 的行（智能补货未写入 amount）

-- 1. 先修复明细表：amount 为 NULL → 用 order_quantity * unit_price 回填
UPDATE purchase_order_item poi
SET poi.amount = poi.order_quantity * poi.unit_price
WHERE poi.amount IS NULL
  AND poi.order_quantity IS NOT NULL
  AND poi.unit_price IS NOT NULL;

-- 2. 修复明细表：received_quantity 为 NULL → 0
UPDATE purchase_order_item poi
SET poi.received_quantity = 0
WHERE poi.received_quantity IS NULL;

-- 3. 从明细汇总更新 PO 总金额和已收货数量（NULL-safe）
UPDATE purchase_order po
JOIN (
    SELECT poi.po_id,
           COALESCE(SUM(poi.amount), 0) AS calc_amount,
           COALESCE(SUM(poi.received_quantity), 0) AS calc_received
    FROM purchase_order_item poi
    GROUP BY poi.po_id
) agg ON po.id = agg.po_id
SET po.total_amount = agg.calc_amount,
    po.received_quantity = agg.calc_received
WHERE po.total_amount IS NULL
   OR po.received_quantity IS NULL
   OR po.total_amount != agg.calc_amount
   OR po.received_quantity != agg.calc_received;
