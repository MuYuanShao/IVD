<!--
  RequisitionList.vue — 申领管理（v2 独立路由页）
  v2 补齐：ReagentPicker 批量选择 + 统计卡点击导航 + 紧急申领入口
-->
<template>
  <div class="page-container">
    <div class="page-title">
      <h2><span class="title-icon" style="background:#eff6ff;color:#3b82f6;">📝</span>申领管理</h2>
      <span class="sub-tag">二级库 → 中心库申领</span>
    </div>

    <!-- 统计卡片（可点击导航） -->
    <el-row :gutter="16" class="stat-cards">
      <el-col :span="6"><el-card shadow="hover" class="stat-card stat-card--warning" @click="statusFilter='PENDING_REVIEW';fetchData()"><el-statistic title="待审核" :value="store.reqStats.pending || 0" /></el-card></el-col>
      <el-col :span="6"><el-card shadow="hover" class="stat-card stat-card--info" @click="statusFilter='PICKING';fetchData()"><el-statistic title="拣选中" :value="store.reqStats.picking || 0" /></el-card></el-col>
      <el-col :span="6"><el-card shadow="hover" class="stat-card stat-card--success" @click="statusFilter='FULFILLED';fetchData()"><el-statistic title="已完成" :value="store.reqStats.completed || 0" /></el-card></el-col>
      <el-col :span="6"><el-card shadow="hover" class="stat-card stat-card--danger" @click="$router.push('/shortage')"><el-statistic title="缺货待采" :value="store.reqStats.shortage || 0" /></el-card></el-col>
    </el-row>

    <!-- 工具栏 -->
    <el-card shadow="never" style="margin-bottom:16px">
      <div style="display:flex;align-items:center;gap:12px;flex-wrap:wrap;">
        <el-input v-model="search" placeholder="搜索申领单号/科室/试剂名称" :prefix-icon="Search" clearable style="width:280px" @keyup.enter="fetchData" />
        <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width:140px" @change="fetchData">
          <el-option label="待审核" value="PENDING_REVIEW" />
          <el-option label="拣选中" value="PICKING" />
          <el-option label="已完成" value="FULFILLED" />
          <el-option label="缺货待采" value="SHORTAGE_PENDING" />
        </el-select>
        <el-button type="primary" :icon="Plus" @click="openNewReqDialog">新建申领</el-button>
        <el-button :icon="Download" @click="handleExport">导出报表</el-button>
      </div>
    </el-card>

    <!-- 申领单表格 -->
    <el-card shadow="never">
      <el-table :data="store.requisitions" stripe v-loading="store.reqLoading" style="width:100%" row-key="id" @expand-change="handleExpandChange">
        <!-- 行展开：内嵌试剂清单明细 -->
        <el-table-column type="expand">
          <template #default="{row}">
            <div style="padding:12px 20px">
              <div style="font-size:13px;font-weight:600;color:var(--text-secondary);margin-bottom:8px">
                申领试剂清单 — 共 {{ (expandItems[row.id] || []).length }} 项
              </div>
              <el-table
                :data="expandItems[row.id] || []"
                size="small" border
                v-loading="expandLoading[row.id]"
                empty-text="暂无试剂明细"
              >
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
                <el-table-column prop="qtyFulfilled" label="已发货" width="70" align="right">
                  <template #default="{row:r}">
                    <span :style="{ color: (r.qtyFulfilled || 0) > 0 ? 'var(--ivd-primary)' : 'var(--text-tertiary)' }">{{ r.qtyFulfilled || 0 }}</span>
                  </template>
                </el-table-column>
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
        <el-table-column prop="deptName" label="申领科室" width="120" />
        <el-table-column label="紧急程度" width="90">
          <template #default="{row}"><el-tag v-if="row.urgency==='URGENT'" type="danger" size="small">紧急</el-tag><span v-else style="color:var(--text-tertiary)">常规</span></template>
        </el-table-column>
        <el-table-column label="申领量" width="80"><template #default="{row}">{{ row.totalQty || 0 }}</template></el-table-column>
        <el-table-column label="总金额" width="100"><template #default="{row}">¥{{ row.totalAmount || 0 }}</template></el-table-column>
        <el-table-column label="发货量" width="80"><template #default="{row}">{{ row.shippedQty || 0 }}</template></el-table-column>
        <el-table-column label="缺货量" width="80">
          <template #default="{row}"><span :style="{ color: (row.shortageQty || 0) > 0 ? 'var(--ivd-danger)' : '' }">{{ row.shortageQty || 0 }}</span></template>
        </el-table-column>
        <el-table-column prop="applicantName" label="申请人" width="90" />
        <el-table-column prop="remark" label="备注" width="120" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="日期" width="100" />
        <el-table-column label="状态" width="110">
          <template #default="{row}"><el-tag :type="getReqStatusType(row.status)" size="small">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="viewDetail(row)">详情</el-button>
            <el-button v-if="row.status==='PENDING_REVIEW'" link type="warning" size="small" @click="doReview(row.id)">审核</el-button>
            <el-button v-if="row.status==='PICKING' || row.status==='PARTIAL_SHIPPED'" link type="success" size="small" @click="openPickAndShip(row)">拣货发货</el-button>
            <el-button v-if="row.status==='SHORTAGE_PENDING'" link type="danger" size="small" @click="$router.push('/shortage')">查看缺货单</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="page" v-model:page-size="pageSize" :total="store.reqTotal"
        :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next,jumper"
        style="margin-top:16px;justify-content:flex-end" @change="fetchData"
      />
    </el-card>

    <!-- 申领明细弹窗（共用组件） -->
    <RequisitionDetailDialog
      v-model="showReqDetailDialog"
      :reqId="reqDetail.id"
      :reqNo="reqDetail.reqNo"
      :deptName="reqDetail.deptName"
      :status="reqDetail.status"
    />

    <!-- 拣货发货弹窗 -->
    <PickAndShipDialog
      v-model="showPickAndShipDialog"
      :requisition="pickAndShipReq"
      @submit="handlePickAndShipSubmit"
    />

    <!-- 新建申领弹窗 -->
    <el-dialog v-model="showNewReqDialog" title="新建申领" width="1100px" top="3vh" @closed="resetNewReq">
      <el-form ref="newReqFormRef" :model="newReqForm" :rules="newReqRules" label-width="100px">
        <el-form-item label="申领科室" prop="deptId">
          <el-select v-model="newReqForm.deptId" placeholder="请选择申领科室" style="width:100%">
            <el-option v-for="d in deptOptions" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="紧急程度" prop="urgency">
          <el-radio-group v-model="newReqForm.urgency">
            <el-radio-button value="NORMAL">常规申领</el-radio-button>
            <el-radio-button value="URGENT">紧急申领</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="添加方式">
          <el-radio-group v-model="addMode" size="small">
            <el-radio-button value="single">单个添加</el-radio-button>
            <el-radio-button value="batch">批量选择</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <!-- 单个添加：选择试剂 + 数量 → 加入清单 -->
        <template v-if="addMode === 'single'">
          <div style="display:flex;gap:10px;align-items:center;margin-bottom:12px">
            <el-select
              v-model="pendingReagent"
              placeholder="搜索并选择试剂…"
              filterable
              clearable
              style="flex:3"
              @change="onSingleReagentSelected"
            >
              <el-option
                v-for="r in reagentOptions"
                :key="r.id"
                :label="(r.genericName || '—') + ' | ' + (r.specification || '—') + ' | ' + (r.manufacturer || '—')"
                :value="r.id"
              >
                <div style="display:flex;align-items:center;justify-content:space-between;width:100%;gap:12px">
                  <span style="font-weight:600;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ r.genericName || '—' }}</span>
                  <span style="font-size:12px;color:var(--text-secondary);flex-shrink:0">{{ r.specification || '—' }}</span>
                  <span style="font-size:12px;color:var(--text-tertiary);flex-shrink:0;max-width:100px;overflow:hidden;text-overflow:ellipsis">{{ r.manufacturer || '—' }}</span>
                  <span style="color:var(--ivd-danger);font-weight:600;flex-shrink:0;min-width:60px;text-align:right">¥{{ (r.pricePerUnit || 0).toFixed(2) }}</span>
                </div>
              </el-option>
            </el-select>
            <el-input-number v-model="pendingQty" :min="1" placeholder="数量" style="flex:1" />
            <el-button type="primary" :icon="Plus" :disabled="!pendingReagent" @click="addSingleItem">加入清单</el-button>
          </div>
        </template>

        <!-- 批量选择模式 -->
        <template v-else>
          <ReagentPicker
            v-model="batchSelected"
            mode="batch"
            :allow-mode-switch="false"
            :items="reagentPickerItems"
            @confirm="onBatchConfirm"
          />
        </template>

        <el-divider content-position="left">
          申领试剂清单
          <span v-if="newReqItems.length" style="font-weight:400;font-size:12px;color:var(--text-tertiary);margin-left:8px">
            共 {{ newReqItems.length }} 项 · 合计 ¥{{ totalNewReqAmount.toFixed(2) }}
          </span>
        </el-divider>

        <!-- 试剂清单表格（两种模式共用） -->
        <el-table :data="newReqItems" size="small" border max-height="360" empty-text="请添加申领试剂">
          <el-table-column prop="reagentName" label="试剂名称" min-width="120" show-overflow-tooltip />
          <el-table-column prop="genericName" label="通用名称" min-width="120" show-overflow-tooltip />
          <el-table-column prop="specification" label="规格型号" min-width="85" show-overflow-tooltip />
          <el-table-column prop="manufacturer" label="厂家" min-width="100" show-overflow-tooltip />
          <el-table-column prop="unit" label="单位" width="55" />
          <el-table-column label="单价" width="80" align="right">
            <template #default="{row}">{{ row.unitPrice != null ? '¥' + Number(row.unitPrice).toFixed(2) : '-' }}</template>
          </el-table-column>
          <el-table-column label="申请数量" width="110" align="center">
            <template #default="{row,$index}">
              <el-input-number
                v-model="row.qtyRequested"
                :min="1"
                size="small"
                style="width:90px"
                @change="recalcItemAmount(row)"
              />
            </template>
          </el-table-column>
          <el-table-column label="申请金额" width="95" align="right">
            <template #default="{row}">¥{{ Number(row.amount || 0).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="60" align="center" fixed="right">
            <template #default="{row,$index}">
              <el-button :icon="Delete" circle size="small" type="danger" @click="newReqItems.splice($index,1)" />
            </template>
          </el-table-column>
        </el-table>

        <el-form-item label="备注" style="margin-top:12px">
          <el-input v-model="newReqForm.remark" type="textarea" :rows="2" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showNewReqDialog = false">取消</el-button>
        <el-button type="primary" :loading="newReqLoading" @click="submitNewReq">提交申领</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Download, Delete } from '@element-plus/icons-vue'
import { useProcurementStore } from '@/stores/procurement'
import { getReqStatusType } from '@/utils/procurement'
import { getDeptOptions } from '@/api/system'
import { getCatalogList } from '@/api/catalog'
import { getRequisitionItems } from '@/api/procurement'
import RequisitionDetailDialog from '@/components/RequisitionDetailDialog.vue'
import PickAndShipDialog from '@/components/PickAndShipDialog.vue'
import ReagentPicker from '@/components/ReagentPicker.vue'

const route = useRoute()
const store = useProcurementStore()
const page = ref(1), pageSize = ref(10)
const search = ref(''), statusFilter = ref('')

// ── 行展开：试剂清单明细（按需加载、缓存）──
const expandItems = ref({})
const expandLoading = ref({})
async function handleExpandChange(row, expandedRows) {
  // expandedRows 是当前所有展开行的数组
  if (!row) return
  // 已缓存则直接展示，不再请求
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

// ── 紧急申领入口 ──
onMounted(async () => {
  fetchData()
  await loadOptions()
  if (route.query.urgency === 'URGENT') {
    newReqForm.urgency = 'URGENT'
    addMode.value = 'single'
    newReqItems.value = []
    pendingReagent.value = null
    pendingQty.value = 1
    showNewReqDialog.value = true
  }
})

// ── 搜索防抖 ──
let searchTimer = null
watch(search, () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => { page.value = 1; fetchData() }, 300)
})

onBeforeUnmount(() => { clearTimeout(searchTimer) })

// ── 新建申领（状态定义必须在 watch 之前）──
const newReqFormRef = ref(null), newReqLoading = ref(false)
const showNewReqDialog = ref(false)
const addMode = ref('single')
const batchSelected = ref([])

// 统一的试剂清单表（两种添加模式共用）
const newReqItems = ref([])
// 单个添加：pending 暂存选择
const pendingReagent = ref(null)
const pendingQty = ref(1)
// 清单合计金额
const totalNewReqAmount = computed(() => {
  return newReqItems.value.reduce((sum, item) => sum + (Number(item.amount) || 0), 0)
})

const newReqForm = reactive({ deptId: null, urgency: 'NORMAL', remark: '' })
const newReqRules = { deptId: [{ required: true, message: '请选择申领科室', trigger: 'change' }] }
const deptOptions = ref([])
const reagentOptions = ref([])

// 根据试剂 ID 从 reagentOptions 查找完整目录信息
function lookupReagent(id) {
  return reagentOptions.value.find(r => r.id === id) || null
}

// 单个模式：选中试剂后自动填充 pendingQty 为 1
function onSingleReagentSelected(id) {
  if (id) pendingQty.value = 1
}

// 单个模式：将 pending 试剂加入清单
function addSingleItem() {
  if (!pendingReagent.value) return
  const id = pendingReagent.value
  // 检查是否已在清单中
  if (newReqItems.value.some(item => item.reagentId === id)) {
    ElMessage.warning('该试剂已在清单中')
    return
  }
  const cat = lookupReagent(id)
  const qty = pendingQty.value || 1
  const price = cat ? (cat.pricePerUnit || 0) : 0
  newReqItems.value.push({
    reagentId: id,
    reagentName: cat ? (cat.genericName || cat.name || '—') : '—',
    genericName: cat ? (cat.genericName || '—') : '—',
    specification: cat ? (cat.specification || '') : '',
    manufacturer: cat ? (cat.manufacturer || '') : '',
    unit: cat ? (cat.unit || '') : '',
    unitPrice: price,
    qtyRequested: qty,
    amount: qty * price
  })
  pendingReagent.value = null
  pendingQty.value = 1
}

// 数量变更后重算金额
function recalcItemAmount(row) {
  const qty = Number(row.qtyRequested) || 1
  row.amount = qty * (Number(row.unitPrice) || 0)
}

async function loadOptions() {
  try {
    const depts = await getDeptOptions()
    if (depts && depts.length) deptOptions.value = depts.map(d => ({ value: d.id, label: d.deptName }))
  } catch (e) { console.error('加载科室列表失败:', e) }
  await loadReagentOptions()
}

async function loadReagentOptions(deptId) {
  try {
    // 未选科室：仅展示已审批，防止草稿/驳回试剂流入申领
    // 已选科室：以 dept_reagent 分配为准，不再叠加审批状态
    //   （科室分配的试剂可能尚未走完审批，由科室管理员自行把控）
    const params = { page: 1, pageSize: 500 }
    if (deptId) {
      params.deptId = deptId
    } else {
      params.approvalStatus = 'APPROVED'
    }
    console.log('[RequisitionList] 加载试剂目录, params:', JSON.stringify(params))
    const list = await getCatalogList(params)
    console.log('[RequisitionList] 试剂目录返回:', list ? list.records?.length : 0, '条, total:', list?.total)
    if (list && list.records) {
      reagentOptions.value = list.records
      console.log('[RequisitionList] reagentOptions 已更新:', reagentOptions.value.length, '条')
    } else {
      console.warn('[RequisitionList] 试剂目录返回为空, list:', list)
      reagentOptions.value = []
    }
  } catch (e) {
    console.error('加载试剂列表失败:', e)
    ElMessage.error('加载试剂目录失败，请检查网络后重试')
  }
}

// 监听科室变更 → 重新加载科室试剂目录
watch(() => newReqForm.deptId, (newDeptId) => {
  if (showNewReqDialog.value) {
    loadReagentOptions(newDeptId || undefined)
  }
})

// ── 申领明细 ──
const showReqDetailDialog = ref(false), reqDetail = ref({})
async function viewDetail(row) {
  reqDetail.value = { id: row.id, reqNo: row.reqNo, deptName: row.deptName, status: row.status }
  showReqDetailDialog.value = true
}

// ── 审核 ──
async function doReview(id) {
  try {
    await ElMessageBox.confirm('确认通过该申领单？', '审核确认', { confirmButtonText: '通过', type: 'warning' })
    await store.doReviewRequisition(id, 'APPROVE')
    ElMessage.success('申领单已审核通过')
    fetchData()
  } catch (e) {
    if (e !== 'cancel') console.error('审核失败:', e)
  }
}

// ── 导出 ──
function handleExport() { ElMessage.success('申领报表生成中，即将下载...') }

// ── 数据加载 ──
function fetchData() {
  store.fetchRequisitions({ page: page.value, pageSize: pageSize.value, status: statusFilter.value || undefined, keyword: search.value || undefined })
  store.fetchRequisitionStats()
}

const reagentPickerItems = computed(() =>
  reagentOptions.value.map(r => ({
    ...r,
    genericName: r.genericName || r.name || '—',
    reagentName: r.genericName || r.name || '—',
    supplierName: r.manufacturer || r.distributor || '—',
    spec: r.specification || r.spec || '—',
    testGroup: r.testGroups || r.testGroup || '通用'
  }))
)

async function openNewReqDialog() {
  addMode.value = 'single'
  newReqItems.value = []
  pendingReagent.value = null
  pendingQty.value = 1
  // 先加载试剂目录，数据就绪后再打开弹窗，避免空下拉
  await loadReagentOptions()
  showNewReqDialog.value = true
}

function onBatchConfirm(selected) {
  for (const item of selected) {
    const id = item.id || item.reagentId
    if (!id || newReqItems.value.some(i => i.reagentId === id)) continue
    const cat = lookupReagent(id)
    const qty = 1
    const price = cat ? (cat.pricePerUnit || 0) : (item.unitPrice || 0)
    newReqItems.value.push({
      reagentId: id,
      reagentName: cat ? (cat.genericName || cat.name || '—') : (item.genericName || item.reagentName || '—'),
      genericName: cat ? (cat.genericName || '—') : (item.genericName || '—'),
      specification: cat ? (cat.specification || '') : (item.specification || item.spec || ''),
      manufacturer: cat ? (cat.manufacturer || '') : (item.manufacturer || ''),
      unit: cat ? (cat.unit || '') : (item.unit || ''),
      unitPrice: price,
      qtyRequested: qty,
      amount: qty * price
    })
  }
}

async function submitNewReq() {
  const valid = await newReqFormRef.value?.validate().catch(() => false)
  if (!valid) return
  newReqLoading.value = true
  try {
    if (!newReqItems.value.length) { ElMessage.warning('请至少选择一种试剂'); newReqLoading.value = false; return }
    const items = newReqItems.value.map(i => ({ reagentId: i.reagentId, qtyRequested: i.qtyRequested || 1 }))
    await store.doCreateRequisition({
      fromDeptId: newReqForm.deptId,
      urgency: newReqForm.urgency,
      remark: newReqForm.remark,
      items
    })
    ElMessage.success('申领单已提交')
    showNewReqDialog.value = false
  } catch {} finally { newReqLoading.value = false }
}

// ── 拣货发货 ──
const showPickAndShipDialog = ref(false)
const pickAndShipReq = ref({})

async function openPickAndShip(row) {
  try {
    // Fetch full items for the requisition
    const items = await getRequisitionItems(row.id)
    pickAndShipReq.value = {
      id: row.id,
      reqNo: row.reqNo,
      deptName: row.deptName,
      status: row.status,
      fromDeptId: row.fromDeptId,
      toWarehouseId: row.toWarehouseId,
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
    showPickAndShipDialog.value = true
  } catch (e) {
    console.error('获取申领明细失败:', e)
  }
}

async function handlePickAndShipSubmit(data) {
  try {
    await store.doPickAndShip(pickAndShipReq.value.id, data)
    showPickAndShipDialog.value = false
    fetchData()
  } catch (e) {
    console.error('拣货发货失败:', e)
  }
}

function resetNewReq() {
  newReqFormRef.value?.resetFields()
  Object.assign(newReqForm, { deptId: null, urgency: 'NORMAL', remark: '' })
  addMode.value = 'single'
  newReqItems.value = []
  pendingReagent.value = null
  pendingQty.value = 1
  batchSelected.value = []
}
</script>

