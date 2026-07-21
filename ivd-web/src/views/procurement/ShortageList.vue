<!--
  ShortageList.vue — 缺货单（v2 独立路由页）
  v2 补齐：搜索框 + ShortageDetailDialog + CreatePOFromShortageDialog + 查看申领单按钮
-->
<template>
  <div class="page-container">
    <div class="page-title">
      <h2><span class="title-icon" style="background:#fef2f2;color:#ef4444;">⚠️</span>缺货单</h2>
      <span class="sub-tag">中心库无库存 → 驱动采购补货</span>
    </div>

    <el-row :gutter="16" class="stat-cards">
      <el-col :span="6"><el-card shadow="hover" class="stat-card stat-card--warning"><el-statistic title="待采购" :value="store.shStats.pending || 0" /></el-card></el-col>
      <el-col :span="6"><el-card shadow="hover" class="stat-card stat-card--info"><el-statistic title="采购中" :value="store.shStats.purchasing || 0" /></el-card></el-col>
      <el-col :span="6"><el-card shadow="hover" class="stat-card stat-card--success"><el-statistic title="已完成" :value="store.shStats.completed || 0" /></el-card></el-col>
      <el-col :span="6"><el-card shadow="hover" class="stat-card"><el-statistic title="缺货品种" :value="store.shStats.total || 0" /></el-card></el-col>
    </el-row>

    <!-- 工具栏：搜索 + 状态筛选 + 导出 -->
    <el-card shadow="never" style="margin-bottom:16px">
      <div style="display:flex;align-items:center;gap:12px;flex-wrap:wrap;">
        <el-input v-model="search" placeholder="搜索缺货单号" :prefix-icon="Search" clearable style="width:260px" @keyup.enter="fetchData" />
        <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width:160px" @change="fetchData">
          <el-option label="待采购" value="PENDING_PROCUREMENT" />
          <el-option label="采购中" value="PURCHASING" />
          <el-option label="已完成" value="COMPLETED" />
        </el-select>
        <el-button :icon="Download" @click="handleExport">导出报表</el-button>
      </div>
    </el-card>

    <el-card shadow="never">
      <el-table :data="store.shortages" stripe v-loading="store.shLoading" style="width:100%">
        <el-table-column prop="shortageNo" label="缺货单号" width="160" />
        <el-table-column label="来源申领" width="130">
          <template #default="{row}"><el-button link type="primary" size="small" @click="viewReqDetail(row)">{{ row.sourceReqNo || '查看' }}</el-button></template>
        </el-table-column>
        <el-table-column prop="deptName" label="申领科室" width="120" />
        <el-table-column prop="reagentName" label="缺货试剂" width="140" />
        <el-table-column prop="spec" label="规格" width="90" />
        <el-table-column prop="shortageQty" label="缺货量" width="80" />
        <el-table-column label="预估金额" width="100"><template #default="{row}">¥{{ row.estimatedAmount || 0 }}</template></el-table-column>
        <el-table-column prop="suggestedSupplier" label="建议供应商" width="120" />
        <el-table-column prop="createdAt" label="日期" width="100" />
        <el-table-column label="状态" width="110">
          <template #default="{row}"><el-tag :type="getShStatusType(row.status)" size="small">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="viewShortageDetail(row)">详情</el-button>
            <el-button v-if="row.reqId" link type="warning" size="small" @click="goViewRequisition(row.reqId)">查看申领单</el-button>
            <el-button v-if="row.status==='PENDING_PROCUREMENT'" link type="primary" size="small" @click="openGeneratePO(row)">生成采购单</el-button>
            <el-button v-if="row.poId" link type="success" size="small" @click="$router.push('/purchase')">查看订单</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="page" v-model:page-size="pageSize" :total="store.shTotal"
        :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next,jumper"
        style="margin-top:16px;justify-content:flex-end" @change="fetchData"
      />
    </el-card>

    <!-- 申领明细弹窗 -->
    <RequisitionDetailDialog
      v-model="showReqDetailDialog"
      :reqId="reqDetail.id"
      :reqNo="reqDetail.reqNo"
      :deptName="reqDetail.deptName"
      :status="reqDetail.status"
    />

    <!-- 缺货明细弹窗 -->
    <ShortageDetailDialog
      v-model="showShortageDetailDialog"
      :header="shortageDetail"
      @generatePO="openGeneratePOFromDetail"
      @viewRequisition="goViewRequisition"
    />

    <!-- 生成采购订单弹窗 -->
    <CreatePOFromShortageDialog
      v-model="showCreatePODialog"
      :shortage-id="selectedShortageId"
      :shortage-no="selectedShortageNo"
      :suppliers="supplierOptions"
      @created="handleCreatePO"
    />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Download } from '@element-plus/icons-vue'
import { useProcurementStore } from '@/stores/procurement'
import { getShStatusType } from '@/utils/procurement'
import RequisitionDetailDialog from '@/components/RequisitionDetailDialog.vue'
import ShortageDetailDialog from '@/components/ShortageDetailDialog.vue'
import CreatePOFromShortageDialog from '@/components/CreatePOFromShortageDialog.vue'
import { getSuppliers } from '@/api/license'

const store = useProcurementStore()
const page = ref(1), pageSize = ref(10)
const statusFilter = ref(''), search = ref('')

const showReqDetailDialog = ref(false), reqDetail = ref({})
const showShortageDetailDialog = ref(false), shortageDetail = ref({})
const showCreatePODialog = ref(false)
const selectedShortageId = ref(null), selectedShortageNo = ref('')
const supplierOptions = ref([])

async function loadSuppliers() {
  try {
    const list = await getSuppliers()
    if (list && list.length) supplierOptions.value = list.map(s => ({ value: s.id, label: s.name }))
  } catch (e) { console.error('Failed to load suppliers:', e) }
}

function viewReqDetail(row) {
  reqDetail.value = { id: row.reqId, reqNo: row.sourceReqNo || '-', deptName: row.deptName || '-', status: row.reqStatus || '-' }
  showReqDetailDialog.value = true
}

function viewShortageDetail(row) {
  shortageDetail.value = {
    id: row.id, shortageNo: row.shortageNo, reqId: row.reqId,
    deptName: row.deptName, status: row.status, poId: row.poId, createdAt: row.createdAt
  }
  showShortageDetailDialog.value = true
}

function goViewRequisition(reqId) {
  if (reqId) {
    showShortageDetailDialog.value = false
    reqDetail.value = { id: reqId, reqNo: '-', deptName: '-', status: '-' }
    showReqDetailDialog.value = true
  }
}

function openGeneratePO(row) {
  selectedShortageId.value = row.id
  selectedShortageNo.value = row.shortageNo
  showCreatePODialog.value = true
}

function openGeneratePOFromDetail(id) {
  showShortageDetailDialog.value = false
  selectedShortageId.value = id
  selectedShortageNo.value = shortageDetail.value?.shortageNo || ''
  showCreatePODialog.value = true
}

async function handleCreatePO(data) {
  try {
    await store.doCreateOrderFromShortage(data)
    ElMessage.success('采购订单已生成')
    showCreatePODialog.value = false
    fetchData()
  } catch { showCreatePODialog.value = false }
}

function handleExport() { ElMessage.success('缺货报表生成中，即将下载...') }

function fetchData() {
  store.fetchShortages({ page: page.value, pageSize: pageSize.value, status: statusFilter.value || undefined, keyword: search.value || undefined })
  store.fetchShortageStats()
}

onMounted(() => { loadSuppliers(); fetchData() })
</script>

