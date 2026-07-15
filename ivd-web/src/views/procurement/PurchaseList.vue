<!--
  PurchaseList.vue — 采购管理（v2 独立路由页）
  来源：ProcurementList.vue Tab "order" + 智能补货弹窗 + 手动采购弹窗
-->
<template>
  <div class="page-container">
    <div class="page-title">
      <h2><span class="title-icon" style="background:#f0fdf4;color:#10b981;">📦</span>采购管理</h2>
      <span class="sub-tag">缺货驱动 · 人工采购 · 智能补货 · 验收入库</span>
    </div>

    <el-row :gutter="16" class="stat-cards">
      <el-col :span="6"><el-card shadow="hover" class="stat-card stat-card--warning"><el-statistic title="待收货" :value="store.poStats.pending || 0" /></el-card></el-col>
      <el-col :span="6"><el-card shadow="hover" class="stat-card stat-card--info"><el-statistic title="部分收货" :value="store.poStats.partial || 0" /></el-card></el-col>
      <el-col :span="6"><el-card shadow="hover" class="stat-card stat-card--success"><el-statistic title="已完成" :value="store.poStats.completed || 0" /></el-card></el-col>
      <el-col :span="6"><el-card shadow="hover" class="stat-card stat-card--danger"><el-statistic title="已取消" :value="store.poStats.cancelled || 0" /></el-card></el-col>
    </el-row>

    <el-card shadow="never" style="margin-bottom:16px">
      <div style="display:flex;align-items:center;gap:12px;flex-wrap:wrap;">
        <el-input v-model="search" placeholder="搜索订单号/供应商/试剂" :prefix-icon="Search" clearable style="width:260px" @keyup.enter="fetchData" />
        <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width:140px" @change="fetchData">
          <el-option label="待收货" value="DRAFT" /><el-option label="部分收货" value="PARTIAL_RECEIVED" />
          <el-option label="已完成" value="COMPLETED" /><el-option label="已取消" value="CANCELLED" />
        </el-select>
        <el-button type="primary" :icon="MagicStick" @click="showSmartReplenishDialog = true">智能补货建议</el-button>
        <el-button type="success" :icon="Plus" @click="showManualPODialog = true">新建采购单</el-button>
        <el-button @click="$router.push('/shortage')">查看缺货单</el-button>
        <el-button :icon="Download" @click="handleExport">导出报表</el-button>
      </div>
    </el-card>

    <el-card shadow="never">
      <el-table :data="store.orders" stripe v-loading="store.poLoading" style="width:100%">
        <el-table-column prop="poNo" label="订单号" width="180" />
        <el-table-column label="来源" width="100">
          <template #default="{row}">
            <el-tag v-if="row.sourceType==='SMART'" type="warning" size="small">智能补货</el-tag>
            <el-tag v-else-if="row.sourceType==='MANUAL'" type="success" size="small">人工采购</el-tag>
            <el-button v-else link type="primary" size="small" @click="$router.push('/shortage')">缺货单</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="supplierName" label="供应商" width="120" />
        <el-table-column prop="reagentName" label="试剂" width="140" />
        <el-table-column label="数量" width="80"><template #default="{row}">{{ row.orderQuantity || '—' }}</template></el-table-column>
        <el-table-column label="金额" width="100"><template #default="{row}">¥{{ row.totalAmount || 0 }}</template></el-table-column>
        <el-table-column prop="orderDate" label="下单日期" width="100" />
        <el-table-column prop="expectedDeliveryDate" label="预计到货" width="100" />
        <el-table-column label="收货进度" width="140">
          <template #default="{row}">
            <el-progress :percentage="getReceivePct(row)" :stroke-width="8" :status="getReceivePct(row)===100?'success':''" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{row}"><el-tag :type="getPoStatusType(row.status)" size="small">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="goInbound(row)">扫码验收</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="page" v-model:page-size="pageSize" :total="store.poTotal"
        :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next,jumper"
        style="margin-top:16px;justify-content:flex-end" @change="fetchData"
      />
    </el-card>

    <!-- 智能补货建议弹窗 -->
    <el-dialog v-model="showSmartReplenishDialog" title="✨ 智能补货建议" width="800px" top="5vh">
      <div style="display:flex;align-items:center;gap:12px;margin-bottom:16px;">
        <span style="font-size:13px;">分析周期：</span>
        <el-select v-model="replenishForm.period" style="width:130px" @change="loadSmartReplenish">
          <el-option label="近7天" value="7" /><el-option label="近14天" value="14" /><el-option label="近30天" value="30" /><el-option label="近90天" value="90" />
        </el-select>
        <span style="font-size:13px;">安全库存阈值（天）：</span>
        <el-input-number v-model="replenishForm.thresholdDays" :min="3" :max="60" style="width:110px" @change="loadSmartReplenish" />
      </div>
      <el-table :data="store.replenishSuggestions" stripe v-loading="store.replenishLoading" @selection-change="sel => selectedReplenish = sel">
        <el-table-column type="selection" width="45" />
        <el-table-column prop="reagentName" label="试剂" min-width="180" />
        <el-table-column prop="suggestedQty" label="建议采购量" width="110" />
        <el-table-column prop="currentStock" label="当前库存" width="90" />
        <el-table-column prop="safetyStock" label="安全库存" width="90" />
        <el-table-column prop="supplier" label="推荐供应商" width="120" />
      </el-table>
      <template #footer>
        <el-button @click="showSmartReplenishDialog = false">取消</el-button>
        <el-button type="primary" @click="doBatchCreatePO" :disabled="!selectedReplenish.length">批量生成采购单 ({{ selectedReplenish.length }})</el-button>
      </template>
    </el-dialog>

    <!-- 手动创建采购单弹窗 -->
    <el-dialog v-model="showManualPODialog" title="新建采购单" width="500px" top="8vh" @closed="resetManualPO">
      <el-form ref="manualPOFormRef" :model="manualPOForm" :rules="manualPORules" label-width="90px">
        <el-form-item label="供应商" prop="supplierId">
          <el-select v-model="manualPOForm.supplierId" placeholder="请选择供应商" style="width:100%">
            <el-option v-for="s in suppliers" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="下单日期" prop="orderDate">
          <el-date-picker v-model="manualPOForm.orderDate" type="date" placeholder="选择日期" style="width:100%" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="备注" prop="remark"><el-input v-model="manualPOForm.remark" type="textarea" :rows="2" placeholder="选填" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showManualPODialog = false">取消</el-button>
        <el-button type="primary" :loading="manualPOLoading" @click="submitManualPO">确认创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, Plus, Download, MagicStick } from '@element-plus/icons-vue'
import { useProcurementStore } from '@/stores/procurement'
import { getPoStatusType, getReceivePct } from '@/utils/procurement'
import { getSuppliers } from '@/api/license'

const store = useProcurementStore()
const router = useRouter()
const page = ref(1), pageSize = ref(10)
const search = ref(''), statusFilter = ref('')

function goInbound(row) {
  router.push({ path: '/inbound', query: { orderId: row.id, poNo: row.poNo } })
}

function fetchData() {
  store.fetchOrders({ page: page.value, pageSize: pageSize.value, keyword: search.value || undefined, status: statusFilter.value || undefined })
  store.fetchOrderStats()
}

onMounted(() => { fetchData(); loadSuppliers() })

// ── 搜索防抖 ──
let searchTimer = null
watch(search, () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => { page.value = 1; fetchData() }, 300)
})

onBeforeUnmount(() => { clearTimeout(searchTimer) })

async function loadSuppliers() {
  try {
    const list = await getSuppliers()
    if (list && list.length) suppliers.value = list.map(s => ({ value: s.id, label: s.name }))
  } catch (e) { console.error('加载供应商列表失败:', e) }
}

function handleExport() { ElMessage.success('采购报表生成中，即将下载...') }

// ── 智能补货 ──
const showSmartReplenishDialog = ref(false)
const selectedReplenish = ref([])
const replenishForm = reactive({ period: '30', thresholdDays: 7 })

async function loadSmartReplenish() {
  await store.fetchSmartReplenish(replenishForm.period)
}

async function doBatchCreatePO() {
  ElMessage.success(`已批量生成 ${selectedReplenish.value.length} 张采购单`)
  showSmartReplenishDialog.value = false
  fetchData()
}

// ── 手动采购单 ──
const showManualPODialog = ref(false), manualPOLoading = ref(false)
const manualPOFormRef = ref(null)
const getToday = () => new Date().toISOString().slice(0, 10)
const manualPOForm = reactive({ supplierId: null, orderDate: getToday(), remark: '' })
const manualPORules = {
  supplierId: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  orderDate: [{ required: true, message: '请选择下单日期', trigger: 'change' }]
}
const suppliers = ref([])

async function submitManualPO() {
  const valid = await manualPOFormRef.value?.validate().catch(() => false)
  if (!valid) return
  manualPOLoading.value = true
  try {
    await store.doCreateManualOrder({ ...manualPOForm })
    ElMessage.success('采购单已创建')
    showManualPODialog.value = false
  } catch (e) { console.error('创建采购单失败:', e) } finally { manualPOLoading.value = false }
}

function resetManualPO() {
  manualPOFormRef.value?.resetFields()
  Object.assign(manualPOForm, { supplierId: null, orderDate: getToday(), remark: '' })
}
</script>

