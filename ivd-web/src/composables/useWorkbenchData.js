import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { getWorkbench, getWorkbenchProducts } from '@/api/workbench'
import {
  validateOverview,
  validateProducts,
  money,
} from '@/utils/workbenchContract.mjs'
import * as demo from '@/mock/workspace'

export function useWorkbenchData(isDemo) {
  const data = ref(null),
    productData = ref(null),
    loading = ref(false),
    productLoading = ref(false)
  const error = ref(''),
    productError = ref(''),
    page = ref(1),
    keyword = ref('')
  let summaryController,
    productsController,
    summaryVersion = 0,
    productVersion = 0,
    searchTimer
  async function loadSummary() {
    const version = ++summaryVersion
    summaryController?.abort()
    if (isDemo.value) return
    summaryController = new AbortController()
    loading.value = true
    error.value = ''
    try {
      const result = validateOverview(
        await getWorkbench(summaryController.signal),
      )
      if (version === summaryVersion) data.value = result
    } catch (e) {
      if (version === summaryVersion && e.code !== 'ERR_CANCELED')
        error.value =
          e.response?.data?.message || e.message || '读取失败，请重试'
    } finally {
      if (version === summaryVersion) loading.value = false
    }
  }
  async function loadProducts() {
    const version = ++productVersion
    productsController?.abort()
    if (isDemo.value) return
    productsController = new AbortController()
    productLoading.value = true
    productError.value = ''
    try {
      const result = validateProducts(
        await getWorkbenchProducts(
          { page: page.value, pageSize: 5, keyword: keyword.value },
          productsController.signal,
        ),
      )
      if (version === productVersion) productData.value = result
    } catch (e) {
      if (version === productVersion && e.code !== 'ERR_CANCELED') {
        productData.value = null
        productError.value =
          e.response?.data?.message || e.message || '批次读取失败，请重试'
      }
    } finally {
      if (version === productVersion) productLoading.value = false
    }
  }
  function search(value) {
    keyword.value = value.trim()
    page.value = 1
    clearTimeout(searchTimer)
    productsController?.abort()
    ++productVersion
    productLoading.value = true
    searchTimer = setTimeout(loadProducts, 300)
  }
  function changePage(value) {
    page.value = value
    clearTimeout(searchTimer)
    loadProducts()
  }
  function refresh() {
    clearTimeout(searchTimer)
    return Promise.allSettled([loadSummary(), loadProducts()])
  }
  watch(
    isDemo,
    () => {
      data.value = null
      productData.value = null
      page.value = 1
      keyword.value = ''
      refresh()
    },
    { immediate: true },
  )
  onBeforeUnmount(() => {
    ++summaryVersion
    ++productVersion
    summaryController?.abort()
    productsController?.abort()
    clearTimeout(searchTimer)
  })
  const statistics = computed(() => {
    if (isDemo.value) return demo.statistics
    if (!data.value) return []
    const m = data.value.metrics
    return [
      {
        title: '今日消耗金额',
        value: m.consumptionAmount,
        precision: 2,
        prefix: '¥',
        tone: 'orange',
        icon: 'money',
        comparison: '消耗台账金额 · 非销售额',
        path: '/consumption',
      },
      {
        title: '今日消耗记录',
        value: m.consumedRecords,
        unit: '笔',
        tone: 'teal',
        icon: 'cart',
        comparison: '按流水记录计数，不混加不同单位',
        path: '/consumption',
      },
      {
        title: '今日入库记录',
        value: m.inboundRecords,
        unit: '笔',
        tone: 'purple',
        icon: 'inbound',
        comparison: '外部入库流水，不含内部调入',
        path: '/receiving',
      },
      {
        title: '今日调拨出库',
        value: m.transferRecords,
        unit: '笔',
        tone: 'blue',
        icon: 'outbound',
        comparison: '按调出库范围计数',
        path: '/pick-and-ship',
      },
    ]
  })
  const alerts = computed(() => {
    if (isDemo.value) return demo.alerts
    if (!data.value) return []
    const a = data.value.alerts
    return [
      {
        title: '已过期在库',
        value: a.expiredBatches,
        unit: '批',
        tone: 'orange',
        description: '库存大于0，效期早于今天',
        path: '/expiry',
      },
      {
        title: '90天内到期',
        value: a.expiringBatches,
        unit: '批',
        tone: 'purple',
        description: '包含今天，不重复计入已过期',
        path: '/expiry',
      },
      {
        title: 'UDI待补全',
        value: a.pendingUdiBatches,
        unit: '批',
        tone: 'blue',
        description: '有库存且状态为待补全',
        path: '/central',
      },
      {
        title: '未闭环缺货',
        value: a.shortageOrders,
        unit: '单',
        tone: 'teal',
        description: '待采购或采购中的缺货单',
        path: '/shortage',
      },
    ]
  })
  const statusLabels = {
    PENDING_REVIEW: '待审核',
    REVIEWED: '已审核',
    PICKING: '拣选中',
    PARTIAL_FULFILLED: '部分发货',
    SHIPPED: '待签收',
    SHORTAGE_PENDING: '缺货待采',
  }
  const todos = computed(() =>
    isDemo.value
      ? demo.todos
      : (data.value?.todos || []).map((row) => ({
          ...row,
          recordId: row.id,
          id: row.reqNo,
          title: `申领单 · ${statusLabels[row.status] || row.status}`,
          status: statusLabels[row.status] || row.status,
          type: 'warning',
          progress: row.lineCount
            ? Math.min(
                100,
                Math.round((row.fulfilledLines / row.lineCount) * 100),
              )
            : 0,
          amount: money(row.amount),
          items: `${row.lineCount} 项明细，${row.fulfilledLines} 项已足量发货`,
          time: row.createdAt?.replace('T', ' ').slice(0, 16),
        })),
  )
  const trend = computed(() =>
    isDemo.value
      ? demo.trend
      : (data.value?.trend || []).map((row) => ({
          ...row,
          date: row.date,
          inbound: row.inbound === null ? null : row.inbound / 10000,
          outbound: row.outbound === null ? null : row.outbound / 10000,
        })),
  )
  const products = computed(() =>
    isDemo.value
      ? demo.products
      : (productData.value?.records || []).map((row) => ({
          ...row,
          holder: row.manufacturer || '未维护',
          unit: row.unit || '未维护',
          department: row.warehouse || '未维护',
          location: row.location || '未维护',
          expiry: row.expiry || '未维护',
        })),
  )
  const notices = computed(() => (isDemo.value ? demo.notices : []))
  return {
    data,
    productData,
    loading,
    productLoading,
    error,
    productError,
    page,
    statistics,
    alerts,
    todos,
    trend,
    products,
    notices,
    refresh,
    loadProducts,
    search,
    changePage,
  }
}
