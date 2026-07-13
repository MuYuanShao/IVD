import request from '@/utils/request'

/**
 * M04 采购管理 API
 */

// ── 申领单 ──
export function getRequisitionList(params) { return request({ url: '/procurement/requisitions', method: 'get', params }) }
export function getRequisitionDetail(id) { return request({ url: `/procurement/requisitions/${id}`, method: 'get' }) }
export function createRequisition(data) { return request({ url: '/procurement/requisitions', method: 'post', data }) }
export function getRequisitionStats() { return request({ url: '/procurement/requisitions/stats', method: 'get' }) }

// ── 缺货单 ──
export function getShortageList(params) { return request({ url: '/procurement/shortages', method: 'get', params }) }
export function getShortageDetail(id) { return request({ url: `/procurement/shortages/${id}`, method: 'get' }) }
export function createShortage(reqId) { return request({ url: '/procurement/shortages', method: 'post', params: { reqId } }) }
export function getShortageStats() { return request({ url: '/procurement/shortages/stats', method: 'get' }) }

// ── 采购订单 ──
export function getOrderList(params) { return request({ url: '/procurement/orders', method: 'get', params }) }
export function getOrderDetail(id) { return request({ url: `/procurement/orders/${id}`, method: 'get' }) }
export function createOrderFromShortage(data) {
  return request({ url: '/procurement/orders/from-shortage', method: 'post', data })
}
export function createManualOrder(data) { return request({ url: '/procurement/orders/manual', method: 'post', data }) }
export function getOrderStats() { return request({ url: '/procurement/orders/stats', method: 'get' }) }

// ── 验收 ──
export function receiveOrder(data) {
  return request({ url: '/procurement/receive', method: 'post', data })
}

// ── 智能补货 ──
export function getSmartReplenish(days = 30) { return request({ url: '/procurement/smart-replenish', method: 'get', params: { days } }) }
