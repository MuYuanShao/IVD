<template>
  <el-dialog v-model="visible" title="目录变更详情" width="min(840px, 94vw)" destroy-on-close>
    <el-descriptions :column="2" border>
      <el-descriptions-item label="院内编码">{{ record?.hisCode || '—' }}</el-descriptions-item>
      <el-descriptions-item label="操作人">{{ record?.operatorName || '—' }}</el-descriptions-item>
      <el-descriptions-item label="时间">{{ record?.createdAt || '—' }}</el-descriptions-item>
      <el-descriptions-item label="类型">{{ record?.changeTypeLabel || '—' }}</el-descriptions-item>
    </el-descriptions>
    <el-table :data="changes" border max-height="420" empty-text="该历史记录未保存字段差异，不能恢复原值" style="margin-top:16px">
      <el-table-column prop="field" label="字段" min-width="160" />
      <el-table-column prop="before" label="变更前" min-width="220" />
      <el-table-column prop="after" label="变更后" min-width="220" />
    </el-table>
    <template #footer><el-button @click="visible = false">关闭</el-button></template>
  </el-dialog>
</template>
<script setup>
import { computed } from 'vue'
const props = defineProps({ modelValue: Boolean, record: { type: Object, default: null } })
const emit = defineEmits(['update:modelValue'])
const visible = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })
const labels = { genericName: '物料名称', manufacturer: '生产厂家', distributor: '配送商', pricePerUnit: '含税单价',
  testsPerKit: '标称测试数', stabilityDaysAfterOpen: '开瓶稳定天数', leadTimeDays: '采购提前期', testGroups: '适用组别',
  deviceBindMode: '设备关联策略', registrationCertNo: '注册证号', udiDi: 'UDI-DI', detectionMethod: '检测方法学',
  specification: '规格', unit: '单位', volumeType: '带量类型', insurancePaymentType: '医保支付类型' }
function display(value) { return value == null ? '空值' : typeof value === 'object' ? JSON.stringify(value) : String(value) }
const changes = computed(() => {
  try {
    const value = typeof props.record?.fieldChanges === 'string' ? JSON.parse(props.record.fieldChanges) : props.record?.fieldChanges
    return Object.entries(value || {}).map(([field, item]) => {
      const diff = item && typeof item === 'object' && ('old' in item || 'new' in item)
      return { field: labels[field] || field, before: diff ? display(item.old) : '历史快照未记录', after: display(diff ? item.new : item) }
    })
  } catch { return [] }
})
</script>
