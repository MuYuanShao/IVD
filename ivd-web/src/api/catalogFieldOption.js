import request from '@/utils/request'

const baseUrl = '/system/catalog-field-options'

export function getCatalogFieldOptionList(params) {
  return request({ url: baseUrl, method: 'get', params })
}

export function getCatalogFieldOptionGroups() {
  return request({ url: `${baseUrl}/options`, method: 'get' })
}

export function createCatalogFieldOption(data) {
  return request({ url: baseUrl, method: 'post', data })
}

export function updateCatalogFieldOption(id, data) {
  return request({ url: `${baseUrl}/${id}`, method: 'put', data })
}

export function toggleCatalogFieldOptionStatus(id) {
  return request({ url: `${baseUrl}/${id}/status`, method: 'put' })
}

export function deleteCatalogFieldOption(id) {
  return request({ url: `${baseUrl}/${id}`, method: 'delete' })
}
