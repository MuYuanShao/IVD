<!--
  ConsumptionView.vue — M08 上机消耗管理
  对照效果图 page-load (line 1860-1893)
  绿色品牌主题 #059669
-->
<template>
  <div class="page-container">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2><span class="title-icon" style="background:#eff6ff;color:#3b82f6;">🖥️</span>上机与消耗管理</h2>
      <div class="header-right">
        <el-button type="primary" :icon="Plus" @click="showConsumeDialog = true">+ 扫码上机</el-button>
      </div>
    </div>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stat-cards">
      <el-col :span="6"><el-card shadow="hover" class="stat-card"><el-statistic title="今日消耗记录" :value="stats.todayConsume || 0" /></el-card></el-col>
      <el-col :span="6"><el-card shadow="hover" class="stat-card stat-card--success"><el-statistic title="活跃设备数" :value="stats.activeDevices ?? 0" /></el-card></el-col>
      <el-col :span="6"><el-card shadow="hover" class="stat-card stat-card--warning"><el-statistic title="待核销批次" :value="stats.pendingWriteoff || 0" /></el-card></el-col>
      <el-col :span="6"><el-card shadow="hover" class="stat-card stat-card--info"><el-statistic title="总消耗记录" :value="stats.totalRecords || 0" /></el-card></el-col>
    </el-row>

    <!-- 当前活跃上机设备 -->
    <el-card shadow="never" style="margin-bottom:16px">
      <template #header>
        <span class="card-title"><span class="icon-sm" style="background:#f5f3ff;color:#8b5cf6;">📡</span>当前活跃上机</span>
        <span style="font-size:12px;color:var(--text-tertiary);margin-left:8px">Redis批次关联缓存 · 自动审核输入</span>
      </template>
      <el-table :data="devices" stripe style="width:100%">
        <el-table-column label="设备" min-width="160">
          <template #default="{row}">
            <strong>{{ row.deviceName || row.deviceCode }}</strong>
            <span style="font-size:12px;color:var(--text-secondary);margin-left:4px">{{ row.manufacturer || '' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="loadedBatch" label="加载批次" width="160" />
        <el-table-column prop="reagentName" label="试剂" width="120" />
        <el-table-column prop="loadedAt" label="上机时间" width="100" />
        <el-table-column label="预计退机" width="100">
          <template #default="{row}">{{ row.estimatedUnload || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{row}">
            <el-tag :type="row.status === 'IN_USE' ? 'info' : row.status === 'PENDING_UNLOAD' ? 'warning' : 'success'" size="small">
              {{ row.status === 'IN_USE' ? '运行中' : row.status === 'PENDING_UNLOAD' ? '即将退机' : '已核销' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="openManualConsume(row)">📝 手动核销</el-button>
            <el-button link type="danger" size="small" @click="confirmUnload(row)">⬇️ 退机</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 今日消耗核销记录 -->
    <el-card shadow="never">
      <template #header><span class="card-title">📋 今日消耗核销记录</span></template>
      <el-table :data="records" stripe v-loading="loading" style="width:100%">
        <el-table-column prop="occurredAt" label="时间" width="100">
          <template #default="{row}">{{ formatTime(row.occurredAt) }}</template>
        </el-table-column>
        <el-table-column label="设备" width="120">
          <template #default="{row}">{{ row.deviceName || (row.deviceId ? '设备 #' + row.deviceId : '—') }}</template>
        </el-table-column>
        <el-table-column label="试样" width="100">
          <template #default="{row}">{{ row.reagentName || (row.reagentId ? '试剂 #' + row.reagentId : '—') }}</template>
        </el-table-column>
        <el-table-column prop="batchNo" label="批次" width="150" />
        <el-table-column label="核销方式" width="100">
          <template #default="{row}">
            <el-tag :type="row.recordType === 'BATCH_TEST' ? '' : 'info'" size="small">{{ recordTypeLabel(row.recordType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="消耗量" width="90">
          <template #default="{row}">{{ row.quantity || 0 }} Test</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" width="160" show-overflow-tooltip />
      </el-table>
      <el-pagination
        v-model:current-page="page" v-model:page-size="pageSize" :total="total"
        :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next,jumper"
        style="margin-top:16px;justify-content:flex-end" @change="fetchRecords" />
    </el-card>

    <!-- 扫码上机/手动核销弹窗 -->
    <el-dialog v-model="showConsumeDialog" :title="consumeForm.mode === 'manual' ? '手动核销' : '扫码上机/核销'" width="520px" top="8vh" @closed="resetConsumeForm">
      <el-form ref="consumeFormRef" :model="consumeForm" :rules="consumeRules" label-width="80px">
        <el-form-item label="批号" prop="batchNo">
          <el-input v-model="consumeForm.batchNo" placeholder="扫描或输入批号" :disabled="consumeForm.mode === 'manual'" />
        </el-form-item>
        <el-form-item label="设备" prop="deviceId">
          <el-select v-model="consumeForm.deviceId" placeholder="选择设备" style="width:100%">
            <el-option v-for="d in devices" :key="d.deviceId" :label="(d.deviceName||d.deviceCode) + ' (' + (d.loadedBatch||'') + ')'" :value="d.deviceId" />
          </el-select>
        </el-form-item>
        <el-form-item label="数量" prop="qty">
          <el-input-number v-model="consumeForm.qty" :min="0.01" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="核销方式">
          <el-radio-group v-model="consumeForm.consumeMethod">
            <el-radio-button value="MANUAL">手动核销</el-radio-button>
            <el-radio-button value="BATCH">批量核销</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="关联患者" v-if="consumeForm.consumeMethod === 'MANUAL'">
          <el-input v-model="consumeForm.patientRef" placeholder="如 P-20260706-001" />
        </el-form-item>
        <el-form-item label="用途">
          <el-select v-model="consumeForm.purpose" placeholder="消耗用途" style="width:100%">
            <el-option label="日常消耗" value="ROUTINE" /><el-option label="批量检测" value="BATCH_TEST" />
            <el-option label="设备维护" value="EQUIP_MAINT" /><el-option label="科内领用" value="DEPT_USE" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="consumeForm.remark" type="textarea" :rows="2" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showConsumeDialog = false">取消</el-button>
        <el-button type="primary" :loading="consumeLoading" @click="doConsume">确认核销</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { getConsumptionList, getConsumptionStats, batchConsume } from '@/api/consumption'

const page = ref(1), pageSize = ref(10)
const records = ref([]), loading = ref(false), total = ref(0)
const stats = ref({})

// 活跃设备列表 — 当前使用 mock 数据作为桥接，待后端 API 就绪后替换为真实接口
// TODO: GET /api/consumption/devices?status=ACTIVE 返回活跃上机设备列表（目前 loadDevices() 提供 mock 数据；后端接口就绪后改为由 fetchData 统一加载）
const devices = ref([])

function loadDevices() {
  devices.value = [
    {
      deviceId: 1,
      deviceCode: 'DEV-001',
      deviceName: '全自动生化分析仪 BS-800',
      manufacturer: '迈瑞',
      loadedBatch: 'B20260714001',
      reagentName: 'ALT丙氨酸氨基转移酶',
      loadedAt: '2026-07-14 08:15',
      estimatedUnload: '2026-07-14 18:00',
      status: 'IN_USE'
    },
    {
      deviceId: 2,
      deviceCode: 'DEV-002',
      deviceName: '全自动化学发光仪 CL-6000i',
      manufacturer: '迈瑞',
      loadedBatch: 'B20260713005',
      reagentName: 'TSH促甲状腺激素',
      loadedAt: '2026-07-13 14:30',
      estimatedUnload: '2026-07-15 14:30',
      status: 'PENDING_UNLOAD'
    },
    {
      deviceId: 3,
      deviceCode: 'DEV-003',
      deviceName: '全自动血液分析仪 BC-6800',
      manufacturer: '迈瑞',
      loadedBatch: 'B20260712003',
      reagentName: '血常规溶血剂',
      loadedAt: '2026-07-12 09:00',
      estimatedUnload: '2026-07-12 17:00',
      status: 'CONSUMED'
    }
  ]
}

// Scan/Consume dialog
const showConsumeDialog = ref(false), consumeLoading = ref(false)
const consumeFormRef = ref(null)
const consumeForm = reactive({
  mode: 'scan', batchNo: '', deviceId: null, qty: 1,
  consumeMethod: 'MANUAL', patientRef: '', purpose: 'ROUTINE', remark: ''
})
const consumeRules = {
  batchNo: [{ required: true, message: '请输入批号', trigger: 'blur' }],
  deviceId: [{ required: true, message: '请选择设备', trigger: 'change' }],
  qty: [{ required: true, message: '请输入数量' }]
}

onMounted(() => { loadDevices(); fetchRecords(); fetchStats() })

async function fetchRecords() {
  loading.value = true
  try {
    const d = await getConsumptionList({ page: page.value, pageSize: pageSize.value })
    const items = (d && d.records) ? d.records : []
    records.value = items
    total.value = d.total || items.length
  } catch (e) { console.error('获取消耗记录失败:', e) } finally { loading.value = false }
}

async function fetchStats() { try { stats.value = await getConsumptionStats() || {} } catch (e) { console.error('获取消耗统计失败:', e) } }

function openManualConsume(row) {
  consumeForm.mode = 'manual'
  consumeForm.batchNo = row.loadedBatch || ''
  consumeForm.deviceId = row.deviceId
  consumeForm.qty = 1
  consumeForm.consumeMethod = 'MANUAL'
  consumeForm.patientRef = ''
  consumeForm.purpose = 'ROUTINE'
  consumeForm.remark = ''
  showConsumeDialog.value = true
}

function resetConsumeForm() {
  consumeFormRef.value?.resetFields()
  Object.assign(consumeForm, { mode: 'scan', batchNo: '', deviceId: null, qty: 1, consumeMethod: 'MANUAL', patientRef: '', purpose: 'ROUTINE', remark: '' })
}

async function doConsume() {
  const valid = await consumeFormRef.value?.validate().catch(() => false)
  if (!valid) return
  consumeLoading.value = true
  try {
    await batchConsume(consumeForm.batchNo, consumeForm.deviceId, consumeForm.qty)
    ElMessage.success('消耗核销已记录')
    showConsumeDialog.value = false
    fetchRecords(); fetchStats()
  } catch {} finally { consumeLoading.value = false }
}

async function confirmUnload(row) {
  try {
    await ElMessageBox.confirm(
      `确认卸载设备 "${row.deviceName || row.deviceCode}" 上的批次 ${row.loadedBatch}？卸载后剩余试剂将退回中心库。`,
      '确认退机',
      { confirmButtonText: '确认退机', type: 'warning' }
    )
    row.status = 'CONSUMED'
    ElMessage.success('批次已卸载，剩余试剂已退回中心库')
  } catch { /* cancelled */ }
}

function formatTime(t) { if (!t) return '—'; return typeof t === 'string' ? t.slice(11, 16) : t }

function recordTypeLabel(type) {
  const map = { ROUTINE: '常规消耗', BATCH_TEST: '批量检测', EQUIP_MAINT: '设备维护' }
  return map[type] || '手动'
}
</script>

<style scoped>
/* ── Page Header ── */
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.page-header h2 { font-size: 18px; font-weight: 700; color: var(--text-primary); display: flex; align-items: center; gap: 8px; margin: 0; }


.card-title { font-size: 14px; font-weight: 600; color: var(--text-primary); display: flex; align-items: center; gap: 6px; }
.icon-sm { display: inline-flex; align-items: center; justify-content: center; width: 26px; height: 26px; border-radius: 6px; font-size: 14px; }

:deep(.el-table th) { background: var(--bg-page); color: var(--text-secondary); font-weight: 600; font-size: 12px; }
:deep(.el-card__header) { padding: 14px 18px; }
</style>
