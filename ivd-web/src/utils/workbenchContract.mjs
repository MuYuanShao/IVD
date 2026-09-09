// Shared browser/Node contract: unknown data must never become zero.
const requireValue = (ok, field) => {
  if (!ok) throw new Error(`工作台响应字段异常：${field}`)
}
const numeric = (value) => typeof value === 'number' && Number.isFinite(value)
const count = (value) => Number.isSafeInteger(value) && value >= 0
export function validateOverview(data) {
  requireValue(
    data && /^\d{4}-\d{2}-\d{2}$/.test(data.businessDate),
    'businessDate',
  )
  requireValue(
    typeof data.scope === 'string' && typeof data.generatedAt === 'string',
    'scope/generatedAt',
  )
  requireValue(
    data.metrics &&
      data.alerts &&
      Array.isArray(data.todos) &&
      Array.isArray(data.trend),
    'sections',
  )
  for (const key of [
    'consumedRecords',
    'inboundRecords',
    'transferRecords',
    'unpricedRecords',
  ])
    requireValue(count(data.metrics[key]), `metrics.${key}`)
  requireValue(
    data.metrics.consumptionAmount === null ||
      numeric(data.metrics.consumptionAmount),
    'consumptionAmount',
  )
  for (const key of [
    'expiredBatches',
    'expiringBatches',
    'pendingUdiBatches',
    'shortageOrders',
  ])
    requireValue(count(data.alerts[key]), `alerts.${key}`)
  requireValue(count(data.todoTotal), 'todoTotal')
  requireValue(
    data.todos.length <= 6 && data.todos.length <= data.todoTotal,
    'todoTotal/items',
  )
  for (const row of data.todos)
    requireValue(
      typeof row.id === 'string' &&
        typeof row.reqNo === 'string' &&
        typeof row.status === 'string' &&
        count(row.lineCount) &&
        count(row.fulfilledLines) &&
        (row.amount === null || numeric(row.amount)),
      'todo',
    )
  requireValue(data.trend.length === 30, 'trend.length')
  const today = Date.parse(`${data.businessDate}T00:00:00Z`)
  requireValue(Number.isFinite(today), 'businessDate')
  data.trend.forEach((row, index) =>
    requireValue(
      row.date ===
        new Date(today - (29 - index) * 86400000).toISOString().slice(0, 10),
      'trend.dateRange',
    ),
  )
  for (const row of data.trend)
    requireValue(
      /^\d{4}-\d{2}-\d{2}$/.test(row.date) &&
        [row.inbound, row.outbound].every(
          (value) => value === null || numeric(value),
        ) &&
        count(row.unpricedRecords),
      'trend.point',
    )
  return data
}
export function validateProducts(data) {
  requireValue(
    data &&
      count(data.total) &&
      count(data.page) &&
      data.page > 0 &&
      count(data.pageSize) &&
      data.pageSize > 0 &&
      Array.isArray(data.records),
    'pagination',
  )
  requireValue(
    data.records.length <= data.pageSize && data.records.length <= data.total,
    'pagination.records',
  )
  for (const row of data.records)
    requireValue(
      typeof row.id === 'string' &&
        typeof row.reagentId === 'string' &&
        typeof row.name === 'string' &&
        numeric(row.quantity),
      'batch',
    )
  return data
}
export function money(value) {
  return value == null
    ? '金额未完整记录'
    : `¥ ${value.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}
export function sumAmount(rows, key) {
  return rows.some((row) => row[key] == null)
    ? null
    : rows.reduce((total, row) => total + row[key], 0)
}
