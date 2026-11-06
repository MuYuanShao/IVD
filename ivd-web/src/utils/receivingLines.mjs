export function stageOrderReceipt(rows, lines) {
  const next = rows.map(row => ({ ...row }))
  for (const line of lines) {
    const matches = next.filter(row => row.itemId != null &&
      String(row.reagentId) === String(line.reagentId) && Number(row.remaining) > 0)
    if (matches.length !== 1) throw new Error('无法唯一匹配待收货订单行，请在原订单明细上填写')
    const row = matches[0]
    const qty = Number(line.receiveQty)
    if (!Number.isFinite(qty) || qty <= 0 || qty > Number(row.remaining)) {
      throw new Error('收货数量必须大于零且不超过订单剩余数量')
    }
    if (Number(row.receiveQty) > 0) throw new Error('该订单行已有待提交验收，请核对原明细')
    Object.assign(row, {
      receiveQty: qty, batchNo: line.batchNo, expiryDate: line.expiryDate,
      productionDate: line.productionDate, udi: line.udi,
      warehouseId: line.warehouseId ?? row.warehouseId
    })
  }
  return next
}

export function receiptUdi(line) {
  return typeof line.udi === 'string' ? line.udi.trim() || undefined : undefined
}

export function requiresReceiptTracking(line) {
  return !['CONSUMABLE', 'OTHER'].includes(line.materialType)
}

export function directReceiptWarehouse(lines) {
  const ids = new Set(lines.map(line => line.warehouseId == null || line.warehouseId === ''
    ? null : String(line.warehouseId)))
  if (ids.size > 1) throw new Error('同一次直收必须使用同一个中心库；请统一库房，或分开验收')
  return ids.values().next().value ?? undefined
}
