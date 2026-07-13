<!--
  AuditView.vue — S2 规则引擎 + S3 自动审核
  对照效果图: page-audit (line 2016-2137) + page-rules (line 1982-2015)
-->
<template>
  <div class="page-container">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="审核流水线" name="pipeline" />
      <el-tab-pane label="规则引擎" name="rules" />
    </el-tabs>

    <!-- ====== 审核流水线 ====== -->
    <template v-if="activeTab === 'pipeline'">
      <!-- 流水线可视化 -->
      <el-card shadow="never" style="margin-bottom:16px">
        <template #header><span class="card-title">审核决策流水线</span></template>
        <el-steps :active="4" finish-status="success" align-center>
          <el-step title="LIS推送" description="接收检验结果" />
          <el-step title="批次关联" description="Redis反查批次" />
          <el-step title="规则匹配" description="L1→L2→L3" />
          <el-step title="决策汇总" description="最高优先级动作" />
          <el-step title="回写LIS" description="同步审核结果" />
        </el-steps>
      </el-card>

      <el-row :gutter="16" style="margin-bottom:16px">
        <el-col :span="12">
          <el-card shadow="never">
            <template #header><span class="card-title">今日审核结果分布</span></template>
            <div ref="auditPieRef" style="height:240px"></div>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="never">
            <template #header><span class="card-title">审核统计</span></template>
            <el-row :gutter="12">
              <el-col :span="8"><div class="audit-tile"><span class="tile-num">{{ auditStats.AUTO_RELEASE }}</span><span class="tile-label">自动放行</span></div></el-col>
              <el-col :span="8"><div class="audit-tile tile--warn"><span class="tile-num">{{ auditStats.WARN_RELEASE }}</span><span class="tile-label">放行带警告</span></div></el-col>
              <el-col :span="8"><div class="audit-tile tile--hold"><span class="tile-num">{{ auditStats.HOLD_REVIEW }}</span><span class="tile-label">转人工审核</span></div></el-col>
            </el-row>
          </el-card>
        </el-col>
      </el-row>

      <!-- 审核队列列表 -->
      <el-card shadow="never" style="margin-bottom:16px">
        <template #header>
          <div style="display:flex;align-items:center;justify-content:space-between">
            <span class="card-title">待人工审核队列</span>
            <el-tag type="danger" size="small" effect="dark">{{ auditQueue.length }} 条待处理</el-tag>
          </div>
        </template>
        <div class="audit-queue">
          <div
            v-for="(item, i) in auditQueue" :key="i"
            :class="['audit-queue-item', getAuditItemClass(item.decision)]"
          >
            <div class="audit-item-header">
              <div class="audit-item-left">
                <span class="audit-item-code">{{ item.testItemCode }}</span>
                <el-tag
                  :type="item.decision === 'AUTO_RELEASE' ? 'success' : item.decision === 'WARN_RELEASE' ? 'warning' : 'danger'"
                  size="small" effect="dark" class="audit-decision-tag"
                >{{ decisionMap[item.decision] || item.decision }}</el-tag>
              </div>
              <div class="audit-item-actions">
                <el-button size="small" type="success" @click="doRelease(item)">放行</el-button>
                <el-button size="small" type="info" @click="showDetail(item)">详情</el-button>
                <el-button size="small" type="warning" @click="doRetest(item)">复测</el-button>
              </div>
            </div>
            <div class="audit-item-body">
              <span class="audit-item-meta">触发规则：<code>{{ item.triggeredRules || '—' }}</code></span>
              <span class="audit-item-meta">当前值：<strong>{{ item.testValue }}</strong></span>
              <span class="audit-item-meta">批次：<code>{{ item.batchNo || '—' }}</code></span>
            </div>
          </div>
        </div>
      </el-card>
    </template>

    <!-- ====== 规则引擎 ====== -->
    <template v-if="activeTab === 'rules'">
      <el-row :gutter="16" class="stat-cards">
        <el-col :span="8"><el-card shadow="hover" class="stat-card stat-card--info"><el-statistic title="L1 结果规则" :value="l1Count" /></el-card></el-col>
        <el-col :span="8"><el-card shadow="hover" class="stat-card stat-card--warning"><el-statistic title="L2 批次规则 ★" :value="l2Count" /></el-card></el-col>
        <el-col :span="8"><el-card shadow="hover" class="stat-card stat-card--danger"><el-statistic title="L3 联动规则" :value="l3Count" /></el-card></el-col>
      </el-row>
      <el-card shadow="never">
        <el-table :data="rules" stripe v-loading="rulesLoading" style="width:100%">
          <el-table-column prop="ruleId" label="规则编号" width="100" />
          <el-table-column prop="name" label="名称" min-width="160" />
          <el-table-column label="层级" width="80">
            <template #default="{row}"><el-tag :type="row.layer==='RESULT'?'info':row.layer==='BATCH'?'warning':'danger'" size="small">{{ row.layer==='RESULT'?'L1结果':row.layer==='BATCH'?'L2批次':'L3联动' }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="priority" label="优先级" width="70" />
          <el-table-column label="动作" width="110">
            <template #default="{row}"><el-tag size="small">{{ decisionMap[row.action] || row.action }}</el-tag></template>
          </el-table-column>
          <el-table-column label="状态" width="80">
            <template #default="{row}"><el-switch v-model="row.enabled" :active-value="1" :inactive-value="0" @change="(v) => handleToggle(row, v)" /></template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, computed, watch } from 'vue'
import * as echarts from 'echarts'
import { getRulesList, toggleRule } from '@/api/rules'
import { getAuditDecisions, manualFeedback } from '@/api/audit'
import { ElMessage } from 'element-plus'

const activeTab = ref('pipeline')
const rules = ref([]), rulesLoading = ref(false)
const decisions = ref([]), decisionsLoading = ref(false)
const auditPieRef = ref(null)

const decisionMap = { AUTO_RELEASE: '自动放行', WARN_RELEASE: '放行带警告', HOLD_REVIEW: '转人工审核', REJECT_RETEST: '拒绝需复测' }
const l1Count = computed(() => rules.value.filter(r => r.layer === 'RESULT').length)
const l2Count = computed(() => rules.value.filter(r => r.layer === 'BATCH').length)
const l3Count = computed(() => rules.value.filter(r => r.layer === 'LINKAGE').length)

// ── 审核统计（从 API 数据计算）──
const auditStats = computed(() => {
  const counts = { AUTO_RELEASE: 0, WARN_RELEASE: 0, HOLD_REVIEW: 0, REJECT_RETEST: 0 }
  decisions.value.forEach(d => {
    if (counts[d.decision] !== undefined) counts[d.decision]++
  })
  return counts
})

// ── 待人工审核队列（HOLD_REVIEW + REJECT_RETEST）──
const auditQueue = computed(() => decisions.value.filter(d =>
  d.decision === 'HOLD_REVIEW' || d.decision === 'REJECT_RETEST'
))

// ── 数据加载 ──
async function fetchDecisions() {
  decisionsLoading.value = true
  try { decisions.value = await getAuditDecisions() || [] } catch {} finally { decisionsLoading.value = false }
}

async function fetchRules() {
  rulesLoading.value = true
  try { rules.value = await getRulesList() || [] } catch {} finally { rulesLoading.value = false }
}

function initPieChart() {
  nextTick(() => {
    if (!auditPieRef.value) return
    const chart = echarts.init(auditPieRef.value)
    chart.setOption({
      tooltip: { trigger: 'item' },
      series: [{ type: 'pie', radius: '70%', data: [
        { value: auditStats.value.AUTO_RELEASE, name: '自动放行', itemStyle: { color: '#10b981' } },
        { value: auditStats.value.WARN_RELEASE, name: '放行带警告', itemStyle: { color: '#f59e0b' } },
        { value: auditStats.value.HOLD_REVIEW, name: '转人工审核', itemStyle: { color: '#ef4444' } },
        { value: auditStats.value.REJECT_RETEST, name: '拒绝需复测', itemStyle: { color: '#8b5cf6' } }
      ]}]
    })
  })
}

// ── 操作 ──
async function handleToggle(row, val) {
  try { await toggleRule(row.ruleId, val === 1); ElMessage.success(val === 1 ? '已启用' : '已禁用') } catch { row.enabled = row.enabled === 1 ? 0 : 1 }
}

function getAuditItemClass(decision) {
  if (decision === 'HOLD_REVIEW' || decision === 'REJECT_RETEST') return 'item-hold'
  if (decision === 'WARN_RELEASE') return 'item-warn'
  return 'item-pass'
}

async function doRelease(item) {
  try {
    await manualFeedback({ observationId: item.observationId, manualDecision: 'AUTO_RELEASE', auditorId: 1, note: '人工放行' })
    ElMessage.success(`已放行: ${item.observationId}`)
    await fetchDecisions()
  } catch {}
}

async function doRetest(item) {
  try {
    await manualFeedback({ observationId: item.observationId, manualDecision: 'REJECT_RETEST', auditorId: 1, note: '标记复测' })
    ElMessage.warning(`已标记复测: ${item.observationId}`)
    await fetchDecisions()
  } catch {}
}

function showDetail(item) {
  ElMessage.info(`${item.observationId} — ${item.testItemCode}: ${item.testValue} (${item.triggeredRule || '—'})`)
}

watch(activeTab, (tab) => {
  if (tab === 'rules' && rules.value.length === 0) fetchRules()
  if (tab === 'pipeline') { fetchDecisions().then(() => initPieChart()) }
})

onMounted(() => { fetchDecisions().then(() => initPieChart()) })
</script>


<style scoped>
.stat-card { border-radius: var(--radius-md); transition: transform 0.2s; }
.stat-card:hover { transform: translateY(-2px); }
.stat-card--info { border-top: 3px solid var(--ivd-info); }
.stat-card--warning { border-top: 3px solid var(--ivd-warning); }
.stat-card--danger { border-top: 3px solid var(--ivd-danger); }
.card-title { font-size: 14px; font-weight: 600; color: var(--text-primary); }
.audit-tile { text-align: center; padding: 16px 8px; border-radius: var(--radius-md); background: var(--bg-page); }
.tile--warn { background: #fffbeb; }
.tile--hold { background: #fef2f2; }
.tile-num { display: block; font-size: 28px; font-weight: 700; color: var(--text-primary); }
.tile-label { display: block; font-size: 12px; color: var(--text-secondary); margin-top: 4px; }
.audit-item-card { border-left: 4px solid var(--ivd-warning); transition: transform 0.2s; }
.audit-item-card:hover { transform: translateY(-2px); }

/* 审核队列列表 */
.audit-queue { display: flex; flex-direction: column; gap: 10px; }
.audit-queue-item {
  border: 1px solid var(--border-color);
  border-left-width: 4px;
  border-radius: var(--radius-md);
  padding: 14px 18px;
  background: #fff;
  transition: box-shadow .15s;
}
.audit-queue-item:hover { box-shadow: 0 4px 12px rgba(0,0,0,.08); }
.audit-queue-item.item-hold { border-left-color: #ef4444; }
.audit-queue-item.item-warn { border-left-color: #f59e0b; }
.audit-queue-item.item-pass { border-left-color: #10b981; }
.audit-item-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.audit-item-left { display: flex; align-items: center; gap: 10px; }
.audit-item-code { font-size: 15px; font-weight: 700; color: var(--text-primary); }
.audit-decision-tag { flex-shrink: 0; }
.audit-item-actions { display: flex; gap: 6px; flex-shrink: 0; }
.audit-item-body { display: flex; gap: 20px; flex-wrap: wrap; }
.audit-item-meta { font-size: 12px; color: var(--text-secondary); }
.audit-item-meta code {
  font-family: 'Courier New', monospace;
  background: var(--bg-page);
  padding: 1px 5px;
  border-radius: 3px;
  font-size: 11px;
}
.audit-item-meta strong { color: var(--text-primary); font-weight: 600; }
:deep(.el-table th) { background: var(--bg-page); color: var(--text-secondary); font-weight: 600; }
</style>
