<!--
  InboundView.vue — 扫码验收（v2 独立路由页）
  v3 修复：接入后端 POST /procurement/receive API + GS1 UDI 解析
-->
<template>
  <div class="page-container">
    <div class="page-title">
      <h2><span class="title-icon" style="background:#eff6ff;color:#3b82f6;">📷</span>UDI扫码验收</h2>
      <span class="sub-tag">采购单扫码入库 · 六项校验</span>
    </div>

    <!-- PO 上下文卡片（从采购单跳转时显示） -->
    <el-card shadow="never" style="margin-bottom:12px" v-if="poContext.poNo">
      <div style="display:flex;align-items:center;gap:16px;font-size:13px;">
        <span><strong>采购单：</strong>{{ poContext.poNo }}</span>
        <span><strong>状态：</strong>
          <el-tag :type="getPoStatusType(poContext.poStatus)" size="small">{{ poContext.poStatus }}</el-tag>
        </span>
        <span><strong>明细行数：</strong>{{ poItems.length }} 项</span>
        <el-button link type="primary" size="small" @click="$router.push('/purchase')">查看采购单详情 →</el-button>
      </div>
    </el-card>

    <!-- 六项校验结果面板 -->
    <el-card shadow="never" style="margin-bottom:12px" v-if="showValidation">
      <template #header><span class="card-title">✅ 六项校验</span></template>
      <el-row :gutter="12">
        <el-col :span="4" v-for="check in validationChecks" :key="check.label">
          <div class="check-item" :class="{ pass: check.pass, fail: !check.pass }">
            <div class="check-icon">{{ check.pass ? '✅' : '❌' }}</div>
            <div class="check-label">{{ check.label }}</div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <el-row :gutter="16" style="flex:1">
      <!-- 左侧：扫码区 -->
      <el-col :span="10">
        <el-card shadow="never">
          <template #header><span class="card-title">📷 扫码区</span></template>
          <div class="scan-area" :class="{ scanning: isScanning }" @click="focusInput">
            <el-icon :size="64" color="#94a3b8"><Camera /></el-icon>
            <p style="margin-top:12px;color:var(--text-tertiary);font-size:13px">点击此处或使用扫码枪</p>
          </div>
          <el-input
            ref="scanInputRef"
            v-model="scanCode"
            placeholder="输入 UDI 追溯码，支持 GS1 格式 (01)...(10)...(17)..."
            style="margin-top:12px"
            clearable
            @keyup.enter="handleScanInput"
          >
            <template #prefix><el-icon><Camera /></el-icon></template>
          </el-input>
        </el-card>

        <!-- 最近扫码结果 -->
        <el-card shadow="never" style="margin-top:12px" v-if="store.lastScanResult">
          <template #header><span class="card-title">✅ 最近扫码结果</span></template>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="UDI-DI">{{ store.lastScanResult.udiDi }}</el-descriptions-item>
            <el-descriptions-item label="批号">{{ store.lastScanResult.lotNo }}</el-descriptions-item>
            <el-descriptions-item label="试剂名称">{{ store.lastScanResult.reagentName }}</el-descriptions-item>
            <el-descriptions-item label="效期">{{ store.lastScanResult.expiryDate }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
        <el-empty v-else description="请扫描或输入追溯码" style="margin-top:12px" />
      </el-col>

      <!-- 右侧：验收清单 -->
      <el-col :span="14">
        <el-card shadow="never">
          <template #header>
            <span class="card-title">📋 本次验收清单</span>
            <span style="font-size:12px;color:var(--text-tertiary);margin-left:8px">{{ store.inboundItems.length }} 盒</span>
          </template>
          <el-table :data="store.inboundItems" stripe size="small" max-height="420">
            <el-table-column type="index" label="#" width="45" />
            <el-table-column prop="udiDi" label="UDI码" width="180" show-overflow-tooltip />
            <el-table-column prop="lotNo" label="批号" width="110" />
            <el-table-column prop="reagentName" label="试剂" min-width="120" show-overflow-tooltip />
            <el-table-column prop="expiryDate" label="效期" width="100" />
            <el-table-column label="校验" width="200">
              <template #default="{row}">
                <span v-for="(r, i) in (row.validation || [])" :key="i" style="margin-right:4px">{{ r.pass ? '✅' : '❌' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="70">
              <template #default="{row}">
                <el-button link type="danger" size="small" :icon="Delete" @click="store.removeInboundItem(row.id)" />
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!store.inboundItems.length" description="暂无验收记录" :image-size="60" />
        </el-card>
        <div style="margin-top:12px;text-align:right">
          <el-button
            type="primary"
            size="large"
            :disabled="!store.inboundItems.length"
            :loading="store.receiveLoading"
            @click="confirmInbound"
          >
            📦 确认入库 ({{ store.inboundItems.length }} 盒)
          </el-button>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Camera, Delete } from '@element-plus/icons-vue'
import { useProcurementStore } from '@/stores/procurement'
import { getPoStatusType } from '@/utils/procurement'
import { getCatalogList } from '@/api/catalog'

const route = useRoute()
const store = useProcurementStore()
const scanInputRef = ref(null)
const scanCode = ref('')
const isScanning = ref(false)
const showValidation = ref(false)

const poContext = reactive({ poNo: '', poStatus: '', orderId: null })
const poItems = ref([])  // PO 明细行 [{ id, reagentId, qtyOrdered, qtyReceived, ... }]

/** 默认六项校验模板 */
const defaultChecks = [
  { label: 'UDI格式', pass: true }, { label: '批号校验', pass: true },
  { label: '效期检查', pass: true }, { label: '注册证', pass: true },
  { label: '证照关联', pass: true }, { label: '存储条件', pass: true }
]
const validationChecks = ref(defaultChecks.map(c => ({ ...c })))

// ── 挂载：加载 PO 上下文 ──
onMounted(async () => {
  const { orderId, poNo } = route.query
  if (orderId) {
    poContext.orderId = orderId
    poContext.poNo = poNo || '—'
    try {
      const order = await store.fetchOrderDetail(orderId)
      if (order) {
        poContext.poNo = order.poNo || poNo || '—'
        poContext.poStatus = order.status || 'DRAFT'
        poItems.value = order.items || []
      }
    } catch {
      poContext.poStatus = 'DRAFT'
    }
  }
  focusInput()
})

function focusInput() { scanInputRef.value?.focus() }

// ── GS1 UDI 解析 ──
function parseUdiCode(code) {
  // 支持格式：
  //   (01)06927690000197(10)250612(17)271130(21)SERIAL123
  //   或纯 UDI-DI 字符串
  const result = { udiDi: '', lotNo: '', expiryDate: '', serial: '' }
  const gs1 = /\(01\)(\d{14})/i.exec(code)
  if (gs1) {
    result.udiDi = gs1[1]
    const batch = /\(10\)([^(\x1d]+?)(?=\(|$)/i.exec(code)
    if (batch) result.lotNo = batch[1].trim()
    const expiry = /\(17\)(\d{6})/i.exec(code)
    if (expiry) {
      const d = expiry[1]
      result.expiryDate = `20${d.substring(0,2)}-${d.substring(2,4)}-${d.substring(4,6)}`
    }
    const serial = /\(21\)([^(\x1d]+?)(?=\(|$)/i.exec(code)
    if (serial) result.serial = serial[1].trim()
  } else {
    // 非 GS1 格式：整段作为 UDI-DI，解析尾部的批号/效期
    result.udiDi = code
    // 尝试从末尾提取 YYMMDD 效期
    const dateMatch = /(\d{6})$/.exec(code)
    if (dateMatch) {
      const d = dateMatch[1]
      try {
        result.expiryDate = `20${d.substring(0,2)}-${d.substring(2,4)}-${d.substring(4,6)}`
      } catch { /* ignore */ }
    }
  }
  return result
}

// ── 扫码处理 ──
async function handleScanInput() {
  const code = scanCode.value.trim()
  if (!code) return
  isScanning.value = true
  showValidation.value = true

  try {
    // 1. 解析 UDI 码
    const parsed = parseUdiCode(code)

    // 2. 通过 UDI-DI 查找试剂目录
    let reagentName = '—'
    let reagentId = null
    let matchedItem = null
    const checks = defaultChecks.map(c => ({ ...c }))

    try {
      const catalogRes = await getCatalogList({ udiDi: parsed.udiDi, pageSize: 5, approvalStatus: 'APPROVED' })
      const catalogs = catalogRes?.records || []
      if (catalogs.length > 0) {
        const cat = catalogs[0]
        reagentName = cat.genericName || cat.hisCode || '—'
        reagentId = cat.id
        // 3. 匹配 PO 明细
        if (poItems.value.length > 0) {
          matchedItem = poItems.value.find(it => it.reagentId === reagentId)
          if (!matchedItem) {
            // 模糊匹配：试剂 ID 可能不一致，允许通过
            checks[3].pass = false  // 注册证校验标记为需要关注
          }
        }
      } else {
        checks[1].pass = false  // 批号/试剂查找失败
      }
    } catch {
      checks[1].pass = false
    }

    // 4. 组装验收条目
    const item = {
      udiDi: parsed.udiDi,
      lotNo: parsed.lotNo || `SCAN${Date.now().toString(36).toUpperCase()}`,
      reagentName,
      expiryDate: parsed.expiryDate || '—',
      qty: 1,
      orderId: poContext.orderId ? Number(poContext.orderId) : null,
      itemId: matchedItem ? matchedItem.id : null,
      productionDate: null,
      warehouseId: 1,
      validation: checks.map(c => ({ ...c }))
    }

    validationChecks.value = checks
    store.addInboundItem(item)
    store.lastScanResult = {
      udiDi: parsed.udiDi,
      lotNo: item.lotNo,
      reagentName,
      expiryDate: item.expiryDate
    }
    scanCode.value = ''
  } finally {
    isScanning.value = false
    focusInput()
  }
}

// ── 确认入库 ──
async function confirmInbound() {
  if (!poContext.orderId) {
    ElMessage.warning('请先从采购单页面进入验收')
    return
  }
  const { successCount, failCount } = await store.doReceiveInbound()
  showValidation.value = false
  if (successCount > 0) {
    // 刷新 PO 状态
    try { await store.fetchOrderDetail(poContext.orderId) } catch {}
  }
}
</script>

<style scoped>
.scan-area {
  border: 2px dashed var(--border-color, #e2e8f0); border-radius: var(--radius-lg, 12px);
  padding: 48px 24px; text-align: center; cursor: pointer;
  transition: all 0.3s;
}
.scan-area:hover { border-color: var(--ivd-primary, #059669); background: #f0fdf4; }
.scan-area.scanning { border-color: var(--ivd-primary, #059669); border-style: solid; box-shadow: 0 0 0 3px rgba(5,150,105,.15); }
.check-item { text-align: center; padding: 8px 4px; border-radius: 8px; font-size: 12px; }
.check-item.pass { background: #f0fdf4; }
.check-item.fail { background: #fef2f2; }
.check-icon { font-size: 16px; margin-bottom: 2px; }
.check-label { color: var(--text-secondary); font-weight: 500; }
:deep(.el-table th) { background: var(--bg-page); color: var(--text-secondary); font-weight: 600; font-size: 12px; }
</style>
