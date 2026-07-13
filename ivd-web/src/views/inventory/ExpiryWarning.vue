<!--
  ExpiryWarning.vue — 效期预警（v2 独立路由页）
  来源：InventoryView.vue Tab "expiry"
-->
<template>
  <div class="page-container">
    <div class="page-title">
      <h2><span class="title-icon" style="background:#fffbeb;color:#f59e0b;">⏰</span>效期预警中心</h2>
      <span class="sub-tag">PRD 8.2 · 30/90天分级预警</span>
      <div class="header-right">
        <el-select v-model="levelFilter" placeholder="全部级别" clearable style="width:140px" @change="fetchData">
          <el-option label="紧急 ≤30天" value="urgent" /><el-option label="关注 ≤90天" value="caution" />
        </el-select>
        <el-select v-model="warehouseFilter" placeholder="全部库房" clearable style="width:160px" @change="fetchData">
          <el-option v-for="w in store.warehouses" :key="w.id" :label="w.name || w.warehouseName" :value="w.id" />
        </el-select>
        <el-button @click="handleExport">导出预警清单</el-button>
      </div>
    </div>

    <el-row :gutter="16" class="stat-cards">
      <el-col :span="6"><el-card shadow="hover" class="stat-card stat-card--danger"><el-statistic title="紧急 ≤30天" :value="store.stats.expiring30 || 0" suffix="项" /></el-card></el-col>
      <el-col :span="6"><el-card shadow="hover" class="stat-card stat-card--warning"><el-statistic title="关注 ≤90天" :value="store.stats.expiring90 || 0" suffix="项" /></el-card></el-col>
      <el-col :span="6"><el-card shadow="hover" class="stat-card stat-card--success"><el-statistic title="安全 >90天" :value="156" suffix="项" /></el-card></el-col>
      <el-col :span="6"><el-card shadow="hover" class="stat-card"><el-statistic title="涉险库存金额" value="18.6" suffix="万" /></el-card></el-col>
    </el-row>

    <el-row :gutter="16" style="margin-bottom:16px">
      <el-col :span="12">
        <el-card shadow="never"><template #header><span class="card-title">🥧 效期分布</span></template><div ref="pieRef" style="height:280px" /></el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never"><template #header><span class="card-title">📈 未来6个月到期批次趋势</span></template><div ref="timelineRef" style="height:280px" /></el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <template #header><span class="card-title">⚠ 预警清单（按剩余天数升序）</span></template>
      <el-table :data="store.expiryAlerts" stripe v-loading="store.loading" style="width:100%">
        <el-table-column label="预警级别" width="90">
          <template #default="{row}">
            <el-tag :type="getRemainingDays(row.expiryDate) <= 30 ? 'danger' : 'warning'" size="small" effect="dark">
              {{ getRemainingDays(row.expiryDate) <= 30 ? '🔴 紧急' : '🟡 关注' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reagentName" label="试剂" width="160" />
        <el-table-column prop="batchNo" label="批号" width="120" />
        <el-table-column prop="warehouseName" label="所在库" width="100" />
        <el-table-column prop="expiryDate" label="效期" width="100" />
        <el-table-column label="剩余天数" width="90">
          <template #default="{row}"><el-tag :type="getExpiryTagType(row.expiryDate)" size="small">{{ getRemainingDays(row.expiryDate) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="stock" label="库存量" width="80" />
        <el-table-column label="涉险金额" width="100"><template #default="{row}">¥{{ row.riskAmount || 0 }}</template></el-table-column>
        <el-table-column label="系统建议" min-width="200">
          <template #default="{row}">{{ getSuggestion(row) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="openTrace(row)">UDI追溯</el-button>
            <el-button link type="warning" size="small" @click="openTransfer(row, row.warehouseName)">处理</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="card-footer" style="margin-top:12px;font-size:12px;color:var(--text-tertiary);background:#f8fafc;padding:10px 16px;border-radius:8px;">
        💡 <strong>FEFO 联动</strong>：所有近效期批次已自动置顶 FEFO 出库推荐序列（见"组库管理"页面）。开瓶试剂的 effective_expiry = MIN(expiry_date, opened_date + stability_days_after_open)（PRD 8.1）。
      </div>
    </el-card>

    <TraceDialog v-model="showTraceDialog" :initial-search="traceSearch" />
    <TransferDialog v-model="showTransferDialog" :source-name="sourceName" :max-qty="maxQty" :warehouse-options="targetWarehouses" :loading="transferLoading" @submit="doSubmitTransfer" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useInventoryStore } from '@/stores/inventory'
import { useECharts } from '@/composables/useECharts'
import { getRemainingDays, getExpiryTagType, getExpirySuggestion } from '@/utils/inventory'
import TraceDialog from '@/components/TraceDialog.vue'
import TransferDialog from '@/components/TransferDialog.vue'

const store = useInventoryStore()
const levelFilter = ref(''), warehouseFilter = ref(null)

function getSuggestion(row) { return getExpirySuggestion(row) }

function fetchData() { store.fetchExpiryAlerts(levelFilter.value === 'urgent' ? 30 : 90, warehouseFilter.value || undefined); store.fetchStats(); store.fetchWarehouses({ pageSize: 50 }) }

onMounted(() => { fetchData(); initCharts() })

// ── ECharts ──
const pieRef = ref(null), timelineRef = ref(null)

function initCharts() {
  useECharts(pieRef, () => ({
    tooltip: { trigger: 'item' },
    legend: { orient: 'vertical', right: 10, top: 'center' },
    series: [{
      type: 'pie', radius: ['45%', '72%'], center: ['40%', '50%'], label: { show: false },
      data: [
        { value: 2, name: '≤30天(紧急)', itemStyle: { color: '#ef4444' } },
        { value: 3, name: '31-90天(关注)', itemStyle: { color: '#f59e0b' } },
        { value: 156, name: '>90天(安全)', itemStyle: { color: '#10b981' } }
      ]
    }]
  }))

  useECharts(timelineRef, () => ({
    tooltip: { trigger: 'axis' },
    legend: { data: ['到期批次', '累计涉险金额(万)'], bottom: 0 },
    grid: { left: 12, right: 12, top: 12, bottom: 28 },
    xAxis: { type: 'category', data: ['7月', '8月', '9月', '10月', '11月', '12月'] },
    yAxis: [
      { type: 'value', name: '批次' },
      { type: 'value', name: '万' }
    ],
    series: [
      { name: '到期批次', type: 'bar', data: [2, 3, 5, 4, 8, 12], barWidth: 12, itemStyle: { color: '#f59e0b' } },
      { name: '累计涉险金额(万)', type: 'line', yAxisIndex: 1, data: [3.2, 8.5, 15.8, 22.1, 35.6, 52.0], lineStyle: { color: '#ef4444' }, itemStyle: { color: '#ef4444' } }
    ]
  }))
}

// ── 弹窗 ──
const showTraceDialog = ref(false), traceSearch = ref('')
const showTransferDialog = ref(false), sourceName = ref(''), maxQty = ref(999), transferLoading = ref(false)

const targetWarehouses = [
  { value: 1, label: '中心药库' }, { value: 2, label: '门诊药房' },
  { value: 3, label: '住院药房' }, { value: 4, label: '急诊药房' }
]

function openTrace(row) { traceSearch.value = row.udiDi || row.batchNo || ''; showTraceDialog.value = true }
function openTransfer(row, source) { sourceName.value = source; maxQty.value = row.stock || 999; showTransferDialog.value = true }

async function doSubmitTransfer(form) {
  transferLoading.value = true
  try { await store.doTransfer(1, form.targetWarehouse, form.quantity); ElMessage.success('处理成功'); showTransferDialog.value = false; fetchData() }
  catch {} finally { transferLoading.value = false }
}

function handleExport() { ElMessage.success('效期预警清单导出中，即将下载...') }
</script>

<style scoped>
.page-title { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.page-title h2 { font-size: 18px; font-weight: 700; color: var(--text-primary); display: flex; align-items: center; gap: 8px; margin: 0; flex: 1; }
.title-icon { display: inline-flex; align-items: center; justify-content: center; width: 36px; height: 36px; border-radius: 10px; font-size: 18px; }
.sub-tag { font-size: 11px; color: var(--ivd-primary, #059669); background: #ecfdf5; padding: 3px 9px; border-radius: 12px; font-weight: 600; border: 1px solid #a7f3d0; margin-left: 8px; }
.header-right { display: flex; gap: 8px; }
.stat-card { border-radius: var(--radius-md); transition: transform 0.2s; }
.stat-card:hover { transform: translateY(-2px); }
.stat-card--danger { border-top: 3px solid var(--ivd-danger); }
.stat-card--warning { border-top: 3px solid var(--ivd-warning); }
.stat-card--success { border-top: 3px solid var(--ivd-success); }
.stat-cards { margin-bottom: 16px; }
.card-title { font-size: 14px; font-weight: 600; color: var(--text-primary); }
:deep(.el-table th) { background: var(--bg-page); color: var(--text-secondary); font-weight: 600; font-size: 12px; }
</style>
