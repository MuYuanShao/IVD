/**
 * 金额/日期/状态格式化工具
 */

/** 金额格式化：千分位 + ¥ 符号 */
export function formatMoney(value) {
  if (value == null) return '¥0.00'
  return '¥' + Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** 日期格式化：YYYY-MM-DD */
export function formatDate(date) {
  if (!date) return ''
  const d = new Date(date)
  return d.toISOString().slice(0, 10)
}

/** 日期时间格式化：YYYY-MM-DD HH:mm:ss */
export function formatDateTime(date) {
  if (!date) return ''
  const d = new Date(date)
  return d.toISOString().slice(0, 19).replace('T', ' ')
}

/** 审批状态标签 */
export function approvalStatusLabel(status) {
  const map = { DRAFT: '草稿', PENDING: '待审批', APPROVED: '已批准', REJECTED: '已驳回' }
  return map[status] || status
}

/** 物料类型标签 */
export function materialTypeLabel(type) {
  const map = { REAGENT: '试剂', CALIBRATOR: '校准品', QC_MATERIAL: '质控品', CONSUMABLE: '耗材', OTHER: '其他' }
  return map[type] || type
}
