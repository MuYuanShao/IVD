import request from '@/utils/request'
export const getSettlementHistory = id => request({ url: `/settlement/${id}/history`, method: 'get' })

/** X2 结算管理 API — 操作人由后端 JWT 自动识别，前端无需传递 operatorId */

export function getSettlementList(params) {
  return request({ url: '/settlement', method: 'get', params })
}

export function settleRequisition(requisitionId) {
  return request({ url: '/settlement/requisition', method: 'post', params: { requisitionId } })
}

/** 科室签收 + 自动结算 */
export function receiveAndSettle(requisitionId) {
  return request({ url: '/settlement/receive', method: 'post', params: { requisitionId } })
}

/** 一键批量签收：签收所有待签收申领单 */
export function batchReceiveAndSettle() {
  return request({ url: '/settlement/receive/batch', method: 'post' })
}

export function settleTransfer(transferRecordId) {
  return request({ url: '/settlement/transfer', method: 'post', params: { transferRecordId } })
}

export function settleConsumption(consumptionRecordId) {
  return request({ url: '/settlement/consumption', method: 'post', params: { consumptionRecordId } })
}

export function reverseSettlement(settlementId, reason) {
  return request({ url: '/settlement/reverse', method: 'post', params: { settlementId, reason } })
}

export function getSettlementStats() {
  return request({ url: '/settlement/stats', method: 'get' })
}

/** 消耗结算明细：消耗数据全量字段 JOIN */
export function getConsumptionDetails(params) {
  return request({ url: '/settlement/consumption-details', method: 'get', params })
}
