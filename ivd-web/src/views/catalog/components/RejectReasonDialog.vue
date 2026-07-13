<template>
  <el-dialog
    v-model="visible"
    title="驳回原因"
    width="480px"
    :close-on-click-modal="false"
    @closed="handleClosed"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="审批人">
        <el-input v-model="form.reviewerName" placeholder="请输入审批人姓名" maxlength="64" />
      </el-form-item>
      <el-form-item label="驳回原因" prop="comment">
        <el-input
          v-model="form.comment"
          type="textarea"
          :rows="4"
          placeholder="请输入驳回原因（必填）"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取 消</el-button>
      <el-button type="danger" :loading="submitting" @click="handleConfirm">
        确认驳回
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useCatalogStore } from '@/stores/catalog'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  logId: { type: Number, default: null }
})

const emit = defineEmits(['update:modelValue', 'rejected'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const catalogStore = useCatalogStore()
const formRef = ref(null)
const submitting = ref(false)

const form = reactive({
  reviewerId: 1,
  reviewerName: '',
  comment: ''
})

const rules = {
  reviewerName: [{ required: true, message: '请输入审批人姓名', trigger: 'blur' }],
  comment: [{ required: true, message: '请输入驳回原因', trigger: 'blur' }]
}

async function handleConfirm() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await catalogStore.rejectApproval(props.logId, {
      reviewerId: form.reviewerId,
      reviewerName: form.reviewerName,
      comment: form.comment
    })
    ElMessage.success('已驳回')
    emit('rejected')
    visible.value = false
  } catch {
    // 错误由 request 拦截器统一处理
  } finally {
    submitting.value = false
  }
}

function handleClosed() {
  formRef.value?.resetFields()
  form.reviewerName = ''
  form.comment = ''
}
</script>
