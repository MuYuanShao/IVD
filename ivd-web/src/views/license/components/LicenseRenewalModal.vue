<template>
  <el-dialog :model-value="modelValue" title="续证审批" width="min(620px, 94vw)"
    :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving"
    @update:model-value="value => emit('update:modelValue', value)">
    <el-alert title="请先在证照详情上传并核验续证附件。普通编辑、上传附件不会解冻，审批通过后才重新计算证照效期。"
      type="info" :closable="false" show-icon />
    <el-form v-loading="loading" label-width="100px" style="margin-top:16px" :disabled="saving">
      <el-form-item label="新证号" required><el-input v-model="form.certNo" maxlength="128" /></el-form-item>
      <el-form-item label="发证日期" required>
        <el-date-picker v-model="form.issueDate" value-format="YYYY-MM-DD" type="date" />
      </el-form-item>
      <el-form-item label="有效期至" required>
        <el-date-picker v-model="form.expireDate" value-format="YYYY-MM-DD" type="date" />
      </el-form-item>
      <el-form-item label="续证证明" required>
        <el-select v-model="form.attachmentKey" placeholder="选择已核验的附件" style="width:100%">
          <el-option v-for="file in attachments" :key="file.objectKey" :label="file.name" :value="file.objectKey" />
        </el-select>
      </el-form-item>
      <el-form-item label="审批说明" required><el-input v-model="form.reason" type="textarea" maxlength="500" show-word-limit /></el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="saving" @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="loading || !loaded" @click="approve">核验并批准续证</el-button>
    </template>
  </el-dialog>
</template>
<script setup>
import { ref, reactive, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getLicenseDetail, approveLicenseRenewal } from '@/api/license'
const props = defineProps({ modelValue: Boolean, licenseId: [String, Number] })
const emit = defineEmits(['update:modelValue', 'saved'])
const loading = ref(false), saving = ref(false), loaded = ref(false), attachments = ref([])
const form = reactive({ certNo: '', issueDate: '', expireDate: '', attachmentKey: '', reason: '' })
let generation = 0
watch(() => [props.modelValue, props.licenseId], async ([open, id]) => {
  const token = ++generation
  loaded.value = false
  if (!open || !id) return
  Object.assign(form, { certNo: '', issueDate: '', expireDate: '', attachmentKey: '', reason: '' })
  attachments.value = []
  loading.value = true
  try {
    const detail = await getLicenseDetail(id)
    if (token !== generation) return
    Object.assign(form, { certNo: detail.certNo || '', issueDate: detail.issueDate || '', expireDate: '' })
    attachments.value = detail.attachments || []
    loaded.value = true
  } catch { ElMessage.error('证照详情加载失败，不能审批') }
  finally { if (token === generation) loading.value = false }
})
async function approve() {
  if (saving.value || !loaded.value) return
  if (!form.certNo.trim() || !form.issueDate || !form.expireDate || !form.attachmentKey || !form.reason.trim()) {
    ElMessage.warning('请填写完整续证信息并选择已核验附件')
    return
  }
  const id = props.licenseId
  const data = { ...form, certNo: form.certNo.trim(), reason: form.reason.trim() }
  saving.value = true
  try {
    await ElMessageBox.confirm('确认已核验新证照及附件，批准续证？', '续证审批', { type: 'warning' })
    await approveLicenseRenewal(id, data)
    ElMessage.success('续证审批通过，采购准入将重新按该供应商全部证照核验')
    emit('update:modelValue', false)
    emit('saved')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error('续证未确认，请核对记录后重试')
  } finally { saving.value = false }
}
</script>
