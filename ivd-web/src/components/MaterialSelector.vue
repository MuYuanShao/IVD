<!--
  MaterialSelector.vue — 试剂/物料选择器
  Usage: <MaterialSelector v-model="selectedId" placeholder="选择试剂" />
-->
<template>
  <el-select
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    :placeholder="placeholder"
    :multiple="multiple"
    filterable
    remote
    :remote-method="searchMaterials"
    :loading="loading"
    style="width:100%"
    v-bind="$attrs"
  >
    <el-option
      v-for="m in options"
      :key="m.id"
      :label="`${m.name || m.reagentName} (${m.spec || ''})`"
      :value="m.id"
    >
      <span style="float:left">{{ m.name || m.reagentName }}</span>
      <span style="float:right;color:var(--text-disabled);font-size:12px">{{ m.spec || m.catalogNo || '' }}</span>
    </el-option>
  </el-select>
</template>

<script setup>
import { ref } from 'vue'

defineProps({
  modelValue: { type: [Number, String, Array], default: null },
  placeholder: { type: String, default: '请选择试剂' },
  multiple: { type: Boolean, default: false }
})
defineEmits(['update:modelValue'])

const options = ref([])
const loading = ref(false)

async function searchMaterials(query) {
  if (!query) { options.value = []; return }
  loading.value = true
  try {
    // TODO: call catalog API search
    options.value = [
      { id: 1, name: '肌酐(酶法)', spec: '100ml', catalogNo: 'C001' },
      { id: 2, name: 'ALT试剂', spec: '200ml', catalogNo: 'C002' },
      { id: 3, name: '葡萄糖测定试剂', spec: '500ml', catalogNo: 'C003' },
    ].filter(m => m.name.includes(query))
  } finally { loading.value = false }
}
</script>
