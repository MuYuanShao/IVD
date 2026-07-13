import request from '@/utils/request'

/** M08 上机与消耗 API */

export function getConsumptionList(params) {
  return request({ url: '/consumption', method: 'get', params })
}

export function recordConsume(batchId, deviceId, qty, purpose, operatorId, remark) {
  return request({ url: '/consumption/consume', method: 'post', params: { batchId, deviceId, qty, purpose, operatorId, remark } })
}

export function recordInbound(batchId, qty, warehouseId, operatorId) {
  return request({ url: '/consumption/inbound', method: 'post', params: { batchId, qty, warehouseId, operatorId } })
}

export function batchConsume(batchNo, deviceId, qty, operatorId) {
  return request({ url: '/consumption/batch-consume', method: 'post', params: { batchNo, deviceId, qty, operatorId } })
}

export function getConsumptionStats(deviceId) {
  return request({ url: '/consumption/stats', method: 'get', params: { deviceId } })
}
