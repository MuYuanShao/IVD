<template>
  <el-dialog v-model="visible" title="批次 / 上机 / 消耗证据" width="min(900px,95vw)" class="scm-theme" destroy-on-close :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving" @closed="clear">
    <el-alert title="只引用已有真实业务证据，不重复扣库存或计费。证据不可覆盖；错误关联须修订结果后重新关联新版本。" type="info" :closable="false" />
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-form v-loading="loading" ref="formRef" :model="form" label-width="110px" :disabled="saving">
      <el-form-item label="当前结果"><span>{{ result?.itemName }} · 版本 {{ result?.version }}</span></el-form-item>
      <el-form-item label="批次 / 上机" prop="selected" :rules="required"><el-select v-model="form.selected" filterable remote :remote-method="searchBatches" placeholder="搜索批号（最多100条有效证据）" @change="selectBatch"><el-option v-for="batch in batches" :key="batch.key" :value="batch.key" :label="`${batch.batchNo} · 批次ID ${batch.batchId} · 上机ID ${batch.deviceLoadId || '无'}`" /></el-select></el-form-item>
      <el-form-item label="消耗证据"><el-select v-model="form.consumptionId" clearable filterable placeholder="选择正向消耗，留空将标记证据不完整"><el-option v-for="item in consumption" :key="item.id" :value="item.id" :label="`${item.occurredAt} · ${item.quantity}${item.unit} · ID ${item.id}`" /></el-select></el-form-item>
      <el-form-item><el-button type="primary" :loading="saving" :disabled="!form.selected" @click="bind">关联证据</el-button></el-form-item>
    </el-form>
    <el-table :data="rows" row-key="id"><el-table-column label="批次" min-width="140"><template #default="{ row }">{{ row.batch.batchNo }}</template></el-table-column><el-table-column label="上机ID" min-width="150"><template #default="{ row }">{{ row.batch.deviceLoadId || '无本地上机' }}</template></el-table-column><el-table-column label="消耗ID" min-width="150"><template #default="{ row }">{{ row.consumption?.id || '未关联' }}</template></el-table-column><el-table-column prop="resultVersion" label="结果版本" width="100" /></el-table>
  </el-dialog>
</template>
<script setup>
import { onBeforeUnmount,reactive,ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getResultEvidence,bindResultEvidence,getEvidenceBatches,getEvidenceConsumption } from '@/api/clinical'
import { clinicalError } from '@/utils/clinicalOptions.mjs'
const visible=ref(false),loading=ref(false),saving=ref(false),error=ref(''),result=ref(null),rows=ref([]),batches=ref([]),consumption=ref([]),formRef=ref(),form=reactive({ selected:null,consumptionId:null }),required=[{ required:true,message:'请选择真实批次证据',trigger:'change' }]
let generation=0,batchGeneration=0,consumptionGeneration=0
function clear() { ++generation;++batchGeneration;++consumptionGeneration;result.value=null;rows.value=[];batches.value=[];consumption.value=[];form.selected=null;form.consumptionId=null;error.value='' }
async function searchBatches(keyword='') { const current=++batchGeneration;try { const data=await getEvidenceBatches(result.value.id,keyword);if(current===batchGeneration)batches.value=data.map(batch=>({ ...batch,batchId:String(batch.batchId),deviceLoadId:batch.deviceLoadId ? String(batch.deviceLoadId) : null,key:`${batch.batchId}:${batch.deviceLoadId || ''}` })) } catch(failure) { if(current===batchGeneration)ElMessage.error(clinicalError(failure)) } }
async function selectBatch(key) { form.consumptionId=null;consumption.value=[];const selected=batches.value.find(batch=>batch.key===key);if(!selected)return;const current=++consumptionGeneration;try { const data=await getEvidenceConsumption(result.value.id,selected.batchId);if(current===consumptionGeneration)consumption.value=data.map(item=>({ ...item,id:String(item.id) })) } catch(failure) { if(current===consumptionGeneration)ElMessage.error(clinicalError(failure)) } }
async function load() { const current=generation;const data=await getResultEvidence(result.value.id,{ resultVersion:result.value.version });if(current===generation)rows.value=data }
async function open(row) { if(saving.value)return;clear();result.value=row;visible.value=true;loading.value=true;const current=++generation;try { await Promise.all([load(),searchBatches()]) } catch(failure) { if(current===generation)error.value=clinicalError(failure) } finally { if(current===generation)loading.value=false } }
async function bind() { if(saving.value || !await formRef.value?.validate().catch(()=>false))return;const batch=batches.value.find(item=>item.key===form.selected);if(!batch)return;saving.value=true;try { await bindResultEvidence(result.value.id,{ resultVersion:result.value.version,batchId:batch.batchId,deviceLoadId:batch.deviceLoadId,consumptionId:form.consumptionId });ElMessage.success('证据已关联，库存与结算未改动');await load() } catch(failure) { ElMessage.error(clinicalError(failure)) } finally { saving.value=false } }
onBeforeUnmount(clear)
defineExpose({ open })
</script>
<style scoped lang="scss" src="./lab-editor.scss" />
