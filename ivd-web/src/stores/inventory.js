import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  getBatchList, getWarehouseList, getFefoRecommendations,
  getExpiryAlerts, getInventoryStats, transferBatch, traceUdi,
  getDeptInventoryList, getDeptRequisitionList, getTracePatients
} from '@/api/inventory'

export const useInventoryStore = defineStore('inventory', () => {
  const batches = ref([])
  const total = ref(0)
  const warehouses = ref([])
  const stats = ref({
    total: 0, expiring30: 0, expiring90: 0,
    todayInbound: 0, todayOutbound: 0,
    deptTotal: 0, deptDispatched: 0,
    lowStock: 0, pendingRequisitions: 0
  })
  const expiryAlerts = ref([])
  const fefoList = ref([])
  const traceResult = ref(null)
  const loading = ref(false)

  // ── 科室库（二级）──
  const deptBatches = ref([])
  const deptTotal = ref(0)
  const deptLoading = ref(false)
  const deptRequisitions = ref([])
  const deptReqTotal = ref(0)
  const deptReqLoading = ref(false)

  // ── 组库管理（三级）──
  const fefoDetailList = ref([])

  // ── 患者关联 ──
  const tracePatients = ref([])
  const tracePatientsLoading = ref(false)

  async function fetchBatches(params = {}) {
    loading.value = true
    try {
      const data = await getBatchList({ page: 1, pageSize: 10, ...params })
      ;(batches.value = data.records || []), (total.value = data.total || 0)
      return data
    } finally {
      loading.value = false
    }
  }

  async function fetchWarehouses(params) {
    const data = await getWarehouseList(params)
    warehouses.value = data.records || []
    return data
  }

  async function fetchFefo(reagentId, warehouseId) {
    fefoList.value = (await getFefoRecommendations(reagentId, warehouseId)) || []
    return fefoList.value
  }

  async function fetchExpiryAlerts(days = 90) {
    expiryAlerts.value = (await getExpiryAlerts(days)) || []
    return expiryAlerts.value
  }

  async function fetchStats(warehouseId, level) {
    const data = await getInventoryStats(warehouseId, level)
    Object.assign(stats.value, data)
    return data
  }

  async function doTransfer(batchId, targetWarehouseId, qty, urgency = 'NORMAL') {
    const res = await transferBatch(batchId, targetWarehouseId, qty, urgency)
    await fetchStats()
    return res
  }

  async function doTrace(udiDi, lotNo) {
    traceResult.value = await traceUdi(udiDi, lotNo)
    return traceResult.value
  }

  async function fetchTracePatients(batchNo) {
    tracePatientsLoading.value = true
    try {
      tracePatients.value = (await getTracePatients(batchNo)) || []
      return tracePatients.value
    } finally { tracePatientsLoading.value = false }
  }

  // ── 科室库 ──
  async function fetchDeptInventory(params = {}) {
    deptLoading.value = true
    try {
      const d = await getDeptInventoryList({ page: 1, pageSize: 10, ...params })
      deptBatches.value = d.records || []
      deptTotal.value = d.total || 0
    } finally { deptLoading.value = false }
  }

  async function fetchDeptRequisitions(params = {}) {
    deptReqLoading.value = true
    try {
      const d = await getDeptRequisitionList({ page: 1, pageSize: 10, ...params })
      deptRequisitions.value = d.records || []
      deptReqTotal.value = d.total || 0
    } finally { deptReqLoading.value = false }
  }

  return {
    batches, total, warehouses, stats, expiryAlerts, fefoList, traceResult, loading,
    deptBatches, deptTotal, deptLoading, deptRequisitions, deptReqTotal, deptReqLoading,
    fefoDetailList, tracePatients, tracePatientsLoading,
    fetchBatches, fetchWarehouses, fetchFefo, fetchExpiryAlerts, fetchStats, doTransfer, doTrace,
    fetchDeptInventory, fetchDeptRequisitions, fetchTracePatients
  }
})
