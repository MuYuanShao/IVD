<template>
  <el-dialog v-model="visible" :title="reportActions[action] || '报告操作'" width="min(650px,94vw)" class="scm-theme" destroy-on-close :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving" @closed="clear">
    <el-alert :title="action==='sign' ? '仅业务签发，未使用 CA 数字签名。私有PDF存储成功后才会提交签发状态。' : '该操作按当前报告版本执行，记录操作人、状态与原因；不会自动扣库存或结算。'" type="info" :closable="false" />
    <el-form ref="formRef" :model="form" label-width="90px" :disabled="saving"><el-form-item label="报告"><span>{{ report?.number }} · 版本 {{ report?.edition }}</span></el-form-item><el-form-item label="说明 / 原因" prop="reason" :rules="needsReason ? required : []"><el-input v-model="form.reason" type="textarea" :rows="4" maxlength="1000" show-word-limit placeholder="复核风险说明或退回、更正、撤销原因" /></el-form-item><el-form-item v-if="action==='review'" label="风险确认" prop="acknowledgeRisks" :rules="[{ validator:(_,value,callback)=>value ? callback() : callback(new Error('请人工确认风险与证据完整性')),trigger:'change' }]"><el-checkbox v-model="form.acknowledgeRisks">已核对异常提示、缺失区间、单位及批次 / 消耗证据</el-checkbox></el-form-item></el-form>
    <template #footer><el-button :disabled="saving" @click="visible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">确认{{ reportActions[action] }}</el-button></template>
  </el-dialog>
</template>
<script setup>
import { computed,nextTick,reactive,ref } from 'vue'
import { ElMessage } from 'element-plus'
import { actOnReport } from '@/api/clinical'
import { clinicalError,reportActions } from '@/utils/clinicalOptions.mjs'
const emit=defineEmits(['saved']),visible=ref(false),saving=ref(false),report=ref(null),action=ref(''),formRef=ref(),form=reactive({ reason:'',acknowledgeRisks:false }),required=[{ required:true,whitespace:true,message:'请填写原因或复核说明',trigger:'blur' }]
const needsReason=computed(()=>['review','return','correct','revoke'].includes(action.value))
function clear() { report.value=null;action.value='';form.reason='';form.acknowledgeRisks=false }
async function open(row,kind) { if(saving.value)return;clear();report.value=row;action.value=kind;visible.value=true;await nextTick();formRef.value?.clearValidate() }
async function save() { if(saving.value || !await formRef.value?.validate().catch(()=>false))return;saving.value=true;try { const data=await actOnReport(report.value.id,action.value,{ version:report.value.version,reason:form.reason || null,acknowledgeRisks:form.acknowledgeRisks });ElMessage.success(`${reportActions[action.value]}成功`);visible.value=false;emit('saved',data,action.value) } catch(failure) { ElMessage.error(clinicalError(failure)) } finally { saving.value=false } }
defineExpose({ open })
</script>
<style scoped lang="scss" src="./lab-editor.scss" />
