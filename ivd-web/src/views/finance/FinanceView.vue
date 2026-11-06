<!--
  FinanceView.vue — 日清月结（结算消耗明细）
  取消收支平衡瀑布图，以消耗数据表格展示结算明细
-->
<template>
  <div class="page-container">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2><span class="title-icon" style="background:#eff6ff;color:#3b82f6;">📅</span>日清月结</h2>
      <span class="text-secondary">结算日期: {{ settleDate }} · 状态:
        <el-tag :type="settleStatus === '已日结' ? 'success' : 'warning'" size="small">{{ settleStatus }}</el-tag>
      </span>
      <div class="header-right">
        <el-date-picker v-model="settleDate" type="date" value-format="YYYY-MM-DD" placeholder="选择结算日期" size="small" style="width:150px" @change="onDateChange" />
        <el-button type="primary" size="small" style="margin-left:8px" @click="doDailySettle">执行日结</el-button>
        <el-button size="small" @click="doMonthlySettle">执行月结</el-button>
      </div>
    </div>

    <!-- KPI 卡片 -->
    <el-row :gutter="16" class="kpi-row">
      <el-col :span="6">
        <div class="kpi-card">
          <div class="kpi-label">消耗记录数</div>
          <div class="kpi-value">{{ totalRecords }}<span class="unit"> 条</span></div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="kpi-card kpi-card--info">
          <div class="kpi-label">有效消耗结算笔数</div>
          <div class="kpi-value" style="color:var(--ivd-danger)">{{ activeSettlementCount ?? '—' }}<span class="unit"> 笔</span></div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="kpi-card kpi-card--warn">
          <div class="kpi-label">有效消耗结算金额</div>
          <div class="kpi-value" style="color:var(--ivd-warning)">{{ formatMoney(totalAmount) }}<span v-if="totalAmount != null" class="unit"> 元</span></div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="kpi-card kpi-card--success">
          <div class="kpi-label">涉及试剂数</div>
          <div class="kpi-value" style="color:var(--ivd-success)">{{ reagentCount ?? '—' }}<span class="unit"> 种</span></div>
        </div>
      </el-col>
    </el-row>

    <!-- 过滤工具栏 -->
    <el-card shadow="never" style="margin-bottom:16px">
      <div style="display:flex;align-items:center;gap:10px;flex-wrap:wrap;">
        <el-input v-model="filterReagent" placeholder="试剂名称或院内编码" clearable maxlength="100" style="width:160px" @keyup.enter="applyFilters" @clear="applyFilters" />
        <el-select v-model="filterWarehouse" placeholder="全部库房" clearable style="width:150px" @change="applyFilters">
          <el-option v-for="w in warehouses" :key="w.id" :label="w.name" :value="w.id" />
        </el-select>
        <el-button type="primary" @click="applyFilters">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
    </el-card>

    <!-- 消耗结算明细表 -->
    <el-card shadow="never">
      <template #header>
        <span class="card-title">📊 消耗结算明细 ({{ settleDate }})</span>
      </template>
      <el-table :data="records" stripe v-loading="loading" style="width:100%" border size="small" max-height="600">
        <el-table-column prop="settlementNo" label="结算单号" width="180" show-overflow-tooltip fixed />
        <el-table-column prop="settlementId" label="结算ID" width="180" show-overflow-tooltip />
        <el-table-column prop="deptName" label="结算科室" width="100" />
        <el-table-column prop="warehouseName" label="结算库房" width="100" />
        <el-table-column prop="reagentName" label="试剂名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="genericName" label="通用名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="specification" label="规格型号" width="120" show-overflow-tooltip />
        <el-table-column prop="manufacturer" label="厂家" width="100" />
        <el-table-column prop="distributor" label="结算配送商" width="100" />
        <el-table-column prop="unitPrice" label="单价" width="80" align="right" />
        <el-table-column prop="batchUnitPrice" label="批次单价" width="90" align="right" />
        <el-table-column prop="quantity" label="结算数量" width="85" align="center" />
        <el-table-column prop="amount" label="流水消耗金额" width="120" align="right" />
        <el-table-column prop="consumerName" label="消耗人" width="90" />
        <el-table-column label="是否带量" width="85" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isVolumeBased === '是' ? 'success' : 'info'" size="small">{{ row.isVolumeBased || '—' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="registrationCertNo" label="注册证号" width="150" show-overflow-tooltip />
        <el-table-column label="是否医保支付" width="105" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isInsurancePayment === '是' ? 'success' : 'info'" size="small">{{ row.isInsurancePayment || '—' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="volumeType" label="带量类型" width="100" />
        <el-table-column prop="insurancePaymentType" label="医保支付类型" width="110" />
        <el-table-column label="是否收费" width="85" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isChargeable === '是' ? 'success' : 'info'" size="small">{{ row.isChargeable || '—' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="deviceCode" label="设备号" width="100" show-overflow-tooltip />
        <el-table-column prop="deviceName" label="设备名称" width="150" show-overflow-tooltip />
        <el-table-column prop="occurredAt" label="消耗时间" width="160" />
      </el-table>

      <el-pagination
        v-model:current-page="page" v-model:page-size="pageSize" :total="total"
        :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next,jumper"
        style="margin-top:16px;justify-content:flex-end" @change="fetchData" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { dailySettle, monthlySettle, getFinanceStats } from '@/api/finance'
import { getConsumptionDetails } from '@/api/settlement'
import { getWarehouseList } from '@/api/inventory'
import { localSettlementDate, settlementPeriod } from '@/utils/settlementDate.mjs'

const page = ref(1), pageSize = ref(20)
const records = ref([]), loading = ref(false), total = ref(0)
const settleDate = ref(localSettlementDate())
const settleStatus = ref('待日结')
const warehouses = ref([])

// Filters
const filterReagent = ref(''), filterWarehouse = ref(null)

// KPI
const totalRecords = ref(0), activeSettlementCount = ref(null), totalAmount = ref(null), reagentCount = ref(null)

onMounted(() => { fetchData(); loadWarehouses(); fetchStats() })

async function loadWarehouses() {
  try { const d = await getWarehouseList({ pageSize: 50 }); warehouses.value = d.records || [] } catch {}
}

function applyFilters() { page.value = 1; fetchData() }
function onDateChange() { applyFilters(); fetchStats() }

function resetFilters() { filterReagent.value = ''; filterWarehouse.value = null; applyFilters() }

let detailsRequest = 0
function clearDetails() {
  records.value = []; total.value = 0; totalRecords.value = 0
  activeSettlementCount.value = null; totalAmount.value = null; reagentCount.value = null
}

async function fetchData() {
  const requestId = ++detailsRequest
  const period = settlementPeriod(settleDate.value)
  if (!period) { clearDetails(); loading.value = false; return }
  loading.value = true
  try {
    const params = { page: page.value, pageSize: pageSize.value, date: period.date }
    if (filterWarehouse.value) params.warehouseId = filterWarehouse.value
    const keyword = filterReagent.value.trim()
    if (keyword) params.keyword = keyword
    const d = await getConsumptionDetails(params)
    if (requestId !== detailsRequest) return
    records.value = d.records || []
    total.value = d.total || 0

    totalRecords.value = total.value
    activeSettlementCount.value = d.summary?.activeSettlementCount ?? null
    totalAmount.value = d.summary?.activeSettlementAmount ?? null
    reagentCount.value = d.summary?.reagentCount ?? null
  } catch { if (requestId === detailsRequest) clearDetails() }
  finally { if (requestId === detailsRequest) loading.value = false }
}

async function fetchStats() {
  const period = settlementPeriod(settleDate.value)
  settleStatus.value = period ? '待日结' : '未选择日期'
  if (!period) return
  try {
    const stats = await getFinanceStats(period.date)
    if (stats && settleDate.value === period.date) settleStatus.value = (stats.totalReagents > 0) ? '已日结' : '待日结'
  } catch {}
}

function formatMoney(n) { return n == null ? '未知' : Number(n).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) }

async function doDailySettle() {
  const period = settlementPeriod(settleDate.value)
  if (!period) { ElMessage.warning('请选择有效的结算日期'); return }
  try {
    await ElMessageBox.confirm(`确认对 ${period.date} 执行日结？`, '确认日结', { type: 'info' })
    await dailySettle(period.date)
    ElMessage.success('日结完成')
    if (settleDate.value === period.date) settleStatus.value = '已日结'
    await fetchData()
  } catch { /* cancelled */ }
}

async function doMonthlySettle() {
  const period = settlementPeriod(settleDate.value)
  if (!period) { ElMessage.warning('请选择有效的结算日期'); return }
  const ym = period.yearMonth
  try {
    await ElMessageBox.confirm(`确认对 ${ym} 执行月结？`, '确认月结', { type: 'warning' })
    await monthlySettle(ym)
    ElMessage.success('月结完成')
  } catch { /* cancelled */ }
}
</script>

<style scoped>
/* ── Page Header ── */
.page-header { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; flex-wrap: wrap; }
.page-header h2 { font-size: 18px; font-weight: 700; color: var(--text-primary); display: flex; align-items: center; gap: 8px; margin: 0; }
.title-icon { display: inline-flex; align-items: center; justify-content: center; width: 36px; height: 36px; border-radius: 10px; font-size: 18px; }
.text-secondary { font-size: 13px; color: var(--text-secondary); }
.header-right { margin-left: auto; display: flex; align-items: center; }

/* ── KPI Cards ── */
.kpi-row { margin-bottom: 16px; }
.kpi-card { background: #fff; border-radius: var(--radius-lg); padding: 20px 24px; border: 1px solid var(--border-color); text-align: center; }
.kpi-card--info { border-top: 3px solid var(--ivd-info); background: linear-gradient(180deg, #eff6ff 0%, #fff 40%); }
.kpi-card--warn { border-top: 3px solid var(--ivd-warning); background: linear-gradient(180deg, #fffbeb 0%, #fff 40%); }
.kpi-card--success { border-top: 3px solid var(--ivd-success); background: linear-gradient(180deg, #f0fdf4 0%, #fff 40%); }
.kpi-label { font-size: 13px; color: var(--text-secondary); margin-bottom: 8px; }
.kpi-value { font-size: 32px; font-weight: 800; color: var(--text-primary); letter-spacing: -0.5px; }
.kpi-value .unit { font-size: 16px; color: var(--text-secondary); margin-left: 2px; }

/* ── Card ── */
.card-title { font-size: 14px; font-weight: 600; color: var(--text-primary); display: flex; align-items: center; gap: 6px; }

/* ── Table ── */
:deep(.el-table th) { background: var(--bg-page); color: var(--text-secondary); font-weight: 600; font-size: 12px; white-space: nowrap; }
:deep(.el-table td) { font-size: 12px; }
</style>
