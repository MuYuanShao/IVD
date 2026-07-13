<!--
  TraceDialog.vue — UDI 追溯弹窗（共用）
  被 CentralWarehouse / DeptWarehouse / GroupWarehouse 共用
  内部调用 useInventoryStore().doTrace()
-->
<template>
  <el-dialog :model-value="modelValue" title="UDI 追溯" width="800px" top="5vh" @update:model-value="$emit('update:modelValue', $event)">
    <el-input
      v-model="search" placeholder="输入 UDI-DI 或批号" :prefix-icon="Search"
      style="width:300px;margin-bottom:16px" @keyup.enter="doSearch"
    />
    <template v-if="result">
      <el-descriptions :column="3" border size="small" style="margin-bottom:20px">
        <el-descriptions-item label="批号">{{ result.batchNo }}</el-descriptions-item>
        <el-descriptions-item label="UDI-DI">{{ result.udiDi }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag size="small">{{ result.statusLabel }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="库存">{{ result.stock }}</el-descriptions-item>
        <el-descriptions-item label="效期至">{{ result.expiryDate }}</el-descriptions-item>
        <el-descriptions-item label="仓库ID">{{ result.warehouseId }}</el-descriptions-item>
      </el-descriptions>
      <el-steps
        :active="result.timeline ? result.timeline.filter(t => t.status === 'completed').length : 0"
        finish-status="success" align-center
      >
        <el-step
          v-for="(t, i) in (result.timeline || [])" :key="i"
          :title="t.node" :status="t.status === 'completed' ? 'success' : i === 0 ? 'finish' : 'wait'"
        />
      </el-steps>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { useInventoryStore } from '@/stores/inventory'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  initialSearch: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue'])

const store = useInventoryStore()
const search = ref('')
const result = ref(null)

watch(() => props.modelValue, (v) => {
  if (v) {
    search.value = props.initialSearch || ''
    result.value = null
    if (search.value.trim()) doSearch()
  }
})

async function doSearch() {
  if (!search.value.trim()) return
  try {
    await store.doTrace(search.value)
    result.value = store.traceResult
  } catch { result.value = null }
}
</script>
