<template>
  <el-dialog v-model="visible" title="样本详情与检测结果" width="min(1100px,96vw)" class="scm-theme" destroy-on-close :close-on-click-modal="!busy" :close-on-press-escape="!busy" :show-close="!busy" @closed="clear">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <div v-loading="loading">
      <template v-if="sample">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="样本编号">{{ sample.number }}</el-descriptions-item>
          <el-descriptions-item label="患者档案 ID">{{ sample.patientId }}</el-descriptions-item>
          <el-descriptions-item label="样本类型">{{ sample.specimenType }}</el-descriptions-item>
          <el-descriptions-item label="采集时间">{{ sample.collectedAt?.replace('T',' ') }}</el-descriptions-item>
          <el-descriptions-item label="状态"><el-tag :type="statusOption(sampleStatuses,sample.status).type">{{ statusOption(sampleStatuses,sample.status).label }}</el-tag></el-descriptions-item>
          <el-descriptions-item label="接收时间">{{ sample.receivedAt?.replace('T',' ') || '—' }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="sample.status==='COLLECTED'" class="sample-actions">
          <el-button type="primary" :loading="busy" @click="receive">接收样本</el-button>
          <el-button type="danger" plain :disabled="busy" @click="reject">拒收样本</el-button>
        </div>
        <result-list-panel :key="sample.id" :sample="sample" />
        <div class="sample-actions"><el-button type="primary" :disabled="sample.status!=='RECEIVED'" @click="reportEditor.open(sample.id)">建立草稿报告</el-button><el-button @click="router.push({ path:'/lab/reports',query:{ sampleId:sample.id } })">查看样本报告</el-button></div>
      </template>
    </div>
    <report-editor-modal ref="reportEditor" @saved="reportSaved" />
    <template #footer><el-button :disabled="busy" @click="visible=false">关闭</el-button></template>
  </el-dialog>
</template>
<script setup>
import { onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import ResultListPanel from './ResultListPanel.vue'
import ReportEditorModal from './ReportEditorModal.vue'
import { getSample, receiveSample, rejectSample } from '@/api/clinical'
import { clinicalError, sampleStatuses, statusOption } from '@/utils/clinicalOptions.mjs'
const emit=defineEmits(['changed']), visible=ref(false), loading=ref(false), busy=ref(false), error=ref(''), sample=ref(null)
const router=useRouter(),reportEditor=ref()
function reportSaved(report) { router.push({ path:'/lab/reports',query:{ sampleId:report.sampleId } }) }
let generation=0
function clear() { ++generation; sample.value=null; error.value=''; loading.value=false }
async function open(id) {
  if(busy.value)return
  clear(); visible.value=true; loading.value=true; const current=++generation
  try { const value=await getSample(id); if(current===generation)sample.value=value }
  catch(failure) { if(current===generation)error.value=clinicalError(failure) }
  finally { if(current===generation)loading.value=false }
}
async function receive() {
  if(busy.value)return
  busy.value=true
  try { sample.value=await receiveSample(sample.value.id,{ version:sample.value.version }); ElMessage.success('样本已接收'); emit('changed') }
  catch(failure) { ElMessage.error(clinicalError(failure)) }
  finally { busy.value=false }
}
async function reject() {
  if(busy.value)return
  busy.value=true
  try {
    const { value }=await ElMessageBox.prompt('请填写拒收原因（不超过200字）','拒收样本',{ inputType:'textarea',inputValidator:(reason)=>Boolean(reason?.trim()) && reason.length<=200 || '请填写1至200字的原因' })
    sample.value=await rejectSample(sample.value.id,{ version:sample.value.version,reason:value }); ElMessage.success('样本已拒收'); emit('changed')
  } catch(failure) { if(failure!=='cancel' && failure!=='close')ElMessage.error(clinicalError(failure)) }
  finally { busy.value=false }
}
onBeforeUnmount(clear)
defineExpose({ open })
</script>
<style scoped>
.sample-actions { display:flex;gap:12px;margin:16px 0 }
</style>
