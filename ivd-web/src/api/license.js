import request from '@/utils/request'

/**
 * M03 证照管理 API
 */

/** 分页查询证照 */
export function getLicenseList(params) {
  return request({ url: '/license', method: 'get', params })
}

/** 证照明细 */
export function getLicenseDetail(id) {
  return request({ url: `/license/${id}`, method: 'get' })
}

/** 新增证照 */
export function createLicense(data) {
  return request({ url: '/license', method: 'post', data })
}

/** 编辑证照 */
export function updateLicense(id, data) {
  return request({ url: `/license/${id}`, method: 'put', data })
}

/** 统计卡片 */
export function getLicenseStats() {
  return request({ url: '/license/stats', method: 'get' })
}

/** 效期预警 */
export function getExpiryAlerts(days = 90) {
  return request({ url: '/license/expiry-alerts', method: 'get', params: { days } })
}

/** 供应商列表 */
export function getSuppliers() {
  return request({ url: '/license/suppliers', method: 'get' })
}

/** 上传附件（注：FormData 上传不要手动设置 Content-Type，axios 会自动带上正确 boundary） */
export function uploadAttachment(id, formData) {
  return request({ url: `/license/${id}/upload`, method: 'post', data: formData })
}

/** 删除证照附件 */
export function deleteAttachment(licenseId, objectKey) {
  return request({ url: `/license/${licenseId}/file/${encodeURIComponent(objectKey)}`, method: 'delete' })
}

/** 替换证照附件（覆盖上传） */
export function replaceAttachment(licenseId, objectKey, formData) {
  return request({ url: `/license/${licenseId}/file/${encodeURIComponent(objectKey)}`, method: 'put', data: formData })
}

/** 分页查询变更记录 */
export function getChangeLogs(params) {
  return request({ url: '/license/change-logs', method: 'get', params })
}
