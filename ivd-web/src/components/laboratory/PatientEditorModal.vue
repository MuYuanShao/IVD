<template>
  <el-dialog v-model="visible" :title="id ? '患者详情 / 编辑' : '新增患者'" width="min(640px,94vw)" class="scm-theme" destroy-on-close :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving" @closed="clear">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-form v-loading="loading" ref="formRef" :model="form" label-width="100px" :disabled="loading || saving">
      <el-form-item label="患者编号" prop="number" :rules="required"><el-input v-model="form.number" maxlength="64" /></el-form-item>
      <el-form-item label="姓名" prop="name" :rules="required"><el-input v-model="form.name" maxlength="100" /></el-form-item>
      <el-form-item label="性别"><el-select v-model="form.sex"><el-option label="男" value="MALE" /><el-option label="女" value="FEMALE" /><el-option label="未知" value="UNKNOWN" /></el-select></el-form-item>
      <el-form-item label="出生日期"><el-date-picker v-model="form.birthDate" type="date" value-format="YYYY-MM-DD" clearable :disabled-date="futureDate" /></el-form-item>
      <p class="lab-hint">默认仅当前科室获得访问授权；不会自动合并同编号患者。不收集证件、诊断或联系方式。</p>
    </el-form>
    <template #footer><el-button :disabled="saving" @click="visible=false">关闭</el-button><el-button type="primary" :loading="saving" :disabled="loading || Boolean(error)" @click="save">保存</el-button></template>
  </el-dialog>
</template>
<script setup>
import { nextTick, onBeforeUnmount, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createPatient, getPatient, updatePatient } from '@/api/clinical'
import { clinicalError } from '@/utils/clinicalOptions.mjs'
const emit = defineEmits(['saved'])
const visible=ref(false), id=ref(null), loading=ref(false), saving=ref(false), error=ref(''), formRef=ref()
const blank=()=>({ number:'',name:'',sex:'UNKNOWN',birthDate:null,version:null })
const form=reactive(blank()), required=[{ required:true,message:'请填写必填项',trigger:'blur' }]
let generation=0
const futureDate=(date)=>date.getTime()>Date.now()
function clear() { ++generation; Object.assign(form,blank()); id.value=null; error.value='' }
async function open(patientId) {
  if(saving.value)return
  clear(); id.value=patientId || null; visible.value=true; loading.value=Boolean(patientId)
  const current=++generation
  try { if(patientId) { const data=await getPatient(patientId); if(current===generation)Object.assign(form,data) } }
  catch(failure) { if(current===generation)error.value=clinicalError(failure) }
  finally { if(current===generation)loading.value=false }
  await nextTick(); formRef.value?.clearValidate()
}
async function save() {
  if(saving.value || loading.value)return
  if(!await formRef.value?.validate().catch(()=>false))return
  saving.value=true
  try {
    const { number,name,sex,birthDate,version }=form
    await (id.value ? updatePatient(id.value,{ number,name,sex,birthDate,version }) : createPatient({ number,name,sex,birthDate }))
    ElMessage.success('患者档案已保存'); visible.value=false; emit('saved')
  } catch(failure) { ElMessage.error(clinicalError(failure)) }
  finally { saving.value=false }
}
onBeforeUnmount(clear)
defineExpose({ open })
</script>
<style scoped lang="scss" src="./lab-editor.scss" />
