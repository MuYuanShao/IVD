<!--
  TransferDialog.vue — 库存调拨弹窗（共用）
  被 CentralWarehouse / DeptWarehouse / ExpiryWarning / GroupWarehouse 共用
-->
<template>
  <el-dialog :model-value="modelValue" title="库存调拨" width="520px" top="5vh" @update:model-value="$emit('update:modelValue', $event)" @closed="resetForm">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="调出库房">
        <el-input :model-value="sourceName" disabled />
      </el-form-item>
      <el-form-item label="目标库房" prop="targetWarehouse">
        <el-select v-model="form.targetWarehouse" placeholder="请选择目标库房" style="width:100%">
          <el-option v-for="w in warehouseOptions" :key="w.value" :label="w.label" :value="w.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="调拨数量" prop="quantity">
        <el-input-number v-model="form.quantity" :min="1" :max="maxQty" style="width:100%" />
        <span style="margin-left:8px;font-size:12px;color:var(--text-tertiary)">最大可调拨：{{ maxQty }}</span>
      </el-form-item>
      <el-form-item label="调拨原因">
        <el-input v-model="form.reason" type="textarea" :rows="2" placeholder="选填" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">确认调拨</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  sourceName: { type: String, default: '' },
  maxQty: { type: Number, default: 999 },
  warehouseOptions: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'submit'])

const formRef = ref(null)
const form = reactive({
  targetWarehouse: null,
  quantity: 1,
  reason: ''
})

const rules = {
  targetWarehouse: [{ required: true, message: '请选择目标库房', trigger: 'change' }],
  quantity: [{ required: true, message: '请输入调拨数量', trigger: 'blur' }]
}

function resetForm() {
  formRef.value?.resetFields()
  form.targetWarehouse = null
  form.quantity = 1
  form.reason = ''
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  emit('submit', { ...form })
}

// 弹窗关闭时重置
watch(() => props.modelValue, (v) => {
  if (!v) resetForm()
})
</script>
