import { defineStore } from 'pinia'
import { ref, reactive } from 'vue'
import {
  getCatalogList,
  getCatalogDetail,
  createReagent,
  updateReagent,
  deleteReagent,
  submitApproval,
  approveReagent,
  rejectReagent,
  getPendingApprovals,
  getApprovalHistory,
  getUdiBacklog
} from '@/api/catalog'

/**
 * M01 试剂目录 Store
 */
export const useCatalogStore = defineStore('catalog', () => {
  // ── 目录列表 ──
  const list = ref([])
  const loading = ref(false)
  const total = ref(0)

  // ── 统计卡片 ──
  const stats = reactive({ total: 0, approved: 0, pending: 0, rejected: 0 })

  // ── 审批 ──
  const approvals = ref([])
  const approvalsTotal = ref(0)
  const approvalsLoading = ref(false)

  // ── 审批历史 ──
  const historyList = ref([])
  const historyTotal = ref(0)
  const historyLoading = ref(false)

  // ── UDI 待补全 ──
  const udiBacklogList = ref([])
  const udiBacklogTotal = ref(0)
  const udiBacklogLoading = ref(false)

  // ── 目录列表 ──
  async function fetchList(params = {}) {
    loading.value = true
    try {
      const data = await getCatalogList(params)
      list.value = data.records || []
      total.value = data.total || 0
    } finally {
      loading.value = false
    }
  }

  // ── 统计卡片 ──
  async function fetchStats() {
    try {
      const [all, approved, pending, rejected] = await Promise.all([
        getCatalogList({ page: 1, pageSize: 1 }),
        getCatalogList({ page: 1, pageSize: 1, approvalStatus: 'APPROVED' }),
        getCatalogList({ page: 1, pageSize: 1, approvalStatus: 'PENDING' }),
        getCatalogList({ page: 1, pageSize: 1, approvalStatus: 'REJECTED' })
      ])
      stats.total = all.total || 0
      stats.approved = approved.total || 0
      stats.pending = pending.total || 0
      stats.rejected = rejected.total || 0
    } catch {
      // 静默失败，stats 保持默认值
    }
  }

  // ── 新增 ──
  async function create(data) {
    return await createReagent(data)
  }

  // ── 编辑 ──
  async function update(id, data) {
    return await updateReagent(id, data)
  }

  // ── 删除 ──
  async function remove(id) {
    await deleteReagent(id)
  }

  // ── 提交审批 ──
  async function submitForApproval(id, data) {
    await submitApproval(id, data)
  }

  // ── 审批通过 ──
  async function approve(logId, data) {
    await approveReagent(logId, data)
  }

  // ── 审批驳回 ──
  async function rejectApproval(logId, data) {
    await rejectReagent(logId, data)
  }

  // ── 待审批列表 ──
  async function fetchPendingApprovals(params = {}) {
    approvalsLoading.value = true
    try {
      const data = await getPendingApprovals(params)
      approvals.value = data.records || []
      approvalsTotal.value = data.total || 0
    } finally {
      approvalsLoading.value = false
    }
  }

  // ── 审批历史 ──
  async function fetchApprovalHistory(params = {}) {
    historyLoading.value = true
    try {
      const data = await getApprovalHistory(params)
      historyList.value = data.records || []
      historyTotal.value = data.total || 0
    } finally {
      historyLoading.value = false
    }
  }

  // ── UDI 待补全 ──
  async function fetchUdiBacklog(params = {}) {
    udiBacklogLoading.value = true
    try {
      const data = await getUdiBacklog(params)
      udiBacklogList.value = data.records || []
      udiBacklogTotal.value = data.total || 0
    } finally {
      udiBacklogLoading.value = false
    }
  }

  return {
    list, loading, total,
    stats,
    approvals, approvalsTotal, approvalsLoading,
    historyList, historyTotal, historyLoading,
    udiBacklogList, udiBacklogTotal, udiBacklogLoading,
    fetchList, fetchStats,
    create, update, remove,
    submitForApproval, approve, rejectApproval,
    fetchPendingApprovals, fetchApprovalHistory,
    fetchUdiBacklog
  }
})
