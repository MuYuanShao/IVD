<template>
  <el-dialog v-model="visible" title="检验报告详情" width="min(1100px,96vw)" top="5vh" class="scm-theme" destroy-on-close @closed="clear">
    <el-alert v-if="error" :title="error" type="error" :closable="false" /><div v-loading="loading" class="report-detail-body"><template v-if="report">
      <el-alert title="首版采用业务签发，未使用 CA 数字签名；系统管理员不默认拥有临床复核、签发或报告读取权限。" type="info" :closable="false" />
      <el-descriptions :column="2" border><el-descriptions-item label="报告编号">{{ report.number }} · 版本 {{ report.edition }}</el-descriptions-item><el-descriptions-item label="状态"><el-tag :type="statusOption(reportStatuses,report.status).type">{{ statusOption(reportStatuses,report.status).label }}</el-tag></el-descriptions-item><el-descriptions-item label="患者快照">{{ report.patient.name }} · {{ report.patient.number }}</el-descriptions-item><el-descriptions-item label="样本 ID">{{ report.sampleId }}</el-descriptions-item><el-descriptions-item label="录入者 ID">{{ report.createdBy }}</el-descriptions-item><el-descriptions-item label="复核 / 签发者 ID">{{ report.reviewedBy || '未复核' }} / {{ report.signedBy || '未签发' }}</el-descriptions-item></el-descriptions>
      <div class="report-actions"><el-button v-if="['DRAFT','RETURNED'].includes(report.status)" @click="editor.open(report.sampleId,report)">编辑草稿</el-button><el-button v-if="['DRAFT','RETURNED'].includes(report.status)" type="primary" @click="action.open(report,'submit')">提交复核</el-button><el-button v-if="report.status==='PENDING_REVIEW' && !isEntryActor" type="primary" @click="action.open(report,'review')">人工复核</el-button><el-button v-if="['PENDING_REVIEW','REVIEWED'].includes(report.status) && !isEntryActor" @click="action.open(report,'return')">退回编辑</el-button><el-button v-if="report.status==='REVIEWED' && auth.role==='LAB_DIRECTOR' && !isEntryActor" type="success" @click="action.open(report,'sign')">业务签发</el-button><el-button v-if="report.status==='SIGNED'" @click="action.open(report,'correct')">发起更正</el-button><el-button v-if="report.status==='SIGNED' && auth.role==='LAB_DIRECTOR'" type="danger" plain @click="action.open(report,'revoke')">撤销报告</el-button><el-button :disabled="['REVOKED','SUPERSEDED'].includes(report.status)" @click="pdf.open(report)">PDF 阅览</el-button></div>
      <el-alert v-if="isEntryActor && ['PENDING_REVIEW','REVIEWED'].includes(report.status)" title="您参与了报告或结果录入、提交或证据关联，不能复核或签发本报告。" type="warning" :closable="false" />
      <el-table :data="report.results" row-key="id"><el-table-column prop="itemName" label="项目" min-width="150" /><el-table-column prop="result" label="结果" min-width="80" /><el-table-column prop="unit" label="单位" min-width="90" /><el-table-column prop="deviceName" label="设备快照" min-width="130" /><el-table-column prop="testingInstitutionName" label="检测机构" min-width="150" /><el-table-column label="参考提示" min-width="130"><template #default="{ row }"><el-tooltip :content="row.referenceReason"><el-tag :type="statusOption(resultStatuses,row.referenceStatus).type">{{ statusOption(resultStatuses,row.referenceStatus).label }}</el-tag></el-tooltip></template></el-table-column></el-table>
      <h3 class="report-subtitle">报告版本</h3><el-table :data="versions" row-key="id"><el-table-column prop="edition" label="版本" width="80" /><el-table-column label="状态" min-width="120"><template #default="{ row }">{{ statusOption(reportStatuses,row.status).label }}</template></el-table-column><el-table-column label="操作" width="110"><template #default="{ row }"><el-button link type="primary" @click="open(row.id)">查看版本</el-button></template></el-table-column></el-table>
      <h3 class="report-subtitle">流转留痕</h3><el-timeline><el-timeline-item v-for="(item,index) in history" :key="index" :timestamp="item.occurredAt">{{ item.action }} · {{ item.fromStatus || '新建' }} → {{ item.toStatus }} · 操作人 {{ item.actorId }}<p v-if="item.reason">{{ item.reason }}</p></el-timeline-item></el-timeline>
    </template></div>
    <report-action-modal ref="action" @saved="changed" /><report-editor-modal ref="editor" @saved="changed" /><report-pdf-modal ref="pdf" />
  </el-dialog>
</template>
<script setup>
import { computed,onBeforeUnmount,ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { getReport,getReportHistory,getReportVersions } from '@/api/clinical'
import { clinicalError,reportStatuses,resultStatuses,statusOption } from '@/utils/clinicalOptions.mjs'
import ReportActionModal from './ReportActionModal.vue'
import ReportEditorModal from './ReportEditorModal.vue'
import ReportPdfModal from './ReportPdfModal.vue'
const emit=defineEmits(['changed']),auth=useAuthStore(),visible=ref(false),loading=ref(false),error=ref(''),report=ref(null),versions=ref([]),history=ref([]),action=ref(),editor=ref(),pdf=ref()
const isEntryActor=computed(()=>report.value?.entryActorIds?.map(String).includes(String(auth.user?.userId)))
let generation=0
function clear() { ++generation;report.value=null;versions.value=[];history.value=[];error.value='' }
async function open(id) { clear();visible.value=true;loading.value=true;const current=++generation;try { const [data,editions,events]=await Promise.all([getReport(id),getReportVersions(id),getReportHistory(id)]);if(current!==generation)return;report.value=data;versions.value=editions;history.value=events } catch(failure) { if(current===generation)error.value=clinicalError(failure) } finally { if(current===generation)loading.value=false } }
function changed(data) { emit('changed');open(data.id) }
onBeforeUnmount(clear)
defineExpose({ open })
</script>
<style scoped>.report-detail-body { max-height:78vh;overflow:auto;padding-right:6px }.report-actions { display:flex;gap:10px;flex-wrap:wrap;margin:16px 0 }.report-subtitle { margin:20px 0 12px }</style>
