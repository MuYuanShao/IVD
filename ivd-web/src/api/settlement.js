import request from '@/utils/request'

/** X2 结算管理 API — 操作人由后端 JWT 自动识别，前端无需传递 operatorId */

export function getSettlementList(params) {
  return request({ url: '/settlement', method: 'get', params })
}

export function settleRequisition(requisitionId) {
  return request({ url: '/settlement/requisition', method: 'post', params: { requisitionId } })
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
