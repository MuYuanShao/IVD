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
        <el-table-column label="操作" width="180">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="goInbound(row)">扫码验收</el-button>
            <el-button link type="success" size="small" @click="goReceiving(row)">人工验收</el-button>
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
    <el-dialog v-model="showSmartReplenishDialog" title="✨ 智能补货建议" width="1000px" align-center @open="loadSmartReplenish">
      <div style="display:flex;align-items:center;gap:12px;margin-bottom:16px;">
        <span style="font-size:13px;">分析周期：</span>
        <el-select v-model="replenishForm.period" style="width:130px" @change="loadSmartReplenish">
          <el-option label="近7天" value="7" /><el-option label="近14天" value="14" /><el-option label="近30天" value="30" /><el-option label="近90天" value="90" />
        </el-select>
        <el-button :icon="Refresh" size="small" @click="loadSmartReplenish">刷新数据</el-button>
      </div>
      <el-table :data="store.replenishSuggestions" stripe v-loading="store.replenishLoading" @selection-change="sel => selectedReplenish = sel" max-height="400">
        <el-table-column type="selection" width="45" />
        <el-table-column prop="reagentName" label="试剂名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="specification" label="规格型号" min-width="120" show-overflow-tooltip />
        <el-table-column prop="manufacturer" label="厂家" min-width="100" show-overflow-tooltip />
        <el-table-column prop="currentStock" label="当前库存" width="90" align="right" />
        <el-table-column prop="dailyConsumption" label="日均消耗" width="90" align="right" />
        <el-table-column prop="safetyStock" label="安全库存" width="90" align="right" />
        <el-table-column prop="suggestedQty" label="建议补货量" width="120" align="right">
          <template #default="{row}">
            <el-input-number
              v-model="row.suggestedQty"
              :min="1"
              :max="999"
              :step="1"
              step-strictly
              size="small"
              style="width:100px"
            />
          </template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="60" />
        <el-table-column prop="supplier" label="推荐供应商" width="120" show-overflow-tooltip />
      </el-table>
      <template #footer>
        <el-button @click="showSmartReplenishDialog = false">取消</el-button>
        <el-button type="primary" :loading="replenishCreating" @click="doBatchCreatePO" :disabled="!selectedReplenish.length">批量生成采购单 ({{ selectedReplenish.length }})</el-button>
      </template>
    </el-dialog>

    <!-- 手动创建采购单弹窗 -->
    <el-dialog v-model="showManualPODialog" title="新建采购单" width="1100px" top="4vh" @closed="resetManualPO" @open="loadReagentsAndSuppliers">
      <el-form ref="manualPOFormRef" :model="manualPOForm" :rules="manualPORules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="下单日期" prop="orderDate">
              <el-date-picker v-model="manualPOForm.orderDate" type="date" placeholder="选择日期" style="width:100%" value-format="YYYY-MM-DD" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="结算模式">
          <el-select v-model="manualPOForm.settlementMode" placeholder="请选择结算模式" style="width:100%">
            <el-option label="消耗结算" value="消耗结算" />
            <el-option label="领用结算" value="领用结算" />
            <el-option label="验收结算" value="验收结算" />
            <el-option label="调拨结算" value="调拨结算" />
          </el-select>
        </el-form-item>
        <el-divider content-position="left" style="margin:12px 0">采购明细</el-divider>
        <el-table :data="manualPOForm.items" stripe size="small" max-height="320" style="width:100%">
          <el-table-column label="试剂名称" min-width="160">
            <template #default="{row, $index}">
              <el-select v-model="row.reagentId" placeholder="搜索/选择试剂" filterable style="width:100%" size="small" @change="onReagentChange($index, $event)">
                <el-option v-for="r in reagentOptions" :key="r.value" :label="r.label" :value="r.value" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="供应商" width="130">
            <template #default="{row}">
              <el-select v-model="row.supplierId" placeholder="选择供应商" filterable style="width:100%" size="small" clearable>
                <el-option v-for="s in suppliers" :key="s.value" :label="s.label" :value="s.value" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="规格型号" min-width="100">
            <template #default="{row}">{{ row.specification || '—' }}</template>
          </el-table-column>
          <el-table-column label="厂家" min-width="90">
            <template #default="{row}">{{ row.manufacturer || '—' }}</template>
          </el-table-column>
          <el-table-column label="注册证" min-width="110">
            <template #default="{row}">{{ row.registrationCertNo || '—' }}</template>
          </el-table-column>
          <el-table-column label="单价" width="100" align="right">
            <template #default="{row}">{{ row.unitPrice ? '¥' + row.unitPrice : '—' }}</template>
          </el-table-column>
          <el-table-column label="采购数量" width="110" align="center">
            <template #default="{row}">
              <el-input-number v-model="row.quantity" :min="1" :max="999" size="small" style="width:90px" />
            </template>
          </el-table-column>
          <el-table-column label="采购金额" width="110" align="right">
            <template #default="{row}">{{ row.unitPrice ? '¥' + (row.unitPrice * row.quantity).toFixed(2) : '—' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="60" align="center">
            <template #default="{ $index }">
              <el-button :icon="Delete" circle size="small" type="danger" plain @click="removeManualItem($index)" :disabled="manualPOForm.items.length <= 1" />
            </template>
          </el-table-column>
        </el-table>
        <el-button size="small" :icon="Plus" @click="addManualItem" style="margin-top:4px">添加试剂</el-button>
        <el-form-item label="备注" prop="remark" style="margin-top:12px">
          <el-input v-model="manualPOForm.remark" type="textarea" :rows="2" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showManualPODialog = false">取消</el-button>
        <el-button type="primary" :loading="manualPOLoading" @click="submitManualPO" :disabled="!canSubmitPO">确认创建</el-button>
      </template>
    </el-dialog>

    <!-- 采购单拆分结果弹窗 -->
    <el-dialog v-model="showPOResultDialog" title="采购单已按供应商拆分" width="550px" align-center @closed="fetchData">
      <el-table :data="createdPOs" stripe size="small" max-height="300">
        <el-table-column label="#" type="index" width="50" />
        <el-table-column prop="poNo" label="订单号" width="200" />
        <el-table-column label="供应商" min-width="180">
          <template #default="{row}">
            {{ getSupplierName(row.supplierId) }}
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button type="primary" @click="showPOResultDialog = false">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, Plus, Download, MagicStick, Refresh, Delete } from '@element-plus/icons-vue'
import { useProcurementStore } from '@/stores/procurement'
import { getPoStatusType, getReceivePct } from '@/utils/procurement'
import { getSuppliers } from '@/api/license'
import { getCatalogList } from '@/api/catalog'
import { createOrdersFromReplenish } from '@/api/procurement'

const store = useProcurementStore()
const router = useRouter()
const page = ref(1), pageSize = ref(10)
const search = ref(''), statusFilter = ref('')

function goInbound(row) {
  router.push({ path: '/inbound', query: { orderId: row.id, poNo: row.poNo } })
}

function goReceiving(row) {
  router.push({ path: '/receiving', query: { orderId: row.id, poNo: row.poNo } })
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
    if (list && list.length) {
      suppliers.value = list.map(s => ({ value: s.id, label: s.name }))
    } else {
      suppliers.value = []
      ElMessage.warning('未获取到供应商列表，请检查供应商数据')
    }
  } catch (e) {
    console.error('加载供应商列表失败:', e)
    suppliers.value = []
    ElMessage.error('加载供应商列表失败，请刷新页面重试')
  }
}

function handleExport() { ElMessage.success('采购报表生成中，即将下载...') }

// ── 智能补货 ──
const showSmartReplenishDialog = ref(false)
const selectedReplenish = ref([])
const replenishCreating = ref(false)
const replenishForm = reactive({ period: '30' })

// 点击"智能补货建议"按钮 → 触发运算事件 → 加载补货数据
async function loadSmartReplenish() {
  await store.fetchSmartReplenish(replenishForm.period)
}

// 批量生成采购单
async function doBatchCreatePO() {
  if (!selectedReplenish.value.length) return
  replenishCreating.value = true
  try {
    const items = selectedReplenish.value.map(s => ({
      id: s.id,                              // 建议表记录 ID
      reagentId: s.reagentId,
      suggestedQty: s.suggestedQty            // 用户可能已修改
    }))
    await createOrdersFromReplenish(items)
    ElMessage.success(`已批量生成 ${items.length} 张采购单`)
    showSmartReplenishDialog.value = false
    selectedReplenish.value = []
    fetchData()
  } catch (e) {
    console.error('批量生成采购单失败:', e)
  } finally { replenishCreating.value = false }
}

// ── 手动采购单 ──
const showManualPODialog = ref(false), manualPOLoading = ref(false)
const showPOResultDialog = ref(false)
const createdPOs = ref([])
const manualPOFormRef = ref(null)
const getToday = () => new Date().toISOString().slice(0, 10)
const createEmptyItem = () => ({
  reagentId: null,
  reagentName: '',
  supplierId: null,
  specification: '',
  manufacturer: '',
  registrationCertNo: '',
  unitPrice: null,
  quantity: 1
})
const manualPOForm = reactive({
  orderDate: getToday(),
  settlementMode: '调拨结算',
  remark: '',
  items: [createEmptyItem()]
})
const manualPORules = {
  orderDate: [{ required: true, message: '请选择下单日期', trigger: 'change' }]
}
const suppliers = ref([])
const reagentOptions = ref([])

async function loadReagents() {
  try {
    const res = await getCatalogList({ page: 1, pageSize: 500, approvalStatus: 'APPROVED' })
    const list = res?.records || res?.data?.records || []
    reagentOptions.value = list.map(r => ({
      value: r.id,
      label: `${r.genericName || r.name || '—'} (${r.specification || '—'})`,
      genericName: r.genericName,
      specification: r.specification,
      manufacturer: r.manufacturer,
      distributor: r.distributor,
      registrationCertNo: r.registrationCertNo,
      pricePerUnit: r.pricePerUnit
    }))
  } catch (e) { console.error('加载试剂列表失败:', e) }
}

/** 打开手动采购弹窗时同步加载试剂和供应商 */
async function loadReagentsAndSuppliers() {
  await Promise.all([loadReagents(), loadSuppliers()])
}

function onReagentChange(idx, reagentId) {
  const found = reagentOptions.value.find(r => r.value === reagentId)
  if (found) {
    const item = manualPOForm.items[idx]
    item.reagentName = found.genericName || ''
    item.specification = found.specification || ''
    item.manufacturer = found.manufacturer || ''
    item.registrationCertNo = found.registrationCertNo || ''
    item.unitPrice = found.pricePerUnit
    // 自动匹配试剂目录中的配送商到供应商列表
    if (!item.supplierId && found.distributor) {
      const matchedSupplier = suppliers.value.find(s =>
        s.label === found.distributor || s.label.includes(found.distributor) || found.distributor.includes(s.label)
      )
      if (matchedSupplier) {
        item.supplierId = matchedSupplier.value
      }
    }
    // 选择试剂后自动追加一行空行
    const isLastRow = idx === manualPOForm.items.length - 1
    if (isLastRow) {
      manualPOForm.items.push(createEmptyItem())
    }
  }
}

// 至少有一个试剂被选中且采购数量 > 1 时允许创建
const canSubmitPO = computed(() => {
  return manualPOForm.items.some(i => i.reagentId && i.quantity >= 1)
})

function addManualItem() { manualPOForm.items.push(createEmptyItem()) }
function removeManualItem(idx) { if (manualPOForm.items.length > 1) manualPOForm.items.splice(idx, 1) }

async function submitManualPO() {
  const valid = await manualPOFormRef.value?.validate().catch(() => false)
  if (!valid) return
  // Ensure at least one item has reagent selected
  const hasReagent = manualPOForm.items.some(i => i.reagentId)
  if (!hasReagent) { ElMessage.warning('请至少选择一个试剂'); return }
  manualPOLoading.value = true
  try {
    const payload = {
      orderDate: manualPOForm.orderDate,
      settlementMode: manualPOForm.settlementMode,
      remark: manualPOForm.remark,
      items: manualPOForm.items.filter(i => i.reagentId).map(i => ({
        reagentId: i.reagentId,
        supplierId: i.supplierId || null,
        quantity: i.quantity
      }))
    }
    const result = await store.doCreateManualOrder(payload)
    createdPOs.value = Array.isArray(result) ? result : []
    showManualPODialog.value = false
    if (createdPOs.value.length > 0) {
      showPOResultDialog.value = true
    } else {
      ElMessage.warning('未生成采购单，请检查供应商设置')
    }
  } catch (e) {
    console.error('创建采购单失败:', e)
    ElMessage.error('创建采购单失败，请检查明细数据或联系管理员')
  } finally { manualPOLoading.value = false }
}

function getSupplierName(supplierId) {
  if (!supplierId) return '未指定供应商'
  const s = suppliers.value.find(s => s.value === supplierId)
  return s ? s.label : `供应商#${supplierId}`
}

function resetManualPO() {
  manualPOFormRef.value?.resetFields()
  Object.assign(manualPOForm, {
    orderDate: getToday(),
    settlementMode: '调拨结算',
    remark: '',
    items: [createEmptyItem()]
  })
  manualPOForm.items.forEach((item, idx) => {
    Object.assign(item, createEmptyItem())
  })
}
</script>

