import request from '@/utils/request'

export const getConsumptionDevices = () => request({url:'/consumption/devices',method:'get'})
export const getActiveLoads = () => request({url:'/consumption/loads',method:'get'})
export const getConsumptionBatches = keyword => request({url:'/consumption/batches',method:'get',params:{keyword}})
export const loadInstrument = (batchId,deviceId) => request({url:'/consumption/load',method:'post',params:{batchId,deviceId}})
export const unloadInstrument = loadId => request({url:'/consumption/unload',method:'post',params:{loadId}})

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

export function batchConsume(batchNo, deviceId, qty, purpose, remark) {
  return request({ url: '/consumption/batch-consume', method: 'post', params: { batchNo, deviceId, qty, purpose, remark } })
}

export function getConsumptionStats(deviceId) {
  return request({ url: '/consumption/stats', method: 'get', params: { deviceId } })
}
