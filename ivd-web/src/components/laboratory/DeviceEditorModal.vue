<template>
  <el-dialog v-model="visible" :title="id ? '编辑设备档案' : '新增设备档案'" width="min(720px,94vw)" class="scm-theme" destroy-on-close :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving" @closed="clear">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-form v-loading="loading" ref="formRef" :model="form" label-width="110px" :disabled="loading || saving">
      <el-form-item label="设备编码" prop="code" :rules="required"><el-input v-model="form.code" maxlength="64" /></el-form-item><el-form-item label="设备名称" prop="name" :rules="required"><el-input v-model="form.name" maxlength="128" /></el-form-item>
      <el-form-item label="型号"><el-input v-model="form.model" maxlength="64" /></el-form-item><el-form-item label="厂家"><el-input v-model="form.manufacturer" maxlength="128" /></el-form-item>
      <el-form-item label="所在库房" prop="locationId" :rules="required"><el-select v-model="form.locationId" filterable @change="locationChanged"><el-option v-for="warehouse in warehouses" :key="warehouse.id" :value="warehouse.id" :label="`${warehouse.name} · ${warehouse.level}级`" /></el-select></el-form-item>
      <el-form-item label="所属科室 ID"><el-input v-model="form.departmentId" placeholder="科室库自动确定；中心库留空为共享设备" /></el-form-item>
      <el-form-item label="上次校准"><el-date-picker v-model="form.lastCalibrationDate" type="date" value-format="YYYY-MM-DD" clearable /></el-form-item><el-form-item label="下次校准"><el-date-picker v-model="form.nextCalibrationDate" type="date" value-format="YYYY-MM-DD" clearable /></el-form-item><el-form-item label="启用"><el-switch v-model="form.active" /></el-form-item>
      <p class="lab-hint">科室与库房须属于当前机构且归属一致；保存设备不会自动增加项目能力或改变既有上机记录。</p>
    </el-form>
    <template #footer><el-button :disabled="saving" @click="visible=false">取消</el-button><el-button type="primary" :loading="saving" :disabled="loading || Boolean(error)" @click="save">保存设备</el-button></template>
  </el-dialog>
</template>
<script setup>
import { nextTick,onBeforeUnmount,reactive,ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getDeviceRegistryDetail,getDeviceWarehouses,saveDeviceRegistry } from '@/api/clinical'
import { clinicalError } from '@/utils/clinicalOptions.mjs'
const emit=defineEmits(['saved']),visible=ref(false),loading=ref(false),saving=ref(false),id=ref(null),error=ref(''),formRef=ref(),warehouses=ref([])
const blank=()=>({ code:'',name:'',model:'',manufacturer:'',locationId:null,departmentId:'',active:true,lastCalibrationDate:null,nextCalibrationDate:null,version:null }),form=reactive(blank()),required=[{ required:true,message:'请填写或选择必填项',trigger:'change' }]
let generation=0
function clear() { ++generation;Object.assign(form,blank());warehouses.value=[];id.value=null;error.value='' }
async function open(row) { if(saving.value)return;clear();id.value=row?.id || null;visible.value=true;loading.value=true;const current=++generation;try { const [options,detail]=await Promise.all([getDeviceWarehouses(),row ? getDeviceRegistryDetail(row.id) : Promise.resolve(null)]);if(current!==generation)return;warehouses.value=options;if(detail)Object.assign(form,detail,{ departmentId:detail.departmentId || '' }) } catch(failure) { if(current===generation)error.value=clinicalError(failure) } finally { if(current===generation)loading.value=false }await nextTick();formRef.value?.clearValidate() }
function locationChanged(value) { form.departmentId=warehouses.value.find(row=>row.id===value)?.departmentId || '' }
async function save() { if(saving.value || loading.value || !await formRef.value?.validate().catch(()=>false))return;saving.value=true;try { await saveDeviceRegistry(id.value,{ ...form,departmentId:form.departmentId || null });ElMessage.success('设备档案已保存');visible.value=false;emit('saved') } catch(failure) { ElMessage.error(clinicalError(failure)) } finally { saving.value=false } }
onBeforeUnmount(clear)
defineExpose({ open })
</script>
<style scoped lang="scss" src="./lab-editor.scss" />
