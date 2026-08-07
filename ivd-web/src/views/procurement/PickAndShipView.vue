<!--
  PickAndShipView.vue — 拣货发货（独立功能节点 · Tab 分离版）
  库管员对申领单执行拣货出库 → 科室库收货

  Tab 分离规则：
    - 进行中：草稿 + 待审批 + 已审批 + 拣货中 + 部分完成
    - 已完成：FULFILLED
    - 缺货待采：SHORTAGE_PENDING
-->
<template>
  <div class="page-container">
    <div class="page-title">
      <h2><span class="title-icon" style="background:#ecfdf5;color:#10b981;">📦</span>拣货发货</h2>
      <span class="sub-tag">中心库 → 科室库 · 申领单全流程管理</span>
    </div>

    <!-- Tab 页签 -->
    <el-tabs v-model="activeTab" @tab-change="onTabChange" style="margin-bottom:16px">
      <el-tab-pane label="进行中" name="active">
        <template #label>
          <span>进行中<el-badge :value="stats.active" :max="999" style="margin-left:6px" /></span>
        </template>
      </el-tab-pane>
      <el-tab-pane label="已完成" name="fulfilled">
        <template #label>
          <span>已完成<el-badge :value="stats.fulfilled" :max="999" style="margin-left:6px" /></span>
        </template>
      </el-tab-pane>
      <el-tab-pane label="缺货待采" name="shortage">
        <template #label>
          <span>缺货待采<el-badge :value="stats.shortage" :max="999" style="margin-left:6px" /></span>
        </template>
      </el-tab-pane>
    </el-tabs>

    <!-- 统计卡片（按当前 Tab 动态） -->
    <el-row :gutter="16" class="stat-cards">
      <template v-if="activeTab === 'active'">
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card stat-card--all" @click="subFilter='ALL';fetchData()">
            <el-statistic title="全部" :value="stats.active || 0" />
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card stat-card--draft" @click="subFilter='DRAFT';fetchData()">
            <el-statistic title="草稿" :value="statsByStatus.DRAFT || 0" />
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card stat-card--warning" @click="subFilter='PENDING_REVIEW';fetchData()">
            <el-statistic title="待审批" :value="statsByStatus.PENDING_REVIEW || 0" />
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card stat-card--info" @click="subFilter='PICKABLE';fetchData()">
            <el-statistic title="待拣货/拣货中" :value="statsByStatus.PICKABLE || 0" />
          </el-card>
        </el-col>
      </template>
      <template v-else-if="activeTab === 'shortage'">
        <el-col :span="12">
          <el-card shadow="hover" class="stat-card stat-card--danger">
            <el-statistic title="缺货待采" :value="stats.shortage || 0" />
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="hover" class="stat-card stat-card--success" @click="$router.push('/shortage')">
            <el-statistic title="跳转缺货单管理" value="→" />
          </el-card>
        </el-col>
      </template>
      <template v-else>
        <el-col :span="24">
          <el-card shadow="hover" class="stat-card stat-card--success">
            <el-statistic title="已完成申领" :value="stats.fulfilled || 0" />
          </el-card>
        </el-col>
      </template>
    </el-row>

    <!-- 工具栏 -->
    <el-card shadow="never" style="margin-bottom:16px">
      <div style="display:flex;align-items:center;gap:12px;flex-wrap:wrap;">
        <el-input v-model="search" placeholder="搜索申领单号/科室" :prefix-icon="Search" clearable style="width:280px" @keyup.enter="fetchData" />
        <el-select v-if="activeTab === 'active'" v-model="subFilter" placeholder="全部状态" clearable style="width:150px" @change="fetchData">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="待审批" value="PENDING_REVIEW" />
          <el-option label="已审批" value="REVIEWED" />
          <el-option label="拣货中" value="PICKING" />
          <el-option label="部分完成" value="PARTIAL_FULFILLED" />
          <el-option label="全部可拣货" value="PICKABLE" />
        </el-select>
        <el-button :icon="Refresh" @click="fetchData">刷新</el-button>
      </div>
    </el-card>

    <!-- 进行中 Tab — 申领单表格 -->
    <el-card v-if="activeTab !== 'shortage'" shadow="never">
      <el-table :data="requisitions" stripe v-loading="loading" style="width:100%" row-key="id" @expand-change="handleExpandChange">
        <el-table-column type="expand">
          <template #default="{row}">
            <div style="padding:12px 20px">
              <div style="font-size:13px;font-weight:600;color:var(--text-secondary);margin-bottom:8px">
                申领试剂清单 — 共 {{ (expandItems[row.id] || []).length }} 项
              </div>
              <el-table :data="expandItems[row.id] || []" size="small" border v-loading="expandLoading[row.id]" empty-text="暂无试剂明细">
                <el-table-column prop="reagentName" label="试剂名称" min-width="130" show-overflow-tooltip />
                <el-table-column prop="genericName" label="通用名称" min-width="130" show-overflow-tooltip />
                <el-table-column prop="specification" label="规格型号" min-width="90" show-overflow-tooltip />
                <el-table-column prop="manufacturer" label="厂家" min-width="110" show-overflow-tooltip />
                <el-table-column prop="unit" label="单位" width="55" />
                <el-table-column prop="unitPrice" label="单价" width="85" align="right">
                  <template #default="{row:r}">{{ r.unitPrice != null ? '¥' + Number(r.unitPrice).toFixed(2) : '-' }}</template>
                </el-table-column>
                <el-table-column prop="qtyRequested" label="申请数量" width="80" align="right" />
                <el-table-column label="申请金额" width="90" align="right">
                  <template #default="{row:r}">{{ r.amount != null ? '¥' + Number(r.amount).toFixed(2) : '-' }}</template>
                </el-table-column>
                <el-table-column prop="qtyFulfilled" label="已发货" width="70" align="right" />
                <el-table-column label="缺货" width="60" align="right">
                  <template #default="{row:r}">
                    <span :style="{ color: (r.qtyShortage || 0) > 0 ? 'var(--ivd-danger)' : '' }">{{ r.qtyShortage || 0 }}</span>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="reqNo" label="申领单号" width="180" />
        <el-table-column prop="deptName" label="申领科室" width="140" />
        <el-table-column label="状态" width="110">
          <template #default="{row}">
            <el-tag v-if="row.status==='DRAFT'" size="small" type="info">草稿</el-tag>
            <el-tag v-else-if="row.status==='PENDING_REVIEW'" type="warning" size="small">待审批</el-tag>
            <el-tag v-else-if="row.status==='REVIEWED'" type="success" size="small">已审批</el-tag>
            <el-tag v-else-if="row.status==='PICKING'" type="primary" size="small">拣货中</el-tag>
            <el-tag v-else-if="row.status==='PARTIAL_FULFILLED'" size="small">部分发货</el-tag>
            <el-tag v-else-if="row.status==='SHIPPED'" type="warning" size="small">待签收</el-tag>
            <el-tag v-else-if="row.status==='FULFILLED'" type="success" size="small">已签收</el-tag>
            <el-tag v-else size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="紧急程度" width="85">
          <template #default="{row}">
            <el-tag v-if="row.urgency==='URGENT'" type="danger" size="small">紧急</el-tag>
            <span v-else style="color:var(--text-tertiary)">常规</span>
          </template>
        </el-table-column>
        <el-table-column label="申领量" width="70" align="right">
          <template #default="{row}">{{ row.totalQty || 0 }}</template>
        </el-table-column>
        <el-table-column label="总金额" width="95" align="right">
          <template #default="{row}">¥{{ row.totalAmount || 0 }}</template>
        </el-table-column>
        <el-table-column label="已发货" width="70" align="right">
          <template #default="{row}">{{ row.shippedQty || 0 }}</template>
        </el-table-column>
        <el-table-column prop="applicantName" label="申请人" width="85" />
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="申领时间" width="100" />
        <!-- 操作列：按状态动态展示 -->
        <el-table-column label="操作" min-width="220" fixed="right">
          <template #default="{row}">
            <!-- DRAFT: 提交审批 + 作废 -->
            <template v-if="row.status === 'DRAFT'">
              <el-button type="primary" size="small" @click="doSubmit(row)">提交审批</el-button>
              <el-button type="danger" size="small" @click="doCancel(row)">作废</el-button>
            </template>
            <!-- PENDING_REVIEW: 审批通过 + 驳回 -->
            <template v-else-if="row.status === 'PENDING_REVIEW'">
              <el-button type="success" size="small" @click="doApprove(row)">审批通过</el-button>
              <el-button type="warning" size="small" @click="doReject(row)">驳回</el-button>
            </template>
            <!-- REVIEWED / PICKING / PARTIAL_FULFILLED / SHIPPED: 拣货 + 发货 -->
            <template v-else-if="PICKABLE_STATUSES.includes(row.status)">
              <el-button type="primary" size="small" :icon="Goods" @click="openPickDialog(row, 'pick')">拣货</el-button>
              <el-button type="success" size="small" :icon="Goods" @click="openPickDialog(row, 'ship')">发货</el-button>
            </template>
            <!-- FULFILLED / CANCELLED: 只读 -->
            <span v-else style="font-size:12px;color:var(--text-tertiary)">—</span>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top:16px;display:flex;justify-content:flex-end">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          layout="total, prev, pager, next"
          @update:current-page="fetchData"
        />
      </div>
    </el-card>

    <!-- 缺货待采 Tab — 简化表格 -->
    <el-card v-else shadow="never">
      <el-table :data="requisitions" stripe v-loading="loading" style="width:100%">
        <el-table-column prop="reqNo" label="申领单号" width="180" />
        <el-table-column prop="deptName" label="申领科室" width="140" />
        <el-table-column label="状态" width="100">
          <template #default>
            <el-tag type="danger" size="small">缺货待采</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="缺货量" width="80" align="right">
          <template #default="{row}"><span style="color:var(--ivd-danger);font-weight:600">{{ row.shortageQty || 0 }}</span></template>
        </el-table-column>
        <el-table-column label="总金额" width="95" align="right">
          <template #default="{row}">¥{{ row.totalAmount || 0 }}</template>
        </el-table-column>
        <el-table-column prop="applicantName" label="申请人" width="85" />
        <el-table-column prop="createdAt" label="申领时间" width="100" />
        <el-table-column label="操作" width="140">
          <template #default>
            <el-button type="danger" size="small" @click="$router.push('/shortage')">查看缺货单</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 拣货发货弹窗 -->
    <PickAndShipDialog
      v-model="dialogVisible"
      :requisition="currentReq"
      @submit="handlePickSubmit"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Goods } from '@element-plus/icons-vue'
import { getRequisitionList, getRequisitionItems, submitRequisition, cancelRequisition } from '@/api/procurement'
import { useProcurementStore } from '@/stores/procurement'
import PickAndShipDialog from '@/components/PickAndShipDialog.vue'

const router = useRouter()
const store = useProcurementStore()

// ── Tab ──
const activeTab = ref('active')
const subFilter = ref('')

// 可拣货的状态集合（含待签收，可继续补充拣货）
const PICKABLE_STATUSES = ['REVIEWED', 'PICKING', 'PARTIAL_FULFILLED', 'SHIPPED']
// 进行中 Tab 的状态集合
const ACTIVE_STATUSES = ['DRAFT', 'PENDING_REVIEW', 'REVIEWED', 'PICKING', 'PARTIAL_FULFILLED', 'SHIPPED']

// ── 列表数据 ──
const loading = ref(false)
const search = ref('')
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const requisitions = ref([])

// ── 行展开 ──
const expandItems = ref({})
const expandLoading = ref({})

// ── 统计 ──
const stats = reactive({ active: 0, fulfilled: 0, shortage: 0 })
const statsByStatus = reactive({ DRAFT: 0, PENDING_REVIEW: 0, PICKABLE: 0 })

const dialogVisible = ref(false)
const currentReq = ref({})

// ── 数据加载 ──
async function fetchData() {
  loading.value = true
  try {
    const params = { page: page.value, pageSize: pageSize.value }
    if (search.value) params.keyword = search.value

    // 按 Tab 确定后端筛选状态
    if (activeTab.value === 'fulfilled') {
      params.status = 'FULFILLED'
    } else if (activeTab.value === 'shortage') {
      params.status = 'SHORTAGE_PENDING'
    }
    // active tab: 不传 status，后端返回全部，前端再过滤

    const result = await getRequisitionList(params)
    const all = (result && result.records) ? result.records : []
    total.value = (result && result.total) || 0

    // 前端按 Tab + 子筛选过滤
    if (activeTab.value === 'active') {
      const subset = all.filter(r => ACTIVE_STATUSES.includes(r.status))
      // 子筛选
      if (subFilter.value === 'PICKABLE') {
        requisitions.value = subset.filter(r => PICKABLE_STATUSES.includes(r.status))
      } else if (subFilter.value) {
        requisitions.value = subset.filter(r => r.status === subFilter.value)
      } else {
        requisitions.value = subset
      }
    } else {
      requisitions.value = all
    }

    // 统计（基于当页数据）
    stats.active = all.filter(r => ACTIVE_STATUSES.includes(r.status)).length
    stats.fulfilled = all.filter(r => r.status === 'FULFILLED').length
    stats.shortage = all.filter(r => r.status === 'SHORTAGE_PENDING').length
    statsByStatus.DRAFT = all.filter(r => r.status === 'DRAFT').length
    statsByStatus.PENDING_REVIEW = all.filter(r => r.status === 'PENDING_REVIEW').length
    statsByStatus.PICKABLE = all.filter(r => PICKABLE_STATUSES.includes(r.status)).length
  } catch (e) {
    console.error('获取申领列表失败:', e)
  } finally {
    loading.value = false
  }
}

function onTabChange() {
  subFilter.value = ''
  page.value = 1
  fetchData()
}

// 搜索防抖
let searchTimer = null
watch(search, () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => { page.value = 1; fetchData() }, 300)
})

// ── 行展开 ──
async function handleExpandChange(row) {
  if (!row) return
  if (expandItems.value[row.id] && expandItems.value[row.id].length) return
  expandLoading.value = { ...expandLoading.value, [row.id]: true }
  try {
    const items = await getRequisitionItems(row.id)
    expandItems.value = { ...expandItems.value, [row.id]: items || [] }
  } catch (e) {
    console.error('加载申领明细失败:', e)
    expandItems.value = { ...expandItems.value, [row.id]: [] }
  } finally {
    expandLoading.value = { ...expandLoading.value, [row.id]: false }
  }
}

// ── DRAFT 操作 ──
async function doSubmit(row) {
  try {
    await ElMessageBox.confirm(`确认提交申领单「${row.reqNo}」进入审批流程？`, '提交审批', { type: 'warning' })
    await submitRequisition(row.id)
    ElMessage.success('已提交审批')
    fetchData()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error('提交失败: ' + (e?.message || e || '未知错误'))
  }
}

async function doCancel(row) {
  let reason = ''
  try {
    const result = await ElMessageBox.prompt('请输入作废原因', '作废申领单', {
      confirmButtonText: '确认作废',
      cancelButtonText: '取消',
      inputPlaceholder: '作废原因（选填）',
      inputValue: ''
    })
    reason = result.value || ''
  } catch {
    return // 用户取消或关闭
  }
  try {
    await cancelRequisition(row.id, reason || '用户主动作废')
    ElMessage.success('申领单已作废')
    fetchData()
  } catch (e) {
    ElMessage.error('作废失败: ' + (e?.message || e || '未知错误'))
  }
}

// ── PENDING_REVIEW 操作 ──
async function doApprove(row) {
  try {
    await ElMessageBox.confirm(`确认审批通过申领单「${row.reqNo}」？审批通过后即可拣货发货。`, '审批通过', { type: 'warning' })
    await store.doReviewRequisition(row.id, 'APPROVE')
    ElMessage.success('审批通过')
    fetchData()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error('审批失败: ' + (e?.message || e || '未知错误'))
  }
}

async function doReject(row) {
  try {
    await ElMessageBox.confirm(`确认驳回申领单「${row.reqNo}」？驳回后将退回草稿状态。`, '驳回', { type: 'warning' })
    await store.doReviewRequisition(row.id, 'REJECT')
    ElMessage.success('已驳回')
    fetchData()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error('驳回失败: ' + (e?.message || e || '未知错误'))
  }
}

// ── 拣货发货 ──
async function openPickDialog(row, mode = 'pick') {
  try {
    const items = await getRequisitionItems(row.id)
    currentReq.value = {
      id: row.id,
      reqNo: row.reqNo,
      deptName: row.deptName,
      status: row.status,
      fromDeptId: row.fromDeptId,
      toWarehouseId: row.toWarehouseId,
      mode,
      items: (items || []).map(item => ({
        id: item.id,
        reagentId: item.reagentId,
        reagentName: item.reagentName,
        genericName: item.genericName,
        specification: item.specification,
        manufacturer: item.manufacturer,
        unit: item.unit,
        unitPrice: item.unitPrice,
        amount: item.amount,
        qtyRequested: item.qtyRequested,
        qtyFulfilled: item.qtyFulfilled || 0
      }))
    }
    dialogVisible.value = true
  } catch (e) {
    console.error('获取申领明细失败:', e)
  }
}

async function handlePickSubmit(data) {
  try {
    await store.doPickAndShip(currentReq.value.id, data)
    dialogVisible.value = false
    fetchData()
  } catch (e) {
    ElMessage.error('拣货发货失败: ' + (e.message || '未知错误'))
  }
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.page-container { max-width: 1300px; margin: 0 auto; padding: 20px; }
.page-title { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.page-title h2 { font-size: 18px; font-weight: 600; margin: 0; display: flex; align-items: center; gap: 8px; }
.title-icon { width: 36px; height: 36px; border-radius: 10px; display: inline-flex; align-items: center; justify-content: center; font-size: 18px; }
.sub-tag { font-size: 12px; color: var(--text-tertiary); background: var(--bg-hover); padding: 2px 10px; border-radius: 4px; }
.stat-cards { margin-bottom: 16px; }
.stat-card { cursor: pointer; transition: transform 0.2s; }
.stat-card:hover { transform: translateY(-2px); }
.stat-card--warning :deep(.el-statistic__number) { color: #f59e0b; }
.stat-card--info :deep(.el-statistic__number) { color: #3b82f6; }
.stat-card--success :deep(.el-statistic__number) { color: #10b981; }
.stat-card--danger :deep(.el-statistic__number) { color: #ef4444; }
.stat-card--draft :deep(.el-statistic__number) { color: #6b7280; }
.stat-card--all :deep(.el-statistic__number) { color: #1f2937; }
</style>
