import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  getUserList, getUserById, createUser, updateUser, deleteUser, toggleUserStatus, resetUserPassword, getUserOptions,
  getDeptList, getDeptById, createDept, updateDept, deleteDept, toggleDeptStatus, getDeptTree, getDeptOptions,
  getDeptReagents, addDeptReagents, removeDeptReagents,
  getWarehouseList, getWarehouseById, createWarehouse, updateWarehouse, deleteWarehouse, toggleWarehouseStatus, getWarehouseTree, getWarehouseOptions,
} from '@/api/system'

export const useSystemStore = defineStore('system', () => {
  // ── 用户 ──
  const users = ref([])
  const userTotal = ref(0)
  const userLoading = ref(false)
  const userOptions = ref([])

  async function fetchUsers(params = {}) {
    userLoading.value = true
    try {
      const d = await getUserList({ page: 1, pageSize: 15, ...params })
      users.value = d.records || []
      userTotal.value = d.total || 0
      return d
    } finally { userLoading.value = false }
  }

  async function fetchUserOptions(role) {
    userOptions.value = (await getUserOptions(role)) || []
    return userOptions.value
  }

  async function addUser(data) {
    const r = await createUser(data)
    return r
  }

  async function editUser(id, data) {
    const r = await updateUser(id, data)
    return r
  }

  async function removeUser(id) {
    await deleteUser(id)
  }

  async function toggleUser(id) {
    await toggleUserStatus(id)
  }

  async function resetPwd(id) {
    await resetUserPassword(id)
  }

  // ── 科室 ──
  const depts = ref([])
  const deptTotal = ref(0)
  const deptLoading = ref(false)
  const deptTree = ref([])
  const deptOptions = ref([])

  async function fetchDepts(params = {}) {
    deptLoading.value = true
    try {
      const d = await getDeptList({ page: 1, pageSize: 15, ...params })
      depts.value = d.records || []
      deptTotal.value = d.total || 0
      return d
    } finally { deptLoading.value = false }
  }

  async function fetchDeptTree() {
    deptTree.value = (await getDeptTree()) || []
    return deptTree.value
  }

  async function fetchDeptOptions() {
    deptOptions.value = (await getDeptOptions()) || []
    return deptOptions.value
  }

  async function addDept(data) {
    const r = await createDept(data)
    return r
  }

  async function editDept(id, data) {
    const r = await updateDept(id, data)
    return r
  }

  async function removeDept(id) {
    await deleteDept(id)
  }

  async function toggleDept(id) {
    await toggleDeptStatus(id)
  }

  // ── 科室试剂目录 ──
  const deptReagents = ref([])
  const deptReagentLoading = ref(false)
  const selectedDeptId = ref(null)

  async function fetchDeptReagents(deptId) {
    deptReagentLoading.value = true
    selectedDeptId.value = deptId
    try {
      deptReagents.value = (await getDeptReagents(deptId)) || []
      return deptReagents.value
    } finally { deptReagentLoading.value = false }
  }

  async function batchAddDeptReagents(deptId, reagentIds) {
    await addDeptReagents(deptId, reagentIds)
  }

  async function batchRemoveDeptReagents(deptId, reagentIds) {
    await removeDeptReagents(deptId, reagentIds)
  }

  function clearDeptReagents() {
    deptReagents.value = []
    selectedDeptId.value = null
  }

  // ── 库房 ──
  const warehouses = ref([])
  const whTotal = ref(0)
  const whLoading = ref(false)
  const whTree = ref([])
  const whOptions = ref([])

  async function fetchWarehouses(params = {}) {
    whLoading.value = true
    try {
      const d = await getWarehouseList({ page: 1, pageSize: 15, ...params })
      warehouses.value = d.records || []
      whTotal.value = d.total || 0
      return d
    } finally { whLoading.value = false }
  }

  async function fetchWhTree() {
    whTree.value = (await getWarehouseTree()) || []
    return whTree.value
  }

  async function fetchWhOptions(level) {
    whOptions.value = (await getWarehouseOptions(level)) || []
    return whOptions.value
  }

  async function addWarehouse(data) {
    const r = await createWarehouse(data)
    return r
  }

  async function editWarehouse(id, data) {
    const r = await updateWarehouse(id, data)
    return r
  }

  async function removeWarehouse(id) {
    await deleteWarehouse(id)
  }

  async function toggleWh(id) {
    await toggleWarehouseStatus(id)
  }

  return {
    users, userTotal, userLoading, userOptions,
    depts, deptTotal, deptLoading, deptTree, deptOptions,
    warehouses, whTotal, whLoading, whTree, whOptions,
    fetchUsers, fetchUserOptions, addUser, editUser, removeUser, toggleUser, resetPwd,
    fetchDepts, fetchDeptTree, fetchDeptOptions, addDept, editDept, removeDept, toggleDept,
    deptReagents, deptReagentLoading, selectedDeptId,
    fetchDeptReagents, batchAddDeptReagents, batchRemoveDeptReagents, clearDeptReagents,
    fetchWarehouses, fetchWhTree, fetchWhOptions, addWarehouse, editWarehouse, removeWarehouse, toggleWh,
  }
})
