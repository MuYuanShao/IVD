import request from '@/utils/request'

/**
 * 配送商管理 API
 */

/** 分页查询配送商 */
export function getDistributorList(params) {
  return request({ url: '/distributor', method: 'get', params })
}

/** 配送商明细 */
export function getDistributorDetail(id) {
  return request({ url: `/distributor/${id}`, method: 'get' })
}

/** 新增配送商 */
export function createDistributor(data) {
  return request({ url: '/distributor', method: 'post', data })
}

/** 编辑配送商 */
export function updateDistributor(id, data) {
  return request({ url: `/distributor/${id}`, method: 'put', data })
}

/** 删除配送商 */
export function deleteDistributor(id) {
  return request({ url: `/distributor/${id}`, method: 'delete' })
}

/** 启用/停用 */
export function toggleDistributorActive(id) {
  return request({ url: `/distributor/${id}/toggle`, method: 'put' })
}

/** 下拉选项 */
export function getDistributorOptions(keyword) {
  return request({ url: '/distributor/options', method: 'get', params: { keyword } })
}
