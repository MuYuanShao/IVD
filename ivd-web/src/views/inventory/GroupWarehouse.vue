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
        <el-table-column prop="code" label="编码" width="120" />
        <el-table-column prop="name" label="名称" width="160" />
        <el-table-column prop="batchNo" label="批号" width="120" />
        <el-table-column prop="expiryDate" label="有效期" width="100" />
        <el-table-column label="剩余天数" width="90">
          <template #default="{row}"><el-tag :type="getExpiryTagType(row.expiryDate)" size="small">{{ getRemainingDays(row.expiryDate) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="stock" label="库存量" width="80" />
        <el-table-column prop="location" label="库位" width="80" />
        <el-table-column label="状态" width="100">
          <template #default="{row}">
            <el-tag :type="getRemainingDays(row.expiryDate) <= 30 ? 'danger' : getRemainingDays(row.expiryDate) <= 90 ? 'warning' : 'success'" size="small">
              {{ getRemainingDays(row.expiryDate) <= 30 ? '紧急出库' : getRemainingDays(row.expiryDate) <= 90 ? '优先消耗' : '正常' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140">
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
      <el-table :data="fefoDetailList" stripe style="width:100%">
        <el-table-column prop="code" label="编码" width="100" />
        <el-table-column prop="name" label="名称" width="140" />
        <el-table-column prop="spec" label="规格" width="90" />
        <el-table-column prop="model" label="型号" width="90" />
        <el-table-column label="单价(¥)" width="80"><template #default="{row}">{{ row.unitPrice }}</template></el-table-column>
        <el-table-column prop="unit" label="单位" width="60" />
        <el-table-column prop="qty" label="数量" width="70" />
        <el-table-column label="金额(¥)" width="90"><template #default="{row}">{{ row.amount }}</template></el-table-column>
        <el-table-column prop="manufacturer" label="厂家" width="120" />
        <el-table-column prop="supplier" label="配送商" width="110" />
        <el-table-column prop="productionDate" label="生产日期" width="100" />
        <el-table-column prop="expiryDate" label="有效期" width="100" />
        <el-table-column prop="batchNo" label="批号" width="110" />
      </el-table>
      <div style="margin-top:12px;font-size:12px;color:var(--text-secondary);background:#f0fdf4;padding:8px 16px;border-radius:8px;">
        共 <strong>{{ fefoDetailList.length }}</strong> 种试剂 · <strong>116</strong> 盒 · 库存金额 <strong>¥284,900</strong>
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

const fefoDetailList = ref([
  { code: '051266', name: 'CK-MB检测试剂盒', spec: '100T', model: 'R1:20ml×2, R2:10ml×2', unitPrice: 1200, unit: '盒', qty: 32, amount: 38400, manufacturer: '九强生物', supplier: '国药控股', productionDate: '2026-04-12', expiryDate: '2027-04-12', batchNo: '250612' },
  { code: '051267', name: 'ALT测定试剂盒', spec: '200T', model: 'R1:50ml×2, R2:25ml×2', unitPrice: 850, unit: '盒', qty: 25, amount: 21250, manufacturer: '美康生物', supplier: '九州通医药', productionDate: '2026-05-08', expiryDate: '2027-05-08', batchNo: '250608' },
  { code: '051268', name: '肌酐测定试剂盒(酶法)', spec: '200T', model: 'R1:40ml×2, R2:20ml×2', unitPrice: 1800, unit: '盒', qty: 18, amount: 32400, manufacturer: '九强生物', supplier: '国药控股', productionDate: '2026-03-25', expiryDate: '2026-12-25', batchNo: '250520' },
  { code: '051269', name: 'CEA检测试剂盒', spec: '100T', model: 'R1:20ml×1, R2:10ml×1', unitPrice: 3200, unit: '盒', qty: 15, amount: 48000, manufacturer: '罗氏诊断', supplier: '华润医药', productionDate: '2026-02-18', expiryDate: '2027-02-18', batchNo: 'LOT-CEA-2409' },
  { code: '051270', name: '凝血酶原时间测定试剂盒', spec: '150T', model: 'R1:30ml×2', unitPrice: 2500, unit: '盒', qty: 12, amount: 30000, manufacturer: '希森美康', supplier: '国药控股', productionDate: '2026-01-20', expiryDate: '2027-01-20', batchNo: 'PT-250120' },
  { code: '051271', name: 'HbA1c检测试剂盒', spec: '100T', model: 'R1:20ml×1, R2:10ml×1', unitPrice: 2800, unit: '盒', qty: 8, amount: 22400, manufacturer: '东曹', supplier: '九州通医药', productionDate: '2026-04-05', expiryDate: '2027-04-05', batchNo: 'A1C-250405' },
  { code: '051272', name: 'hs-CRP检测试剂盒', spec: '200T', model: 'R1:40ml×2, R2:20ml×2', unitPrice: 3200, unit: '盒', qty: 4, amount: 12800, manufacturer: '九强生物', supplier: '国药控股', productionDate: '2026-05-30', expiryDate: '2027-05-30', batchNo: 'CRP-250530' },
  { code: '051273', name: '降钙素原检测试剂盒', spec: '100T', model: 'R1:20ml×1, R2:10ml×1', unitPrice: 4500, unit: '盒', qty: 2, amount: 9000, manufacturer: '罗氏诊断', supplier: '华润医药', productionDate: '2026-03-10', expiryDate: '2027-03-10', batchNo: 'PCT-250310' }
])

function fetchData() { store.fetchFefo(null, selectedGroup.value) }

function handleGroupChange() { fetchData() }

onMounted(() => { fetchData() })

// ── 弹窗 ──
const showTraceDialog = ref(false), traceSearch = ref('')
const showTransferDialog = ref(false), sourceName = ref(''), maxQty = ref(999), transferLoading = ref(false)

const targetWarehouses = [
  { value: 1, label: '中心药库' }, { value: 5, label: '检验科' }
]

function openTrace(row) { traceSearch.value = row.udiDi || row.batchNo || ''; showTraceDialog.value = true }
function openTransfer(row, source) { sourceName.value = source; maxQty.value = row.stock || row.qty || 999; showTransferDialog.value = true }

async function doSubmitTransfer(form) {
  transferLoading.value = true
  try { await store.doTransfer(1, form.targetWarehouse, form.quantity); ElMessage.success('出库成功'); showTransferDialog.value = false; fetchData() }
  catch {} finally { transferLoading.value = false }
}
</script>

<style scoped>
.page-title { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.page-title h2 { font-size: 18px; font-weight: 700; color: var(--text-primary); display: flex; align-items: center; gap: 8px; margin: 0; flex: 1; }
.title-icon { display: inline-flex; align-items: center; justify-content: center; width: 36px; height: 36px; border-radius: 10px; font-size: 18px; }
.sub-tag { font-size: 11px; color: var(--ivd-primary, #059669); background: #ecfdf5; padding: 3px 9px; border-radius: 12px; font-weight: 600; border: 1px solid #a7f3d0; margin-left: 8px; }
.header-right { display: flex; align-items: center; gap: 8px; }
.card-title { font-size: 14px; font-weight: 600; color: var(--text-primary); }
:deep(.el-table th) { background: var(--bg-page); color: var(--text-secondary); font-weight: 600; font-size: 12px; }
</style>
