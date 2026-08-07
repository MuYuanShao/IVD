<!--
  GroupWarehouse.vue — 组库管理(三级)（v2 独立路由页）
  来源：InventoryView.vue Tab "warehouse"
-->
<template>
  <div class="page-container">
    <div class="page-title">
      <h2><span class="title-icon" style="background:#f5f3ff;color:#8b5cf6;">🧪</span>组库管理（三级库）</h2>
      <span class="sub-tag">FEFO出库推荐 · M06</span>
      <div class="header-right">
        <span style="font-size:13px;color:var(--text-secondary);margin-right:8px;">当前组别：</span>
        <el-select v-model="selectedGroup" style="width:140px" @change="handleGroupChange">
          <el-option v-for="g in groups" :key="g.value" :label="g.label" :value="g.value" />
        </el-select>
        <span style="font-size:12px;color:var(--text-tertiary);margin-left:4px">组长：{{ currentGroupLeader }}</span>
      </div>
    </div>

    <!-- FEFO出库推荐 -->
    <el-card shadow="never" style="margin-bottom:16px">
      <template #header>
        <span class="card-title">📋 FEFO出库推荐</span>
        <span style="font-size:11px;color:var(--text-tertiary);margin-left:8px">（近效期优先出库）</span>
      </template>
      <el-table :data="store.fefoList" stripe style="width:100%">
        <el-table-column type="index" label="FEFO优先级" width="100" />
        <el-table-column prop="hisCode" label="试剂编码" width="110" show-overflow-tooltip />
        <el-table-column prop="reagentName" label="名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="batchNo" label="批号" width="120" />
        <el-table-column prop="expiryDate" label="有效期" width="100" />
        <el-table-column label="剩余天数" width="85">
          <template #default="{row}"><el-tag :type="getExpiryTagType(row.expiryDate)" size="small">{{ getRemainingDays(row.expiryDate) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="stock" label="库存量" width="75" />
        <el-table-column prop="location" label="库位" width="80" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{row}">
            <el-tag :type="getRemainingDays(row.expiryDate) <= 30 ? 'danger' : getRemainingDays(row.expiryDate) <= 90 ? 'warning' : 'success'" size="small">
              {{ getRemainingDays(row.expiryDate) <= 30 ? '紧急出库' : getRemainingDays(row.expiryDate) <= 90 ? '优先消耗' : '正常' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="openTrace(row)">UDI追溯</el-button>
            <el-button link type="success" size="small" @click="openTransfer(row, groups.find(g=>g.value===selectedGroup)?.label || '组库')">出库</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="card-footer" style="margin-top:12px;font-size:12px;color:var(--text-tertiary);background:#f8fafc;padding:10px 16px;border-radius:8px;">
        💡 <strong>FEFO策略</strong>：按有效期升序排列，优先消耗近效期批次；开瓶试剂剩余天数 = MIN(效期, 开瓶日期+稳定天数)。<el-button link type="primary" size="small" @click="$router.push('/expiry')">查看效期预警 →</el-button>
      </div>
    </el-card>

    <!-- 组库库存明细 -->
    <el-card shadow="never">
      <template #header><span class="card-title">📦 {{ groups.find(g=>g.value===selectedGroup)?.label || '组库' }} 库存明细</span></template>
      <div style="display:flex;align-items:center;gap:10px;margin-bottom:12px;flex-wrap:wrap;">
        <el-input v-model="filterHisCode" placeholder="商品编码" clearable style="width:130px" @keyup.enter="applyFilter" />
        <el-input v-model="filterReagentName" placeholder="商品名称" clearable style="width:150px" @keyup.enter="applyFilter" />
        <el-input v-model="filterDistributor" placeholder="配送商" clearable style="width:130px" @keyup.enter="applyFilter" />
        <el-input v-model="filterManufacturer" placeholder="厂家" clearable style="width:130px" @keyup.enter="applyFilter" />
        <el-input v-model="filterRegistrationCert" placeholder="注册证号" clearable style="width:140px" @keyup.enter="applyFilter" />
        <el-button type="primary" @click="applyFilter">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
      <el-table :data="filteredList" stripe style="width:100%">
        <el-table-column prop="hisCode" label="试剂编码" width="110" show-overflow-tooltip />
        <el-table-column prop="reagentName" label="试剂名称" min-width="130" show-overflow-tooltip />
        <el-table-column prop="genericName" label="通用名称" min-width="130" show-overflow-tooltip />
        <el-table-column prop="specification" label="规格型号" width="100" show-overflow-tooltip />
        <el-table-column prop="manufacturer" label="厂家" width="100" show-overflow-tooltip />
        <el-table-column prop="distributor" label="配送商" width="100" show-overflow-tooltip />
        <el-table-column prop="batchNo" label="批号" width="120" show-overflow-tooltip />
        <el-table-column prop="manufactureDate" label="生产日期" width="100" />
        <el-table-column prop="expiryDate" label="效期" width="100" />
        <el-table-column label="剩余天数" width="85">
          <template #default="{row}"><el-tag :type="getExpiryTagType(row.expiryDate)" size="small">{{ getRemainingDays(row.expiryDate) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="stock" label="库存量" width="75" />
        <el-table-column label="金额" width="100" align="right">
          <template #default="{row}">{{ row.amount ? '¥' + row.amount.toFixed(2) : '—' }}</template>
        </el-table-column>
        <el-table-column prop="inboundDate" label="入库日期" width="100" />
        <el-table-column prop="registrationCertNo" label="注册证号" width="120" show-overflow-tooltip />
        <el-table-column label="是否带量" width="80" align="center">
          <template #default="{row}">
            <el-tag :type="row.isVolumeBased ? 'warning' : 'info'" size="small">{{ row.isVolumeBased ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="volumeType" label="带量类型" width="110" show-overflow-tooltip>
          <template #default="{row}">{{ row.volumeType || '—' }}</template>
        </el-table-column>
        <el-table-column label="是否收费" width="80" align="center">
          <template #default="{row}">
            <el-tag :type="row.isChargeable ? 'success' : 'info'" size="small">{{ row.isChargeable ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="insuranceCode" label="医保编码" width="120" show-overflow-tooltip />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="openTrace(row)">UDI追溯</el-button>
            <el-button link type="success" size="small" @click="openTransfer(row, groups.find(g=>g.value===selectedGroup)?.label || '组库')">出库</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top:12px;font-size:12px;color:var(--text-secondary);background:#f0fdf4;padding:8px 16px;border-radius:8px;">
        共 <strong>{{ filteredList.length }}</strong> 种试剂 · <strong>{{ totalBoxes }}</strong> 盒 · 库存金额 <strong>¥{{ totalAmount }}</strong>
      </div>
    </el-card>

    <TraceDialog v-model="showTraceDialog" :initial-search="traceSearch" />
    <TransferDialog v-model="showTransferDialog" :source-name="sourceName" :max-qty="maxQty" :warehouse-options="targetWarehouses" :loading="transferLoading" @submit="doSubmitTransfer" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useInventoryStore } from '@/stores/inventory'
import { getRemainingDays, getExpiryTagType } from '@/utils/inventory'
import TraceDialog from '@/components/TraceDialog.vue'
import TransferDialog from '@/components/TransferDialog.vue'

const store = useInventoryStore()
const selectedGroup = ref(1)
const groups = [
  { value: 1, label: '免疫组', leader: '赵六' }, { value: 2, label: '生化组', leader: '钱七' },
  { value: 3, label: '临检组', leader: '孙八' }, { value: 4, label: '微生物组', leader: '周九' }, { value: 5, label: '分子组', leader: '吴十' }
]

const currentGroupLeader = computed(() => groups.find(g => g.value === selectedGroup.value)?.leader || '—')
const totalBoxes = computed(() => filteredList.value.reduce((sum, r) => sum + (Number(r.stock) || 0), 0))
const totalAmount = computed(() => filteredList.value.reduce((sum, r) => sum + (Number(r.amount) || 0), 0).toFixed(2))

// ── 搜索过滤 ──
const filterHisCode = ref(''), filterReagentName = ref('')
const filterDistributor = ref(''), filterManufacturer = ref(''), filterRegistrationCert = ref('')

function fetchData() { store.fetchFefo(null, selectedGroup.value) }

function handleGroupChange() { fetchData() }

onMounted(() => { fetchData() })

// ── 客户端过滤 ──
const filteredList = computed(() => {
  let list = store.fefoList
  const hc = filterHisCode.value?.trim().toLowerCase()
  const rn = filterReagentName.value?.trim().toLowerCase()
  const db = filterDistributor.value?.trim().toLowerCase()
  const mf = filterManufacturer.value?.trim().toLowerCase()
  const rc = filterRegistrationCert.value?.trim().toLowerCase()
  if (hc) list = list.filter(r => (r.hisCode || '').toLowerCase().includes(hc))
  if (rn) list = list.filter(r => (r.reagentName || '').toLowerCase().includes(rn))
  if (db) list = list.filter(r => (r.distributor || '').toLowerCase().includes(db))
  if (mf) list = list.filter(r => (r.manufacturer || '').toLowerCase().includes(mf))
  if (rc) list = list.filter(r => (r.registrationCertNo || '').toLowerCase().includes(rc))
  return list
})

function applyFilter() { /* computed 自动更新，占位用于后续 API 对接 */ }
function resetFilters() {
  filterHisCode.value = ''
  filterReagentName.value = ''
  filterDistributor.value = ''
  filterManufacturer.value = ''
  filterRegistrationCert.value = ''
}

// ── 弹窗 ──
const showTraceDialog = ref(false), traceSearch = ref('')
const showTransferDialog = ref(false), sourceName = ref(''), maxQty = ref(999), transferLoading = ref(false), selectedBatchId = ref(null)

const targetWarehouses = [
  { value: 1, label: '中心药库' }, { value: 5, label: '检验科' }
]

function openTrace(row) { traceSearch.value = row.udiDi || row.batchNo || ''; showTraceDialog.value = true }
function openTransfer(row, source) { sourceName.value = source; maxQty.value = row.stock || row.qty || 999; selectedBatchId.value = row.id; showTransferDialog.value = true }

async function doSubmitTransfer(form) {
  transferLoading.value = true
  try { await store.doTransfer(selectedBatchId.value, form.targetWarehouse, form.quantity); ElMessage.success('出库成功'); showTransferDialog.value = false; fetchData() }
  catch (e) { console.error('Transfer failed', e) } finally { transferLoading.value = false }
}
</script>

