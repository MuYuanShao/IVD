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

    <!-- 工具栏：搜索 -->
    <el-card shadow="never" style="margin-bottom:16px">
      <div style="display:flex;align-items:center;gap:10px;flex-wrap:wrap;">
        <el-input v-model="search" placeholder="搜索批号/UDI" :prefix-icon="SearchIcon" clearable style="width:180px" @keyup.enter="fetchData" />
        <el-input v-model="filterHisCode" placeholder="商品编码" clearable style="width:130px" @keyup.enter="fetchData" />
        <el-input v-model="filterReagentName" placeholder="商品名称" clearable style="width:150px" @keyup.enter="fetchData" />
        <el-input v-model="filterDistributor" placeholder="配送商" clearable style="width:130px" @keyup.enter="fetchData" />
        <el-input v-model="filterManufacturer" placeholder="厂家" clearable style="width:130px" @keyup.enter="fetchData" />
        <el-input v-model="filterRegistrationCert" placeholder="注册证号" clearable style="width:140px" @keyup.enter="fetchData" />
        <el-button type="primary" @click="fetchData">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
        <el-button type="danger" @click="handleUrgentReq">🚨 紧急请领</el-button>
      </div>
    </el-card>

    <el-card shadow="never">
      <el-table :data="store.deptBatches" stripe v-loading="store.deptLoading" style="width:100%">
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
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="openTrace(row)">UDI追溯</el-button>
            <el-button link type="success" size="small" @click="openTransfer(row, '检验科库')">下发组库</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="page" v-model:page-size="pageSize" :total="store.deptTotal"
        :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next,jumper"
        style="margin-top:16px;justify-content:flex-end" @change="fetchData"
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
const page = ref(1), pageSize = ref(10)
const search = ref('')
const filterHisCode = ref(''), filterReagentName = ref('')
const filterDistributor = ref(''), filterManufacturer = ref(''), filterRegistrationCert = ref('')
const lowStockAlert = ref(null)

function fetchData() {
  store.fetchDeptInventory({
    page: page.value, pageSize: pageSize.value,
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

onMounted(() => { fetchData(); populateChartData(); nextTick(() => { pieChart.refresh?.(); barChart.refresh?.() }) })

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

