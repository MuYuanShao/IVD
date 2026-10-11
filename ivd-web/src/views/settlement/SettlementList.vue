<!--
  SettlementList.vue — X2 结算管理
  申领结算 + 消耗结算 + 冲销
-->
<template>
  <div class="page-container">
    <el-row :gutter="16" class="stat-cards">
      <el-col :span="6"><el-card shadow="hover" class="stat-card"><el-statistic title="总结算单数" :value="stats.total || 0" /></el-card></el-col>
      <el-col :span="6"><el-card shadow="hover" class="stat-card stat-card--success"><el-statistic title="有效结算" :value="stats.active || 0" /></el-card></el-col>
      <el-col :span="6"><el-card shadow="hover" class="stat-card stat-card--warning"><el-statistic title="已冲销" :value="stats.reversed || 0" /></el-card></el-col>
      <el-col :span="6"><el-card shadow="hover" class="stat-card stat-card--info"><span>本月有效结算金额</span><strong>{{ stats.monthlyAmount == null ? '待核对' : `¥${Number(stats.monthlyAmount).toFixed(2)}` }}</strong></el-card></el-col>
    </el-row>

    <el-card shadow="never">
      <template #header>
        <span class="card-title">结算记录</span>
        <el-button type="primary" size="small" style="float:right;margin-left:8px" @click="showSettleDialog=true">手动结算</el-button>
      </template>
      <el-table :data="records" stripe v-loading="loading" style="width:100%">
        <el-table-column prop="settlementNo" label="结算单号" width="180" />
        <el-table-column label="结算类型" width="100">
          <template #default="{row}"><el-tag :type="getSettlementTagType(row.settlementType)" size="small">{{ getSettlementTypeLabel(row.settlementType) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="sourceId" label="源单据ID" width="100" />
        <el-table-column prop="quantity" label="数量" width="80" />
        <el-table-column prop="amount" label="金额" width="100" />
        <el-table-column label="状态" width="90">
          <template #default="{row}"><el-tag :type="row.status==='ACTIVE'?'success':'danger'" size="small">{{ row.status==='ACTIVE'?'有效':'已冲销' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="settledAt" label="结算时间" width="160" />
        <el-table-column label="操作" width="160">
          <template #default="{row}">
            <el-button v-if="row.status==='ACTIVE'" link type="danger" size="small" @click="doReverse(row)">冲销</el-button>
            <el-button link type="primary" size="small" @click="historyId = row.id">追溯</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :total="total" :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next,jumper" style="margin-top:16px;justify-content:flex-end" @change="fetchRecords" />
    </el-card>

    <el-dialog v-model="showSettleDialog" title="手动结算" width="400px">
      <el-form label-width="80px">
        <el-form-item label="结算类型"><el-select v-model="settleForm.type" style="width:100%"><el-option label="申领结算" value="REQUISITION" /><el-option label="消耗结算" value="CONSUMPTION" /><el-option label="调拨结算" value="TRANSFER" /></el-select></el-form-item>
        <el-alert title="按采购模式结算，不重复补账；来源ID按文本输入以保留完整精度。" type="info" :closable="false" />
        <el-form-item label="源单据ID"><el-input v-model="settleForm.sourceId" placeholder="粘贴完整来源ID" maxlength="19" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showSettleDialog=false">取消</el-button><el-button type="primary" @click="doSettle">确认结算</el-button></template>
    </el-dialog>
    <settlement-history-modal v-model="historyVisible" :settlement-id="historyId" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getSettlementList, getSettlementStats, settleRequisition, settleConsumption, settleTransfer, reverseSettlement } from '@/api/settlement'
import { ElMessageBox, ElMessage } from 'element-plus'
import SettlementHistoryModal from './components/SettlementHistoryModal.vue'
import { validSourceId } from '@/utils/settlementFlow.mjs'

const page = ref(1), pageSize = ref(10)
const records = ref([]), loading = ref(false), total = ref(0)
const stats = ref({})
const showSettleDialog = ref(false)
const settleForm = ref({ type: 'REQUISITION', sourceId: '' })
const historyId = ref(null)
const historyVisible = computed({ get: () => historyId.value !== null, set: value => { if (!value) historyId.value = null } })

onMounted(() => { fetchRecords(); fetchStats() })

async function fetchRecords() {
  loading.value = true
  try { const d = await getSettlementList({ page: page.value, pageSize: pageSize.value }); records.value = d.records || []; total.value = d.total || 0 } catch {} finally { loading.value = false }
}
async function fetchStats() { try { stats.value = await getSettlementStats() || {} } catch {} }

async function doSettle() {
  if (!validSourceId(settleForm.value.sourceId)) { ElMessage.warning('请输入完整、有效的来源ID'); return }
  settleForm.value.sourceId = String(settleForm.value.sourceId).trim()
  try {
    if (settleForm.value.type === 'REQUISITION') await settleRequisition(settleForm.value.sourceId)
    else if (settleForm.value.type === 'TRANSFER') await settleTransfer(settleForm.value.sourceId)
    else await settleConsumption(settleForm.value.sourceId)
    showSettleDialog.value = false
    fetchRecords(); fetchStats()
  } catch {}
}

function getSettlementTagType(type) {
  if (type === 'REQUISITION') return 'info'
  if (type === 'TRANSFER') return 'warning'
  return 'success'
}

function getSettlementTypeLabel(type) {
  if (type === 'INBOUND') return '验收结算'
  if (type === 'REQUISITION') return '申领结算'
  if (type === 'TRANSFER') return '调拨结算'
  return '消耗结算'
}

async function doReverse(row) {
  try {
    const { value } = await ElMessageBox.prompt('请输入冲销原因', '冲销结算', { confirmButtonText: '确认冲销', type: 'warning', inputValidator: value => value?.trim() && value.trim().length <= 500 || '请填写不超过500字的冲销原因' })
    await reverseSettlement(row.id, value || '')
    fetchRecords(); fetchStats()
  } catch {}
}
</script>

