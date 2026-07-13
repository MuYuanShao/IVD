/**
 * Inventory 工具函数
 * 从 InventoryView.vue 提取，供多个库存页面共用
 */

/** 计算剩余天数 */
export function getRemainingDays(date) {
  if (!date) return '—'
  return Math.ceil((new Date(date) - new Date()) / (1000 * 60 * 60 * 24))
}

/** 根据效期剩余天数返回 Element Plus Tag type */
export function getExpiryTagType(date) {
  const d = getRemainingDays(date)
  if (d === '—') return 'info'
  if (d <= 30) return 'danger'
  if (d <= 90) return 'warning'
  return 'success'
}

/** 根据效期预警行返回系统建议 */
export function getExpirySuggestion(row) {
  const days = getRemainingDays(row.expiryDate)
  if (days === '—') return '—'
  if (days <= 30) return '⚠ 立即处置：联系供应商退货或院内报损'
  if (days <= 90) return '📋 FEFO优先消耗：置顶出库推荐序列'
  return '✅ 库存安全'
}
