<template>
  <el-dialog v-model="visible" title="登记患者样本" width="min(640px,94vw)" class="scm-theme" destroy-on-close :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving" @closed="clear">
    <el-form ref="formRef" :model="form" label-width="100px" :disabled="saving">
      <el-form-item label="患者"><span>{{ patientLabel }}</span></el-form-item>
      <el-form-item label="样本编号" prop="number" :rules="required"><el-input v-model="form.number" maxlength="64" /></el-form-item>
      <el-form-item label="样本类型" prop="specimenType" :rules="required"><el-input v-model="form.specimenType" maxlength="64" placeholder="按实际采集类型录入，如血清" /></el-form-item>
      <el-form-item label="采集时间" prop="collectedAt" :rules="required"><el-date-picker v-model="form.collectedAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
      <p class="lab-hint">手工填写真实样本编号，默认状态为已采集；接收后才允许录入检测结果。</p>
    </el-form>
    <template #footer><el-button :disabled="saving" @click="visible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">登记样本</el-button></template>
  </el-dialog>
</template>
<script setup>
import { nextTick, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createSample } from '@/api/clinical'
import { clinicalError, localDateTimeNow } from '@/utils/clinicalOptions.mjs'
const emit=defineEmits(['saved']), visible=ref(false), saving=ref(false), formRef=ref(), patientLabel=ref('')
const blank=()=>({ patientId:null, number:'', specimenType:'', collectedAt:null })
const form=reactive(blank()), required=[{ required:true,message:'请填写必填项',trigger:'change' }]
function clear() { Object.assign(form,blank()); patientLabel.value='' }
async function open(patient) {
  if(saving.value)return
  clear(); form.patientId=patient.id; form.collectedAt=localDateTimeNow(); patientLabel.value=`${patient.maskedName} · ${patient.maskedNumber}`
  visible.value=true; await nextTick(); formRef.value?.clearValidate()
}
async function save() {
  if(saving.value || !await formRef.value?.validate().catch(()=>false))return
  saving.value=true
  try { const sample=await createSample({ ...form }); ElMessage.success('样本已登记'); visible.value=false; emit('saved',sample) }
  catch(failure) { ElMessage.error(clinicalError(failure)) }
  finally { saving.value=false }
}
defineExpose({ open })
</script>
<style scoped lang="scss" src="./lab-editor.scss" />
