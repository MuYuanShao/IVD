import request from '@/utils/request'

/** M09 日清月结 API */

export function getSnapshots(params) {
  return request({ url: '/finance/snapshots', method: 'get', params })
}

export function dailySettle(date) {
  return request({ url: '/finance/daily-settle', method: 'post', params: { date } })
}

export function monthlySettle(yearMonth) {
  return request({ url: '/finance/monthly-settle', method: 'post', params: { yearMonth } })
}

export function getFinanceStats(date) {
  return request({ url: '/finance/stats', method: 'get', params: { date } })
}

export function getBalanceSummary(startDate, endDate) {
  return request({ url: '/finance/balance', method: 'get', params: { startDate, endDate } })
}
