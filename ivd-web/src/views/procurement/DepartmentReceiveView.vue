<!--
  DepartmentReceiveView.vue — 科室签收（独立功能菜单）
  科室人员对已发货的申领单进行签收确认，签收后自动生成结算记录
-->
<template>
  <div class="page-container">
    <div class="page-title">
      <h2><span class="title-icon" style="background:#fef3c7;color:#d97706;">📋</span>科室签收</h2>
      <span class="sub-tag">待签收申领单 · 确认收货后自动生成结算记录</span>
    </div>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stat-cards">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-card--all" @click="statusFilter='ALL';fetchData()">
          <el-statistic title="全部" :value="stats.total || 0" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-card--warning" @click="statusFilter='SHIPPED';fetchData()">
          <el-statistic title="待签收（整单发货）" :value="statsByStatus.SHIPPED || 0" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-card--info" @click="statusFilter='PARTIAL_FULFILLED';fetchData()">
          <el-statistic title="待签收（部分发货）" :value="statsByStatus.PARTIAL_FULFILLED || 0" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-card--success" @click="statusFilter='FULFILLED';fetchData()">
          <el-statistic title="已签收" :value="statsByStatus.FULFILLED || 0" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 搜索/筛选工具栏 -->
    <div class="toolbar">
      <el-input v-model="search" placeholder="搜索申领单号 / 申领科室…" :prefix-icon="Search" style="width:280px" clearable @keyup.enter="fetchData" />
      <el-select v-model="statusFilter" placeholder="签收状态" style="width:160px" clearable @change="fetchData">
        <el-option label="全部" value="ALL" />
        <el-option label="待签收（整单）" value="SHIPPED" />
        <el-option label="待签收（部分）" value="PARTIAL_FULFILLED" />
        <el-option label="已签收" value="FULFILLED" />
      </el-select>
      <el-button :icon="Refresh" @click="fetchData">刷新</el-button>
      <el-button
        type="warning"
        :disabled="records.filter(r => r.status !== 'FULFILLED').length === 0"
        @click="doBatchReceive"
      >一键签收全部</el-button>
    </div>

    <!-- 申领单列表 -->
    <el-table :data="records" v-loading="loading" stripe class="data-table" @sort-change="onSortChange">
      <el-table-column prop="reqNo" label="申领单号" width="200" fixed />
      <el-table-column prop="fromDeptName" label="申领科室" width="140" />
      <el-table-column prop="status" label="状态" width="110">
        <template #default="{ row }">
          <el-tag v-if="row.status === 'SHIPPED'" type="warning" size="small">待签收</el-tag>
          <el-tag v-else-if="row.status === 'PARTIAL_FULFILLED'" type="info" size="small">部分发货</el-tag>
          <el-tag v-else-if="row.status === 'FULFILLED'" type="success" size="small">已签收</el-tag>
          <el-tag v-else size="small">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="itemCount" label="申领品种数" width="100" align="center" />
      <el-table-column prop="totalQty" label="申领总量" width="100" align="center" />
      <el-table-column prop="fulfilledQty" label="已发货量" width="100" align="center" />
      <el-table-column prop="createdAt" label="创建时间" width="170" sortable="custom" />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'SHIPPED' || row.status === 'PARTIAL_FULFILLED'"
            type="warning"
            size="small"
            @click="doReceive(row)"
          >签收</el-button>
          <el-tag v-else-if="row.status === 'FULFILLED'" type="success" size="small">已签收</el-tag>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        background
        @change="fetchData"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { getRequisitionList } from '@/api/procurement'
import { receiveAndSettle, batchReceiveAndSettle } from '@/api/settlement'

// ── 可签收状态（科室签收页面的默认查询范围）──
const RECEIVABLE_STATUSES = ['SHIPPED', 'PARTIAL_FULFILLED', 'FULFILLED']

// ── 筛选 ──
const search = ref('')
const statusFilter = ref('ALL')

// ── 列表数据 ──
const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const records = ref([])

// ── 统计 ──
const stats = reactive({ total: 0 })
const statsByStatus = reactive({})

// ── 排序 ──
const sortField = ref('')
const sortOrder = ref('')

function onSortChange({ prop, order }) {
  sortField.value = prop || ''
  sortOrder.value = order === 'ascending' ? 'asc' : order === 'descending' ? 'desc' : ''
  fetchData()
}

// ── 获取数据 ──
async function fetchData() {
  loading.value = true
  try {
    const params = {
      page: page.value,
      pageSize: pageSize.value,
      keyword: search.value || undefined,
      sortField: sortField.value || undefined,
      sortOrder: sortOrder.value || undefined
    }
    // 后端支持逗号分隔多状态查询；ALL 时查所有可签收相关状态
    if (statusFilter.value !== 'ALL') {
      params.status = statusFilter.value
    } else {
      params.status = RECEIVABLE_STATUSES.join(',')
    }
    const res = await getRequisitionList(params)
    records.value = res.records || []
    total.value = res.total || 0

    // 更新统计
    stats.total = res.total || 0
    const byStatus = {}
    if (res.records) {
      res.records.forEach(r => {
        byStatus[r.status] = (byStatus[r.status] || 0) + 1
      })
    }
    // 如果当前筛选不是全部，则从后端获取完整统计
    if (statusFilter.value === 'ALL') {
      Object.assign(statsByStatus, byStatus)
    }
  } catch (e) {
    ElMessage.error('加载失败: ' + (e?.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

// ── 获取完整统计（按状态分别查询总数）──
async function fetchStats() {
  let totalCount = 0
  for (const st of RECEIVABLE_STATUSES) {
    try {
      const r = await getRequisitionList({ page: 1, pageSize: 1, status: st })
      statsByStatus[st] = r.total || 0
      totalCount += (r.total || 0)
    } catch { statsByStatus[st] = 0 }
  }
  stats.total = totalCount
}

// ── 签收操作 ──
async function doReceive(row) {
  try {
    await ElMessageBox.confirm(
      `确认签收申领单「${row.reqNo}」？签收后将自动生成结算记录。`,
      '科室签收确认',
      { type: 'warning', confirmButtonText: '确认签收', cancelButtonText: '取消' }
    )
    await receiveAndSettle(row.id)
    ElMessage.success('签收完成，结算记录已自动生成')
    fetchData()
    fetchStats()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error('签收失败: ' + (e?.message || e || '未知错误'))
    }
  }
}

// ── 批量签收操作 ──
async function doBatchReceive() {
  try {
    await ElMessageBox.confirm(
      '确认一键签收所有待签收申领单？签收后将自动生成结算记录。',
      '批量签收确认',
      { type: 'warning', confirmButtonText: '确认签收', cancelButtonText: '取消' }
    )
    const res = await batchReceiveAndSettle()
    const msg = `批量签收完成：共 ${res.total} 单，成功 ${res.success} 单` + (res.failed > 0 ? `，失败 ${res.failed} 单` : '')
    if (res.failed > 0) {
      ElMessage.warning(msg)
    } else {
      ElMessage.success(msg)
    }
    fetchData()
    fetchStats()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error('批量签收失败: ' + (e?.message || e || '未知错误'))
    }
  }
}

onMounted(() => {
  fetchStats()
  fetchData()
})
</script>

<style scoped>
.page-container {
  padding: 0;
}
.page-title {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 20px;
}
.page-title h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
  display: flex;
  align-items: center;
  gap: 10px;
}
.title-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
}
.sub-tag {
  color: var(--text-tertiary);
  font-size: 13px;
}
.stat-cards {
  margin-bottom: 20px;
}
.stat-card {
  cursor: pointer;
  transition: box-shadow 0.2s;
}
.stat-card:hover {
  box-shadow: var(--shadow-md);
}
.stat-card--all { border-left: 3px solid var(--ivd-primary); }
.stat-card--warning { border-left: 3px solid #f59e0b; }
.stat-card--info { border-left: 3px solid #3b82f6; }
.stat-card--success { border-left: 3px solid #10b981; }
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.data-table {
  width: 100%;
}
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
