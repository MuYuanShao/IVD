<!--
  CreatePOFromShortageDialog.vue — 从缺货单生成采购订单弹窗
  表单：供应商下拉/预计到货日期/结算模式/备注
-->
<template>
  <el-dialog :model-value="modelValue" title="生成采购订单" width="520px" top="8vh" @update:model-value="$emit('update:modelValue', $event)" @closed="resetForm">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
      <el-form-item label="来源缺货单">
        <el-input :model-value="shortageNo || '—'" disabled />
      </el-form-item>
      <el-form-item label="供应商" prop="supplierId">
        <el-select v-model="form.supplierId" placeholder="请选择供应商" style="width:100%" filterable>
          <el-option v-for="s in suppliers" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="预计到货日期" prop="expectedDeliveryDate">
        <el-date-picker v-model="form.expectedDeliveryDate" type="date" placeholder="选择日期" style="width:100%" value-format="YYYY-MM-DD" />
      </el-form-item>
      <el-form-item label="结算模式">
        <el-select v-model="form.settlementMode" placeholder="请选择结算模式" style="width:100%">
          <el-option label="消耗结算" value="消耗结算" />
          <el-option label="领用结算" value="领用结算" />
          <el-option label="验收结算" value="验收结算" />
          <el-option label="调拨结算" value="调拨结算" />
        </el-select>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="选填" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="loading" @click="submitForm">确认生成</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  shortageId: { type: [Number, String], default: null },
  shortageNo: { type: String, default: '' },
  suppliers: { type: Array, default: () => [
    { value: 1, label: '九州通医药集团' },
    { value: 2, label: '国药控股' },
    { value: 3, label: '华润医药' }
  ]}
})

const emit = defineEmits(['update:modelValue', 'created'])

const formRef = ref(null)
const loading = ref(false)
const form = reactive({
  supplierId: null,
  expectedDeliveryDate: '',
  settlementMode: '调拨结算',
  remark: ''
})

const rules = {
  supplierId: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  expectedDeliveryDate: [{ required: true, message: '请选择预计到货日期', trigger: 'change' }]
}

function resetForm() {
  formRef.value?.resetFields()
  Object.assign(form, { supplierId: null, expectedDeliveryDate: '', settlementMode: '调拨结算', remark: '' })
  loading.value = false
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  emit('created', {
    shortageId: props.shortageId,
    supplierId: form.supplierId,
    expectedDeliveryDate: form.expectedDeliveryDate,
    settlementMode: form.settlementMode,
    remark: form.remark
  })
  loading.value = false
}
</script>
