<template>
  <el-dialog v-model="visible" :title="id ? '编辑草稿报告' : '新建草稿报告'" width="min(900px,95vw)" class="scm-theme" destroy-on-close :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving" @closed="clear">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-form ref="formRef" :model="form" label-width="100px" :disabled="loading || saving"><el-form-item label="报告编号" prop="number" :rules="required"><el-input v-model="form.number" maxlength="64" :disabled="Boolean(id)" placeholder="手工填写机构内报告编号" /></el-form-item><el-form-item label="样本 ID"><span>{{ form.sampleId }}</span></el-form-item></el-form>
    <el-alert title="请选择实际已录入的结果；保存及导入均只创建草稿，不自动复核或签发。提交复核时会重新获取当前版本并冻结待审核结果。" type="info" :closable="false" />
    <el-table ref="tableRef" v-loading="loading" :data="rows" row-key="id" @selection-change="selectionChanged"><el-table-column type="selection" :reserve-selection="true" width="45" /><el-table-column prop="itemCode" label="项目编码" min-width="110" /><el-table-column prop="itemName" label="项目名称" min-width="160" /><el-table-column prop="result" label="结果" width="90" /><el-table-column prop="unit" label="单位" width="90" /><el-table-column prop="version" label="结果版本" width="95" /></el-table>
    <div class="report-selection"><span>已选择 {{ selected.length }} 项（最多1000项）</span><el-pagination :current-page="page" :page-size="20" :total="total" layout="total, prev, pager, next" @current-change="changePage" /></div>
    <template #footer><el-button :disabled="saving" @click="visible=false">取消</el-button><el-button type="primary" :loading="saving" :disabled="loading || !selected.length || Boolean(error)" @click="save">保存草稿</el-button></template>
  </el-dialog>
</template>
<script setup>
import { nextTick,onBeforeUnmount,reactive,ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getSampleResults,saveReport } from '@/api/clinical'
import { clinicalError } from '@/utils/clinicalOptions.mjs'
const emit=defineEmits(['saved']),visible=ref(false),loading=ref(false),saving=ref(false),error=ref(''),id=ref(null),rows=ref([]),selected=ref([]),total=ref(0),page=ref(1),formRef=ref(),tableRef=ref(),form=reactive({ number:'',sampleId:null,version:null }),required=[{ required:true,message:'请填写报告编号',trigger:'blur' }]
let generation=0,restoring=false
function clear() { ++generation;id.value=null;rows.value=[];selected.value=[];total.value=0;page.value=1;error.value='';Object.assign(form,{ number:'',sampleId:null,version:null }) }
async function load() { const current=++generation;loading.value=true;restoring=true;const wanted=[...selected.value];try { const data=await getSampleResults(form.sampleId,{ page:page.value,pageSize:20 });if(current!==generation)return;rows.value=data.records;total.value=data.total;await nextTick();for(const row of rows.value)tableRef.value?.toggleRowSelection(row,wanted.includes(row.id)) } catch(failure) { if(current===generation)error.value=clinicalError(failure) } finally { if(current===generation){loading.value=false;restoring=false} } }
async function open(sampleId,report) { if(saving.value)return;clear();restoring=true;id.value=report?.id || null;Object.assign(form,{ sampleId,number:report?.number || '',version:report?.version ?? null });selected.value=report?.results?.map(row=>row.id) || [];visible.value=true;await nextTick();tableRef.value?.clearSelection();await load();formRef.value?.clearValidate() }
function selectionChanged(value) { if(!restoring)selected.value=value.map(row=>row.id) }
function changePage(value) { page.value=value;load() }
async function save() { if(saving.value || !selected.value.length || !await formRef.value?.validate().catch(()=>false))return;saving.value=true;try { const payload={ number:form.number,sampleId:form.sampleId,resultIds:selected.value };if(id.value)payload.version=form.version;const report=await saveReport(id.value,payload);ElMessage.success('报告草稿已保存');visible.value=false;emit('saved',report) } catch(failure) { ElMessage.error(clinicalError(failure)) } finally { saving.value=false } }
onBeforeUnmount(clear)
defineExpose({ open })
</script>
<style scoped>.report-selection { display:flex;align-items:center;justify-content:space-between;gap:12px;margin-top:14px;flex-wrap:wrap }</style>
