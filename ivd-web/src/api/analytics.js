import request from '@/utils/request'

/** S1 效益分析 + 驾驶舱 API */

export function getDashboardKpi() {
  return request({ url: '/analytics/dashboard/kpi', method: 'get' })
}

export function getCostPerTest(startDate, endDate) {
  return request({ url: '/analytics/cost-per-test', method: 'get', params: { startDate, endDate } })
}

export function getBrandComparison(startDate, endDate) {
  return request({ url: '/analytics/brand-comparison', method: 'get', params: { startDate, endDate } })
}

export function getWasteTop10(startDate, endDate) {
  return request({ url: '/analytics/waste-top10', method: 'get', params: { startDate, endDate } })
}

export function getAuditRateTrend(days = 30) {
  return request({ url: '/analytics/audit-rate-trend', method: 'get', params: { days } })
}
