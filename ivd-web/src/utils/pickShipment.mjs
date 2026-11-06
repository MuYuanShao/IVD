export function remainingShipmentQuantity(row) {
  return Math.max(0, (row.qtyRequested || 0) - (row.qtyFulfilled || 0))
}

export function buildShipmentItems(rows) {
  const items = []
  for (const row of rows) {
    const qty = row._pickQty == null ? 0 : Number(row._pickQty)
    if (qty === 0) continue
    const label = row.reagentName || '未命名试剂'
    if (!Number.isFinite(qty) || qty < 0 || qty > remainingShipmentQuantity(row)) {
      throw new Error(`试剂「${label}」拣货数量无效或超过待发货数量`)
    }
    if (!row._batchId) throw new Error(`请为试剂「${label}」选择批次`)
    const reqItemId = row.id || row.reqItemId
    if (!reqItemId) throw new Error('缺少申领明细编号，请刷新后重试')
    items.push({ reqItemId, sourceBatchId: row._batchId, pickQty: qty })
  }
  if (!items.length) throw new Error('请至少填写一项本次拣货数量')
  return items
}
