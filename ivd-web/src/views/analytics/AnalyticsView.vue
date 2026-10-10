<!--
  AnalyticsView.vue — S1 效益分析
  对照效果图 page-efficiency (line 1896-1981)
  绿色品牌主题 #059669
-->
<template>
  <div class="page-container">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2><span class="title-icon" style="background:#ecfdf5;color:#059669;">💰</span>效益分析<span class="sub-tag">★ 核心差异化 · 成交器</span></h2>
      <div class="header-actions">
        <el-select v-model="period" size="small" style="width:130px">
          <el-option v-for="m in months" :key="m.value" :label="m.label" :value="m.value" />
        </el-select>
        <el-select v-model="groupFilter" size="small" style="width:110px;margin-left:8px">
          <el-option label="全部组别" value="" />
          <el-option label="免疫组" value="IMMUNO" />
          <el-option label="生化组" value="BIOCHEM" />
          <el-option label="临检组" value="CLINICAL" />
        </el-select>
        <el-button size="small" style="margin-left:8px" @click="handleExport">📥 导出报表</el-button>
      </div>
    </div>

    <!-- KPI 卡片 -->
    <el-row :gutter="16" class="kpi-row">
      <el-col :span="8">
        <div class="kpi-card">
          <div class="kpi-label">试剂年采购额</div>
          <div class="kpi-value">¥{{ formatLargeNum(kpi.annualPurchase) }}<span class="unit"> 万</span></div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="kpi-card kpi-card--warn">
          <div class="kpi-label">年度隐性浪费（估算）</div>
          <div class="kpi-value">¥{{ formatLargeNum(kpi.annualWaste) }}<span class="unit"> 万</span></div>
          <div class="kpi-desc">年度估算数据源尚未接入</div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="kpi-card kpi-card--danger">
          <div class="kpi-label">年化可节省金额</div>
          <div class="kpi-value">¥{{ formatLargeNum(kpi.annualSavable) }}<span class="unit"> 万</span></div>
          <div class="kpi-desc" style="color:#991b1b;">暂无可验证的年化预测</div>
        </div>
      </el-col>
    </el-row>

    <!-- 图表区 -->
    <el-row :gutter="16" style="margin-bottom:16px">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <span class="card-title"><span class="icon-sm" style="background:#eff6ff;color:#3b82f6;">📊</span>单测试成本对比（实际 vs 标称）</span>
          </template>
          <div ref="costBarRef" style="height:300px"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <span class="card-title"><span class="icon-sm" style="background:#fef2f2;color:#ef4444;">🎯</span>浪费类型占比</span>
          </template>
          <div ref="wastePieRef" style="height:300px"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 三张报表 Tab -->
    <el-tabs v-model="activeTab" class="eff-tabs">
      <el-tab-pane label="📋 报表一：单测试实际成本表" name="report1" />
      <el-tab-pane label="📊 报表二：品牌成本对比表" name="report2" />
      <el-tab-pane label="🎯 报表三：浪费点 Top10" name="report3" />
    </el-tabs>

    <!-- 报表一：单测试实际成本表 -->
    <el-card v-if="activeTab === 'report1'" shadow="never">
      <template #header>
        <span class="card-title">📋 报表一：单测试实际成本表（按试剂 × 批次）</span>
        <span class="card-subtitle">C_actual = (采购 + W_exp + W_open + W_retest + W_qc) / N_eff</span>
      </template>
      <el-table :data="costData" stripe v-loading="costLoading" style="width:100%" empty-text="暂无成本数据">
        <el-table-column prop="testItem" label="试剂" min-width="100">
          <template #default="{ row }"><strong>{{ row.testItem }}</strong></template>
        </el-table-column>
        <el-table-column prop="batchNo" label="批次" width="130" show-overflow-tooltip />
        <el-table-column label="采购成本" width="100">
          <template #default="{ row }">¥{{ formatNum(row.purchaseCost || row.reagentCost || 0) }}</template>
        </el-table-column>
        <el-table-column label="过期 W_exp" width="100">
          <template #default="{ row }">
            <span :class="{ 'text-highlight': (row.wExp || 0) > 0 }">¥{{ formatNum(row.wExp || 0) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="开瓶 W_open" width="100">
          <template #default="{ row }">
            <span :class="{ 'text-highlight': (row.wOpen || 0) > 0 }">¥{{ formatNum(row.wOpen || 0) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="复测 W_retest" width="110">
          <template #default="{ row }">¥{{ formatNum(row.wRetest || 0) }}</template>
        </el-table-column>
        <el-table-column prop="effectiveCount" label="有效次数" width="90" align="center" />
        <el-table-column label="C_actual" width="100">
          <template #default="{ row }">
            <span :class="{ 'text-highlight': row.deviation > 1.5 }">¥{{ formatNum(row.cActual || 0) }}/test</span>
          </template>
        </el-table-column>
        <el-table-column label="C_nominal" width="100">
          <template #default="{ row }">¥{{ formatNum(row.cNominal || 0) }}/test</template>
        </el-table-column>
        <el-table-column label="偏离度" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="getDeviationType(row.deviation)" size="small">{{ ((row.deviation || 1) * 100).toFixed(0) }}%</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="建议" min-width="120">
          <template #default="{ row }">{{ row.suggestion || getAutoSuggestion(row) }}</template>
        </el-table-column>
      </el-table>
      <div class="card-footer">⚠ 数据口径：标称测试数来自试剂目录，复测数据来自LIS（一期若LIS无retest_flag，该列置0并标注"未含复测"）</div>
    </el-card>

    <!-- 报表二：品牌成本对比表 -->
    <el-card v-if="activeTab === 'report2'" shadow="never">
      <template #header><span class="card-title">📊 报表二：品牌成本对比表（按项目 × 品牌）</span></template>
      <el-table :data="brandData" stripe v-loading="brandLoading" style="width:100%" empty-text="暂无品牌对比数据">
        <el-table-column prop="testItem" label="项目" width="100" />
        <el-table-column prop="brand" label="品牌" width="100" />
        <el-table-column label="C_actual" width="90">
          <template #default="{ row }">¥{{ formatNum(row.avgPrice || 0) }}</template>
        </el-table-column>
        <el-table-column label="C_nominal" width="90">
          <template #default="{ row }">¥{{ formatNum(0) }}</template>
        </el-table-column>
        <el-table-column label="月消耗金额" width="110">
          <template #default="{ row }">¥{{ formatNum(row.totalCost || 0) }}</template>
        </el-table-column>
        <el-table-column label="市占率" min-width="140">
          <template #default="{ row }">
            <span>{{ ((row.share || 0) * 100).toFixed(0) }}%</span>
            <el-progress :percentage="(row.share || 0) * 100" :stroke-width="6" :show-text="false" style="margin-top:4px" :color="row.share > 0.5 ? '#3b82f6' : '#f59e0b'" />
          </template>
        </el-table-column>
        <el-table-column label="结论" width="120">
          <template #default="{ row }">
            <el-tag :type="row.share > 0.5 ? 'success' : 'danger'" size="small">{{ row.share > 0.5 ? '维持' : '成本偏高' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 报表三：浪费点 Top10 -->
    <el-card v-if="activeTab === 'report3'" shadow="never">
      <template #header><span class="card-title">🎯 报表三：浪费点 Top10（按金额排序 + 规则建议）</span></template>
      <el-table :data="wasteData" stripe v-loading="wasteLoading" style="width:100%" empty-text="暂无浪费数据">
        <el-table-column label="#" width="50">
          <template #default="{ row }"><el-tag :type="row.rank <= 2 ? 'danger' : row.rank <= 4 ? 'warning' : 'info'" size="small">{{ row.rank }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="reagent" label="试剂" min-width="100" />
        <el-table-column label="浪费类型" width="100">
          <template #default="{ row }">
            <el-tag :type="getWasteTagType(row.wasteType)" size="small">{{ row.wasteTypeLabel || row.wasteType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="浪费金额" width="110">
          <template #default="{ row }" class="text-highlight">¥{{ formatNum(row.amount || 0) }}</template>
        </el-table-column>
        <el-table-column label="占浪费总额" width="100" align="center">
          <template #default="{ row }">{{ ((row.wasteRatio || 0) * 100).toFixed(0) }}%</template>
        </el-table-column>
        <el-table-column prop="rootCause" label="根因分析" min-width="160" show-overflow-tooltip />
        <el-table-column label="系统建议" min-width="180">
          <template #default="{ row }"><strong>{{ row.suggestion || getWasteSuggestion(row) }}</strong></template>
        </el-table-column>
      </el-table>
      <div class="card-footer">
        <div class="rule-hint">
          💡 <strong>规则引擎自动生成建议</strong>（基于 5.4 浪费点定位算法）：W_exp&gt;30%→采购过量 / W_open&gt;20%→换小包装 / W_retest&gt;15%→排查设备质控 / C偏离度&gt;1.5→全面复盘
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { getCostPerTest, getBrandComparison, getWasteTop10 } from '@/api/analytics'

const activeTab = ref('report1')
const period = ref(new Date().getFullYear() + '-' + String(new Date().getMonth() + 1).padStart(2, '0'))
const groupFilter = ref('')

const months = Array.from({ length: 12 }, (_, i) => {
  const day = new Date(new Date().getFullYear(), new Date().getMonth() - i, 1)
  const value = day.getFullYear() + '-' + String(day.getMonth() + 1).padStart(2, '0')
  return { label: value.replace('-', '年') + '月', value }
})

// Annual projections are unavailable until their authoritative sources are integrated.
const kpi = reactive({ annualPurchase: null, annualWaste: null, wasteRate: null, annualSavable: null })

function dateRange() {
  const [year, month] = period.value.split('-').map(Number)
  return [period.value + '-01', period.value + '-' + new Date(year, month, 0).getDate()]
}

// Report 1: Cost per test
const costData = ref([]), costLoading = ref(false)
// Report 2: Brand comparison
const brandData = ref([]), brandLoading = ref(false)
// Report 3: Waste top 10
const wasteData = ref([]), wasteLoading = ref(false)

// Charts
const costBarRef = ref(null), wastePieRef = ref(null)
let costChart = null, wasteChart = null

onMounted(() => { fetchAll() })
onBeforeUnmount(() => { costChart?.dispose(); wasteChart?.dispose() })

watch(activeTab, () => nextTick(renderCharts))
watch(period, () => { fetchAll() })

async function fetchAll() {
  await Promise.all([fetchCostData(), fetchBrandData(), fetchWasteData()])
  nextTick(renderCharts)
}

async function fetchCostData() {
  costLoading.value = true
  try {
    const d = await getCostPerTest(...dateRange())
    costData.value = (d?.items || []).map(item => ({
      ...item,
      deviation: item.deviation ?? (item.cNominal ? item.cActual / item.cNominal : null),
      suggestion: item.suggestion || getAutoSuggestion(item)
    }))
  } catch { costData.value = []; ElMessage.error('成本报表加载失败') }
  finally { costLoading.value = false }
}

async function fetchBrandData() {
  brandLoading.value = true
  try {
    const d = await getBrandComparison(...dateRange())
    const items = d?.brands || []
    const total = items.reduce((sum, item) => sum + Number(item.totalCost || 0), 0)
    brandData.value = items.map(item => ({ ...item, share: total ? Number(item.totalCost || 0) / total : 0 }))
  } catch { brandData.value = []; ElMessage.error('品牌报表加载失败') }
  finally { brandLoading.value = false }
}

async function fetchWasteData() {
  wasteLoading.value = true
  try {
    const d = await getWasteTop10(...dateRange())
    const items = d?.wastes || []
    const total = items.reduce((sum, item) => sum + Number(item.amount || 0), 0)
    wasteData.value = items.map(item => ({
      ...item,
      wasteRatio: total ? Number(item.amount || 0) / total : 0,
      rootCause: item.rootCause || autoRootCause(item),
      suggestion: item.suggestion || getWasteSuggestion(item)
    }))
  } catch { wasteData.value = []; ElMessage.error('浪费报表加载失败') }
  finally { wasteLoading.value = false }
}

function renderCharts() {
  renderCostChart()
  renderWasteChart()
}

function renderCostChart() {
  if (!costBarRef.value) return
  costChart?.dispose()
  costChart = echarts.init(costBarRef.value)
  const items = costData.value.slice(0, 10)
  costChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { bottom: 0 },
    grid: { top: 10, left: 8, right: 8, bottom: 30, containLabel: true },
    xAxis: { type: 'category', data: items.map(d => d.testItem) },
    yAxis: { type: 'value', name: '¥/test' },
    series: [
      { type: 'bar', name: '实际成本 C_actual', data: items.map(d => d.cActual || d.reagentCost || 0), itemStyle: { color: '#ef4444' }, barWidth: '35%' },
      { type: 'bar', name: '标称成本 C_nominal', data: items.map(d => d.cNominal || 0), itemStyle: { color: '#10b981' }, barWidth: '35%' }
    ]
  })
}

function renderWasteChart() {
  if (!wastePieRef.value) return
  wasteChart?.dispose()
  wasteChart = echarts.init(wastePieRef.value)
  wasteChart.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    series: [{
      type: 'pie', radius: ['50%', '75%'], center: ['50%', '45%'],
      label: { show: true, formatter: '{b}\n{d}%' },
      data: wasteData.value.map(item => ({ value: Number(item.amount || 0), name: item.wasteTypeLabel || item.wasteType }))
    }]
  })
}

// ── Utility ──
function formatNum(n) { return (Number(n) || 0).toFixed(2) }
function formatLargeNum(n) { return n == null ? '暂无数据' : Number(n).toFixed(0) }
function getDeviationType(d) { return d > 1.5 ? 'danger' : d > 1.3 ? 'warning' : d < 1.05 ? 'success' : 'info' }
function getWasteTagType(type) {
  return type === 'EXPIRED' ? 'danger' : type === 'OPEN_EXPIRED' ? 'warning' : 'info'
}

function getAutoSuggestion(row) {
  const d = row.deviation || 1
  if (d > 1.7) return '换小包装'
  if (d > 1.5) return '全面复盘'
  if (d > 1.3) return '降采购频次'
  if ((row.wRetest || 0) > 0) return '排查复测率'
  return '正常'
}

function autoRootCause(row) {
  if (row.wasteType === 'OPEN_EXPIRED') return '包装规格过大'
  if (row.wasteType === 'EXPIRED') return '采购频次低，单次采购量大'
  if (row.wasteType === 'RETEST') return '复测率偏高'
  return '—'
}

function getWasteSuggestion(row) {
  if (row.wasteType === 'OPEN_EXPIRED') return '换小包装'
  if (row.wasteType === 'EXPIRED') return '降采购量，提高采购频次'
  if (row.wasteType === 'RETEST') return '排查设备质控状态'
  return '—'
}

function handleExport() { ElMessage.info('导出报表功能开发中') }
</script>

<style scoped>
/* ── Page Header ── */
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.page-header h2 { font-size: 18px; font-weight: 700; color: var(--text-primary); display: flex; align-items: center; gap: 8px; margin: 0; }
.title-icon { display: inline-flex; align-items: center; justify-content: center; width: 36px; height: 36px; border-radius: 10px; font-size: 18px; }
.sub-tag { font-size: 12px; font-weight: 500; color: var(--ivd-primary); background: var(--ivd-primary-bg); padding: 2px 10px; border-radius: 20px; }
.header-actions { display: flex; align-items: center; }

/* ── KPI Cards ── */
.kpi-row { margin-bottom: 16px; }
.kpi-card { background: #fff; border-radius: var(--radius-lg); padding: 20px 24px; border: 1px solid var(--border-color); text-align: center; }
.kpi-card--warn { border-top: 3px solid var(--ivd-warning); background: linear-gradient(180deg, #fffbeb 0%, #fff 40%); }
.kpi-card--danger { border-top: 3px solid var(--ivd-danger); background: linear-gradient(180deg, #fef2f2 0%, #fff 40%); }
.kpi-label { font-size: 13px; color: var(--text-secondary); margin-bottom: 8px; }
.kpi-value { font-size: 32px; font-weight: 800; color: var(--text-primary); letter-spacing: -0.5px; }
.kpi-value .unit { font-size: 16px; color: var(--text-secondary); margin-left: 2px; }
.kpi-desc { font-size: 12px; color: var(--text-tertiary); margin-top: 6px; }

/* ── Charts ── */
.card-title { font-size: 14px; font-weight: 600; color: var(--text-primary); display: flex; align-items: center; gap: 6px; }
.card-subtitle { font-size: 12px; color: var(--text-tertiary); margin-left: 12px; }
.icon-sm { display: inline-flex; align-items: center; justify-content: center; width: 26px; height: 26px; border-radius: 6px; font-size: 14px; }

/* ── Tabs ── */
.eff-tabs { margin-bottom: 0; }

/* ── Table ── */
.text-highlight { color: var(--ivd-danger); font-weight: 600; }
.card-footer { padding: 10px 14px; font-size: 12px; color: var(--text-tertiary); background: var(--bg-page); border-radius: 0 0 var(--radius-md) var(--radius-md); border-top: 1px solid var(--border-light); }
.rule-hint { padding: 10px 14px; background: var(--ivd-info-bg); border-radius: 8px; font-size: 12px; border: 1px solid #bfdbfe; line-height: 1.6; }

:deep(.el-table th) { background: var(--bg-page); color: var(--text-secondary); font-weight: 600; font-size: 12px; }
:deep(.el-card__header) { padding: 14px 18px; }
</style>
