import request from '@/utils/request'

/** M08 上机与消耗 API */

export function getConsumptionList(params) {
  return request({ url: '/consumption', method: 'get', params })
}

export function recordConsume(batchId, deviceId, qty, purpose, remark) {
  return request({ url: '/consumption/consume', method: 'post', params: { batchId, deviceId, qty, purpose, remark } })
}

export function recordInbound(batchId, qty, warehouseId) {
  return request({ url: '/consumption/inbound', method: 'post', params: { batchId, qty, warehouseId } })
}

export function batchConsume(batchNo, deviceId, qty) {
  return request({ url: '/consumption/batch-consume', method: 'post', params: { batchNo, deviceId, qty } })
}

export function getConsumptionStats(deviceId) {
  return request({ url: '/consumption/stats', method: 'get', params: { deviceId } })
}
