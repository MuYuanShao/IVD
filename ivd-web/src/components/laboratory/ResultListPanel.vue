<template>
  <section class="result-panel">
    <header><h3>检测结果</h3><el-button type="primary" :disabled="sample.status!=='RECEIVED'" @click="editor.open(sample.id)">录入结果</el-button></header>
    <el-alert title="当前为手工录入的草稿结果，尚未复核或签发；不生成诊断，不自动扣库存或结算，不能作为正式报告打印。" type="info" :closable="false" />
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-table v-loading="loading" :data="rows" row-key="id">
      <el-table-column prop="itemName" label="检验项目" min-width="140" />
      <el-table-column prop="result" label="结果" min-width="90" />
      <el-table-column prop="unit" label="单位" min-width="90" />
      <el-table-column prop="deviceName" label="实际设备" min-width="130"><template #default="{ row }">{{ row.deviceName || '不绑定本地设备' }}</template></el-table-column>
      <el-table-column prop="testingInstitutionName" label="实际检测机构" min-width="150" />
      <el-table-column label="参考提示" min-width="140"><template #default="{ row }"><el-tooltip :content="row.referenceReason"><el-tag :type="statusOption(resultStatuses,row.referenceStatus).type">{{ statusOption(resultStatuses,row.referenceStatus).label }}</el-tag></el-tooltip></template></el-table-column>
      <el-table-column label="测量范围" min-width="130"><template #default="{ row }"><el-tooltip :content="row.measurementReason"><el-tag :type="statusOption(resultStatuses,row.measurementStatus).type">{{ statusOption(resultStatuses,row.measurementStatus).label }}</el-tag></el-tooltip></template></el-table-column>
      <el-table-column label="操作" width="220" fixed="right"><template #default="{ row }"><el-button link type="primary" :disabled="sample.status!=='RECEIVED'" @click="editor.open(sample.id,row)">修订</el-button><el-button link type="primary" @click="history.open(row.id)">版本 / 区间</el-button><el-button link type="primary" @click="evidence.open(row)">业务证据</el-button></template></el-table-column>
    </el-table>
    <el-pagination :current-page="page" :page-size="20" :total="total" layout="total, prev, pager, next" @current-change="changePage" />
    <result-editor-modal ref="editor" @saved="load" /><result-history-modal ref="history" />
    <result-evidence-modal ref="evidence" />
  </section>
</template>
<script setup>
import { computed, ref } from 'vue'
import ResultEditorModal from './ResultEditorModal.vue'
import ResultHistoryModal from './ResultHistoryModal.vue'
import ResultEvidenceModal from './ResultEvidenceModal.vue'
import { getSampleResults } from '@/api/clinical'
import { useLabCatalogList } from '@/composables/useLabCatalogList'
import { resultStatuses, statusOption } from '@/utils/clinicalOptions.mjs'
const props=defineProps({ sample:{ type:Object,required:true } }), editor=ref(), history=ref(),evidence=ref()
const enabled=computed(()=>Boolean(props.sample.id))
const { rows,total,page,loading,error,load }=useLabCatalogList(enabled,(value,signal)=>getSampleResults(props.sample.id,{ page:value,pageSize:20 },signal))
function changePage(value) { page.value=value;load() }
</script>
<style scoped>
.result-panel { margin-top:20px }
header { display:flex;align-items:center;justify-content:space-between;margin-bottom:12px }
.el-pagination { justify-content:flex-end;margin-top:12px }
</style>
