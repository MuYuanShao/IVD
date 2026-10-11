<template>
  <el-dialog v-model="visible" title="结算业务追溯" width="min(860px, 94vw)" destroy-on-close>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-table v-loading="loading" :data="rows" border max-height="460" empty-text="暂无不可变业务事件；历史记录不会伪造补录">
      <el-table-column prop="createdAt" label="操作时间" min-width="170" />
      <el-table-column prop="operatorName" label="操作人" min-width="110" />
      <el-table-column label="状态变化" min-width="140">
        <template #default="{ row }">{{ row.fromStatus || '新增' }} → {{ row.toStatus }}</template>
      </el-table-column>
      <el-table-column prop="reason" label="操作原因／业务依据" min-width="260" />
    </el-table>
    <template #footer><el-button @click="visible = false">关闭</el-button></template>
  </el-dialog>
</template>
<script setup>
import { computed, ref, watch } from 'vue'
import { getSettlementHistory } from '@/api/settlement'
const props = defineProps({ modelValue: Boolean, settlementId: { type: [String, Number], default: null } })
const emit = defineEmits(['update:modelValue'])
const visible = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })
const rows = ref([]), error = ref(''), loading = ref(false)
let version = 0
watch(() => [props.modelValue, props.settlementId], async ([open, id]) => {
  const current = ++version
  rows.value = []; error.value = ''; loading.value = false
  if (!open || !id) return
  loading.value = true
  try { const data = await getSettlementHistory(id); if (current === version) rows.value = data }
  catch (failure) { if (current === version) error.value = failure.message || '加载结算追溯失败' }
  finally { if (current === version) loading.value = false }
})
</script>
