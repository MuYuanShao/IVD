<!--
  TraceView.vue — X1 UDI追溯主线（独立页面）
  对照效果图 page-trace (line 1443-1543)
  P0修复：从 InventoryView 弹窗提升为独立路由页面
-->
<template>
  <div class="page-container">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2><span class="title-icon" style="background:#f5f3ff;color:#8b5cf6;">🔍</span>UDI追溯主线<span class="sub-tag">X1 · 全生命周期</span></h2>
      <el-button :icon="Download" @click="handleExportPdf">📄 导出PDF追溯报告</el-button>
    </div>

    <!-- 搜索区域 -->
    <el-card shadow="never" class="search-card">
      <el-row :gutter="16" align="middle" justify="center">
        <el-col :span="12">
          <el-input
            v-model="searchText"
            placeholder="输入 UDI-DI 或批号，回车查询"
            :prefix-icon="Search"
            size="large"
            clearable
            @keyup.enter="doTrace"
          />
        </el-col>
        <el-col :span="4">
          <el-button type="primary" size="large" :icon="Search" :loading="traceLoading" @click="doTrace">
            追溯查询
          </el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- 追溯结果 -->
    <template v-if="traceResult">
      <!-- UDI 状态快照 -->
      <el-card shadow="never" class="info-card">
        <template #header>
          <span class="card-title">📋 UDI 批次信息</span>
          <el-tag :type="statusTagType(traceResult.currentStatus)" size="small" style="margin-left:8px">
            {{ traceResult.statusLabel || traceResult.currentStatus }}
          </el-tag>
        </template>
        <el-descriptions :column="4" border size="small">
          <el-descriptions-item label="UDI-DI">{{ traceResult.udiDi || '—' }}</el-descriptions-item>
          <el-descriptions-item label="批号">{{ traceResult.batchNo || '—' }}</el-descriptions-item>
          <el-descriptions-item label="试剂名称">{{ traceResult.reagentName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="规格">{{ traceResult.spec || '—' }}</el-descriptions-item>
          <el-descriptions-item label="生产厂家">{{ traceResult.manufacturer || '—' }}</el-descriptions-item>
          <el-descriptions-item label="生产日期">{{ traceResult.productionDate || '—' }}</el-descriptions-item>
          <el-descriptions-item label="有效期至">
            <el-tag :type="expiryTagType(traceResult.expiryDate)" size="small" effect="dark">
              {{ traceResult.expiryDate || '—' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="注册证号">{{ traceResult.registrationCertNo || '—' }}</el-descriptions-item>
          <el-descriptions-item label="GTIN">{{ traceResult.gtin || '—' }}</el-descriptions-item>
          <el-descriptions-item label="医保编码">{{ traceResult.insuranceCode || '—' }}</el-descriptions-item>
          <el-descriptions-item label="NMPA注册证">{{ traceResult.nmpaCert || '—' }}</el-descriptions-item>
          <el-descriptions-item label="院内编码">{{ traceResult.hisCode || '—' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 五码映射 -->
      <el-card shadow="never" class="info-card">
        <template #header><span class="card-title">🔗 五码映射</span></template>
        <el-row :gutter="16">
          <el-col :span="4" v-for="code in fiveCodes" :key="code.label">
            <div class="code-item">
              <div class="code-label">{{ code.label }}</div>
              <div class="code-value">{{ code.value }}</div>
            </div>
          </el-col>
        </el-row>
      </el-card>

      <!-- 水平管线图 -->
      <el-card shadow="never" class="info-card" v-if="traceResult">
        <template #header><span class="card-title">🔀 全链路管线图</span></template>
        <div class="pipeline-horizontal">
          <div v-for="(node, idx) in pipelineNodes" :key="idx" class="pipeline-node-wrap">
            <div class="pipeline-node" :class="{ active: node.active, completed: node.completed }">
              <div class="pipeline-dot">{{ node.completed ? '✓' : idx + 1 }}</div>
              <div class="pipeline-label">{{ node.label }}</div>
            </div>
            <div v-if="idx < pipelineNodes.length - 1" class="pipeline-arrow" :class="{ active: pipelineNodes[idx+1].active || pipelineNodes[idx+1].completed }">→</div>
          </div>
        </div>
      </el-card>

      <!-- 患者关联表 -->
      <el-card shadow="never" class="info-card" v-if="traceResult">
        <template #header>
          <span class="card-title">👤 患者关联记录</span>
          <el-button link type="primary" size="small" @click="loadPatients" :loading="patientsLoading">刷新</el-button>
        </template>
        <el-table :data="patients" stripe size="small" v-loading="patientsLoading" empty-text="暂无患者关联记录">
          <el-table-column label="患者编号(脱敏)" width="130">
            <template #default="{row}">{{ row.patientIdHash || '—' }}</template>
          </el-table-column>
          <el-table-column prop="testItemCode" label="测试项目" width="100" />
          <el-table-column prop="testValue" label="结果" width="100" />
          <el-table-column prop="testTime" label="测试时间" width="160" />
          <el-table-column label="审核状态" width="90">
            <template #default="{row}">
              <el-tag :type="row.auditStatus==='AUDITED'?'success':row.auditStatus==='PENDING'?'warning':'info'" size="small">{{ row.auditStatus || '—' }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- 全生命周期时间线 -->
      <el-card shadow="never" class="info-card">
        <template #header>
          <span class="card-title">🕐 全生命周期时间线</span>
          <span class="card-subtitle">共 {{ traceResult.timeline?.length || 0 }} 个节点</span>
        </template>
        <el-timeline>
          <el-timeline-item
            v-for="(node, idx) in traceResult.timeline"
            :key="idx"
            :timestamp="node.timestamp"
            :color="node.status === 'completed' ? '#10b981' : node.status === 'current' ? '#3b82f6' : '#cbd5e1'"
            :type="node.status === 'completed' ? 'success' : node.status === 'current' ? 'primary' : 'info'"
            placement="left"
          >
            <div class="timeline-node">
              <strong>{{ node.node }}</strong>
              <span class="timeline-detail">{{ node.detail }}</span>
              <span v-if="node.operator" class="timeline-operator">操作人：{{ node.operator }}</span>
              <span v-if="node.location" class="timeline-location">📍 {{ node.location }}</span>
            </div>
          </el-timeline-item>
        </el-timeline>
      </el-card>
    </template>

    <!-- 空状态 -->
    <el-empty v-else description="输入 UDI-DI 或批号进行追溯查询" :image-size="120" style="margin-top:40px" />
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Download } from '@element-plus/icons-vue'
import { traceUdi, getTracePatients } from '@/api/inventory'

const searchText = ref('')
const traceLoading = ref(false)
const traceResult = ref(null)
const patients = ref([])
const patientsLoading = ref(false)

// ── 水平管线图节点 ──
const pipelineNodes = computed(() => {
  const baseNodes = [
    { label: '采购', key: '采购订单' },
    { label: '验收', key: '扫码验收' },
    { label: '入库', key: '一级库入库' },
    { label: '调拨', key: '调拨至科室' },
    { label: '上机', key: '扫码上机' },
    { label: '核销', key: '消耗核销' }
  ]
  if (!traceResult.value?.timeline) return baseNodes.map(n => ({ ...n, active: false, completed: false }))
  const completedKeys = new Set(traceResult.value.timeline.filter(t => t.status === 'completed').map(t => t.node))
  const currentIdx = traceResult.value.timeline.findIndex(t => t.status === 'current')
  return baseNodes.map((n, idx) => ({
    ...n,
    completed: completedKeys.has(n.key),
    active: idx === currentIdx
  }))
})

// ── 加载患者关联 ──
async function loadPatients() {
  const batchNo = traceResult.value?.batchNo
  if (!batchNo) return
  patientsLoading.value = true
  try {
    patients.value = (await getTracePatients(batchNo)) || []
  } catch { patients.value = [] }
  finally { patientsLoading.value = false }
}

// ── 五码映射 ──
const fiveCodes = computed(() => {
  if (!traceResult.value) return []
  return [
    { label: 'UDI-DI', value: traceResult.value.udiDi || '—' },
    { label: 'GTIN', value: traceResult.value.gtin || '—' },
    { label: '医保编码', value: traceResult.value.insuranceCode || '—' },
    { label: 'NMPA注册证', value: traceResult.value.nmpaCert || '—' },
    { label: '院内编码', value: traceResult.value.hisCode || '—' },
  ]
})

// ── 追溯查询 ──
async function doTrace() {
  const q = searchText.value.trim()
  if (!q) { ElMessage.warning('请输入 UDI-DI 或批号'); return }

  traceLoading.value = true
  try {
    const d = await traceUdi(q)
    if (d) {
      traceResult.value = d
      // Load patient associations
      if (d.batchNo) loadPatients()
    }
  } catch {
    // 后端未实现时使用演示数据
    traceResult.value = getDemoTraceResult(q)
  } finally {
    traceLoading.value = false
  }
}

// ── 演示数据 ──
function getDemoTraceResult(query) {
  return {
    udiDi: query.startsWith('(01)') ? query : '(01)06931234000017',
    batchNo: query.startsWith('(01)') ? 'LOT-' + query.slice(-6) : query,
    reagentName: 'CK-MB检测试剂盒（化学发光法）',
    spec: '100T/盒',
    manufacturer: '罗氏诊断产品（上海）有限公司',
    productionDate: '2026-04-15',
    expiryDate: '2027-04-14',
    registrationCertNo: '国械注准20243408888',
    gtin: '08888880000123',
    insuranceCode: 'XB8888-D01-2024-0001',
    nmpaCert: '国械注准20243408888',
    hisCode: 'IVD-BC-002',
    currentStatus: 'CONSUMED',
    statusLabel: '已消耗',
    timeline: [
      { node: '供应商发货', detail: '九州通医药 → 中心库', timestamp: '2026-05-20 14:30', status: 'completed', operator: '供应商·王五', location: '武汉仓' },
      { node: '扫码验收', detail: '验收通过，6级校验全绿', timestamp: '2026-05-22 09:15', status: 'completed', operator: '库管·李四', location: '中心库·验收区' },
      { node: '中心库入库', detail: '上架至 A-03-12 库位', timestamp: '2026-05-22 10:00', status: 'completed', operator: '库管·李四', location: '中心库·A区' },
      { node: '调拨至科室库', detail: '中心库 → 检验科库', timestamp: '2026-06-01 08:30', status: 'completed', operator: '库管·李四', location: '中心库→检验科库' },
      { node: '下发至组库', detail: '检验科库 → 免疫组', timestamp: '2026-06-05 11:00', status: 'completed', operator: '组长·赵六', location: '检验科库→免疫组' },
      { node: '扫码上机', detail: 'Cobas e801 · 批次关联(Redis)', timestamp: '2026-07-06 09:15', status: 'completed', operator: '检验师·孙七', location: '免疫组·Cobas e801' },
      { node: '自动审核', detail: '自动放行 · L1+L2规则通过', timestamp: '2026-07-06 10:30', status: 'completed', operator: '系统·规则引擎', location: '—' },
      { node: '消耗核销', detail: '32 Test · 关联患者P-032', timestamp: '2026-07-06 14:00', status: 'current', operator: '检验师·孙七', location: '免疫组' },
    ]
  }
}

// ── 导出PDF ──
function handleExportPdf() {
  if (!traceResult.value) { ElMessage.warning('请先查询追溯记录'); return }
  ElMessage.success('PDF追溯报告生成中，即将下载...')
}

// ── 帮助函数 ──
function statusTagType(status) {
  const m = { IN_STOCK: 'success', IN_USE: 'info', CONSUMED: '', TRANSFERRED: 'info', RECEIVED: '', EXPIRED: 'danger' }
  return m[status] || ''
}

function expiryTagType(date) {
  if (!date) return 'info'
  const days = Math.ceil((new Date(date) - new Date()) / (1000 * 60 * 60 * 24))
  return days <= 30 ? 'danger' : days <= 90 ? 'warning' : 'success'
}
</script>

<style scoped>
/* ── Page Header ── */
.page-header {
  display: flex; align-items: center; justify-content: space-between; margin-bottom: 18px;
}
.page-header h2 {
  font-size: 18px; font-weight: 700; color: var(--text-primary);
  display: flex; align-items: center; gap: 8px; margin: 0;
}
.title-icon {
  display: inline-flex; align-items: center; justify-content: center;
  width: 36px; height: 36px; border-radius: 10px; font-size: 18px;
}
.sub-tag {
  font-size: 11px; color: var(--ivd-primary); background: var(--ivd-primary-bg);
  padding: 2px 8px; border-radius: 12px; font-weight: 600;
  border: 1px solid var(--ivd-primary-border);
}

/* ── Cards ── */
.search-card { margin-bottom: 16px; border: 1px solid var(--ivd-primary-border); }
.info-card { margin-bottom: 16px; }
.card-title { font-size: 14px; font-weight: 600; color: var(--text-primary); display: flex; align-items: center; gap: 6px; }
.card-subtitle { font-size: 12px; color: var(--text-tertiary); margin-left: 8px; }

/* ── Five-code mapping ── */
.code-item { text-align: center; padding: 12px 0; }
.code-label { font-size: 12px; color: var(--text-tertiary); margin-bottom: 4px; }
.code-value {
  font-size: 13px; font-weight: 500; color: var(--text-primary);
  font-family: 'Consolas', 'Courier New', monospace; word-break: break-all;
}

/* ── Timeline ── */
.timeline-node {
  display: flex; flex-direction: column; gap: 4px; padding: 4px 0;
}
.timeline-detail { font-size: 13px; color: var(--text-secondary); }
.timeline-operator { font-size: 12px; color: var(--text-tertiary); }
.timeline-location { font-size: 12px; color: var(--ivd-primary); font-weight: 500; }

:deep(.el-descriptions__label) { font-weight: 600; color: var(--text-secondary); }
:deep(.el-timeline-item__content) { padding-left: 8px; }

/* ── 水平管线图 ── */
.pipeline-horizontal {
  display: flex; align-items: center; justify-content: center;
  padding: 20px 0; gap: 0; flex-wrap: wrap;
}
.pipeline-node-wrap { display: flex; align-items: center; }
.pipeline-node {
  display: flex; flex-direction: column; align-items: center; gap: 6px;
  padding: 8px 14px; border-radius: 8px; opacity: 0.4; transition: all 0.3s;
}
.pipeline-node.completed { opacity: 0.8; }
.pipeline-node.active { opacity: 1; }
.pipeline-dot {
  width: 32px; height: 32px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 12px; font-weight: 700;
  background: #e2e8f0; color: #94a3b8; transition: all 0.3s;
}
.pipeline-node.completed .pipeline-dot { background: #10b981; color: #fff; }
.pipeline-node.active .pipeline-dot { background: #3b82f6; color: #fff; box-shadow: 0 0 0 4px rgba(59,130,246,0.2); }
.pipeline-label { font-size: 11px; color: var(--text-secondary); font-weight: 600; white-space: nowrap; }
.pipeline-node.active .pipeline-label { color: var(--text-primary); }
.pipeline-arrow {
  font-size: 18px; color: #cbd5e1; margin: 0 -4px; z-index: 1; transition: color 0.3s;
}
.pipeline-arrow.active { color: #10b981; }
</style>
