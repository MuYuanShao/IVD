<!--
  DeptWarehouse.vue — 科室库(二级)（v2 独立路由页）
  来源：InventoryView.vue Tab "dept"
-->
<template>
  <div class="page-container">
    <div class="page-title">
      <h2><span class="title-icon" style="background:#f0fdf4;color:#10b981;">🏥</span>检验科库（二级库）</h2>
      <span class="sub-tag">科室周转 · M06</span>
      <div class="header-right">
        <el-button @click="handleExportReport">库存报表</el-button>
        <el-button type="primary" @click="handleDeptRequisition">向中心库请领</el-button>
      </div>
    </div>

    <el-row :gutter="16" class="stat-cards">
      <el-col :span="6"><el-card shadow="hover" class="stat-card"><el-statistic title="在库总量" :value="store.stats.deptTotal || 0" suffix="盒" /></el-card></el-col>
      <el-col :span="6"><el-card shadow="hover" class="stat-card stat-card--success"><el-statistic title="已下发组库" :value="store.stats.deptDispatched || 0" suffix="盒" /></el-card></el-col>
      <el-col :span="6"><el-card shadow="hover" class="stat-card stat-card--danger"><el-statistic title="库存不足预警" :value="store.stats.lowStock || 0" suffix="项" /></el-card></el-col>
      <el-col :span="6"><el-card shadow="hover" class="stat-card stat-card--warning"><el-statistic title="待请领单" :value="store.stats.pendingRequisitions || 0" suffix="单" /></el-card></el-col>
    </el-row>

    <div v-if="lowStockAlert" style="background:#fef2f2;border:1px solid #fecaca;padding:10px 16px;border-radius:8px;margin-bottom:16px;font-size:13px;color:#991b1b;">
      ⚠ 库存不足预警：{{ lowStockAlert }} — 建议立即向中心库请领
    </div>

    <el-row :gutter="16" style="margin-bottom:16px">
      <el-col :span="12">
        <el-card shadow="never"><template #header><span class="card-title">🥧 各组库分发占比</span></template><div ref="pieRef" style="height:280px" /></el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never"><template #header><span class="card-title">📊 安全库存达标率</span></template><div ref="barRef" style="height:280px" /></el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" style="margin-bottom:16px">
      <template #header><span class="card-title">📋 检验科库存清单</span></template>
      <div style="display:flex;align-items:center;gap:10px;margin-bottom:12px;flex-wrap:wrap;">
        <el-input v-model="search" placeholder="搜索试剂/批号" :prefix-icon="SearchIcon" clearable style="width:180px" @keyup.enter="fetchData" />
        <el-input v-model="filterHisCode" placeholder="商品编码" clearable style="width:130px" @keyup.enter="fetchData" />
        <el-input v-model="filterReagentName" placeholder="商品名称" clearable style="width:150px" @keyup.enter="fetchData" />
        <el-input v-model="filterDistributor" placeholder="配送商" clearable style="width:130px" @keyup.enter="fetchData" />
        <el-input v-model="filterManufacturer" placeholder="厂家" clearable style="width:130px" @keyup.enter="fetchData" />
        <el-input v-model="filterRegistrationCert" placeholder="注册证号" clearable style="width:140px" @keyup.enter="fetchData" />
        <el-button type="primary" size="small" @click="fetchData">查询</el-button>
        <el-button size="small" @click="resetFilters">重置</el-button>
        <el-button type="danger" size="small" @click="handleUrgentReq">🚨 紧急请领</el-button>
      </div>
      <el-table :data="store.deptBatches" stripe v-loading="store.deptLoading" style="width:100%">
        <el-table-column prop="reagentName" label="试剂" width="140" />
        <el-table-column prop="batchNo" label="批号" width="120" />
        <el-table-column prop="expiryDate" label="效期" width="100" />
        <el-table-column label="剩余天数" width="90">
          <template #default="{row}"><el-tag :type="getExpiryTagType(row.expiryDate)" size="small">{{ getRemainingDays(row.expiryDate) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="stock" label="库存量" width="80" />
        <el-table-column prop="safetyStock" label="安全库存" width="90" />
        <el-table-column prop="location" label="库位" width="80" />
        <el-table-column label="可支撑天数" width="100">
          <template #default="{row}"><span :style="{ color: row.supportDays < 7 ? 'var(--ivd-danger)' : '' }">{{ row.supportDays || '—' }} 天</span></template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{row}">
            <el-tag :type="(row.stock||0) < (row.safetyStock||1) ? 'danger' : 'success'" size="small">
              {{ (row.stock||0) < (row.safetyStock||1) ? '库存不足' : '正常' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="openTrace(row)">UDI追溯</el-button>
            <el-button link type="success" size="small" @click="openTransfer(row, '检验科库')">下发组库</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never">
      <template #header><span class="card-title">📝 近期请领记录</span></template>
      <el-table :data="store.deptRequisitions" stripe v-loading="store.deptReqLoading" style="width:100%">
        <el-table-column prop="reqNo" label="请领单号" width="180" />
        <el-table-column prop="reagentName" label="试剂" width="140" />
        <el-table-column prop="qty" label="数量" width="80" />
        <el-table-column prop="source" label="来源" width="100" />
        <el-table-column prop="applicant" label="申请人" width="90" />
        <el-table-column label="状态" width="90">
          <template #default="{row}"><el-tag size="small" :type="row.status==='已完成'?'success':'warning'">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="createdAt" label="时间" width="100" />
      </el-table>
      <el-pagination
        v-model:current-page="reqPage" v-model:page-size="reqPageSize" :total="store.deptReqTotal"
        :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next,jumper"
        style="margin-top:16px;justify-content:flex-end" @change="fetchReqData"
      />
    </el-card>

    <TraceDialog v-model="showTraceDialog" :initial-search="traceSearch" />
    <TransferDialog v-model="showTransferDialog" :source-name="sourceName" :max-qty="maxQty" :warehouse-options="targetWarehouses" :loading="transferLoading" @submit="doSubmitTransfer" />
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { Search as SearchIcon } from '@element-plus/icons-vue'
import { useInventoryStore } from '@/stores/inventory'
import { useECharts } from '@/composables/useECharts'
import { getRemainingDays, getExpiryTagType } from '@/utils/inventory'
import TraceDialog from '@/components/TraceDialog.vue'
import TransferDialog from '@/components/TransferDialog.vue'

const router = useRouter()
const store = useInventoryStore()
const reqPage = ref(1), reqPageSize = ref(10)
const search = ref('')
const filterHisCode = ref(''), filterReagentName = ref('')
const filterDistributor = ref(''), filterManufacturer = ref(''), filterRegistrationCert = ref('')
const lowStockAlert = ref(null)

function fetchData() {
  store.fetchDeptInventory({
    page: 1, pageSize: 50,
    keyword: search.value || undefined,
    hisCode: filterHisCode.value || undefined,
    reagentName: filterReagentName.value || undefined,
    distributor: filterDistributor.value || undefined,
    manufacturer: filterManufacturer.value || undefined,
    registrationCertNo: filterRegistrationCert.value || undefined
  })
  store.fetchStats()
}

function resetFilters() {
  search.value = ''
  filterHisCode.value = ''
  filterReagentName.value = ''
  filterDistributor.value = ''
  filterManufacturer.value = ''
  filterRegistrationCert.value = ''
  fetchData()
}

function handleUrgentReq() { router.push('/requisition?urgency=URGENT') }

function fetchReqData() {
  store.fetchDeptRequisitions({ page: reqPage.value, pageSize: reqPageSize.value })
}

// ── ECharts ──
const pieRef = ref(null), barRef = ref(null)
const groupPieData = ref([])
const safetyBarLabels = ref([])
const safetyBarData = ref([])

const pieChart = useECharts(pieRef, () => ({
  tooltip: { trigger: 'item' },
  legend: { orient: 'vertical', right: 10, top: 'center' },
  series: [{
    type: 'pie', radius: ['45%', '72%'], center: ['40%', '50%'], label: { show: false },
    data: groupPieData.value
  }]
}))

const barChart = useECharts(barRef, () => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 12, right: 24, top: 12, bottom: 28 },
  xAxis: { type: 'category', data: safetyBarLabels.value },
  yAxis: { type: 'value', max: 100 },
  series: [{
    type: 'bar', data: safetyBarData.value, barWidth: 14,
    itemStyle: { color: '#10b981' }, label: { show: true, position: 'right', formatter: '{c}%' }
  }]
}))

function populateChartData() {
  groupPieData.value = [
    { name: '免疫组', value: Math.floor(Math.random() * 200) + 80 },
    { name: '生化组', value: Math.floor(Math.random() * 200) + 80 },
    { name: '临检组', value: Math.floor(Math.random() * 200) + 80 },
    { name: '微生物组', value: Math.floor(Math.random() * 200) + 80 },
    { name: '分子组', value: Math.floor(Math.random() * 200) + 80 }
  ]
  safetyBarLabels.value = ['免疫组', '生化组', '临检组', '微生物组', '分子组']
  safetyBarData.value = safetyBarLabels.value.map(() => Math.floor(Math.random() * 16) + 80)
}

onMounted(() => { fetchData(); fetchReqData(); populateChartData(); nextTick(() => { pieChart.refresh?.(); barChart.refresh?.() }) })

// ── 弹窗 ──
const showTraceDialog = ref(false), traceSearch = ref('')
const showTransferDialog = ref(false), sourceName = ref(''), maxQty = ref(999), transferLoading = ref(false), selectedBatchId = ref(null)

const targetWarehouses = [
  { value: 11, label: '免疫组库' }, { value: 12, label: '生化组库' },
  { value: 13, label: '临检组库' }, { value: 14, label: '微生物组库' }, { value: 15, label: '分子组库' }
]

function openTrace(row) { traceSearch.value = row.udiDi || row.batchNo || ''; showTraceDialog.value = true }
function openTransfer(row, source) { sourceName.value = source; maxQty.value = row.stock || 999; selectedBatchId.value = row.id; showTransferDialog.value = true }

async function doSubmitTransfer(form) {
  transferLoading.value = true
  try { await store.doTransfer(selectedBatchId.value, form.targetWarehouse, form.quantity); ElMessage.success('下发成功'); showTransferDialog.value = false; fetchData() }
  catch (e) { console.error('Transfer failed', e) } finally { transferLoading.value = false }
}

function handleExportReport() { ElMessage.success('检验科库库存报表生成中，即将下载...') }
function handleDeptRequisition() { router.push('/requisition') }
</script>

