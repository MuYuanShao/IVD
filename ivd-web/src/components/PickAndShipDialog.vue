<!--
  PickAndShipDialog.vue — 拣货发货弹窗
  库管员对已审核的申领单执行拣货出库 → 科室库收货
-->
<template>
  <el-dialog
    :model-value="modelValue"
    :title="isShipMode ? '发货 — UDI扫码绑定' : '拣货'"
    width="1100px"
    align-center
    @update:model-value="$emit('update:modelValue', $event)"
    @closed="resetForm"
  >
    <el-descriptions :column="2" border size="small" style="margin-bottom:16px">
      <el-descriptions-item label="申领单号">{{ requisition?.reqNo || '-' }}</el-descriptions-item>
      <el-descriptions-item label="申领科室">{{ requisition?.deptName || '-' }}</el-descriptions-item>
    </el-descriptions>

    <div style="display:flex;gap:16px;margin-bottom:16px">
      <div style="flex:1">
        <label style="font-size:13px;color:var(--text-secondary);margin-bottom:4px;display:block">源库房（拣货出库）<span style="color:var(--ivd-primary);font-size:11px"> 自动</span></label>
        <div class="readonly-warehouse">
          <span v-if="sourceWarehouseName">{{ sourceWarehouseName }}</span>
          <el-icon v-else class="is-loading"><Loading /></el-icon>
        </div>
      </div>
      <div style="flex:1">
        <label style="font-size:13px;color:var(--text-secondary);margin-bottom:4px;display:block">目标科室库（收货）<span style="color:var(--ivd-primary);font-size:11px"> 自动</span></label>
        <div class="readonly-warehouse">
          <span v-if="targetWarehouseName">{{ targetWarehouseName }}</span>
          <el-icon v-else class="is-loading"><Loading /></el-icon>
        </div>
      </div>
    </div>

    <!-- 发货模式：UDI 扫码绑定 -->
    <div v-if="isShipMode" class="udi-scan-section">
      <div class="udi-scan-header">
        <span class="udi-scan-title">📱 UDI 扫码绑定</span>
        <span class="udi-scan-hint">扫描试剂 UDI/唯一码自动回填绑定，批次按 FEFO 自动匹配</span>
      </div>
      <div class="udi-scan-input-row">
        <el-input
          ref="udiInputRef"
          v-model="udiScanCode"
          placeholder="扫描或输入 UDI / 唯一码，按回车确认"
          :prefix-icon="Search"
          clearable
          size="default"
          @keyup.enter="handleUdiScan"
        >
          <template #append>
            <el-button :icon="Search" :loading="udiScanning" @click="handleUdiScan">识别绑定</el-button>
          </template>
        </el-input>
      </div>
      <!-- 扫描记录 -->
      <div v-if="udiScanLog.length" class="udi-scan-log">
        <div v-for="(log, i) in udiScanLog" :key="i" class="udi-scan-log-item" :class="{ 'is-error': !log.success }">
          <el-icon v-if="log.success" style="color:#10b981"><CircleCheck /></el-icon>
          <el-icon v-else style="color:#ef4444"><CircleClose /></el-icon>
          <span class="udi-scan-log-code">{{ log.code }}</span>
          <span v-if="log.success">→ {{ log.reagentName }}（{{ log.batchNo }}） +{{ log.qty }}</span>
          <span v-else class="udi-scan-log-error">{{ log.error }}</span>
        </div>
      </div>
    </div>

    <el-table :data="pickItems" size="small" border max-height="400">
      <el-table-column prop="reagentName" label="试剂名称" min-width="120" show-overflow-tooltip fixed="left" />
      <el-table-column prop="genericName" label="通用名称" min-width="110" show-overflow-tooltip />
      <el-table-column prop="specification" label="规格型号" min-width="85" show-overflow-tooltip />
      <el-table-column prop="manufacturer" label="厂家" min-width="100" show-overflow-tooltip />
      <el-table-column prop="unit" label="单位" width="50" />
      <el-table-column prop="unitPrice" label="单价" width="75" align="right">
        <template #default="{row}">{{ row.unitPrice != null ? '¥' + Number(row.unitPrice).toFixed(2) : '-' }}</template>
      </el-table-column>
      <el-table-column prop="qtyRequested" label="申领量" width="65" align="right" />
      <el-table-column label="金额" width="75" align="right">
        <template #default="{row}">{{ row.amount != null ? '¥' + Number(row.amount).toFixed(2) : '-' }}</template>
      </el-table-column>
      <el-table-column prop="qtyFulfilled" label="已发货" width="65" align="right" />
      <el-table-column label="待发货" width="65" align="right">
        <template #default="{row}">{{ getRemaining(row) }}</template>
      </el-table-column>
      <el-table-column label="批次选择" width="200">
        <template #default="{row}">
          <el-select
            v-model="row._batchId"
            placeholder="选择批次"
            size="small"
            style="width:100%"
            :disabled="getRemaining(row) <= 0"
            @focus="loadBatches(row)"
          >
            <el-option
              v-for="b in (row._batches || [])"
              :key="b.id || b.batchId"
              :label="(b.batchNo || '—') + ' | 库存:' + (b.stock ?? b.qtyAvailable ?? 0) + ' | 效期:' + (b.expiryDate || '—')"
              :value="b.id || b.batchId"
            />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="拣货数量" width="110" fixed="right">
        <template #default="{row}">
          <el-input-number
            v-model="row._pickQty"
            :min="1"
            :max="getRemaining(row)"
            size="small"
            style="width:100%"
            :disabled="getRemaining(row) <= 0"
          />
        </template>
      </el-table-column>
    </el-table>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">{{ isShipMode ? '确认发货' : '确认拣货' }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch, computed, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading, Search, CircleCheck, CircleClose } from '@element-plus/icons-vue'
import { getFefoRecommendations, traceUdi } from '@/api/inventory'
import { getWarehouseOptions } from '@/api/system'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  requisition: { type: Object, default: () => ({}) }
})

const emit = defineEmits(['update:modelValue', 'submit'])

const sourceWarehouseId = ref(null)
const targetWarehouseId = ref(null)
const sourceWarehouseName = ref('')
const targetWarehouseName = ref('')
const pickItems = ref([])
const submitting = ref(false)

// ── 发货模式: UDI 扫码 ──
const isShipMode = computed(() => props.requisition?.mode === 'ship')
const udiScanCode = ref('')
const udiScanning = ref(false)
const udiScanLog = ref([])  // { code, success, reagentName, batchNo, qty, error }
const udiInputRef = ref(null)

// Load warehouse options on open
watch(() => props.modelValue, async (open) => {
  if (open) {
    await loadWarehouseOptions()
    initPickItems()
    // 发货模式：自动聚焦 UDI 扫码输入框
    if (isShipMode.value) {
      nextTick(() => udiInputRef.value?.focus())
    }
  }
})

async function loadWarehouseOptions() {
  try {
    const req = props.requisition
    // 源库房：固定为一级库房（中心库），自动获取第一个启用的中心库
    const centers = await getWarehouseOptions(1)
    if (centers && centers.length) {
      sourceWarehouseId.value = centers[0].id
      sourceWarehouseName.value = centers[0].name || ('中心库#' + centers[0].id)
    } else {
      sourceWarehouseName.value = '未找到中心库房'
    }

    // 目标科室库：固定为申领科室关联的科室库（deptId = fromDeptId, level = 2）
    if (req.fromDeptId) {
      const deptWhs = await getWarehouseOptions(2)
      const matched = (deptWhs || []).find(w => Number(w.deptId) === Number(req.fromDeptId))
      if (matched) {
        targetWarehouseId.value = matched.id
        targetWarehouseName.value = matched.name || ('科室库#' + matched.id)
      } else {
        targetWarehouseName.value = '未找到科室库房（科室ID:' + req.fromDeptId + '）'
      }
    }
  } catch (e) {
    console.error('加载库房信息失败:', e)
  }
}

function initPickItems() {
  const items = props.requisition?.items || []
  pickItems.value = items.map(item => ({
    ...item,
    _batchId: null,
    _batches: [],
    _pickQty: getRemaining(item) > 0 ? getRemaining(item) : 0
  }))
}

function getRemaining(row) {
  return Math.max(0, (row.qtyRequested || 0) - (row.qtyFulfilled || 0))
}

async function loadBatches(row) {
  if (!row.reagentId) return
  if (!sourceWarehouseId.value) {
    ElMessage.warning('源库房信息未加载，请稍后重试')
    return
  }
  if (row._batches && row._batches.length) return // already loaded
  try {
    const batches = await getFefoRecommendations(row.reagentId, sourceWarehouseId.value)
    row._batches = (batches && batches.length) ? batches : []
    // 发货模式：FEFO 自动选择第一个（最早到期）批次
    if (isShipMode.value && row._batches.length && !row._batchId) {
      row._batchId = row._batches[0].id || row._batches[0].batchId
    }
  } catch (e) {
    console.error('加载FEFO批次失败:', e)
    row._batches = []
  }
}

// ── UDI 扫码绑定（发货模式）──
async function handleUdiScan() {
  const code = udiScanCode.value?.trim()
  if (!code) {
    ElMessage.warning('请输入或扫描 UDI 码')
    return
  }
  udiScanning.value = true
  try {
    // 1. 通过 UDI 追溯查询批次信息
    const traceResult = await traceUdi(code)
    if (!traceResult || !traceResult.reagentId) {
      addScanLog(code, false, '', '', 0, '未找到该 UDI 对应的试剂信息')
      return
    }

    const reagentId = traceResult.reagentId
    const traceBatchId = traceResult.batchId
    const traceBatchNo = traceResult.batchNo || '—'

    // 2. 在申领明细中匹配试剂
    const matchedItem = pickItems.value.find(item => Number(item.reagentId) === Number(reagentId))
    if (!matchedItem) {
      addScanLog(code, false, '', '', 0, '该 UDI 试剂不在当前申领清单中')
      return
    }

    // 3. 检查是否还有待发货数量
    const remaining = getRemaining(matchedItem)
    if (remaining <= 0) {
      addScanLog(code, false, matchedItem.reagentName, traceBatchNo, 0, '该试剂已全部发货')
      return
    }

    // 4. 加载/刷新批次列表（如果还没加载）
    if (!matchedItem._batches || !matchedItem._batches.length) {
      await loadBatches(matchedItem)
    }

    // 5. 优先匹配 UDI 追溯到的批次，否则使用 FEFO 第一个
    if (traceBatchId && matchedItem._batches && matchedItem._batches.length) {
      const traceMatched = matchedItem._batches.find(
        b => String(b.id || b.batchId) === String(traceBatchId)
      )
      if (traceMatched) {
        matchedItem._batchId = traceMatched.id || traceMatched.batchId
      } else if (!matchedItem._batchId) {
        // FEFO 自动选择第一个
        matchedItem._batchId = matchedItem._batches[0].id || matchedItem._batches[0].batchId
      }
    }

    // 6. 自增拣货数量（默认 +1）
    const currentPick = matchedItem._pickQty || 0
    matchedItem._pickQty = Math.min(currentPick + 1, remaining)

    // 7. 记录扫描日志
    const batchLabel = matchedItem._batches?.find(
      b => String(b.id || b.batchId) === String(matchedItem._batchId)
    )?.batchNo || traceBatchNo
    addScanLog(code, true, matchedItem.reagentName, batchLabel, 1)

    // 清空输入框准备下一次扫描
    udiScanCode.value = ''
    nextTick(() => udiInputRef.value?.focus())
  } catch (e) {
    console.error('UDI扫描失败:', e)
    addScanLog(code, false, '', '', 0, e?.message || 'UDI 识别失败')
  } finally {
    udiScanning.value = false
  }
}

function addScanLog(code, success, reagentName, batchNo, qty, error) {
  udiScanLog.value.unshift({ code, success, reagentName, batchNo, qty, error })
  // 只保留最近 20 条
  if (udiScanLog.value.length > 20) udiScanLog.value = udiScanLog.value.slice(0, 20)
}

function handleSubmit() {
  // validate
  if (!targetWarehouseId.value) {
    ElMessage.warning('目标科室库信息未加载，请稍后重试')
    return
  }
  if (!sourceWarehouseId.value) {
    ElMessage.warning('源库房信息未加载，请稍后重试')
    return
  }

  const pickList = []
  for (const item of pickItems.value) {
    if (getRemaining(item) <= 0) continue
    if (!item._batchId) {
      ElMessage.warning(`请为试剂「${item.reagentName}」选择批次`)
      return
    }
    if (!item._pickQty || item._pickQty <= 0) {
      ElMessage.warning(`请为试剂「${item.reagentName}」填写拣货数量`)
      return
    }
    if (item._pickQty > getRemaining(item)) {
      ElMessage.warning(`试剂「${item.reagentName}」拣货数量不能超过待发货数量`)
      return
    }
    pickList.push({
      reqItemId: item.id || item.reqItemId,
      sourceBatchId: item._batchId,
      pickQty: item._pickQty
    })
  }

  if (!pickList.length) {
    ElMessage.warning('没有需要拣货的试剂')
    return
  }

  emit('submit', {
    sourceWarehouseId: sourceWarehouseId.value,
    targetWarehouseId: targetWarehouseId.value,
    items: pickList
  })
}

function resetForm() {
  sourceWarehouseId.value = null
  targetWarehouseId.value = null
  sourceWarehouseName.value = ''
  targetWarehouseName.value = ''
  pickItems.value = []
  udiScanCode.value = ''
  udiScanLog.value = []
}
</script>

<style scoped>
.readonly-warehouse {
  padding: 8px 12px;
  background: var(--bg-page, #f5f7fa);
  border: 1px solid var(--border-color, #dcdfe6);
  border-radius: 4px;
  font-size: 14px;
  color: var(--text-primary, #303133);
  min-height: 36px;
  display: flex;
  align-items: center;
}

/* ── UDI 扫码绑定区域 ── */
.udi-scan-section {
  margin-bottom: 16px;
  padding: 12px 16px;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  border-radius: var(--radius-lg, 8px);
}
.udi-scan-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.udi-scan-title {
  font-size: 14px;
  font-weight: 600;
  color: #166534;
}
.udi-scan-hint {
  font-size: 12px;
  color: #6b7280;
}
.udi-scan-input-row {
  margin-bottom: 8px;
}
.udi-scan-log {
  max-height: 120px;
  overflow-y: auto;
  font-size: 12px;
}
.udi-scan-log-item {
  padding: 2px 0;
  display: flex;
  align-items: center;
  gap: 6px;
  color: #166534;
}
.udi-scan-log-item.is-error {
  color: #991b1b;
}
.udi-scan-log-code {
  font-family: monospace;
  background: rgba(0,0,0,0.06);
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 11px;
}
.udi-scan-log-error {
  color: #ef4444;
}
</style>
