import request from '@/utils/request'

/** M06 三级库管理 API */

/** 批次列表（分页+筛选） */
export function getBatchList(params) {
  return request({ url: '/inventory', method: 'get', params })
}

/** 仓库列表 */
export function getWarehouseList(params) {
  return request({ url: '/inventory/warehouses', method: 'get', params })
}

/** FEFO 推荐（按试剂ID查询最早到期批次） */
export function getFefoRecommendations(reagentId, warehouseId) {
  return request({ url: '/inventory/fefo', method: 'get', params: { reagentId, warehouseId } })
}

/** 效期预警列表 */
export function getExpiryAlerts(days = 90, warehouseId) {
  return request({ url: '/inventory/expiry-alerts', method: 'get', params: { days, warehouseId } })
}

/** 库存统计（total/expiring30/expiring90） */
export function getInventoryStats(warehouseId) {
  return request({ url: '/inventory/stats', method: 'get', params: { warehouseId } })
}

/** 调拨批次 */
export function transferBatch(batchId, targetWarehouseId, qty, urgency = 'NORMAL') {
  return request({ url: '/inventory/transfer', method: 'post', params: { batchId, targetWarehouseId, qty, urgency } })
}

/** UDI 追溯 */
export function traceUdi(udiDi, lotNo) {
  return request({ url: '/inventory/trace', method: 'get', params: { udiDi, lotNo } })
}

/** 患者关联追溯 */
export function getTracePatients(batchNo) {
  return request({ url: `/inventory/trace/patients/${batchNo}`, method: 'get' })
}

/** 科室库库存列表 */
export function getDeptInventoryList(params) {
  return request({ url: '/inventory/dept', method: 'get', params })
}

/** 科室库请领记录（数据在 procurement 模块，避免循环依赖） */
export function getDeptRequisitionList(params) {
  return request({ url: '/procurement/dept/requisitions', method: 'get', params })
}
