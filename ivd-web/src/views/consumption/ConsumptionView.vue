<template>
  <div class="page-container">
    <div class="page-title">
      <h2>上机与消耗管理</h2>
      <div>
        <el-button @click="openAction('load')">登记上机</el-button
        ><el-button type="primary" @click="openAction('consume')"
          >记录消耗</el-button
        >
      </div>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false"
      ><el-button link @click="refresh">重试</el-button></el-alert
    >
    <el-row :gutter="16" class="stat-cards">
      <el-col v-for="metric in metrics" :key="metric.key" :xs="12" :sm="6"
        ><el-card shadow="never"
          ><span>{{ metric.label }}</span
          ><strong class="metric-number">{{
            stats ? stats[metric.key] : '—'
          }}</strong></el-card
        ></el-col
      >
    </el-row>
    <el-card shadow="never" class="load-card">
      <template #header
        ><div class="page-title">
          <h3>当前上机记录</h3>
          <el-button @click="refresh">刷新</el-button>
        </div></template
      >
      <el-table
        :data="loads"
        v-loading="loading"
        row-key="loadId"
        empty-text="暂无正在上机的批次"
      >
        <el-table-column prop="deviceName" label="仪器" min-width="150" />
        <el-table-column prop="loadedBatch" label="批号" min-width="170" />
        <el-table-column prop="reagentName" label="试剂" min-width="150" />
        <el-table-column prop="loadedAt" label="上机时间" min-width="170" />
        <el-table-column label="剩余库存" min-width="100"
          ><template #default="{ row }"
            >{{ row.stock ?? '—' }} {{ row.unit || '单位未记录' }}</template
          ></el-table-column
        >
        <el-table-column label="操作" width="140"
          ><template #default="{ row }"
            ><el-button
              link
              type="primary"
              :disabled="!row.batchId"
              @click="openAction('consume', row)"
              >消耗</el-button
            ><el-button link type="warning" @click="unload(row)"
              >退机</el-button
            ></template
          ></el-table-column
        >
      </el-table>
    </el-card>
    <el-card shadow="never">
      <template #header><h3>消耗记录（全部日期）</h3></template>
      <el-table
        :data="records"
        v-loading="loading"
        row-key="id"
        empty-text="暂无消耗记录"
      >
        <el-table-column prop="occurredAt" label="发生时间" min-width="170" />
        <el-table-column prop="id" label="消耗来源ID" min-width="190" show-overflow-tooltip />
        <el-table-column label="结算模式" min-width="130"><template #default="{ row }">{{ row.settlementMode || '历史待核对' }}</template></el-table-column>
        <el-table-column prop="settlementNo" label="消耗结算单号" min-width="210" />
        <el-table-column label="结算状态" min-width="170"><template #default="{ row }"><el-tag :type="row.settlementStatus === 'ACTIVE' ? 'success' : row.settlementStatus === 'REVERSED' ? 'danger' : 'info'">{{ billingLabel(row) }}</el-tag></template></el-table-column>
        <el-table-column prop="reagentName" label="试剂" min-width="150" />
        <el-table-column prop="batchNo" label="批号" min-width="170" />
        <el-table-column label="仪器" min-width="120"
          ><template #default="{ row }">{{
            row.deviceName || '未绑定'
          }}</template></el-table-column
        >
        <el-table-column label="消耗量" width="110"
          ><template #default="{ row }"
            >{{ row.quantity }} {{ row.unit || '单位未记录' }}</template
          ></el-table-column
        >
        <el-table-column label="金额（元）" width="110"
          ><template #default="{ row }">{{
            row.amount == null ? '未记录' : Number(row.amount).toFixed(2)
          }}</template></el-table-column
        >
        <el-table-column
          prop="remark"
          label="备注"
          min-width="150"
          show-overflow-tooltip
        />
      </el-table>
      <el-pagination
        v-model:current-page="page"
        :page-size="10"
        :total="total"
        layout="total,prev,pager,next"
        @current-change="fetchRecords"
      />
    </el-card>
    <el-dialog
      v-model="dialogOpen"
      :title="action === 'load' ? '登记上机' : '记录消耗'"
      width="min(640px,94vw)"
      class="scm-theme"
    >
      <el-form label-width="90px">
        <el-form-item label="库存批次" required
          ><el-select
            v-model="form.batchId"
            filterable
            remote
            :remote-method="searchBatches"
            :loading="searching"
            placeholder="搜索试剂、UDI或批号，选择具体库房批次"
            style="width: 100%"
            @change="form.deviceId = null"
            ><el-option
              v-for="b in batches"
              :key="b.batchId"
              :value="String(b.batchId)"
              :label="
                b.reagentName +
                ' / ' +
                b.batchNo +
                ' / ' +
                b.warehouseName +
                ' / 库存 ' +
                b.stock +
                ' ' +
                (b.unit || '') + ' / ' + (b.settlementMode || '历史待核对') + ' / 库存ID ' + b.batchId
              " /></el-select
        ></el-form-item>
        <el-form-item label="仪器" :required="requiresDevice"
          ><el-select
            v-model="form.deviceId"
            clearable
            :placeholder="requiresDevice ? '必须选择仪器' : '可不绑定仪器'"
            style="width: 100%"
            ><el-option
              v-for="d in matchingDevices"
              :key="d.deviceId"
              :value="String(d.deviceId)"
              :label="d.deviceName + ' / ' + d.deviceCode" /></el-select
        ></el-form-item>
        <el-alert
          v-if="selectedBatch"
          type="info"
          :closable="false"
          :title="
            selectedBatch.deviceBindMode === 'FORCE'
              ? '该试剂必须绑定同库房仪器。'
              : '该试剂无需强制绑定仪器。'
          "
        />
        <template v-if="action === 'consume'">
          <el-form-item label="消耗数量" required
            ><el-input-number
              v-model="form.qty"
              :min="0.01"
              :precision="2"
            /><span>{{
              selectedBatch?.unit || '原库存单位'
            }}</span></el-form-item
          >
          <el-form-item label="用途"
            ><el-select v-model="form.purpose"
              ><el-option label="日常消耗" value="ROUTINE" /><el-option
                label="批量检测"
                value="BATCH_TEST" /><el-option
                label="设备维护"
                value="EQUIP_MAINT" /><el-option
                label="科室领用"
                value="DEPT_USE" /></el-select
          ></el-form-item>
          <el-form-item label="备注"
            ><el-input v-model="form.remark" type="textarea" :rows="2"
          /></el-form-item>
        </template>
      </el-form>
      <template #footer
        ><el-button @click="dialogOpen = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="submit"
          >确认</el-button
        ></template
      >
    </el-dialog>
  </div>
</template>
<script setup>
import { billingLabel } from '@/utils/settlementFlow.mjs'
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getConsumptionList,
  getConsumptionStats,
  getConsumptionDevices,
  getActiveLoads,
  getConsumptionBatches,
  loadInstrument,
  unloadInstrument,
  recordConsume,
} from '@/api/consumption'
const records = ref([]),
  loads = ref([]),
  devices = ref([]),
  batches = ref([]),
  stats = ref(null),
  total = ref(0),
  page = ref(1),
  error = ref(''),
  loading = ref(false),
  saving = ref(false),
  searching = ref(false)
const dialogOpen = ref(false),
  action = ref('consume')
const form = reactive({
  batchId: null,
  deviceId: null,
  qty: 1,
  purpose: 'ROUTINE',
  remark: '',
})
const metrics = [
  { key: 'todayConsume', label: '今日消耗记录' },
  { key: 'activeDevices', label: '启用仪器数' },
  { key: 'pendingWriteoff', label: '正在上机批次' },
  { key: 'totalRecords', label: '累计消耗记录' },
]
const selectedBatch = computed(() =>
  batches.value.find((b) => String(b.batchId) === form.batchId),
)
const requiresDevice = computed(
  () =>
    action.value === 'load' || selectedBatch.value?.deviceBindMode === 'FORCE',
)
const matchingDevices = computed(() =>
  devices.value.filter(
    (d) => String(d.warehouseId) === String(selectedBatch.value?.warehouseId),
  ),
)
async function fetchRecords() {
  try {
    const data = await getConsumptionList({
      page: page.value,
      pageSize: 10,
      recordType: 'CONSUME',
    })
    records.value = data.records
    total.value = data.total
  } catch (e) {
    error.value = e.message
  }
}
async function refresh() {
  loading.value = true
  error.value = ''
  try {
    const [d, l, s] = await Promise.all([
      getConsumptionDevices(),
      getActiveLoads(),
      getConsumptionStats(),
    ])
    devices.value = d
    loads.value = l
    stats.value = s
    await fetchRecords()
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
let searchVersion = 0
async function searchBatches(keyword) {
  const version = ++searchVersion
  searching.value = true
  try {
    const rows = await getConsumptionBatches(keyword)
    if (version === searchVersion) batches.value = rows
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    if (version === searchVersion) searching.value = false
  }
}
async function openAction(mode, row) {
  action.value = mode
  Object.assign(form, {
    batchId: null,
    deviceId: null,
    qty: 1,
    purpose: 'ROUTINE',
    remark: '',
  })
  dialogOpen.value = true
  await searchBatches(row?.loadedBatch || '')
  if (row) {
    form.batchId = String(row.batchId)
    form.deviceId = String(row.deviceId)
  }
}
async function submit() {
  if (!selectedBatch.value) {
    ElMessage.warning('请选择明确的库存批次')
    return
  }
  if (requiresDevice.value && !form.deviceId) {
    ElMessage.warning('该试剂或上机操作必须选择仪器')
    return
  }
  if (action.value === 'consume' && (!form.qty || form.qty <= 0)) {
    ElMessage.warning('请输入正数数量')
    return
  }
  saving.value = true
  try {
    if (action.value === 'load')
      await loadInstrument(form.batchId, form.deviceId)
    else
      await recordConsume(
        form.batchId,
        form.deviceId || undefined,
        form.qty,
        form.purpose,
        form.remark,
      )
    ElMessage.success(action.value === 'load' ? '上机已登记' : '消耗已保存')
    dialogOpen.value = false
    await refresh()
  } catch {
  } finally {
    saving.value = false
  }
}
async function unload(row) {
  try {
    await ElMessageBox.confirm(
      '确认退机？剩余库存保留在当前库房，不会自动退回中心库。',
      '确认退机',
    )
    await unloadInstrument(row.loadId)
    ElMessage.success('退机已记录')
    await refresh()
  } catch {}
}
onMounted(refresh)
onBeforeUnmount(() => {
  ++searchVersion
})
</script>
<style scoped>
.load-card {
  margin-bottom: 16px;
}
.stat-cards {
  margin-bottom: 16px;
  row-gap: 12px;
}
.metric-number {
  display: block;
  font-size: 25px;
  margin-top: 9px;
}
.el-pagination {
  justify-content: flex-end;
  margin-top: 14px;
}
</style>
