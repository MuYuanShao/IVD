import request from '@/utils/request'

/**
 * S3 自动审核 API
 */

/** 接口 A：LIS 结果推送 + 同步审核 */
export function auditObservation(data) {
  return request({ url: '/audit/observations', method: 'post', data })
}

/** 接口 B：人工审核结果回流 */
export function manualFeedback(data) {
  return request({ url: '/audit/manual-feedback', method: 'post', data })
}

/** 接口 C：批量审核状态查询 */
export function getAuditStatus(params) {
  return request({ url: '/audit/status', method: 'get', params })
}

/** 决策日志列表 */
export function getAuditDecisions(params) {
  return request({ url: '/audit/decisions', method: 'get', params })
}
