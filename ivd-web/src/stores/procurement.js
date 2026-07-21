import { defineStore } from 'pinia'
import { ref, reactive } from 'vue'
import {
  getRequisitionList, getRequisitionStats, getRequisitionDetail,
  getShortageList, getShortageStats, getShortageDetail,
  getOrderList, getOrderDetail, getOrderStats,
  createRequisition, createOrderFromShortage, createManualOrder, getSmartReplenish,
  reviewRequisition,
  receiveOrder
} from '@/api/procurement'
import { ElMessage } from 'element-plus'

export const useProcurementStore = defineStore('procurement', () => {
  // ── 申领单 ──
  const requisitions = ref([])
  const reqTotal = ref(0)
  const reqLoading = ref(false)
  const reqStats = ref({ total: 0, pending: 0, picking: 0, completed: 0, shortage: 0 })

  // ── 缺货单 ──
  const shortages = ref([])
  const shTotal = ref(0)
  const shLoading = ref(false)
  const shStats = ref({ total: 0, pending: 0, purchasing: 0, completed: 0 })

  // ── 采购订单 ──
  const orders = ref([])
  const poTotal = ref(0)
  const poLoading = ref(false)
  const poStats = ref({ total: 0, pending: 0, partial: 0, completed: 0, cancelled: 0 })

  // ── 智能补货 ──
  const replenishSuggestions = ref([])
  const replenishLoading = ref(false)

  // ── 扫码验收 ──
  const inboundItems = ref([])
  const lastScanResult = ref(null)
  const currentOrder = ref(null)  // 当前验收的采购订单（含明细）
  const receiveLoading = ref(false)

  // ========== 申领 ==========
  async function fetchRequisitions(params = {}) {
    reqLoading.value = true
    try {
      const d = await getRequisitionList({ page: 1, pageSize: 10, ...params })
      requisitions.value = d.records || []
      reqTotal.value = d.total || 0
    } finally { reqLoading.value = false }
  }

  async function fetchRequisitionStats() {
    try { const s = await getRequisitionStats(); if (s) Object.assign(reqStats.value, s) } catch (e) { console.error('获取申领统计失败:', e) }
  }

  async function fetchRequisitionDetail(id) {
    return await getRequisitionDetail(id)
  }

  async function doCreateRequisition(data) {
    await createRequisition(data)
    await fetchRequisitions()
    await fetchRequisitionStats()
  }

  async function doReviewRequisition(id, action, comment = '') {
    await reviewRequisition(id, { action, comment })
  }

  // ========== 缺货 ==========
  async function fetchShortages(params = {}) {
    shLoading.value = true
    try {
      const d = await getShortageList({ page: 1, pageSize: 10, ...params })
      shortages.value = d.records || []
      shTotal.value = d.total || 0
    } finally { shLoading.value = false }
  }

  async function fetchShortageStats() {
    try { const s = await getShortageStats(); if (s) Object.assign(shStats.value, s) } catch (e) { console.error('获取缺货统计失败:', e) }
  }

  async function doCreateOrderFromShortage(data) {
    await createOrderFromShortage(data)
    await fetchShortages()
    await fetchShortageStats()
  }

  async function fetchShortageDetail(id) {
    return await getShortageDetail(id)
  }

  // ========== 采购订单 ==========
  async function fetchOrders(params = {}) {
    poLoading.value = true
    try {
      const d = await getOrderList({ page: 1, pageSize: 10, ...params })
      orders.value = d.records || []
      poTotal.value = d.total || 0
    } finally { poLoading.value = false }
  }

  async function fetchOrderStats() {
    try { const s = await getOrderStats(); if (s) Object.assign(poStats.value, s) } catch (e) { console.error('获取订单统计失败:', e) }
  }

  async function fetchOrderDetail(id) {
    currentOrder.value = await getOrderDetail(id)
    return currentOrder.value
  }

  async function doCreateManualOrder(data) {
    await createManualOrder(data)
    await fetchOrders()
    await fetchOrderStats()
  }

  // ========== 智能补货 ==========
  async function fetchSmartReplenish(days = 30) {
    replenishLoading.value = true
    try {
      const d = await getSmartReplenish(days)
      replenishSuggestions.value = (d && d.length) ? d : []
    } finally { replenishLoading.value = false }
  }

  // ========== 扫码验收 ==========
  function addInboundItem(item) {
    inboundItems.value.push({ ...item, id: Date.now() })
  }

  function removeInboundItem(id) {
    inboundItems.value = inboundItems.value.filter(i => i.id !== id)
  }

  function clearInbound() {
    inboundItems.value = []
    lastScanResult.value = null
  }

  /** 确认入库 — 逐条调用后端验收接口 */
  async function doReceiveInbound() {
    if (!inboundItems.value.length) return
    receiveLoading.value = true
    let successCount = 0
    let failCount = 0
    for (const item of inboundItems.value) {
      if (!item.orderId || !item.itemId) continue
      try {
        await receiveOrder({
          orderId: item.orderId,
          itemId: item.itemId,
          qtyReceived: item.qty || 1,
          udiDi: item.udiDi,
          batchNo: item.lotNo,
          productionDate: item.productionDate,
          expiryDate: item.expiryDate,
          warehouseId: item.warehouseId || 1
        })
        successCount++
      } catch (e) {
        console.error('验收请求失败:', e)
        failCount++
      }
    }
    receiveLoading.value = false
    if (successCount > 0) {
      ElMessage.success(`验收完成：成功 ${successCount} 条` + (failCount > 0 ? `，失败 ${failCount} 条` : ''))
    }
    clearInbound()
    return { successCount, failCount }
  }

  return {
    requisitions, reqTotal, reqLoading, reqStats,
    shortages, shTotal, shLoading, shStats,
    orders, poTotal, poLoading, poStats,
    replenishSuggestions, replenishLoading,
    inboundItems, lastScanResult, currentOrder, receiveLoading,
    fetchRequisitions, fetchRequisitionStats, fetchRequisitionDetail, doCreateRequisition, doReviewRequisition,
    fetchShortages, fetchShortageStats, fetchShortageDetail, doCreateOrderFromShortage,
    fetchOrders, fetchOrderStats, fetchOrderDetail, doCreateManualOrder,
    fetchSmartReplenish,
    addInboundItem, removeInboundItem, clearInbound, doReceiveInbound
  }
})
