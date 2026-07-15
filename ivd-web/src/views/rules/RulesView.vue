<!--
  RulesView.vue — S2 统一规则引擎（独立页面）
  对照效果图 page-rules (line 1982-2013)
  P0修复：从 AuditView Tab 提升为独立路由页面
-->
<template>
  <div class="page-container">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2><span class="title-icon" style="background:#f5f3ff;color:#8b5cf6;">⚙️</span>统一规则引擎<span class="sub-tag">★ 大脑</span></h2>
      <el-button type="primary" :icon="Plus" @click="handleCreateRule">+ 新建规则</el-button>
    </div>

    <!-- L1/L2/L3 KPI 卡片 -->
    <el-row :gutter="16" class="kpi-row">
      <el-col :span="8">
        <div class="kpi-card kpi-card--info">
          <div class="kpi-label">L1 结果规则</div>
          <div class="kpi-value">{{ kpiL1 }}<span class="unit"> 条</span></div>
          <div class="kpi-desc">参考范围 / 危急值 / Delta Check</div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="kpi-card kpi-card--warn">
          <div class="kpi-label">L2 批次规则 ★</div>
          <div class="kpi-value">{{ kpiL2 }}<span class="unit"> 条</span></div>
          <div class="kpi-desc">护城河核心 · 批次感知</div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="kpi-card kpi-card--purple">
          <div class="kpi-label">L3 联动规则 ★</div>
          <div class="kpi-value">{{ kpiL3 }}<span class="unit"> 条</span></div>
          <div class="kpi-desc">跨域联动 · 证照/库存</div>
        </div>
      </el-col>
    </el-row>

    <!-- 内置规则库表格 -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header-row">
          <span class="card-title">一期内置规则库（9条规则，L1+L2+L3三层架构）</span>
          <span class="card-subtitle">优先级数字越大越先执行</span>
        </div>
      </template>
      <el-table :data="rules" stripe style="width:100%" v-loading="loading">
        <el-table-column prop="ruleId" label="规则ID" width="120">
          <template #default="{ row }"><span class="mono-text">{{ row.ruleId }}</span></template>
        </el-table-column>
        <el-table-column label="层级" width="110">
          <template #default="{ row }">
            <el-tag :type="levelTagType(row.level)" size="small" effect="dark">
              {{ row.levelLabel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="规则名称" min-width="150">
          <template #default="{ row }">
            <span :class="{ 'row-highlight-text': row.level === 'L2' }">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="condition" label="触发条件" min-width="200" show-overflow-tooltip />
        <el-table-column label="动作" width="130">
          <template #default="{ row }">
            <el-tag :type="actionTagType(row.action)" size="small" effect="dark">{{ row.actionLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="75" align="center">
          <template #default="{ row }">
            <span class="priority-num">{{ row.priority }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="scope" label="范围" width="100" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
              {{ row.enabled ? '启用' : '待启用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" :icon="Edit" @click="handleEditRule(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleToggleRule(row)">
              {{ row.enabled ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新建/编辑规则弹窗（预留） -->
    <el-dialog v-model="showRuleDialog" :title="editingRule ? '编辑规则' : '新建规则'" width="600px" top="5vh">
      <el-form ref="ruleFormRef" :model="ruleForm" label-width="100px">
        <el-form-item label="规则名称" prop="name">
          <el-input v-model="ruleForm.name" placeholder="例：近效期拦截" />
        </el-form-item>
        <el-form-item label="层级" prop="level">
          <el-select v-model="ruleForm.level" style="width:100%">
            <el-option label="L1 - 结果规则" value="L1" />
            <el-option label="L2 - 批次规则 ★" value="L2" />
            <el-option label="L3 - 联动规则 ★" value="L3" />
          </el-select>
        </el-form-item>
        <el-form-item label="触发条件" prop="condition">
          <el-input v-model="ruleForm.condition" type="textarea" :rows="2" placeholder="描述触发条件..." />
        </el-form-item>
        <el-form-item label="动作" prop="action">
          <el-select v-model="ruleForm.action" style="width:100%">
            <el-option label="WARN_RELEASE (警告放行)" value="WARN_RELEASE" />
            <el-option label="HOLD_REVIEW (转人工审核)" value="HOLD_REVIEW" />
            <el-option label="REJECT_RETEST (拒绝需复测)" value="REJECT_RETEST" />
          </el-select>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="优先级" prop="priority">
              <el-input-number v-model="ruleForm.priority" :min="1" :max="10" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="范围" prop="scope">
              <el-input v-model="ruleForm.scope" placeholder="全项目 / 全组别 / 指定批次" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="showRuleDialog = false">取消</el-button>
        <el-button type="primary" @click="doSaveRule">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit } from '@element-plus/icons-vue'
import { getRulesList, createRule, updateRule, toggleRule } from '@/api/rules'

// ── 层级映射 ──
const LAYER_MAP = { RESULT: 'L1', BATCH: 'L2', LINKAGE: 'L3' }
const LAYER_REVERSE = { L1: 'RESULT', L2: 'BATCH', L3: 'LINKAGE' }
const LAYER_LABEL = { RESULT: 'L1-结果', BATCH: 'L2-批次★', LINKAGE: 'L3-联动★' }
const ACTION_LABEL = { AUTO_RELEASE: '自动放行', WARN_RELEASE: '警告放行', HOLD_REVIEW: '转人工审核', REJECT_RETEST: '拒绝需复测' }

const loading = ref(false)

// ── API 数据 → 前端展示 ──
function mapRule(api) {
  return {
    id: api.id,
    ruleId: api.ruleId,
    level: LAYER_MAP[api.layer] || api.layer,
    levelLabel: LAYER_LABEL[api.layer] || api.layer,
    name: api.name,
    condition: api.ruleCondition || '',
    action: api.action,
    actionLabel: ACTION_LABEL[api.action] || api.action,
    priority: api.priority,
    scope: api.scope || '',
    enabled: api.enabled === 1 || api.enabled === true,
    reasonTemplate: api.reasonTemplate,
    linkTo: api.linkTo
  }
}

// ── 规则列表 ──
const rules = ref([])

async function fetchRules() {
  loading.value = true
  try {
    const data = await getRulesList()
    rules.value = (data || []).map(mapRule)
  } catch { /* error handled by request interceptor */ }
  finally { loading.value = false }
}

// ── KPI 统计（从 API 数据动态计算）──
const kpiL1 = computed(() => rules.value.filter(r => r.level === 'L1').length)
const kpiL2 = computed(() => rules.value.filter(r => r.level === 'L2').length)
const kpiL3 = computed(() => rules.value.filter(r => r.level === 'L3').length)

// ── 弹窗 ──
const showRuleDialog = ref(false)
const editingRule = ref(null)
const ruleFormRef = ref(null)
const saving = ref(false)
const ruleForm = reactive({
  name: '', level: 'L1', condition: '', action: 'WARN_RELEASE', priority: 1, scope: ''
})

function handleCreateRule() {
  editingRule.value = null
  Object.assign(ruleForm, { name: '', level: 'L1', condition: '', action: 'WARN_RELEASE', priority: 1, scope: '' })
  showRuleDialog.value = true
}

function handleEditRule(row) {
  editingRule.value = row
  Object.assign(ruleForm, {
    name: row.name, level: row.level, condition: row.condition,
    action: row.action, priority: row.priority, scope: row.scope
  })
  showRuleDialog.value = true
}

async function handleToggleRule(row) {
  const action = row.enabled ? '停用' : '启用'
  try {
    await ElMessageBox.confirm(`确认${action}规则 "${row.name}"？`, `${action}规则`, { confirmButtonText: `确认${action}`, type: row.enabled ? 'warning' : 'success' })
    await toggleRule(row.ruleId, !row.enabled)
    row.enabled = !row.enabled
    ElMessage.success(`规则已${action}`)
  } catch { /* cancelled */ }
}

async function doSaveRule() {
  if (!ruleForm.name) { ElMessage.warning('请输入规则名称'); return }
  saving.value = true
  try {
    const payload = {
      name: ruleForm.name,
      layer: LAYER_REVERSE[ruleForm.level] || 'RESULT',
      ruleCondition: ruleForm.condition,
      action: ruleForm.action,
      priority: ruleForm.priority,
      scope: ruleForm.scope
    }
    if (editingRule.value) {
      await updateRule(editingRule.value.ruleId, payload)
      ElMessage.success('规则已更新')
    } else {
      // 生成 ruleId
      const prefix = ruleForm.level === 'L1' ? 'RST' : ruleForm.level === 'L2' ? 'BATCH' : 'LNK'
      payload.ruleId = `R-${prefix}-${String(Date.now()).slice(-3)}`
      await createRule(payload)
      ElMessage.success('规则已创建')
    }
    showRuleDialog.value = false
    await fetchRules()
  } catch { /* error handled by interceptor */ }
  finally { saving.value = false }
}

onMounted(() => { fetchRules() })

// ── 帮助函数 ──
function levelTagType(level) {
  return level === 'L1' ? 'info' : level === 'L2' ? 'warning' : ''
}

function actionTagType(action) {
  return action === 'WARN_RELEASE' || action === 'AUTO_RELEASE' ? 'warning' : action === 'HOLD_REVIEW' ? 'danger' : 'danger'
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

/* ── KPI Cards ── */
.kpi-row { margin-bottom: 18px; }
.kpi-card {
  background: #fff; border-radius: var(--radius-lg); padding: 18px 20px;
  border: 1px solid var(--border-color); transition: transform 0.2s, box-shadow 0.2s;
}
.kpi-card:hover { transform: translateY(-2px); box-shadow: var(--shadow-md); }
.kpi-card--info { border-top: 3px solid #3b82f6; }
.kpi-card--warn { border-top: 3px solid #f59e0b; }
.kpi-card--purple { border-top: 3px solid #8b5cf6; }
.kpi-label { font-size: 13px; color: var(--text-secondary); margin-bottom: 4px; }
.kpi-value { font-size: 28px; font-weight: 800; color: var(--text-primary); line-height: 1.1; }
.kpi-value .unit { font-size: 14px; color: var(--text-secondary); margin-left: 3px; font-weight: 500; }
.kpi-desc { font-size: 12px; color: var(--text-tertiary); margin-top: 4px; }

/* ── Table ── */
.card-header-row {
  display: flex; align-items: center; justify-content: space-between;
}
.card-subtitle { font-size: 12px; color: var(--text-tertiary); }

.mono-text { font-family: 'Consolas', 'Courier New', monospace; font-size: 12px; }
.priority-num { font-weight: 700; font-size: 14px; }

.row-highlight-text { color: #92400e; font-weight: 600; }

:deep(.el-table th) { background: var(--bg-page); color: var(--text-secondary); font-weight: 600; font-size: 12px; }
:deep(.el-table .el-button--small) { padding: 2px 6px; }

/* ── Row highlight backgrounds ── */
:deep(.el-table__body tr:nth-child(4)) { background: #fffbeb; }
:deep(.el-table__body tr:nth-child(5)) { background: #fffbeb; }
:deep(.el-table__body tr:nth-child(6)) { background: #fffbeb; }
:deep(.el-table__body tr:nth-child(7)) { background: #fffbeb; }
:deep(.el-table__body tr:nth-child(8)) { background: #f5f3ff; }
:deep(.el-table__body tr:nth-child(9)) { background: #f5f3ff; }
</style>
