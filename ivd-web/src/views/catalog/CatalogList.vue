<!--
  CatalogList.vue — 试剂目录列表页 (S1 完整实现)

  功能：分页展示 + 多条件筛选 + 审批流 + UDI待补全
  路由：/catalog
-->
<template>
  <div class="page-container">
    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stat-cards">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <el-statistic title="试剂总数" :value="catalogStore.stats.total">
            <template #prefix><el-icon :size="24" color="#059669"><Box /></el-icon></template>
          </el-statistic>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-card--success">
          <el-statistic title="已批准" :value="catalogStore.stats.approved">
            <template #suffix><span class="status-dot" style="background:#10b981"></span></template>
          </el-statistic>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-card--warning">
          <el-statistic title="待审批" :value="catalogStore.stats.pending">
            <template #suffix><span class="status-pending-dot"></span></template>
          </el-statistic>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-card--danger">
          <el-statistic title="已驳回" :value="catalogStore.stats.rejected">
            <template #suffix><span class="status-dot" style="background:#ef4444"></span></template>
          </el-statistic>
        </el-card>
      </el-col>
    </el-row>

    <!-- 五码映射概览 -->
    <el-card class="toolbar-card five-code-card">
      <template #header>
        <div class="card-header">
          <span class="card-header-title">🔗 五码映射</span>
          <el-button link type="primary">管理映射</el-button>
        </div>
      </template>
      <el-row :gutter="24">
        <el-col :span="4" v-for="code in fiveCodes" :key="code.label">
          <div class="code-item">
            <div class="code-label">{{ code.label }}</div>
            <div class="code-desc">{{ code.desc }}</div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 工具栏 -->
    <el-card class="toolbar-card">
      <el-row :gutter="16" align="middle">
        <el-col :span="8">
          <el-input v-model="searchText" placeholder="搜索 院内编码 / 试剂名称 / UDI-DI / 注册证号 / 厂家 / 配送商" clearable />
        </el-col>
        <el-col :span="3">
          <el-select v-model="filterStatus" placeholder="审批状态" clearable>
            <el-option label="已批准" value="APPROVED" />
            <el-option label="待审批" value="PENDING" />
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已驳回" value="REJECTED" />
          </el-select>
        </el-col>
        <el-col :span="3">
          <el-select v-model="filterMaterialType" placeholder="物料类型" clearable>
            <el-option label="试剂" value="REAGENT" />
            <el-option label="校准品" value="CALIBRATOR" />
            <el-option label="质控品" value="QC_MATERIAL" />
            <el-option label="耗材" value="CONSUMABLE" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-col>
        <el-col :span="10" style="text-align:right">
          <el-button type="primary" :icon="Plus" @click="showCreateDialog = true">新增试剂</el-button>
          <el-button :icon="Upload">批量导入</el-button>
          <el-button :icon="Download">导出Excel</el-button>
        </el-col>
      </el-row>
      <!-- 组别多选胶囊 -->
      <div class="group-pills-row">
        <span class="group-pills-label">组别：</span>
        <span
          v-for="g in groups" :key="g"
          :class="['group-pill', { active: filterGroups.includes(g) }]"
          @click="toggleGroup(g)"
        >{{ g }}</span>
        <span
          v-if="filterGroups.length"
          class="group-pill group-pill--clear"
          @click="clearGroups"
        >清除</span>
      </div>
    </el-card>

    <!-- Tab 栏 -->
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="目录列表" name="list" />
      <el-tab-pane label="变更审批" name="approval" />
      <el-tab-pane label="审批历史" name="history" />
      <el-tab-pane label="⚠️ UDI 待补全" name="udi-backlog" />
    </el-tabs>

    <!-- ==================== Tab: 目录列表 ==================== -->
    <template v-if="activeTab === 'list'">
      <el-table :data="catalogStore.list" stripe v-loading="catalogStore.loading" style="width:100%"
        :row-class-name="catalogRowClass"
      >
        <el-table-column prop="hisCode" label="院内编码" width="130" />
        <el-table-column prop="genericName" label="试剂名称" min-width="160" />
        <el-table-column prop="materialTypeLabel" label="物料类型" width="90">
          <template #default="{ row }">
            <el-tag :type="materialTypeTag(row.materialType)" size="small">
              {{ row.materialTypeLabel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="manufacturer" label="厂家" width="100" />
        <el-table-column prop="distributor" label="配送商" width="100" />
        <el-table-column prop="registrationCertNo" label="注册证号" width="130" />
        <el-table-column prop="specification" label="规格" width="80" />
        <el-table-column prop="testsPerKit" label="测试数" width="70" />
        <el-table-column prop="pricePerUnit" label="单价(¥)" width="90">
          <template #default="{ row }">{{ formatMoney(row.pricePerUnit) }}</template>
        </el-table-column>
        <el-table-column label="带量类型" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="row.isVolumeBased ? 'warning' : 'info'">
              {{ row.isVolumeBased ? (row.volumeType || '—') : '非带量' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="医保支付类型" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="row.isInsurancePayment ? 'success' : 'info'">
              {{ row.isInsurancePayment ? (row.insurancePaymentType || '—') : '非医保' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审批状态" width="90">
          <template #default="{ row }">
            <span :class="statusDotClass(row.approvalStatus)"></span>
            {{ row.approvalStatusLabel }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" :icon="Edit" :disabled="row.approvalStatus === 'PENDING'" @click="handleEdit(row.id)">编辑</el-button>
            <el-button link type="primary" size="small" :icon="View" @click="handleViewDetail(row)">查看</el-button>
            <el-button link type="primary" size="small" @click="router.push({ path: '/change-log', query: { module: 'CATALOG', keyword: row.hisCode } })">变更记录</el-button>
            <el-button
              v-if="row.approvalStatus === 'DRAFT' || row.approvalStatus === 'REJECTED'"
              link type="warning" size="small" :icon="DocumentChecked" @click="handleSubmitApproval(row)">提交审批</el-button>
            <el-popconfirm title="确认删除该试剂？" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button link type="danger" size="small" :icon="Delete">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="catalogStore.total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top:16px; justify-content:flex-end"
        @change="fetchData"
      />
    </template>

    <!-- ==================== Tab: 变更审批 ==================== -->
    <template v-if="activeTab === 'approval'">
      <el-table :data="catalogStore.approvals" stripe v-loading="catalogStore.approvalsLoading" style="width:100%">
        <el-table-column prop="reagentId" label="试剂ID" width="80" />
        <el-table-column prop="changeType" label="变更类型" width="90">
          <template #default="{ row }">
            <el-tag :type="changeTypeTag(row.changeType)" size="small">{{ changeTypeLabel(row.changeType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="changeReason" label="变更原因" min-width="160" show-overflow-tooltip />
        <el-table-column prop="submitterName" label="申请人" width="100" />
        <el-table-column prop="submittedAt" label="提交时间" width="160" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" :icon="View" @click="handleViewApproval(row)">详情</el-button>
            <el-button link type="success" size="small" :icon="Check" @click="handleApprove(row.id)">通过</el-button>
            <el-button link type="danger" size="small" :icon="Close" @click="openRejectDialog(row.id)">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="approvalPage"
        v-model:page-size="approvalPageSize"
        :total="catalogStore.approvalsTotal"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top:16px; justify-content:flex-end"
        @change="fetchPendingApprovals"
      />
    </template>

    <!-- ==================== Tab: 审批历史 ==================== -->
    <template v-if="activeTab === 'history'">
      <el-table :data="catalogStore.historyList" stripe v-loading="catalogStore.historyLoading" style="width:100%">
        <el-table-column prop="reagentId" label="试剂ID" width="80" />
        <el-table-column prop="changeType" label="变更类型" width="90">
          <template #default="{ row }">
            <el-tag :type="changeTypeTag(row.changeType)" size="small">{{ changeTypeLabel(row.changeType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reviewStatus" label="审批结果" width="90">
          <template #default="{ row }">
            <el-tag
              :type="row.reviewStatus === 'APPROVED' ? 'success' : row.reviewStatus === 'REJECTED' ? 'danger' : 'warning'"
              size="small"
            >
              {{ reviewStatusLabel(row.reviewStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="submitterName" label="申请人" width="100" />
        <el-table-column prop="reviewerName" label="审批人" width="100" />
        <el-table-column prop="reviewedAt" label="审批时间" width="160" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleViewApproval(row)">👁️ 详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="historyPage"
        v-model:page-size="historyPageSize"
        :total="catalogStore.historyTotal"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top:16px; justify-content:flex-end"
        @change="fetchApprovalHistory"
      />
    </template>

    <!-- ==================== Tab: UDI 待补全 ==================== -->
    <template v-if="activeTab === 'udi-backlog'">
      <div style="margin-bottom:12px">
        <el-select v-model="udiImpactFilter" placeholder="影响级别" clearable @change="fetchUdiBacklog">
          <el-option label="阻断采购" value="BLOCK" />
          <el-option label="预警" value="WARN" />
        </el-select>
      </div>
      <el-table :data="catalogStore.udiBacklogList" stripe v-loading="catalogStore.udiBacklogLoading" style="width:100%">
        <el-table-column prop="reagentId" label="试剂ID" width="80" />
        <el-table-column label="物料类型" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ materialTypeLabel(row.materialType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="impactLevel" label="影响级别" width="100">
          <template #default="{ row }">
            <el-tag :type="row.impactLevel === 'BLOCK' ? 'danger' : 'warning'" size="small">
              {{ row.impactLevel === 'BLOCK' ? '阻断采购' : '预警' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="daysPending" label="待补全天数" width="110" />
        <el-table-column prop="hasOpenPo" label="在途订单" width="90">
          <template #default="{ row }">
            {{ row.hasOpenPo ? '是' : '否' }}
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="160" />
      </el-table>

      <el-pagination
        v-model:current-page="udiPage"
        v-model:page-size="udiPageSize"
        :total="catalogStore.udiBacklogTotal"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top:16px; justify-content:flex-end"
        @change="fetchUdiBacklog"
      />
    </template>

    <!-- ==================== Dialogs ==================== -->
    <CreateReagentDialog v-model="showCreateDialog" @created="onCreated" />
    <EditReagentDialog v-model="showEditDialog" :reagent-id="editingId" @updated="onUpdated" />
    <CatalogDetailDialog v-model="showCatalogDetail" :reagent-id="detailReagentId" />
    <ApprovalDetailDialog v-model="showApprovalDetail" :detail="approvalDetail" />
    <RejectReasonDialog v-model="showRejectDialog" :log-id="rejectLogId" @rejected="onRejected" />

    <!-- 提交审批弹窗 -->
    <el-dialog v-model="showSubmitApprovalDialog" title="提交审批" width="480px">
      <el-form ref="submitFormRef" :model="submitApprovalForm" :rules="submitApprovalRules" label-width="100px">
        <el-form-item label="变更原因" prop="changeReason">
          <el-input v-model="submitApprovalForm.changeReason" type="textarea" :rows="3" placeholder="请输入变更原因" />
        </el-form-item>
        <el-form-item label="申请人">
          <el-input v-model="submitApprovalForm.submitterName" placeholder="请输入申请人姓名" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showSubmitApprovalDialog = false">取 消</el-button>
        <el-button type="primary" :loading="submittingApproval" @click="doSubmitApproval">确认提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Upload, Download, Edit, View, DocumentChecked, Delete, Check, Close, Box } from '@element-plus/icons-vue'
import { useCatalogStore } from '@/stores/catalog'
import { formatMoney } from '@/utils/format'
import CreateReagentDialog from './components/CreateReagentDialog.vue'
import EditReagentDialog from './components/EditReagentDialog.vue'
import CatalogDetailDialog from './components/CatalogDetailDialog.vue'
import ApprovalDetailDialog from './components/ApprovalDetailDialog.vue'
import RejectReasonDialog from './components/RejectReasonDialog.vue'
import { useCatalogFieldOptions } from '@/composables/useCatalogFieldOptions'

const catalogStore = useCatalogStore()
const router = useRouter()
const { optionGroups, loadCatalogFieldOptions } = useCatalogFieldOptions()

// ── 五码映射数据 — 由后端 API 填充，初始为空
// TODO: 对接五码映射查询 API 后由 fetchData 统一加载
const fiveCodes = ref([])

// ── 搜索 & 筛选 ──
const searchText = ref('')
const filterGroups = ref([])  // 多选组别
const filterStatus = ref('')
const filterMaterialType = ref('')
const groups = computed(() => optionGroups.value.TEST_GROUP.map(item => item.optionValue))

function toggleGroup(g) {
  const idx = filterGroups.value.indexOf(g)
  if (idx === -1) filterGroups.value.push(g)
  else filterGroups.value.splice(idx, 1)
  fetchData()
}
function clearGroups() {
  filterGroups.value = []
  fetchData()
}

// ── 分页 ──
const page = ref(1)
const pageSize = ref(10)

// ── Tab ──
const activeTab = ref('list')

// ── 目录对话框 ──
const showCreateDialog = ref(false)
const showEditDialog = ref(false)
const editingId = ref(null)
const showCatalogDetail = ref(false)
const detailReagentId = ref(null)

// ── 提交审批 ──
const showSubmitApprovalDialog = ref(false)
const submittingApproval = ref(false)
const submitFormRef = ref(null)
const submitTargetId = ref(null)
const submitApprovalForm = reactive({ changeReason: '', submitterId: 1, submitterName: '' })
const submitApprovalRules = {
  changeReason: [{ required: true, message: '请输入变更原因', trigger: 'blur' }]
}

// ── 审批 Tab 分页 ──
const approvalPage = ref(1)
const approvalPageSize = ref(10)
const historyPage = ref(1)
const historyPageSize = ref(10)

// ── 审批详情 ──
const showApprovalDetail = ref(false)
const approvalDetail = ref({})

// ── 驳回 ──
const showRejectDialog = ref(false)
const rejectLogId = ref(null)

// ── UDI 待补全 ──
const udiPage = ref(1)
const udiPageSize = ref(10)
const udiImpactFilter = ref('')

// ── 搜索防抖 ──
let searchTimer = null
watch(searchText, () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    page.value = 1
    fetchData()
  }, 300)
})

// ── 筛选变化 ──
watch([filterGroups, filterStatus, filterMaterialType], () => {
  page.value = 1
  fetchData()
})

// ── 分页变化 ──
watch([page, pageSize], () => { fetchData() })

// ── Lifecycle ──
onMounted(() => {
  loadCatalogFieldOptions()
  fetchData()
  catalogStore.fetchStats()
})

// ── 构建查询参数 ──
function buildQuery() {
  const params = { page: page.value, pageSize: pageSize.value }
  if (searchText.value) {
    params.genericName = searchText.value
    params.hisCode = searchText.value
    params.manufacturer = searchText.value
    params.distributor = searchText.value
  }
  if (filterGroups.value.length) params.testGroups = filterGroups.value.join(',')
  if (filterStatus.value) params.approvalStatus = filterStatus.value
  if (filterMaterialType.value) params.materialType = filterMaterialType.value
  return params
}

async function fetchData() {
  await catalogStore.fetchList(buildQuery())
}

// ── Tab 切换 ──
function handleTabChange(tab) {
  switch (tab) {
    case 'approval': fetchPendingApprovals(); break
    case 'history': fetchApprovalHistory(); break
    case 'udi-backlog': fetchUdiBacklog(); break
  }
}

// ── 待审批列表 ──
async function fetchPendingApprovals() {
  await catalogStore.fetchPendingApprovals({ page: approvalPage.value, pageSize: approvalPageSize.value })
}

// ── 审批历史 ──
async function fetchApprovalHistory() {
  await catalogStore.fetchApprovalHistory({ page: historyPage.value, pageSize: historyPageSize.value })
}

// ── UDI 待补全 ──
async function fetchUdiBacklog() {
  const params = { page: udiPage.value, pageSize: udiPageSize.value }
  if (udiImpactFilter.value) params.impactLevel = udiImpactFilter.value
  await catalogStore.fetchUdiBacklog(params)
}

// ── 行高亮 ──
function catalogRowClass({ row }) {
  if (row.approvalStatus === 'PENDING') return 'row-pending'
  if (!row.udiDi && row.approvalStatus === 'APPROVED') return 'row-udi-missing'
  if (row.approvalStatus === 'REJECTED') return 'row-rejected'
  return ''
}

// ── 操作：新建 ──
function onCreated() {
  fetchData()
  catalogStore.fetchStats()
}

// ── 操作：编辑 ──
function handleEdit(id) {
  editingId.value = id
  showEditDialog.value = true
}
function onUpdated() {
  fetchData()
  catalogStore.fetchStats()
}

// ── 操作：查看详情 ──
function handleViewDetail(row) {
  detailReagentId.value = row.id
  showCatalogDetail.value = true
}

// ── 操作：删除 ──
async function handleDelete(id) {
  try {
    await catalogStore.remove(id)
    ElMessage.success('删除成功')
    fetchData()
    catalogStore.fetchStats()
  } catch {
    // 错误已由拦截器处理
  }
}

// ── 操作：提交审批 ──
function handleSubmitApproval(row) {
  submitTargetId.value = row.id
  submitApprovalForm.changeReason = ''
  submitApprovalForm.submitterName = ''
  showSubmitApprovalDialog.value = true
}
async function doSubmitApproval() {
  const valid = await submitFormRef.value.validate().catch(() => false)
  if (!valid) return
  submittingApproval.value = true
  try {
    await catalogStore.submitForApproval(submitTargetId.value, {
      changeReason: submitApprovalForm.changeReason,
      submitterId: submitApprovalForm.submitterId,
      submitterName: submitApprovalForm.submitterName
    })
    ElMessage.success('审批已提交')
    showSubmitApprovalDialog.value = false
    fetchData()
    catalogStore.fetchStats()
  } catch {
    // 错误已由拦截器处理
  } finally {
    submittingApproval.value = false
  }
}

// ── 审批操作 ──
async function handleApprove(logId) {
  try {
    await catalogStore.approve(logId, { reviewerId: 1, reviewerName: '审批人', comment: '' })
    ElMessage.success('审批已通过')
    fetchPendingApprovals()
    fetchData()
    catalogStore.fetchStats()
  } catch { /* handled */ }
}

function openRejectDialog(logId) {
  rejectLogId.value = logId
  showRejectDialog.value = true
}

function onRejected() {
  fetchPendingApprovals()
  fetchData()
  catalogStore.fetchStats()
}

function handleViewApproval(row) {
  approvalDetail.value = row
  showApprovalDetail.value = true
}

// ── 帮助函数 ──
function materialTypeTag(type) {
  const code = typeof type === 'string' ? type : type?.code || ''
  const map = {
    'REAGENT': 'primary',
    'CALIBRATOR': 'success',
    'QC_MATERIAL': 'warning',
    'CONSUMABLE': 'info',
    'OTHER': 'info'
  }
  return map[code] || 'info'
}

function materialTypeLabel(type) {
  const code = typeof type === 'string' ? type : type?.code || ''
  const map = { REAGENT: '试剂', CALIBRATOR: '校准品', QC_MATERIAL: '质控品', CONSUMABLE: '耗材', OTHER: '其他' }
  return map[code] || code || '—'
}

function statusDotClass(status) {
  if (status === 'APPROVED') return 'status-dot'
  if (status === 'PENDING') return 'status-pending-dot'
  if (status === 'REJECTED') return 'status-dot'
  return 'status-dot'
}

function changeTypeLabel(t) {
  return { CREATE: '新增', UPDATE: '编辑', DELETE: '删除', DISABLE: '停用' }[t] || t
}

function changeTypeTag(t) {
  return { CREATE: 'success', UPDATE: 'warning', DELETE: 'danger', DISABLE: 'info' }[t] || 'info'
}

function reviewStatusLabel(s) {
  return { PENDING: '待审批', APPROVED: '已通过', REJECTED: '已驳回' }[s] || s
}
</script>

<style scoped>
/* === 统计卡片 === */
.stat-card {
  border-radius: var(--radius-md);
  transition: transform 0.2s, box-shadow 0.2s;
}

.stat-card:hover {
  transform: translateY(-2px);
}

.stat-card--success {
  border-top: 3px solid var(--ivd-success);
}

.stat-card--warning {
  border-top: 3px solid var(--ivd-warning);
}

.stat-card--danger {
  border-top: 3px solid var(--ivd-danger);
}

/* === 五码映射 === */
.five-code-card {
  border: 1px solid var(--ivd-primary-border);
  background: var(--ivd-primary-bg);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-header-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.code-item {
  text-align: center;
  padding: 8px 0;
}

.code-label {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-bottom: 4px;
}

.code-desc {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
  font-family: 'Courier New', monospace;
}

/* === 组别多选胶囊 === */
.group-pills-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid var(--border-light);
}
.group-pills-label {
  font-size: 12px;
  color: var(--text-secondary);
  font-weight: 500;
  flex-shrink: 0;
}
.group-pill {
  display: inline-block;
  padding: 5px 14px;
  border: 1.5px solid var(--border-color);
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  background: #fff;
  cursor: pointer;
  transition: all .15s;
  user-select: none;
}
.group-pill:hover {
  border-color: var(--ivd-primary);
  color: var(--ivd-primary);
  background: var(--ivd-primary-bg);
}
.group-pill.active {
  border-color: var(--ivd-primary);
  background: var(--ivd-primary-bg);
  color: var(--ivd-primary-dark, #047857);
  box-shadow: 0 0 0 2px rgba(16,185,129,.15);
}
.group-pill.active::after {
  content: ' ✓';
  font-size: 11px;
}
.group-pill--clear {
  border-color: #fde68a;
  color: #92400e;
  background: #fffbeb;
}
.group-pill--clear:hover {
  border-color: #f59e0b;
  background: #fef3c7;
}

/* === 表格优化 === */
:deep(.el-table th) {
  background: var(--bg-page);
  color: var(--text-secondary);
  font-weight: 600;
}

:deep(.el-table .el-button--small) {
  padding: 2px 6px;
}

/* === 行高亮 === */
:deep(.row-pending td.el-table__cell) {
  background: #fffbeb !important;
}
:deep(.row-pending:hover td.el-table__cell) {
  background: #fef3c7 !important;
}
:deep(.row-udi-missing td.el-table__cell) {
  background: #fef2f2 !important;
}
:deep(.row-udi-missing:hover td.el-table__cell) {
  background: #fee2e2 !important;
}
:deep(.row-rejected td.el-table__cell) {
  background: #fef2f2 !important;
  opacity: .8;
}
:deep(.row-rejected:hover td.el-table__cell) {
  background: #fee2e2 !important;
}

/* === 响应式 === */
@media (max-width: 1400px) {
  .stat-cards .el-col {
    margin-bottom: 12px;
  }
}
</style>
