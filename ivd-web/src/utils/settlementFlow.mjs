export function validSourceId(value) {
  if (typeof value === 'number' && !Number.isSafeInteger(value)) return false
  const text = String(value ?? '').trim()
  return /^[1-9][0-9]{0,18}$/.test(text) && BigInt(text) <= 9223372036854775807n
}
export function billingLabel(row) {
  if (row.settlementStatus === 'ACTIVE') return '已生成消耗结算'
  if (row.settlementStatus === 'REVERSED') return '消耗结算已冲销'
  const mode = row.settlementMode
  if (['领用结算', 'REQUISITION', '调拨结算', 'TRANSFER'].includes(mode)) return '按科室签收模式结算'
  if (['验收结算', 'INBOUND'].includes(mode)) return '按验收入库模式结算'
  if (['消耗结算', 'CONSUMPTION'].includes(mode)) return '待核对消耗结算'
  return '历史来源／模式待核对'
}
