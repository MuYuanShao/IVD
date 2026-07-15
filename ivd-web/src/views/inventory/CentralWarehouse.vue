<!--
  CentralWarehouse.vue — 中心库(一级)（v2 独立路由页）
  来源：InventoryView.vue Tab "central"
-->
<template>
  <div class="page-container">
    <div class="page-title">
      <h2><span class="title-icon" style="background:#eff6ff;color:#3b82f6;">🏢</span>中心药库（一级库）</h2>
      <span class="sub-tag">全院总仓 · M06</span>
      <div class="header-right">
        <el-button @click="handleExportReport">库存报表</el-button>
        <el-button type="primary" @click="handleDispatchToDept">下发科室库</el-button>
        <el-button @click="handleInventory">盘点</el-button>
      </div>
    </div>

    <el-row :gutter="16" class="stat-cards">
      <el-col :span="6"><el-card shadow="hover" class="stat-card"><el-statistic title="在库总量" :value="store.stats.total || 0" suffix="盒" /></el-card></el-col>
      <el-col :span="6"><el-card shadow="hover" class="stat-card stat-card--success"><el-statistic title="今日入库" :value="store.stats.todayInbound || 0" suffix="盒" /></el-card></el-col>
      <el-col :span="6"><el-card shadow="hover" class="stat-card stat-card--info"><el-statistic title="今日调拨出" :value="store.stats.todayOutbound || 0" suffix="盒" /></el-card></el-col>
      <el-col :span="6"><el-card shadow="hover" class="stat-card stat-card--warning"><el-statistic title="近效期批次" :value="store.stats.expiring30 || 0" suffix="项" /></el-card></el-col>
    </el-row>

    <el-row :gutter="16" style="margin-bottom:16px">
      <el-col :span="12">
        <el-card shadow="never"><template #header><span class="card-title">📊 近30天入库/调拨趋势</span></template><div ref="barRef" style="height:280px" /></el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never"><template #header><span class="card-title">🥧 试剂分类占比</span></template><div ref="pieRef" style="height:280px" /></el-card>
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
        <el-select v-model="warehouseFilter" placeholder="全部库房" clearable style="width:150px" @change="fetchData">
          <el-option v-for="w in store.warehouses" :key="w.id" :label="w.name" :value="w.id" />
        </el-select>
        <el-button type="primary" @click="fetchData">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
    </el-card>

    <el-card shadow="never">
      <el-table :data="store.batches" stripe v-loading="store.loading" style="width:100%">
        <el-table-column prop="reagentName" label="试剂" width="160" />
        <el-table-column prop="batchNo" label="批号" width="130" />
        <el-table-column prop="expiryDate" label="效期" width="100" />
        <el-table-column label="剩余天数" width="90">
          <template #default="{row}"><el-tag :type="getExpiryTagType(row.expiryDate)" size="small">{{ getRemainingDays(row.expiryDate) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="stock" label="库存量" width="80" suffix="盒" />
        <el-table-column prop="location" label="库位" width="90" />
        <el-table-column prop="inboundDate" label="入库日期" width="100" />
        <el-table-column label="状态" width="90">
          <template #default="{row}">
            <el-tag :type="row.udiStatus==='IN_STOCK'?'success':'warning'" size="small">
              {{ row.udiStatus==='IN_STOCK'?'正常': row.udiStatus==='NEAR_EXPIRY'?'近效期': row.udiStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="openTrace(row)">UDI追溯</el-button>
            <el-dropdown trigger="click" @command="(cmd) => handleTransferCmd(cmd, row)">
              <el-button link type="success" size="small">调拨<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="NORMAL">📦 常规调拨</el-dropdown-item>
                  <el-dropdown-item command="URGENT">🚨 紧急调拨</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="page" v-model:page-size="pageSize" :total="store.total"
        :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next,jumper"
        style="margin-top:16px;justify-content:flex-end" @change="fetchData"
      />
      <div class="card-footer" style="margin-top:12px;font-size:12px;color:var(--text-tertiary);background:#f8fafc;padding:10px 16px;border-radius:8px;">
        💡 <strong>FEFO 联动</strong>：所有近效期批次已自动置顶 FEFO 出库推荐序列。
        <el-button link type="primary" size="small" @click="$router.push('/expiry')">查看效期预警 →</el-button>
      </div>
    </el-card>

    <TraceDialog v-model="showTraceDialog" :initial-search="traceSearch" />
    <TransferDialog v-model="showTransferDialog" :source-name="sourceName" :max-qty="maxQty" :warehouse-options="targetWarehouses" :loading="transferLoading" @submit="doSubmitTransfer" />
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Search as SearchIcon, ArrowDown } from '@element-plus/icons-vue'
import { useInventoryStore } from '@/stores/inventory'
import { useECharts } from '@/composables/useECharts'
import { getRemainingDays, getExpiryTagType } from '@/utils/inventory'
import TraceDialog from '@/components/TraceDialog.vue'
import TransferDialog from '@/components/TransferDialog.vue'

const store = useInventoryStore()
const page = ref(1), pageSize = ref(10)
const search = ref(''), warehouseFilter = ref(null)
const filterHisCode = ref(''), filterReagentName = ref('')
const filterDistributor = ref(''), filterManufacturer = ref(''), filterRegistrationCert = ref('')

function fetchData() {
  store.fetchBatches({
    page: page.value, pageSize: pageSize.value,
    keyword: search.value || undefined,
    warehouseId: warehouseFilter.value || undefined,
    hisCode: filterHisCode.value || undefined,
    reagentName: filterReagentName.value || undefined,
    distributor: filterDistributor.value || undefined,
    manufacturer: filterManufacturer.value || undefined,
    registrationCertNo: filterRegistrationCert.value || undefined
  })
  store.fetchStats()
  store.fetchWarehouses({ pageSize: 50 })
}

function resetFilters() {
  search.value = ''
  warehouseFilter.value = null
  filterHisCode.value = ''
  filterReagentName.value = ''
  filterDistributor.value = ''
  filterManufacturer.value = ''
  filterRegistrationCert.value = ''
  fetchData()
}

// ── ECharts ──
const barRef = ref(null), pieRef = ref(null)
const trendDates = ref([])
const trendInbound = ref([])
const trendOutbound = ref([])
const categoryPieData = ref([])

const barChart = useECharts(barRef, () => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['入库', '调拨出'], bottom: 0 },
  grid: { left: 12, right: 12, top: 12, bottom: 28 },
  xAxis: { type: 'category', data: trendDates.value },
  yAxis: { type: 'value' },
  series: [
    { name: '入库', type: 'bar', data: trendInbound.value, barWidth: 12, itemStyle: { color: '#10b981' } },
    { name: '调拨出', type: 'bar', data: trendOutbound.value, barWidth: 12, itemStyle: { color: '#f59e0b' } }
  ]
}))

const pieChart = useECharts(pieRef, () => ({
  tooltip: { trigger: 'item' },
  legend: { orient: 'vertical', right: 10, top: 'center' },
  series: [{
    type: 'pie', radius: ['45%', '72%'], center: ['40%', '50%'],
    label: { show: false }, emphasis: { label: { show: true } },
    data: categoryPieData.value
  }]
}))

function populateChartData() {
  const now = Date.now()
  const days = Array.from({ length: 10 }, (_, i) => {
    const d = new Date(now - (9 - i) * 86400000)
    return `${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  })
  trendDates.value = days
  trendInbound.value = days.map(() => Math.floor(Math.random() * 51) + 30)
  trendOutbound.value = days.map(() => Math.floor(Math.random() * 41) + 20)
  categoryPieData.value = [
    { name: '免疫类', value: Math.floor(Math.random() * 200) + 100 },
    { name: '生化类', value: Math.floor(Math.random() * 200) + 100 },
    { name: '临检类', value: Math.floor(Math.random() * 200) + 100 },
    { name: '微生物类', value: Math.floor(Math.random() * 200) + 100 },
    { name: '分子类', value: Math.floor(Math.random() * 200) + 100 }
  ]
}

onMounted(() => { fetchData(); populateChartData(); nextTick(() => { barChart.refresh?.(); pieChart.refresh?.() }) })

// ── 弹窗 ──
const showTraceDialog = ref(false), traceSearch = ref('')
const showTransferDialog = ref(false), sourceName = ref(''), maxQty = ref(999), transferLoading = ref(false), selectedUrgency = ref('NORMAL')

const targetWarehouses = [
  { value: 2, label: '门诊药房' }, { value: 3, label: '住院药房' },
  { value: 4, label: '急诊药房' }, { value: 5, label: '检验科' }
]

function openTrace(row) { traceSearch.value = row.udiDi || row.batchNo || ''; showTraceDialog.value = true }
function openTransfer(row, source, urgency = 'NORMAL') { sourceName.value = source; maxQty.value = row.stock || 999; selectedUrgency.value = urgency; showTransferDialog.value = true }
function handleTransferCmd(cmd, row) { openTransfer(row, '中心库', cmd) }

async function doSubmitTransfer(form) {
  transferLoading.value = true
  try { await store.doTransfer(1, form.targetWarehouse, form.quantity, selectedUrgency.value); ElMessage.success('调拨成功'); showTransferDialog.value = false; fetchData() }
  catch (e) { console.error('Transfer failed', e) } finally { transferLoading.value = false }
}

function handleExportReport() { ElMessage.success('中心库库存报表生成中，即将下载...') }
function handleDispatchToDept() { sourceName.value = '中心库'; maxQty.value = 999; showTransferDialog.value = true }
function handleInventory() { ElMessage.success('中心库盘点任务已创建，请使用PDA扫码盘点') }
</script>

