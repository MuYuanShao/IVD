/**
 * Procurement 状态映射工具函数
 * 从 ProcurementList.vue 提取，供多个页面共用
 */

/** 申领状态 → Element Plus Tag type */
export function getReqStatusType(s) {
  if (s === 'PENDING_REVIEW') return 'warning'
  if (s === 'PICKING') return 'info'
  if (s === 'PARTIAL_SHIPPED') return ''
  if (s === 'FULFILLED') return 'success'
  if (s === 'SHORTAGE_PENDING') return 'danger'
  return ''
}

/** 缺货单状态 → Element Plus Tag type */
export function getShStatusType(s) {
  if (s === 'PENDING_PROCUREMENT') return 'warning'
  if (s === 'PURCHASING') return 'info'
  if (s === 'COMPLETED') return 'success'
  return ''
}

/** 采购订单状态 → Element Plus Tag type */
export function getPoStatusType(s) {
  if (s === 'PENDING_RECEIVE') return 'warning'
  if (s === 'PARTIAL_RECEIVED') return 'info'
  if (s === 'COMPLETED') return 'success'
  if (s === 'CANCELLED') return 'danger'
  return ''
}

/** 计算采购订单收货百分比 */
export function getReceivePct(row) {
  const q = row.quantity || row.qty || 1
  const r = row.receivedQty || 0
  return Math.round((r / q) * 100)
}
