<!--
  BatchSelector.vue — 批次选择器
  Usage: <BatchSelector v-model="batchId" :reagent-id="1" />
-->
<template>
  <el-select
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    :placeholder="placeholder"
    :loading="loading"
    filterable
    style="width:100%"
    v-bind="$attrs"
  >
    <el-option
      v-for="b in batches"
      :key="b.id"
      :label="`${b.batchNo} (效期至: ${b.expiryDate}, 在库: ${b.stock})`"
      :value="b.id"
    >
      <span style="float:left">{{ b.batchNo }}</span>
      <span style="float:right;color:var(--text-disabled);font-size:12px">效期: {{ b.expiryDate }} | 库存: {{ b.stock }}</span>
    </el-option>
  </el-select>
</template>

<script setup>
import { ref, watch } from 'vue'
import { getBatchList } from '@/api/inventory'

const props = defineProps({
  modelValue: { type: [Number, String], default: null },
  placeholder: { type: String, default: '请选择批次' },
  reagentId: { type: Number, default: null }
})
defineEmits(['update:modelValue'])

const batches = ref([])
const loading = ref(false)

watch(() => props.reagentId, fetchBatches, { immediate: true })

async function fetchBatches() {
  loading.value = true
  try {
    const data = await getBatchList({ page: 1, pageSize: 100 })
    batches.value = (data.records || []).filter(b => !props.reagentId || b.reagentId === props.reagentId)
  } finally { loading.value = false }
}
</script>
