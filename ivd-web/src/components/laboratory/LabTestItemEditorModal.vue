<template>
  <el-dialog
    v-model="editorOpen"
    :title="`${editingId ? '编辑' : '新增'}检验项目`"
    width="min(680px, 94vw)"
    :close-on-click-modal="!saving"
    :close-on-press-escape="!saving"
    :show-close="!saving"
    class="scm-theme"
  >
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-form ref="itemFormRef" :model="itemForm" label-width="100px">
      <el-form-item label="项目编码" prop="code" :rules="required"
        ><el-input v-model="itemForm.code" maxlength="64"
      /></el-form-item>
      <el-form-item label="项目名称" prop="name" :rules="required"
        ><el-input v-model="itemForm.name" maxlength="200"
      /></el-form-item>
      <el-form-item label="方法学" prop="methodology" :rules="required"
        ><el-input v-model="itemForm.methodology" maxlength="100"
      /></el-form-item>
      <el-form-item label="结果类型"
        ><el-radio-group v-model="itemForm.resultType"
          ><el-radio-button
            v-for="option in resultTypeOptions"
            :key="option.value"
            :value="option.value"
            >{{ option.label }}</el-radio-button
          ></el-radio-group
        ></el-form-item
      >
      <el-form-item
        label="单位"
        prop="unit"
        :rules="itemForm.resultType === ResultType.NUMERIC ? required : []"
        ><el-input
          v-model="itemForm.unit"
          maxlength="32"
          placeholder="定量项目必填；不自动换算"
      /></el-form-item>
      <el-form-item label="启用"
        ><el-switch v-model="itemForm.active"
      /></el-form-item>
      <el-form-item label="要求仪器"><el-switch v-model="itemForm.deviceRequired" /><span class="lab-hint">启用后不允许手工检测；仪器还须配置可执行项目。</span></el-form-item>
      <p class="lab-hint">
        已关联区间的项目不可直接变更方法学、单位或结果类型。
      </p> </el-form
    ><template #footer
      ><el-button :disabled="saving" @click="editorOpen = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="save"
        >保存</el-button
      ></template
    ></el-dialog
  >
</template>
<script setup>
import { nextTick, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createLabItem, updateLabItem } from '@/api/laboratory'
import { ResultType, resultTypeOptions } from '@/utils/laboratoryOptions.mjs'
const emit = defineEmits(['saved'])
const editorOpen = ref(false),
  editingId = ref(null),
  saving = ref(false),
  error = ref(''),
  itemFormRef = ref()
const required = [
  { required: true, message: '请填写必填项', trigger: 'change' },
]
const blank = () => ({
  code: '',
  name: '',
  methodology: '',
  resultType: ResultType.NUMERIC,
  unit: '',
  active: true,
  deviceRequired: false,
  version: null,
})
const itemForm = reactive(blank())
async function open(row) {
  if (saving.value) return
  editingId.value = row?.id || null
  error.value = ''
  Object.assign(itemForm, blank(), row || {})
  editorOpen.value = true
  await nextTick()
  itemFormRef.value?.clearValidate()
}
async function save() {
  if (saving.value) return
  const valid = await itemFormRef.value?.validate().catch(() => false)
  if (!valid || saving.value) return
  saving.value = true
  error.value = ''
  const { code, name, methodology, resultType, unit, active, version, deviceRequired } =
    itemForm
  try {
    const payload = {
      code,
      name,
      methodology,
      resultType,
      unit,
      active,
      version,
      deviceRequired,
    }
    await (editingId.value
      ? updateLabItem(editingId.value, payload)
      : createLabItem(payload))
    ElMessage.success('保存成功')
    editorOpen.value = false
    emit('saved')
  } catch (failure) {
    error.value =
      failure.response?.data?.message || failure.message || '保存失败'
  } finally {
    saving.value = false
  }
}
defineExpose({ open })
</script>
<style scoped lang="scss" src="./lab-editor.scss" />
