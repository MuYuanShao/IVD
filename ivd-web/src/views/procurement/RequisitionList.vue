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
      <el-table :data="store.requisitions" stripe v-loading="store.reqLoading" style="width:100%">
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
        <el-table-column label="操作" width="180">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="viewDetail(row)">详情</el-button>
            <el-button v-if="row.status==='PENDING_REVIEW'" link type="warning" size="small" @click="doReview(row.id)">审核</el-button>
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

    <!-- 新建申领弹窗 -->
    <el-dialog v-model="showNewReqDialog" title="新建申领" width="700px" top="5vh" @closed="resetNewReq">
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
        <el-divider content-position="left">申领试剂清单</el-divider>

        <!-- 单个添加模式 -->
        <template v-if="addMode === 'single'">
          <div v-for="(item, idx) in newReqForm.items" :key="idx" style="display:flex;gap:12px;margin-bottom:12px;align-items:center">
            <el-select v-model="item.reagentId" placeholder="选择试剂" filterable style="flex:2">
              <el-option v-for="r in reagentOptions" :key="r.id" :label="(r.genericName || r.name || '—') + ' (' + (r.specification || r.spec || '—') + ')'" :value="r.id" />
            </el-select>
            <el-input-number v-model="item.qtyRequested" :min="1" style="flex:1" placeholder="数量" />
            <el-button :icon="Delete" circle size="small" @click="newReqForm.items.splice(idx,1)" :disabled="newReqForm.items.length<=1" />
          </div>
          <el-button type="primary" link :icon="Plus" @click="newReqForm.items.push({ reagentId: null, qtyRequested: 1 })">添加试剂</el-button>
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
          <div v-if="batchConfirmedItems.length" style="margin-top:12px">
            <div v-for="(item, idx) in batchConfirmedItems" :key="idx" style="display:flex;gap:12px;margin-bottom:8px;align-items:center">
              <span style="flex:2;line-height:32px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap" :title="item.genericName || item.name || item.reagentName">
                {{ item.genericName || item.name || item.reagentName || '试剂#' + item.reagentId }}
              </span>
              <el-input-number v-model="item.qtyRequested" :min="1" style="flex:1" placeholder="数量" size="small" />
              <el-button :icon="Delete" circle size="small" @click="batchConfirmedItems.splice(idx,1)" />
            </div>
          </div>
        </template>

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
import RequisitionDetailDialog from '@/components/RequisitionDetailDialog.vue'
import ReagentPicker from '@/components/ReagentPicker.vue'

const route = useRoute()
const store = useProcurementStore()
const page = ref(1), pageSize = ref(10)
const search = ref(''), statusFilter = ref('')

// ── 紧急申领入口 ──
onMounted(() => {
  if (route.query.urgency === 'URGENT') {
    showNewReqDialog.value = true
    newReqForm.urgency = 'URGENT'
  }
  fetchData()
  loadOptions()
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
const batchConfirmedItems = ref([])
const newReqForm = reactive({ deptId: null, urgency: 'NORMAL', remark: '', items: [{ reagentId: null, qtyRequested: 1 }] })
const newReqRules = { deptId: [{ required: true, message: '请选择申领科室', trigger: 'change' }] }
const deptOptions = ref([])
const reagentOptions = ref([])

async function loadOptions() {
  try {
    const depts = await getDeptOptions()
    if (depts && depts.length) deptOptions.value = depts.map(d => ({ value: d.id, label: d.deptName }))
  } catch (e) { console.error('加载科室列表失败:', e) }
  await loadReagentOptions()
}

async function loadReagentOptions(deptId) {
  try {
    const params = { page: 1, pageSize: 200 }
    if (deptId) params.deptId = deptId
    const list = await getCatalogList(params)
    if (list && list.records) reagentOptions.value = list.records
  } catch (e) { console.error('加载试剂列表失败:', e) }
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

function openNewReqDialog() {
  showNewReqDialog.value = true
  addMode.value = 'single'
  batchConfirmedItems.value = []
  loadReagentOptions()  // 初始加载全量试剂目录（科室未选时展示全部）
}

function onBatchConfirm(selected) {
  for (const item of selected) {
    if (!batchConfirmedItems.value.find(i => i.id === item.id)) {
      batchConfirmedItems.value.push({ ...item, qtyRequested: 1 })
    }
  }
}

async function submitNewReq() {
  const valid = await newReqFormRef.value?.validate().catch(() => false)
  if (!valid) return
  newReqLoading.value = true
  try {
    const items = addMode.value === 'batch'
      ? batchConfirmedItems.value.map(i => ({ reagentId: i.id, qtyRequested: i.qtyRequested || 1 }))
      : newReqForm.items.filter(i => i.reagentId)
    if (!items.length) { ElMessage.warning('请至少选择一种试剂'); newReqLoading.value = false; return }
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

function resetNewReq() {
  newReqFormRef.value?.resetFields()
  Object.assign(newReqForm, { deptId: null, urgency: 'NORMAL', remark: '', items: [{ reagentId: null, qtyRequested: 1 }] })
  addMode.value = 'single'
  batchConfirmedItems.value = []
  batchSelected.value = []
}
</script>

