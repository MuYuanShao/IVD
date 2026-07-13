import request from '@/utils/request'

/** M00 系统基础数据管理 API */

// ── 用户管理 ──
export function getUserList(params) {
  return request({ url: '/system/users', method: 'get', params })
}

export function getUserById(id) {
  return request({ url: `/system/users/${id}`, method: 'get' })
}

export function createUser(data) {
  return request({ url: '/system/users', method: 'post', data })
}

export function updateUser(id, data) {
  return request({ url: `/system/users/${id}`, method: 'put', data })
}

export function deleteUser(id) {
  return request({ url: `/system/users/${id}`, method: 'delete' })
}

export function toggleUserStatus(id) {
  return request({ url: `/system/users/${id}/status`, method: 'put' })
}

export function resetUserPassword(id) {
  return request({ url: `/system/users/${id}/reset-password`, method: 'put' })
}

export function getUserOptions(role) {
  return request({ url: '/system/users/options', method: 'get', params: { role } })
}

// ── 科室管理 ──
export function getDeptList(params) {
  return request({ url: '/system/depts', method: 'get', params })
}

export function getDeptById(id) {
  return request({ url: `/system/depts/${id}`, method: 'get' })
}

export function createDept(data) {
  return request({ url: '/system/depts', method: 'post', data })
}

export function updateDept(id, data) {
  return request({ url: `/system/depts/${id}`, method: 'put', data })
}

export function deleteDept(id) {
  return request({ url: `/system/depts/${id}`, method: 'delete' })
}

export function toggleDeptStatus(id) {
  return request({ url: `/system/depts/${id}/status`, method: 'put' })
}

export function getDeptTree() {
  return request({ url: '/system/depts/tree', method: 'get' })
}

export function getDeptOptions() {
  return request({ url: '/system/depts/options', method: 'get' })
}

// ── 库房管理 ──
export function getWarehouseList(params) {
  return request({ url: '/system/warehouses', method: 'get', params })
}

export function getWarehouseById(id) {
  return request({ url: `/system/warehouses/${id}`, method: 'get' })
}

export function createWarehouse(data) {
  return request({ url: '/system/warehouses', method: 'post', data })
}

export function updateWarehouse(id, data) {
  return request({ url: `/system/warehouses/${id}`, method: 'put', data })
}

export function deleteWarehouse(id) {
  return request({ url: `/system/warehouses/${id}`, method: 'delete' })
}

export function toggleWarehouseStatus(id) {
  return request({ url: `/system/warehouses/${id}/status`, method: 'put' })
}

export function getWarehouseTree() {
  return request({ url: '/system/warehouses/tree', method: 'get' })
}

export function getWarehouseOptions(level) {
  return request({ url: '/system/warehouses/options', method: 'get', params: { level } })
}
