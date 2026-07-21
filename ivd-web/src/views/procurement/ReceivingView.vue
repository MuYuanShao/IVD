<!--
  ReceivingView.vue — 人工验收管理
  支持：有/无采购订单 · Excel 导入/导出 · 手动添加
-->
<template>
  <div class="page-container">
    <div class="page-title">
      <h2><span class="title-icon" style="background:#fef3c7;color:#f59e0b;">📋</span>人工验收管理</h2>
      <span class="sub-tag">手动录入 · Excel批量导入 · 直接验收入库</span>
    </div>

    <!-- PO 上下文卡片 -->
    <el-card shadow="never" style="margin-bottom:16px">
      <div v-if="poContext.poNo" style="display:flex;align-items:center;gap:20px;font-size:13px;flex-wrap:wrap;">
        <span><strong>采购单：</strong>{{ poContext.poNo }}</span>
        <span><strong>供应商：</strong>{{ poContext.supplierName || '—' }}</span>
        <span><strong>状态：</strong>
          <el-tag :type="getPoStatusType(poContext.poStatus)" size="small">{{ poContext.poStatus }}</el-tag>
        </span>
        <span><strong>来源：</strong>
          <el-tag v-if="poContext.sourceType === 'SMART'" type="warning" size="small">智能补货</el-tag>
          <el-tag v-else-if="poContext.sourceType === 'MANUAL'" type="success" size="small">人工采购</el-tag>
          <el-tag v-else-if="poContext.sourceType" type="info" size="small">缺货单</el-tag>
          <span v-else style="color:#94a3b8">—</span>
        </span>
        <el-button link type="primary" size="small" @click="$router.push('/purchase')">← 返回采购订单</el-button>
      </div>
      <div v-else style="display:flex;align-items:center;gap:16px;font-size:13px;flex-wrap:wrap;">
        <strong style="color:#f59e0b;">⚡ 无采购订单 · 直接验收入库</strong>
        <span style="color:var(--text-tertiary)">通过导入Excel或手动添加试剂，直接生成库存批次</span>
      </div>
    </el-card>

    <!-- 工具栏 -->
    <el-card shadow="never" style="margin-bottom:12px">
      <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap;">
        <el-button type="primary" :icon="Upload" @click="triggerImport">导入Excel</el-button>
        <el-button :icon="Download" @click="exportExcel" :disabled="!receiveItems.length">导出Excel</el-button>
        <el-button :icon="Document" @click="downloadTemplate" plain>下载模板</el-button>
        <el-button :icon="Plus" @click="showAddDialog = true" type="success" plain>添加行</el-button>
        <el-button :icon="Delete" @click="clearAll" :disabled="!receiveItems.length" type="danger" plain>清空</el-button>
        <input ref="importFileRef" type="file" accept=".xlsx,.xls,.csv" style="display:none" @change="handleImportFile" />
        <span v-if="receiveItems.length" style="font-size:12px;color:var(--text-tertiary);margin-left:auto">
          {{ receiveSummary.totalItems }} 项，待收 {{ receiveSummary.totalRemaining }} 件
        </span>
      </div>
    </el-card>

    <!-- 验收明细表 -->
    <el-card shadow="never">
      <template #header><span class="card-title">验收明细</span></template>
      <el-table :data="receiveItems" stripe size="small" max-height="480" style="width:100%">
        <el-table-column type="index" label="#" width="45" />
        <el-table-column prop="reagentName" label="试剂名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="specification" label="规格型号" min-width="100" show-overflow-tooltip />
        <el-table-column label="订购数" width="80" align="right">
          <template #default="{row}">{{ row.qtyOrdered || '—' }}</template>
        </el-table-column>
        <el-table-column label="已收数" width="80" align="right">
          <template #default="{row}">
            <span :style="{ color: row.qtyReceived > 0 ? '#10b981' : '#94a3b8' }">{{ row.qtyReceived > 0 ? row.qtyReceived : '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="待收数" width="80" align="right">
          <template #default="{row}">
            <el-tag v-if="!poContext.orderId" type="info" size="small">直收</el-tag>
            <el-tag v-else-if="row.remaining <= 0" type="success" size="small">已收齐</el-tag>
            <span v-else style="color:#f59e0b;font-weight:600">{{ row.remaining }}</span>
          </template>
        </el-table-column>
        <el-table-column label="本次收货" width="110" align="center">
          <template #default="{row}">
            <el-input-number
              v-if="poContext.orderId ? row.remaining > 0 : true"
              v-model="row.receiveQty" :min="0" :max="poContext.orderId ? row.remaining : 999"
              :step="1" step-strictly size="small" style="width:90px"
            />
            <span v-else style="color:#94a3b8;font-size:12px">—</span>
          </template>
        </el-table-column>
        <el-table-column label="批号" width="130">
          <template #default="{row}">
            <el-input v-if="row.receiveQty > 0" v-model="row.batchNo" placeholder="选填" size="small" style="width:110px" />
            <span v-else style="color:#94a3b8;font-size:12px">—</span>
          </template>
        </el-table-column>
        <el-table-column label="效期" width="130">
          <template #default="{row}">
            <el-date-picker v-if="row.receiveQty > 0" v-model="row.expiryDate" type="date" placeholder="选填" size="small" style="width:110px" value-format="YYYY-MM-DD" />
            <span v-else style="color:#94a3b8;font-size:12px">—</span>
          </template>
        </el-table-column>
        <el-table-column label="生产日期" width="130">
          <template #default="{row}">
            <el-date-picker v-if="row.receiveQty > 0" v-model="row.productionDate" type="date" placeholder="选填" size="small" style="width:110px" value-format="YYYY-MM-DD" />
            <span v-else style="color:#94a3b8;font-size:12px">—</span>
          </template>
        </el-table-column>
        <el-table-column label="供应商" width="120" show-overflow-tooltip>
          <template #default="{row}">{{ row.supplier || poContext.supplierName || '—' }}</template>
        </el-table-column>
        <el-table-column label="厂家" min-width="100" show-overflow-tooltip>
          <template #default="{row}">{{ row.manufacturer || '—' }}</template>
        </el-table-column>
        <el-table-column label="单价" width="90" align="right">
          <template #default="{row}">{{ row.unitPrice ? '¥' + row.unitPrice : '—' }}</template>
        </el-table-column>
        <el-table-column label="金额" width="100" align="right">
          <template #default="{row}">
            <span v-if="row.receiveQty > 0 && row.unitPrice" style="color:#10b981;font-weight:600">¥{{ (row.receiveQty * row.unitPrice).toFixed(2) }}</span>
            <span v-else style="color:#94a3b8;font-size:12px">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="60" align="center" fixed="right">
          <template #default="{ $index }">
            <el-button link type="danger" size="small" :icon="Delete" @click="receiveItems.splice($index, 1)" />
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!receiveItems.length" description="暂无验收明细，请导入Excel或手动添加" :image-size="80" />
      <div style="margin-top:16px;text-align:right">
        <el-button @click="$router.push('/purchase')">取消</el-button>
        <el-button type="primary" size="large" :disabled="!hasReceivableItems" :loading="submitting" @click="confirmReceiving">
          确认入库 ({{ receiveSummary.receivableCount }} 项，{{ receiveSummary.receivableQty }} 件)
        </el-button>
      </div>
    </el-card>

    <!-- 手动添加行对话框 -->
    <el-dialog v-model="showAddDialog" title="添加验收明细" width="520px" align-center @closed="resetAddForm">
      <el-form :model="addForm" label-width="90px">
        <el-form-item label="试剂名称" required>
          <el-select v-model="addForm.reagentId" placeholder="搜索/选择试剂" filterable style="width:100%">
            <el-option v-for="r in reagentOptions" :key="r.value" :label="r.label" :value="r.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="收货数量" required>
          <el-input-number v-model="addForm.receiveQty" :min="1" :max="999" style="width:100%" />
        </el-form-item>
        <el-form-item label="批号">
          <el-input v-model="addForm.batchNo" placeholder="选填" />
        </el-form-item>
        <el-form-item label="效期">
          <el-date-picker v-model="addForm.expiryDate" type="date" placeholder="选填" style="width:100%" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="生产日期">
          <el-date-picker v-model="addForm.productionDate" type="date" placeholder="选填" style="width:100%" value-format="YYYY-MM-DD" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmAddItem">添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Upload, Download, Document, Delete } from '@element-plus/icons-vue'
import * as XLSX from 'xlsx'
import { useProcurementStore } from '@/stores/procurement'
import { getPoStatusType } from '@/utils/procurement'
import { getCatalogList } from '@/api/catalog'
import { receiveOrder, receiveDirect } from '@/api/procurement'

const route = useRoute()
const router = useRouter()
const store = useProcurementStore()

const submitting = ref(false)
const importFileRef = ref(null)
const showAddDialog = ref(false)
const catalogMap = ref({})
const reagentOptions = ref([])

const poContext = reactive({ poNo: '', poStatus: '', supplierName: '', sourceType: '', orderId: null })
const receiveItems = ref([])
const addForm = reactive({ reagentId: null, receiveQty: 1, batchNo: '', expiryDate: '', productionDate: '' })

const hasReceivableItems = computed(() => receiveItems.value.some(i => i.receiveQty > 0))
const receiveSummary = computed(() => {
  const items = receiveItems.value
  const receivable = items.filter(i => i.receiveQty > 0)
  return {
    totalItems: items.length,
    totalRemaining: items.reduce((s, i) => s + i.remaining, 0),
    receivableCount: receivable.length,
    receivableQty: receivable.reduce((s, i) => s + i.receiveQty, 0)
  }
})

function findReagent(nameOrCode) {
  const q = (nameOrCode || '').toString().trim().toLowerCase()
  if (!q) return null
  for (const opt of reagentOptions.value) {
    if (opt.genericName && opt.genericName.toLowerCase() === q) return opt
    if (opt.label && opt.label.toLowerCase() === q) return opt
  }
  for (const opt of reagentOptions.value) {
    if (opt.hisCode && opt.hisCode.toLowerCase() === q) return opt
  }
  for (const opt of reagentOptions.value) {
    if (opt.genericName && opt.genericName.toLowerCase().includes(q)) return opt
  }
  return null
}

function buildItem(reagentId, source = {}) {
  const cat = catalogMap.value[reagentId] || {}
  const qtyOrdered = source.qtyOrdered || 0
  const qtyReceived = source.qtyReceived || 0
  const remaining = Math.max(0, qtyOrdered - qtyReceived)
  return {
    itemId: source.id || null, reagentId,
    reagentName: cat.genericName || `试剂#${reagentId}`,
    specification: cat.specification || '—',
    manufacturer: cat.manufacturer || '—',
    unitPrice: source.unitPrice || cat.pricePerUnit || null,
    supplier: poContext.supplierName || '',
    qtyOrdered, qtyReceived, remaining,
    receiveQty: poContext.orderId ? 0 : qtyOrdered,
    batchNo: source.batchNo || '', expiryDate: source.expiryDate || '', productionDate: source.productionDate || ''
  }
}

onMounted(async () => {
  try {
    const catalogRes = await getCatalogList({ page: 1, pageSize: 200 })
    const list = catalogRes?.records || catalogRes?.data?.records || []
    catalogMap.value = {}
    reagentOptions.value = list.map(r => ({
      value: r.id, label: `${r.genericName || r.name || '—'} (${r.specification || '—'})`,
      genericName: r.genericName || r.name, specification: r.specification,
      manufacturer: r.manufacturer, hisCode: r.hisCode, pricePerUnit: r.pricePerUnit
    }))
    for (const r of list) {
      catalogMap.value[r.id] = { genericName: r.genericName || r.name || '—', specification: r.specification || '—', manufacturer: r.manufacturer || '—', pricePerUnit: r.pricePerUnit }
    }
  } catch { /* 静默 */ }

  const { orderId, poNo } = route.query
  if (!orderId) return
  poContext.orderId = orderId; poContext.poNo = poNo || '—'
  try {
    const order = await store.fetchOrderDetail(orderId)
    if (order) {
      poContext.poNo = order.poNo || poNo || '—'; poContext.poStatus = order.status || 'DRAFT'
      poContext.supplierName = order.supplierName || '—'; poContext.sourceType = order.sourceType || ''
      receiveItems.value = (order.items || []).map(it => buildItem(it.reagentId, it))
    }
  } catch { ElMessage.error('加载采购订单数据失败') }
})

// ── Excel 导入 ──
function triggerImport() { importFileRef.value?.click() }

function handleImportFile(e) {
  const file = e.target.files[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = (ev) => {
    try {
      const wb = XLSX.read(ev.target.result, { type: 'array' })
      const ws = wb.Sheets[wb.SheetNames[0]]
      const rows = XLSX.utils.sheet_to_json(ws)
      let added = 0, skipped = 0
      for (const row of rows) {
        const nameOrCode = (row['试剂名称'] || row['试剂编码'] || row['试剂'] || row['reagentName'] || row['编码'] || '').toString().trim()
        const qty = Number(row['收货数量'] || row['数量'] || row['qty'] || row['receiveQty'] || 0)
        if (!nameOrCode || qty <= 0) { skipped++; continue }
        const matched = findReagent(nameOrCode)
        if (!matched) { skipped++; continue }
        receiveItems.value.push({
          itemId: null, reagentId: matched.value,
          reagentName: matched.genericName || '—', specification: matched.specification || '—',
          manufacturer: matched.manufacturer || '—', unitPrice: matched.pricePerUnit || null,
          supplier: poContext.supplierName || '',
          qtyOrdered: qty, qtyReceived: 0, remaining: poContext.orderId ? qty : 0, receiveQty: qty,
          batchNo: (row['批号'] || row['batchNo'] || '').toString().trim(),
          expiryDate: (row['效期'] || row['expiryDate'] || '').toString().trim(),
          productionDate: (row['生产日期'] || row['productionDate'] || '').toString().trim()
        })
        added++
      }
      ElMessage.success(`导入完成：成功 ${added} 项，跳过 ${skipped} 项`)
    } catch (err) { console.error('Excel 解析失败:', err); ElMessage.error('Excel 文件解析失败') }
  }
  reader.readAsArrayBuffer(file)
  importFileRef.value.value = ''
}

// ── Excel 导出 ──
function exportExcel() {
  const data = receiveItems.value.map(r => ({
    '试剂名称': r.reagentName, '规格型号': r.specification,
    '收货数量': r.receiveQty || r.qtyOrdered, '批号': r.batchNo,
    '效期': r.expiryDate, '生产日期': r.productionDate,
    '厂家': r.manufacturer, '单价': r.unitPrice,
    '金额': r.receiveQty > 0 && r.unitPrice ? (r.receiveQty * r.unitPrice).toFixed(2) : ''
  }))
  const ws = XLSX.utils.json_to_sheet(data)
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, '验收明细')
  XLSX.writeFile(wb, `人工验收_${new Date().toISOString().slice(0, 10).replace(/-/g, '')}.xlsx`)
  ElMessage.success('导出成功')
}

function downloadTemplate() {
  const template = [{ '试剂名称': 'CK-MB检测试剂盒', '收货数量': 10, '批号': 'B250701', '效期': '2027-07-01', '生产日期': '2025-07-01' }]
  const ws = XLSX.utils.json_to_sheet(template)
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, '验收模板')
  XLSX.writeFile(wb, '人工验收导入模板.xlsx')
}

// ── 手动添加 ──
function confirmAddItem() {
  if (!addForm.reagentId || addForm.receiveQty < 1) { ElMessage.warning('请选择试剂并填写收货数量'); return }
  const opt = reagentOptions.value.find(r => r.value === addForm.reagentId)
  if (!opt) return
  receiveItems.value.push({
    itemId: null, reagentId: opt.value, reagentName: opt.genericName || '—',
    specification: opt.specification || '—', manufacturer: opt.manufacturer || '—',
    unitPrice: opt.pricePerUnit || null, supplier: poContext.supplierName || '',
    qtyOrdered: addForm.receiveQty, qtyReceived: 0,
    remaining: poContext.orderId ? addForm.receiveQty : 0, receiveQty: addForm.receiveQty,
    batchNo: addForm.batchNo, expiryDate: addForm.expiryDate, productionDate: addForm.productionDate
  })
  showAddDialog.value = false; ElMessage.success('已添加')
}

function resetAddForm() { Object.assign(addForm, { reagentId: null, receiveQty: 1, batchNo: '', expiryDate: '', productionDate: '' }) }
function clearAll() {
  ElMessageBox.confirm('确定要清空所有验收明细吗？', '确认清空', { type: 'warning' })
    .then(() => { receiveItems.value = [] }).catch(() => {})
}

// ── 确认入库 ──
async function confirmReceiving() {
  const toReceive = receiveItems.value.filter(i => i.receiveQty > 0)
  if (!toReceive.length) return
  try {
    await ElMessageBox.confirm(
      `确认对 ${toReceive.length} 项试剂进行人工验收入库，共 ${toReceive.reduce((s, i) => s + i.receiveQty, 0)} 件？`,
      '确认入库', { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' }
    )
  } catch { return }

  submitting.value = true
  try {
    if (poContext.orderId) {
      let s = 0, f = 0
      for (const item of toReceive) {
        try {
          await receiveOrder({
            orderId: Number(poContext.orderId), itemId: item.itemId, qtyReceived: item.receiveQty,
            batchNo: item.batchNo || undefined, productionDate: item.productionDate || undefined,
            expiryDate: item.expiryDate || undefined, warehouseId: 1
          })
          s++
        } catch (e) { f++; console.error(`验收失败 itemId=${item.itemId}:`, e) }
      }
      if (s > 0) ElMessage.success(`验收完成：成功 ${s} 项` + (f > 0 ? `，失败 ${f} 项` : ''))
      else ElMessage.error('验收失败')
      try {
        const order = await store.fetchOrderDetail(poContext.orderId)
        if (order) { poContext.poStatus = order.status || poContext.poStatus; receiveItems.value = (order.items || []).map(it => buildItem(it.reagentId, it)) }
      } catch { /* 静默 */ }
    } else {
      const res = await receiveDirect({
        supplierId: 1, warehouseId: 1,
        items: toReceive.map(i => ({
          reagentId: i.reagentId, qtyReceived: i.receiveQty,
          batchNo: i.batchNo || null, productionDate: i.productionDate || null,
          expiryDate: i.expiryDate || null, unitPrice: i.unitPrice
        }))
      })
      ElMessage.success(`直收完成：PO ${res.poNo}，成功 ${res.successCount} 项，总金额 ¥${res.totalAmount}`)
      receiveItems.value = receiveItems.value.filter(i => i.receiveQty <= 0)
      if (res && res.poId) {
        poContext.orderId = res.poId; poContext.poNo = res.poNo || ''
        poContext.sourceType = 'MANUAL'; poContext.poStatus = 'COMPLETED'
      }
    }
  } catch (e) { console.error('验收失败:', e); ElMessage.error('验收提交失败') }
  finally { submitting.value = false }
}
</script>

<style scoped>
.card-title { font-size: 14px; font-weight: 600; color: var(--text-primary); }
:deep(.el-table th) { background: var(--bg-page); color: var(--text-secondary); font-weight: 600; font-size: 12px; }
</style>
