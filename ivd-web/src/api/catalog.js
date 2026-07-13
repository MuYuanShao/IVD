import request from '@/utils/request'

/**
 * M01 试剂目录 API
 */

/** 分页查询试剂目录（多条件组合） */
export function getCatalogList(params) {
  return request({ url: '/catalog', method: 'get', params })
}

/** 查询单个试剂详情（含五码映射） */
export function getCatalogDetail(id) {
  return request({ url: `/catalog/${id}`, method: 'get' })
}

/** 新增物料 */
export function createReagent(data) {
  return request({ url: '/catalog', method: 'post', data })
}

/** 编辑物料 */
export function updateReagent(id, data) {
  return request({ url: `/catalog/${id}`, method: 'put', data })
}

/** 软删除物料 */
export function deleteReagent(id) {
  return request({ url: `/catalog/${id}`, method: 'delete' })
}

/** 提交审批 */
export function submitApproval(id, data) {
  return request({ url: `/catalog/${id}/approval`, method: 'post', data })
}

/** 审批通过 */
export function approveReagent(logId, data) {
  return request({ url: `/catalog/approval/${logId}/approve`, method: 'post', data })
}

/** 审批驳回 */
export function rejectReagent(logId, data) {
  return request({ url: `/catalog/approval/${logId}/reject`, method: 'post', data })
}

/** 分页查询待审批列表 */
export function getPendingApprovals(params) {
  return request({ url: '/catalog/approval/pending', method: 'get', params })
}

/** 分页查询审批历史 */
export function getApprovalHistory(params) {
  return request({ url: '/catalog/approval/history', method: 'get', params })
}

/** 分页查询 UDI 待补全列表 */
export function getUdiBacklog(params) {
  return request({ url: '/catalog/udi-backlog', method: 'get', params })
}
