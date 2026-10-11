<template>
  <el-dialog v-model="visible" :title="id ? '修订草稿检测结果' : '录入检测结果'" width="min(740px,94vw)" class="scm-theme" destroy-on-close :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving" @closed="clear">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-form v-loading="loading" ref="formRef" :model="form" label-width="110px" :disabled="loading || saving">
      <el-form-item label="检验项目" prop="itemId" :rules="required"><el-select v-model="form.itemId" :disabled="Boolean(id)" filterable remote :remote-method="searchItems" :loading="itemLoading" placeholder="输入项目编码或名称搜索（最多20项）" @change="itemChanged"><el-option v-for="item in items" :key="item.id" :value="item.id" :label="`${item.code} · ${item.name}`" :disabled="!item.active" /></el-select></el-form-item>
      <el-form-item label="检测方式"><el-radio-group v-model="form.executionMode" @change="modeChanged"><el-radio-button v-for="option in executionModes" :key="option.value" :value="option.value">{{ option.label }}</el-radio-button></el-radio-group></el-form-item>
      <el-form-item v-if="form.executionMode==='INSTRUMENT'" label="实际设备" prop="deviceId" :rules="required"><el-select v-model="form.deviceId" filterable remote :remote-method="searchDevices" :loading="deviceLoading" placeholder="搜索本机构授权设备（最多50项）"><el-option v-for="device in devices" :key="device.id" :value="device.id" :label="`${device.code} · ${device.name}`" /></el-select></el-form-item>
      <el-form-item v-if="form.executionMode==='EXTERNAL'" label="检测机构" prop="testingInstitutionId" :rules="required"><el-select v-model="form.testingInstitutionId" filterable placeholder="选择本机构维护的外送机构"><el-option v-for="institution in institutions" :key="institution.id" :value="institution.id" :label="institution.name" /></el-select></el-form-item>
      <el-form-item label="结果" prop="result" :rules="required"><el-input v-model="form.result" maxlength="200" placeholder="定量填写数值，定性填写实际结果" /></el-form-item>
      <el-form-item label="单位"><el-input v-model="form.unit" maxlength="32" placeholder="按实际单位录入，不自动换算" /></el-form-item>
      <el-form-item label="检测时间" prop="testedAt" :rules="required"><el-date-picker v-model="form.testedAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
      <p class="lab-hint">年龄、性别和方法学由后端授权资料确定；缺失或冲突需人工处理。保存草稿不等于复核或签发。</p>
    </el-form>
    <template #footer><el-button :disabled="saving" @click="visible=false">取消</el-button><el-button type="primary" :loading="saving" :disabled="loading || Boolean(error)" @click="save">保存草稿结果</el-button></template>
  </el-dialog>
</template>
<script setup>
import { nextTick, onBeforeUnmount, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getLabItems, getLabDeviceOptions } from '@/api/laboratory'
import { createSampleResult, updateSampleResult, getTestingInstitutions } from '@/api/clinical'
import { clinicalError, executionModes, localDateTimeNow } from '@/utils/clinicalOptions.mjs'
const emit=defineEmits(['saved']), visible=ref(false), loading=ref(false), saving=ref(false), id=ref(null), sampleId=ref(null), error=ref(''), formRef=ref()
const items=ref([]), devices=ref([]), institutions=ref([]), itemLoading=ref(false), deviceLoading=ref(false)
const blank=()=>({ itemId:null,executionMode:'INSTRUMENT',deviceId:null,testingInstitutionId:null,result:'',unit:'',testedAt:null,version:null })
const form=reactive(blank()), required=[{ required:true,message:'请填写或选择必填项',trigger:'change' }]
let generation=0, itemGeneration=0, deviceGeneration=0
function clear() { ++generation;++itemGeneration;++deviceGeneration;Object.assign(form,blank());items.value=[];devices.value=[];institutions.value=[];error.value='';id.value=null;sampleId.value=null;itemLoading.value=false;deviceLoading.value=false }
async function searchItems(keyword='') {
  const current=++itemGeneration;itemLoading.value=true
  try { const data=await getLabItems({ page:1,pageSize:20,keyword });if(current===itemGeneration)items.value=data.records }
  catch(failure) { if(current===itemGeneration)ElMessage.error(clinicalError(failure)) }
  finally { if(current===itemGeneration)itemLoading.value=false }
}
async function searchDevices(keyword='') {
  const current=++deviceGeneration;deviceLoading.value=true
  try { const data=await getLabDeviceOptions(keyword);if(current===deviceGeneration)devices.value=data }
  catch(failure) { if(current===deviceGeneration)ElMessage.error(clinicalError(failure)) }
  finally { if(current===deviceGeneration)deviceLoading.value=false }
}
function itemChanged(value) { form.unit=items.value.find(item=>item.id===value)?.unit || '' }
function modeChanged() { form.deviceId=null;form.testingInstitutionId=null;formRef.value?.clearValidate() }
async function open(parentId,row) {
  if(saving.value)return
  clear();sampleId.value=parentId;id.value=row?.id || null;visible.value=true;loading.value=true
  Object.assign(form,blank(),row ? { itemId:row.itemId,executionMode:row.executionMode,deviceId:row.deviceId,testingInstitutionId:row.executionMode==='EXTERNAL' ? row.testingInstitutionId : null,result:row.result,unit:row.unit,testedAt:row.testedAt,version:row.version } : { testedAt:localDateTimeNow() })
  const current=++generation
  try {
    const [, , options]=await Promise.all([searchItems(),searchDevices(),getTestingInstitutions()])
    if(current!==generation)return
    institutions.value=options.filter(option=>option.type==='EXTERNAL').map(option=>({ ...option,id:String(option.id) }))
    if(row && !items.value.some(item=>item.id===row.itemId))items.value.push({ id:row.itemId,code:row.itemCode,name:row.itemName,active:true })
    if(row?.deviceId && !devices.value.some(device=>device.id===row.deviceId))devices.value.push({ id:row.deviceId,code:'历史绑定',name:row.deviceName })
  } catch(failure) { if(current===generation)error.value=clinicalError(failure) }
  finally { if(current===generation)loading.value=false }
  await nextTick();formRef.value?.clearValidate()
}
async function save() {
  if(saving.value || loading.value || !await formRef.value?.validate().catch(()=>false))return
  saving.value=true
  try { const data={ ...form };if(!id.value)delete data.version;await (id.value ? updateSampleResult(id.value,data) : createSampleResult(sampleId.value,data));ElMessage.success('草稿结果已保存');visible.value=false;emit('saved') }
  catch(failure) { ElMessage.error(clinicalError(failure)) }
  finally { saving.value=false }
}
onBeforeUnmount(clear)
defineExpose({ open })
</script>
<style scoped lang="scss" src="./lab-editor.scss" />
