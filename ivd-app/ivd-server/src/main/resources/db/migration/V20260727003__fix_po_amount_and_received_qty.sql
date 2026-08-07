-- V20260727003: 修复采购订单主表 total_amount 和 received_quantity 与明细不一致的历史数据
-- 背景：手动创建采购订单的 createManualOrder 方法此前未将 totalAmount 回写到 PO 头，
-- 且 received_quantity 曾被错误地赋值为订购总量（应为已收货总量=0 或明细 received_quantity 汇总）

-- 1. 从明细汇总更新 PO 总金额
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
WHERE po.total_amount != agg.calc_amount
   OR po.received_quantity != agg.calc_received;
