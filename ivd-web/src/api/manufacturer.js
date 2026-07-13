import request from '@/utils/request'

/**
 * 厂家管理 API
 */

/** 分页查询厂家 */
export function getManufacturerList(params) {
  return request({ url: '/manufacturer', method: 'get', params })
}

/** 厂家明细 */
export function getManufacturerDetail(id) {
  return request({ url: `/manufacturer/${id}`, method: 'get' })
}

/** 新增厂家 */
export function createManufacturer(data) {
  return request({ url: '/manufacturer', method: 'post', data })
}

/** 编辑厂家 */
export function updateManufacturer(id, data) {
  return request({ url: `/manufacturer/${id}`, method: 'put', data })
}

/** 删除厂家 */
export function deleteManufacturer(id) {
  return request({ url: `/manufacturer/${id}`, method: 'delete' })
}

/** 启用/停用 */
export function toggleManufacturerActive(id) {
  return request({ url: `/manufacturer/${id}/toggle`, method: 'put' })
}

/** 下拉选项 */
export function getManufacturerOptions(keyword) {
  return request({ url: '/manufacturer/options', method: 'get', params: { keyword } })
}
