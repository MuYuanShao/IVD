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
        <el-button :icon="Plus" @click="openAddDialog" type="success" plain>添加行</el-button>
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

    <!-- 手动添加验收明细对话框 -->
    <el-dialog v-model="showAddDialog" title="添加验收明细" width="95%" top="3vh" align-center :close-on-click-modal="false" @closed="resetAddForm">
      <!-- ═══ 表头信息 ═══ -->
      <el-card shadow="never" style="margin-bottom:12px">
        <template #header><span style="font-weight:600;font-size:13px">📋 表头信息</span></template>
        <el-form :model="addHeader" label-width="90px" inline>
          <el-form-item label="收货库房" required>
            <el-select v-model="addHeader.warehouseId" placeholder="选择收货库房" style="width:170px" filterable>
              <el-option v-for="w in warehouses" :key="w.id" :label="w.name" :value="w.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="配送商">
            <el-input v-model="addHeader.distributor" placeholder="配送商名称" style="width:170px" />
          </el-form-item>
          <el-form-item label="采购订单">
            <el-select v-model="addHeader.orderId" placeholder="选择采购订单（可选）" style="width:200px" filterable clearable @change="onOrderSelect">
              <el-option v-for="o in orderOptions" :key="o.id" :label="o.poNo" :value="o.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="验收单号">
            <el-input :model-value="addHeader.acceptanceNo" disabled style="width:190px" />
          </el-form-item>
          <el-form-item label="创建时间">
            <el-input :model-value="addHeader.createdAt" disabled style="width:170px" />
          </el-form-item>
          <el-form-item label="代理商名称">
            <el-input v-model="addHeader.agentName" placeholder="非必填" style="width:170px" />
          </el-form-item>
          <el-form-item label="备注" style="width:100%">
            <el-input v-model="addHeader.remark" placeholder="验收备注" style="width:540px" />
          </el-form-item>
        </el-form>
      </el-card>

      <!-- ═══ 表体明细 ═══ -->
      <el-card shadow="never">
        <template #header>
          <div style="display:flex;align-items:center;justify-content:space-between">
            <span style="font-weight:600;font-size:13px">📦 表体明细（{{ detailLines.length }} 行）</span>
            <div style="display:flex;align-items:center;gap:8px">
              <el-input
                ref="udiScanRef"
                v-model="udiScanCode"
                placeholder="扫描/输入 UDI 码，回车添加"
                size="small"
                style="width:280px"
                clearable
                @keyup.enter="handleUdiScan"
              >
                <template #prefix><el-icon><Camera /></el-icon></template>
              </el-input>
              <el-button type="primary" size="small" :icon="Plus" @click="addDetailLine">添加明细行</el-button>
            </div>
          </div>
        </template>
        <el-table :data="detailLines" stripe size="small" max-height="380" style="width:100%">
          <el-table-column label="试剂名称" min-width="160">
            <template #default="{ row }">
              <el-select v-model="row.reagentId" placeholder="搜索/选择试剂" filterable size="small" style="width:100%" @change="(v) => onReagentSelect(row, v)">
                <el-option v-for="r in reagentOptions" :key="r.value" :label="r.label" :value="r.value" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="通用名称" min-width="120">
            <template #default="{ row }">{{ row.genericName || '—' }}</template>
          </el-table-column>
          <el-table-column label="规格型号" width="110">
            <template #default="{ row }">{{ row.specification || '—' }}</template>
          </el-table-column>
          <el-table-column label="试剂编码" width="110">
            <template #default="{ row }">{{ row.reagentCode || '—' }}</template>
          </el-table-column>
          <el-table-column label="生产日期" width="130">
            <template #default="{ row }">
              <el-date-picker v-model="row.productionDate" type="date" placeholder="选填" size="small" style="width:110px" value-format="YYYY-MM-DD" />
            </template>
          </el-table-column>
          <el-table-column label="失效日期" width="130">
            <template #default="{ row }">
              <el-date-picker v-model="row.expiryDate" type="date" placeholder="选填" size="small" style="width:110px" value-format="YYYY-MM-DD" />
            </template>
          </el-table-column>
          <el-table-column label="批号" width="110">
            <template #default="{ row }">
              <el-input v-model="row.batchNo" placeholder="批号" size="small" style="width:90px" />
            </template>
          </el-table-column>
          <el-table-column label="批次" width="100">
            <template #default="{ row }">
              <el-input v-model="row.batch" placeholder="批次" size="small" style="width:80px" />
            </template>
          </el-table-column>
          <el-table-column label="单位" width="70">
            <template #default="{ row }">{{ row.unit || '盒' }}</template>
          </el-table-column>
          <el-table-column label="单价" width="100">
            <template #default="{ row }">
              <el-input-number v-model="row.unitPrice" :min="0" :precision="2" size="small" style="width:85px" controls-position="right" @change="calcAmount(row)" />
            </template>
          </el-table-column>
          <el-table-column label="验收数量" width="110">
            <template #default="{ row }">
              <el-input-number v-model="row.receiveQty" :min="1" :max="999" size="small" style="width:90px" controls-position="right" @change="calcAmount(row)" />
            </template>
          </el-table-column>
          <el-table-column label="UDI" width="120">
            <template #default="{ row }">
              <el-input v-model="row.udi" placeholder="UDI-DI" size="small" style="width:100px" />
            </template>
          </el-table-column>
          <el-table-column label="验收金额" width="110" align="right">
            <template #default="{ row }">
              <span v-if="row.amount" style="color:#10b981;font-weight:600">¥{{ row.amount.toFixed(2) }}</span>
              <span v-else style="color:#94a3b8">—</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="60" align="center" fixed="right">
            <template #default="{ $index }">
              <el-button link type="danger" size="small" :icon="Delete" @click="detailLines.splice($index, 1)" />
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!detailLines.length" description="暂无明细行，请点击「添加明细行」按钮" :image-size="60" />

        <!-- 汇总 -->
        <div v-if="detailLines.length" style="margin-top:12px;text-align:right;font-size:13px;color:var(--text-secondary)">
          共 <strong>{{ detailLines.length }}</strong> 行，
          总数量 <strong style="color:var(--ivd-primary)">{{ detailLines.reduce((s, r) => s + (r.receiveQty || 0), 0) }}</strong> 件，
          总金额 <strong style="color:#f59e0b">¥{{ detailLines.reduce((s, r) => s + (r.amount || 0), 0).toFixed(2) }}</strong>
        </div>
      </el-card>

      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" :disabled="!detailLines.length" @click="confirmAddItems">确认添加（{{ detailLines.length }} 行）</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Upload, Download, Document, Delete, Camera } from '@element-plus/icons-vue'
import * as XLSX from 'xlsx'
import { useProcurementStore } from '@/stores/procurement'
import { getPoStatusType } from '@/utils/procurement'
import { getCatalogList } from '@/api/catalog'
import { receiveOrder, receiveDirect, getOrderList } from '@/api/procurement'
import { getWarehouseList } from '@/api/inventory'

const route = useRoute()
const router = useRouter()
const store = useProcurementStore()

const submitting = ref(false)
const importFileRef = ref(null)
const showAddDialog = ref(false)
const catalogMap = ref({})
const reagentOptions = ref([])
const warehouses = ref([])
const orderOptions = ref([])

const poContext = reactive({ poNo: '', poStatus: '', supplierName: '', sourceType: '', orderId: null })
const receiveItems = ref([])

// ── 添加验收明细（新对话框）──
function generateAcceptanceNo() {
  const now = new Date()
  const y = now.getFullYear()
  const m = String(now.getMonth() + 1).padStart(2, '0')
  const d = String(now.getDate()).padStart(2, '0')
  const h = String(now.getHours()).padStart(2, '0')
  const i = String(now.getMinutes()).padStart(2, '0')
  const s = String(now.getSeconds()).padStart(2, '0')
  return `ACPT-${y}${m}${d}-${h}${i}${s}`
}

const addHeader = reactive({
  warehouseId: null,
  distributor: '',
  orderId: null,
  acceptanceNo: '',
  createdAt: '',
  agentName: '',
  remark: ''
})

const detailLines = ref([])
const udiScanCode = ref('')
const udiScanRef = ref(null)

function createEmptyDetail() {
  return {
    reagentId: null, genericName: '', specification: '', reagentCode: '',
    productionDate: '', expiryDate: '', batchNo: '', batch: '',
    unit: '盒', unitPrice: null, receiveQty: 1, udi: '', amount: null
  }
}

function addDetailLine() {
  detailLines.value.push(createEmptyDetail())
}

// ── UDI 扫码添加明细行 ──
function parseUdiCode(code) {
  const result = { udiDi: '', lotNo: '', expiryDate: '', serial: '' }
  const gs1 = /\(01\)(\d{14})/i.exec(code)
  if (gs1) {
    result.udiDi = gs1[1]
    const batch = /\(10\)([^(\x1d]+?)(?=\(|$)/i.exec(code)
    if (batch) result.lotNo = batch[1].trim()
    const expiry = /\(17\)(\d{6})/i.exec(code)
    if (expiry) {
      const d = expiry[1]
      result.expiryDate = `20${d.substring(0, 2)}-${d.substring(2, 4)}-${d.substring(4, 6)}`
    }
    const serial = /\(21\)([^(\x1d]+?)(?=\(|$)/i.exec(code)
    if (serial) result.serial = serial[1].trim()
  } else {
    result.udiDi = code.trim()
  }
  return result
}

async function handleUdiScan() {
  const code = udiScanCode.value.trim()
  if (!code) return

  const parsed = parseUdiCode(code)
  const line = createEmptyDetail()
  line.udi = parsed.udiDi

  // 通过 UDI-DI 查找试剂目录
  if (parsed.udiDi) {
    try {
      const catalogRes = await getCatalogList({ udiDi: parsed.udiDi, pageSize: 5, approvalStatus: 'APPROVED' })
      const catalogs = catalogRes?.records || catalogRes?.data?.records || []
      if (catalogs.length > 0) {
        const cat = catalogs[0]
        line.reagentId = cat.id
        line.genericName = cat.genericName || cat.name || ''
        line.specification = cat.specification || ''
        line.reagentCode = cat.hisCode || ''
        line.unit = cat.unit || '盒'
        line.unitPrice = cat.pricePerUnit || null
        line.manufacturer = cat.manufacturer || ''
      }
    } catch { /* UDI 查找失败，仍添加空行 */ }
  }

  // 填充扫码解析结果
  if (parsed.lotNo) line.batchNo = parsed.lotNo
  if (parsed.expiryDate) line.expiryDate = parsed.expiryDate

  detailLines.value.push(line)
  udiScanCode.value = ''

  // 聚焦回扫码框，便于连续扫码
  setTimeout(() => udiScanRef.value?.focus(), 50)
}

function calcAmount(row) {
  if (row.unitPrice && row.receiveQty) {
    row.amount = parseFloat((row.unitPrice * row.receiveQty).toFixed(2))
  } else {
    row.amount = null
  }
}

function onReagentSelect(row, reagentId) {
  const opt = reagentOptions.value.find(r => r.value === reagentId)
  if (!opt) return
  row.genericName = opt.genericName || ''
  row.specification = opt.specification || ''
  row.reagentCode = opt.hisCode || ''
  row.unit = opt.unit || '盒'
  row.unitPrice = opt.pricePerUnit || null
  calcAmount(row)
}

function onOrderSelect(orderId) {
  if (!orderId) return
  const order = orderOptions.value.find(o => o.id === orderId)
  if (order) {
    addHeader.distributor = order.distributor || addHeader.distributor
  }
}

function resetAddForm() {
  addHeader.warehouseId = null
  addHeader.distributor = ''
  addHeader.orderId = null
  addHeader.acceptanceNo = ''
  addHeader.createdAt = ''
  addHeader.agentName = ''
  addHeader.remark = ''
  detailLines.value = []
}

function confirmAddItems() {
  const validLines = detailLines.value.filter(l => l.reagentId)
  if (!validLines.length) { ElMessage.warning('请至少添加一行明细并选择试剂'); return }
  if (!addHeader.warehouseId) { ElMessage.warning('请选择收货库房'); return }

  const now = new Date().toISOString().replace('T', ' ').slice(0, 19)
  for (const line of validLines) {
    receiveItems.value.push({
      itemId: null,
      reagentId: line.reagentId,
      reagentName: line.genericName || `试剂#${line.reagentId}`,
      specification: line.specification || '—',
      manufacturer: reagentOptions.value.find(r => r.value === line.reagentId)?.manufacturer || '—',
      unitPrice: line.unitPrice,
      supplier: addHeader.distributor || poContext.supplierName || '',
      qtyOrdered: line.receiveQty,
      qtyReceived: 0,
      remaining: poContext.orderId || addHeader.orderId ? line.receiveQty : 0,
      receiveQty: line.receiveQty,
      batchNo: line.batchNo,
      expiryDate: line.expiryDate,
      productionDate: line.productionDate,
      // 附加验收头信息
      warehouseId: addHeader.warehouseId,
      acceptanceNo: addHeader.acceptanceNo,
      remark: addHeader.remark
    })
  }
  showAddDialog.value = false
  ElMessage.success(`已添加 ${validLines.length} 行验收明细`)
}

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

function openAddDialog() {
  addHeader.acceptanceNo = generateAcceptanceNo()
  addHeader.createdAt = new Date().toISOString().replace('T', ' ').slice(0, 19)
  addHeader.warehouseId = addHeader.warehouseId || (warehouses.value.length > 0 ? warehouses.value[0].id : null)
  if (!detailLines.value.length) detailLines.value.push(createEmptyDetail())
  showAddDialog.value = true
}

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
  // 加载试剂目录
  try {
    const catalogRes = await getCatalogList({ page: 1, pageSize: 500, approvalStatus: 'APPROVED' })
    const list = catalogRes?.records || catalogRes?.data?.records || []
    catalogMap.value = {}
    reagentOptions.value = list.map(r => ({
      value: r.id, label: `${r.genericName || r.name || '—'} (${r.specification || '—'})`,
      genericName: r.genericName || r.name, specification: r.specification,
      manufacturer: r.manufacturer, hisCode: r.hisCode, pricePerUnit: r.pricePerUnit,
      unit: r.unit || '盒'
    }))
    for (const r of list) {
      catalogMap.value[r.id] = { genericName: r.genericName || r.name || '—', specification: r.specification || '—', manufacturer: r.manufacturer || '—', pricePerUnit: r.pricePerUnit, unit: r.unit || '盒' }
    }
  } catch { /* 静默 */ }

  // 加载仓库列表
  try {
    const whRes = await getWarehouseList({ pageSize: 50 })
    warehouses.value = whRes?.records || whRes?.data?.records || []
  } catch { /* 静默 */ }

  // 加载采购订单列表（供验收头选择 → 仅显示可收货状态）
  try {
    const orderRes = await getOrderList({ pageSize: 200, status: 'SENT' })
    const orderRes2 = await getOrderList({ pageSize: 200, status: 'PARTIAL_RECEIVED' })
    const list1 = orderRes?.records || orderRes?.data?.records || []
    const list2 = orderRes2?.records || orderRes2?.data?.records || []
    // 按 id 去重合并
    const seen = new Set()
    orderOptions.value = [...list1, ...list2].filter(o => { const k = o.id || o.poNo; if (seen.has(k)) return false; seen.add(k); return true })
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
            expiryDate: item.expiryDate || undefined, warehouseId: item.warehouseId || 1
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
        supplierId: 1, warehouseId: toReceive[0]?.warehouseId || 1,
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
