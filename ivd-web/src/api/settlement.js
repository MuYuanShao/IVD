import request from '@/utils/request'

/** X2 结算管理 API */

export function getSettlementList(params) {
  return request({ url: '/settlement', method: 'get', params })
}

export function settleRequisition(requisitionId, operatorId = 1) {
  return request({ url: '/settlement/requisition', method: 'post', params: { requisitionId, operatorId } })
}

export function settleConsumption(consumptionRecordId, operatorId = 1) {
  return request({ url: '/settlement/consumption', method: 'post', params: { consumptionRecordId, operatorId } })
}

export function reverseSettlement(settlementId, operatorId = 1, reason) {
  return request({ url: '/settlement/reverse', method: 'post', params: { settlementId, operatorId, reason } })
}

export function getSettlementStats() {
  return request({ url: '/settlement/stats', method: 'get' })
}
