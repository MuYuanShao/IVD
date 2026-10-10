<!--
  DashboardView.vue — 主任驾驶舱
  对照效果图 page-dashboard (line 699-804)
  绿色品牌主题 #059669，侧边栏深色渐变
-->
<template>
  <div class="page-container">
    <!-- 4 统计卡片（可点击跳转） -->
    <el-row :gutter="16" class="stat-cards">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-card--green" @click="$router.push('/central')">
          <div class="stat-top">
            <div class="stat-left">
              <div class="stat-label">在库试剂量</div>
              <div class="stat-value">{{ dashboard.stockCount || 0 }}<span class="unit">盒</span></div>
              <div v-if="dashboard.stockChangePct != null" class="stat-change" :class="dashboard.stockChangeUp ? 'up' : 'down'">
                {{ dashboard.stockChangeUp ? '↑' : '↓' }} {{ dashboard.stockChangePct || 0 }}% 较上月
              </div>
            </div>
            <div class="stat-right-col">
              <div class="stat-icon" style="background:#ecfdf5;color:#059669;">
                <el-icon :size="28"><Box /></el-icon>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-card--blue" @click="$router.push('/consumption')">
          <div class="stat-top">
            <div class="stat-left">
              <div class="stat-label">今日消耗核销</div>
              <div class="stat-value">{{ dashboard.consumeToday || 0 }}<span class="unit">盒</span></div>
              <div v-if="dashboard.consumeChangePct != null" class="stat-change" :class="dashboard.consumeChangeUp ? 'up' : 'down'">
                {{ dashboard.consumeChangeUp ? '↑' : '↓' }} {{ dashboard.consumeChangePct || 0 }}% 较昨日
              </div>
            </div>
            <div class="stat-right-col">
              <div class="stat-icon" style="background:#eff6ff;color:#3b82f6;">
                <el-icon :size="28"><DataAnalysis /></el-icon>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-card--orange" @click="$router.push('/audit')">
          <div class="stat-top">
            <div class="stat-left">
              <div class="stat-label">待审核结果</div>
              <div class="stat-value">{{ dashboard.pendingAudit || 0 }}<span class="unit">条</span></div>
              <div class="stat-change down">需要立即处理</div>
            </div>
            <div class="stat-right-col">
              <div class="stat-icon" style="background:#fffbeb;color:#f59e0b;">
                <el-icon :size="28"><WarningFilled /></el-icon>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-card--red" @click="switchToExpiryTab">
          <div class="stat-top">
            <div class="stat-left">
              <div class="stat-label">近效期30天批次</div>
              <div class="stat-value">{{ dashboard.expiringBatches || 0 }}<span class="unit">批</span></div>
              <div class="stat-change down">↓ 需关注</div>
            </div>
            <div class="stat-right-col">
              <div class="stat-icon" style="background:#fef2f2;color:#ef4444;">
                <el-icon :size="28"><Clock /></el-icon>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区：库存趋势 + 科室消耗占比 -->
    <el-row :gutter="16" style="margin-bottom:16px">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <div class="card-header-row">
              <span class="card-title"><span class="title-icon" style="background:#eff6ff;color:#3b82f6;">📈</span>近30天库存趋势</span>
              <span class="card-subtitle">单位：盒</span>
            </div>
          </template>
          <div ref="trendChartRef" style="height:280px"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <div class="card-header-row">
              <span class="card-title"><span class="title-icon" style="background:#f5f3ff;color:#8b5cf6;">🍩</span>科室消耗占比</span>
              <span class="card-subtitle">本月</span>
            </div>
          </template>
          <div ref="deptChartRef" style="height:280px"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 底部双列：UDI 操作流水 + 审核概览 -->
    <el-row :gutter="16">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <div class="card-header-row">
              <span class="card-title"><span class="title-icon" style="background:#ecfdf5;color:#059669;">🕐</span>最近UDI操作流水</span>
              <span class="card-subtitle">实时滚动</span>
            </div>
          </template>
          <el-table :data="udiFlows" stripe size="small" style="width:100%" max-height="300" empty-text="暂无操作记录">
            <el-table-column prop="occurredAt" label="时间" width="90">
              <template #default="{ row }">
                <span class="mono-time">{{ formatTime(row.occurredAt) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="udiCode" label="UDI码" width="150" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="mono-text">{{ row.udiCode || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="reagentName" label="试剂" min-width="80" show-overflow-tooltip />
            <el-table-column prop="operationType" label="操作" width="90">
              <template #default="{ row }">
                <el-tag :type="operationTagType(row.operationType)" size="small">{{ row.operationTypeLabel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态变更" min-width="140">
              <template #default="{ row }">
                <span class="status-change">
                  <span class="old-status">{{ row.fromStatusLabel }}</span>
                  <span style="margin:0 4px;color:#94a3b8">→</span>
                  <el-tag :type="udiStatusTagType(row.toStatus)" size="small">{{ row.toStatusLabel }}</el-tag>
                </span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <div class="card-header-row">
              <span class="card-title"><span class="title-icon" style="background:#fffbeb;color:#f59e0b;">📊</span>今日审核概览</span>
            </div>
          </template>
          <div class="audit-overview">
            <!-- 进度条 -->
            <div class="progress-section">
              <div class="progress-item">
                <div class="progress-label-row">
                  <span>自动审核率</span>
                  <strong>{{ dashboard.autoAuditRate || 0 }}%</strong>
                </div>
                <div class="progress-bar">
                  <div class="progress-fill green" :style="{ width: (dashboard.autoAuditRate || 0) + '%' }"></div>
                </div>
              </div>
              <div class="progress-item">
                <div class="progress-label-row">
                  <span>拦截准确率</span>
                  <strong>{{ dashboard.interceptAccuracy || 0 }}%</strong>
                </div>
                <div class="progress-bar">
                  <div class="progress-fill blue" :style="{ width: (dashboard.interceptAccuracy || 0) + '%' }"></div>
                </div>
              </div>
              <div class="progress-item">
                <div class="progress-label-row">
                  <span>误拦截率</span>
                  <strong>{{ dashboard.falseInterceptRate || 0 }}%</strong>
                </div>
                <div class="progress-bar">
                  <div class="progress-fill yellow" :style="{ width: (dashboard.falseInterceptRate || 0) + '%' }"></div>
                </div>
              </div>
            </div>
            <!-- 数字瓦片 -->
            <div class="tile-row">
              <div class="tile tile--green">
                <div class="tile-num">{{ dashboard.autoReleased || 0 }}</div>
                <div class="tile-label">自动放行</div>
              </div>
              <div class="tile tile--orange">
                <div class="tile-num">{{ dashboard.warnReleased || 0 }}</div>
                <div class="tile-label">警告放行</div>
              </div>
              <div class="tile tile--red">
                <div class="tile-num">{{ dashboard.manualReview || 0 }}</div>
                <div class="tile-label">转人工</div>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { Box, DataAnalysis, WarningFilled, Clock } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { getDashboardKpi } from '@/api/analytics'

const router = useRouter()

const dashboard = reactive({
  stockCount: 0, consumeToday: 0, pendingAudit: 0, expiringBatches: 0,
  stockChangeUp: null, stockChangePct: null,
  consumeChangeUp: null, consumeChangePct: null,
  autoAuditRate: 0, interceptAccuracy: null, falseInterceptRate: null,
  autoReleased: 0, warnReleased: 0, manualReview: 0
})

const udiFlows = ref([])

const trendChartRef = ref(null)
const deptChartRef = ref(null)
let trendChart = null
let deptChart = null

onMounted(() => { fetchDashboard(); fetchUdiFlows(); nextTick(initCharts) })
onBeforeUnmount(() => { trendChart?.dispose(); deptChart?.dispose() })

async function fetchDashboard() {
  try {
    const d = await getDashboardKpi()
    if (d) Object.assign(dashboard, d)
  } catch (e) { console.error('Failed to fetch dashboard KPI:', e) }
}

function fetchUdiFlows() {
  udiFlows.value = []
}

function initCharts() {
  initTrendChart()
  initDeptChart()
}

function initTrendChart() {
  if (!trendChartRef.value) return
  trendChart = echarts.init(trendChartRef.value)
  const days = []
  const inbound = []
  const outbound = []
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { bottom: 0, textStyle: { color: '#64748b' } },
    grid: { top: 10, left: 10, right: 10, bottom: 30, containLabel: true },
    xAxis: { type: 'category', data: days, axisLine: { lineStyle: { color: '#e2e8f0' } } },
    yAxis: { type: 'value', name: '盒', splitLine: { lineStyle: { color: '#f1f5f9' } } },
    series: [
      {
        type: 'line', name: '入库', data: inbound, smooth: true, symbol: 'none',
        lineStyle: { color: '#10b981', width: 2 }, areaStyle: { color: 'rgba(16,185,129,0.08)' }
      },
      {
        type: 'line', name: '出库', data: outbound, smooth: true, symbol: 'none',
        lineStyle: { color: '#ef4444', width: 2 }, areaStyle: { color: 'rgba(239,68,68,0.06)' }
      }
    ]
  })
}

function initDeptChart() {
  if (!deptChartRef.value) return
  deptChart = echarts.init(deptChartRef.value)
  deptChart.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, textStyle: { color: '#64748b' } },
    series: [{
      type: 'pie', radius: ['55%', '78%'], center: ['50%', '45%'], avoidLabelOverlap: false,
      label: { show: true, formatter: '{b}\n{d}%', fontSize: 11 },
      emphasis: { scaleSize: 6 },
      data: []
    }]
  })
}

function switchToExpiryTab() {
  router.push('/expiry')
}

function formatTime(d) {
  if (!d) return '—'
  return new Date(d).toLocaleTimeString('zh-CN', { hour12: false })
}

function operationTagType(type) {
  const m = { SCAN_LOAD: 'info', CONSUME: 'success', TRANSFER: 'info', RECEIVE: 'info', EXPIRY_ALERT: 'warning' }
  return m[type] || 'info'
}

function udiStatusTagType(status) {
  const m = { IN_STOCK: 'success', IN_USE: 'info', CONSUMED: 'info', EXPIRY_ALERT: 'warning', TRANSFERRED: 'info', RECEIVED: 'info' }
  return m[status] || 'info'
}
</script>

<style scoped>
/* ====== 统计卡片 ====== */
.stat-card {
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
  border: 1px solid var(--border-color);
}
.stat-card:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow-brand);
}
.stat-card--green { border-left: 4px solid #10b981; }
.stat-card--blue { border-left: 4px solid #3b82f6; }
.stat-card--orange { border-left: 4px solid #f59e0b; }
.stat-card--red { border-left: 4px solid #ef4444; }

.stat-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}
.stat-left { flex: 1; min-width: 0; }
.stat-right-col {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
  flex-shrink: 0;
  margin-left: 10px;
}
/* sparkline 迷你趋势图 */
.sparkline {
  width: 64px;
  height: 28px;
  opacity: 0.75;
}
.stat-label { font-size: 13px; color: var(--text-secondary); margin-bottom: 4px; }
.stat-value {
  font-size: 28px; font-weight: 700; color: var(--text-primary);
  letter-spacing: -0.5px; line-height: 1.1;
}
.stat-value .unit { font-size: 14px; color: var(--text-secondary); margin-left: 4px; font-weight: 500; }
.stat-change { font-size: 12px; margin-top: 4px; }
.stat-change.up { color: #10b981; }
.stat-change.down { color: #ef4444; }
.stat-icon {
  width: 56px; height: 56px; border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0; margin-left: 12px;
}

/* ====== 图表卡片 ====== */
.card-header-row {
  display: flex; align-items: center; justify-content: space-between;
}
.card-title {
  font-size: 14px; font-weight: 600; color: var(--text-primary);
  display: flex; align-items: center; gap: 6px;
}
.card-subtitle { font-size: 12px; color: var(--text-tertiary); }
.title-icon {
  display: inline-flex; align-items: center; justify-content: center;
  width: 28px; height: 28px; border-radius: 8px; font-size: 15px;
}

/* ====== 审核概览 ====== */
.audit-overview { padding: 0; }
.progress-section { margin-bottom: 18px; }
.progress-item { margin-bottom: 16px; }
.progress-item:last-child { margin-bottom: 0; }
.progress-label-row {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 8px; font-size: 13px; color: var(--text-secondary);
}
.progress-label-row strong { font-size: 15px; color: var(--text-primary); }
.progress-bar {
  height: 8px; border-radius: 4px; background: #f1f5f9; overflow: hidden;
}
.progress-fill { height: 100%; border-radius: 4px; transition: width 0.6s ease; }
.progress-fill.green { background: linear-gradient(90deg, #10b981, #34d399); }
.progress-fill.blue { background: linear-gradient(90deg, #3b82f6, #60a5fa); }
.progress-fill.yellow { background: linear-gradient(90deg, #f59e0b, #fbbf24); }

.tile-row { display: flex; gap: 10px; margin-top: 18px; }
.tile {
  flex: 1; text-align: center; padding: 14px 8px;
  border-radius: 10px; border: 1px solid transparent;
}
.tile--green { background: var(--ivd-primary-bg); border-color: #a7f3d0; }
.tile--orange { background: var(--ivd-warning-bg); border-color: #fde68a; }
.tile--red { background: var(--ivd-danger-bg); border-color: #fecaca; }
.tile-num {
  font-size: 26px; font-weight: 800; font-variant-numeric: tabular-nums;
}
.tile--green .tile-num { color: #065f46; }
.tile--orange .tile-num { color: #92400e; }
.tile--red .tile-num { color: #991b1b; }
.tile-label { font-size: 11px; font-weight: 600; margin-top: 2px; }
.tile--green .tile-label { color: #047857; }
.tile--orange .tile-label { color: #b45309; }
.tile--red .tile-label { color: #dc2626; }

/* ====== 表格辅助 ====== */
.mono-text { font-family: 'Consolas', 'Courier New', monospace; font-size: 12px; }
.mono-time { font-family: 'Consolas', 'Courier New', monospace; font-size: 11px; color: var(--text-secondary); }
.status-change { display: flex; align-items: center; white-space: nowrap; }
.old-status { font-size: 12px; color: var(--text-tertiary); }

:deep(.el-table th) { background: var(--bg-page); color: var(--text-secondary); font-weight: 600; font-size: 12px; }
:deep(.el-card__header) { padding: 14px 18px; border-bottom: 1px solid var(--border-light); }
</style>
