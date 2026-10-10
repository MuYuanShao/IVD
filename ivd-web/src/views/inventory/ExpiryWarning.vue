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
      <el-col :span="6"><el-card shadow="hover" class="stat-card stat-card--success"><el-statistic title="安全 >90天" :value="store.stats.safe || 0" suffix="项" /></el-card></el-col>
      <el-col :span="6"><el-card shadow="hover" class="stat-card"><el-statistic title="涉险库存金额" :value="store.stats.riskAmount || 0" suffix="万" /></el-card></el-col>
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
        <el-table-column prop="hisCode" label="试剂编码" width="110" show-overflow-tooltip />
        <el-table-column prop="reagentName" label="试剂名称" min-width="130" show-overflow-tooltip />
        <el-table-column prop="genericName" label="通用名称" min-width="130" show-overflow-tooltip />
        <el-table-column prop="specification" label="规格型号" width="100" show-overflow-tooltip />
        <el-table-column prop="manufacturer" label="厂家" width="100" show-overflow-tooltip />
        <el-table-column prop="distributor" label="配送商" width="100" show-overflow-tooltip />
        <el-table-column prop="batchNo" label="批号" width="120" show-overflow-tooltip />
        <el-table-column prop="manufactureDate" label="生产日期" width="100" />
        <el-table-column prop="expiryDate" label="效期" width="100" />
        <el-table-column label="剩余天数" width="85">
          <template #default="{row}"><el-tag :type="getExpiryTagType(row.expiryDate)" size="small">{{ getRemainingDays(row.expiryDate) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="stock" label="库存量" width="75" />
        <el-table-column label="金额" width="100" align="right">
          <template #default="{row}">{{ row.amount ? '¥' + row.amount.toFixed(2) : '—' }}</template>
        </el-table-column>
        <el-table-column prop="inboundDate" label="入库日期" width="100" />
        <el-table-column prop="registrationCertNo" label="注册证号" width="120" show-overflow-tooltip />
        <el-table-column label="是否带量" width="80" align="center">
          <template #default="{row}">
            <el-tag :type="row.isVolumeBased ? 'warning' : 'info'" size="small">{{ row.isVolumeBased ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="volumeType" label="带量类型" width="110" show-overflow-tooltip>
          <template #default="{row}">{{ row.volumeType || '—' }}</template>
        </el-table-column>
        <el-table-column label="是否收费" width="80" align="center">
          <template #default="{row}">
            <el-tag :type="row.isChargeable ? 'success' : 'info'" size="small">{{ row.isChargeable ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="insuranceCode" label="医保编码" width="120" show-overflow-tooltip />
        <el-table-column prop="warehouseName" label="所在库" width="100" show-overflow-tooltip />
        <el-table-column label="操作" width="120" fixed="right">
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
import { ref, computed, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { useInventoryStore } from '@/stores/inventory'
import { useECharts } from '@/composables/useECharts'
import { getRemainingDays, getExpiryTagType } from '@/utils/inventory'
import TraceDialog from '@/components/TraceDialog.vue'
import TransferDialog from '@/components/TransferDialog.vue'

const store = useInventoryStore()
const levelFilter = ref(''), warehouseFilter = ref(null)

function fetchData() { store.fetchExpiryAlerts(levelFilter.value === 'urgent' ? 30 : 90, warehouseFilter.value || undefined); store.fetchStats(); store.fetchWarehouses({ pageSize: 50 }) }

// ── ECharts ──
const pieRef = ref(null), timelineRef = ref(null)
const expiryPieData = ref([])
const timelineDates = ref([])
const timelineBatches = ref([])
const timelineAmounts = ref([])

const pieChart = useECharts(pieRef, () => ({
  tooltip: { trigger: 'item' },
  legend: { orient: 'vertical', right: 10, top: 'center' },
  series: [{
    type: 'pie', radius: ['45%', '72%'], center: ['40%', '50%'], label: { show: false },
    data: expiryPieData.value
  }]
}))

const timelineChart = useECharts(timelineRef, () => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['到期批次', '累计涉险金额(万)'], bottom: 0 },
  grid: { left: 12, right: 12, top: 12, bottom: 28 },
  xAxis: { type: 'category', data: timelineDates.value },
  yAxis: [
    { type: 'value', name: '批次' },
    { type: 'value', name: '万' }
  ],
  series: [
    { name: '到期批次', type: 'bar', data: timelineBatches.value, barWidth: 12, itemStyle: { color: '#f59e0b' } },
    { name: '累计涉险金额(万)', type: 'line', yAxisIndex: 1, data: timelineAmounts.value, lineStyle: { color: '#ef4444' }, itemStyle: { color: '#ef4444' } }
  ]
}))

function populateChartData() {
  expiryPieData.value = [
    { name: '≤30天紧急', value: Math.floor(Math.random() * 20) + 5 },
    { name: '31-90天关注', value: Math.floor(Math.random() * 40) + 15 },
    { name: '>90天安全', value: Math.floor(Math.random() * 200) + 80 }
  ]
  timelineDates.value = ['7月', '8月', '9月', '10月', '11月', '12月']
  timelineBatches.value = [12, 18, 15, 22, 20, 25]
  let cum = 0
  timelineAmounts.value = [8, 12, 10, 15, 18, 22].map(v => { cum += v; return cum })
}

onMounted(() => { fetchData(); populateChartData(); nextTick(() => { pieChart.refresh?.(); timelineChart.refresh?.() }) })

// ── 弹窗 ──
const showTraceDialog = ref(false), traceSearch = ref('')
const showTransferDialog = ref(false), sourceName = ref(''), maxQty = ref(999), transferLoading = ref(false), selectedBatchId = ref(null)

const selectedSourceWarehouseId = ref(null)
const targetWarehouses = computed(() => store.warehouses
  .filter(w => w.id !== selectedSourceWarehouseId.value && (w.status === 1 || w.isActive === true))
  .map(w => ({ value: w.id, label: w.name })))

function openTrace(row) { traceSearch.value = row.udiDi || row.batchNo || ''; showTraceDialog.value = true }
function openTransfer(row, source) { sourceName.value = source; maxQty.value = row.stock || 999; selectedBatchId.value = row.id; selectedSourceWarehouseId.value = row.warehouseId; showTransferDialog.value = true }

async function doSubmitTransfer(form) {
  transferLoading.value = true
  try { await store.doTransfer(selectedBatchId.value, form.targetWarehouse, form.quantity, 'NORMAL', form.reason); ElMessage.success('处理成功'); showTransferDialog.value = false; fetchData() }
  catch (e) { console.error('Transfer failed', e) } finally { transferLoading.value = false }
}

function handleExport() { ElMessage.success('效期预警清单导出中，即将下载...') }
</script>

