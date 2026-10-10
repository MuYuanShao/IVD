<template>
  <el-dialog
    v-model="visible"
    title="审批详情"
    width="640px"
    :close-on-click-modal="false"
  >
    <el-descriptions :column="2" border size="small">
      <el-descriptions-item label="试剂ID">{{ detail.reagentId }}</el-descriptions-item>
      <el-descriptions-item label="变更类型">
        <el-tag :type="changeTypeTag(detail.changeType)" size="small">
          {{ changeTypeLabel(detail.changeType) }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="申请人">{{ detail.submitterName }}</el-descriptions-item>
      <el-descriptions-item label="提交时间">{{ detail.submittedAt }}</el-descriptions-item>
      <el-descriptions-item label="变更原因" :span="2">{{ detail.changeReason || '-' }}</el-descriptions-item>
      <el-descriptions-item label="审批状态">
        <el-tag
          :type="detail.reviewStatus === 'PENDING' ? 'warning' : detail.reviewStatus === 'APPROVED' ? 'success' : 'danger'"
          size="small"
        >
          {{ reviewStatusLabel(detail.reviewStatus) }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="审批人">{{ detail.reviewerName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="审批意见" :span="2">{{ detail.reviewComment || '-' }}</el-descriptions-item>
      <el-descriptions-item label="审批时间">{{ detail.reviewedAt || '-' }}</el-descriptions-item>
    </el-descriptions>

    <el-divider content-position="left">变更字段快照</el-divider>
    <div v-if="fieldChangesParsed" class="field-changes">
      <el-table :data="fieldChangesParsed" size="small" max-height="300">
        <el-table-column prop="field" label="字段" width="160" />
        <el-table-column prop="old" label="变更前">
          <template #default="{ row }">
            <span class="old-value">{{ row.old }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="new" label="变更后">
          <template #default="{ row }">
            <span class="new-value">{{ row.new }}</span>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-empty v-else description="无变更详情（首次创建或快照格式）" :image-size="60" />

    <template #footer>
      <el-button @click="visible = false">关 闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  detail: { type: Object, default: () => ({}) }
})

const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const changeTypeLabel = (t) => ({ CREATE: '新增', UPDATE: '编辑', DELETE: '删除', DISABLE: '停用' }[t] || t)
const changeTypeTag = (t) => ({ CREATE: 'success', UPDATE: 'warning', DELETE: 'danger', DISABLE: 'info' }[t] || 'info')
const reviewStatusLabel = (s) => ({ PENDING: '待审批', APPROVED: '已通过', REJECTED: '已驳回' }[s] || s)

const fieldChangesParsed = computed(() => {
  if (!props.detail.fieldChanges) return null
  try {
    const parsed = typeof props.detail.fieldChanges === 'string'
      ? JSON.parse(props.detail.fieldChanges)
      : props.detail.fieldChanges
    // 如果是扁平对象（完整实体快照），展示所有字段
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
      // 检测是否是 {fieldName: {old, new}} 格式
      const keys = Object.keys(parsed)
      if (keys.length > 0 && parsed[keys[0]] && typeof parsed[keys[0]] === 'object' && parsed[keys[0]].old !== undefined) {
        return keys.map(k => ({ field: k, old: formatVal(parsed[k].old), new: formatVal(parsed[k].new) }))
      }
      // 否则是完整快照，展示 key-value
      return keys.map(k => ({ field: k, old: '', new: formatVal(parsed[k]) }))
    }
    return null
  } catch {
    return null
  }
})

function formatVal(v) {
  if (v === null || v === undefined) return '-'
  if (typeof v === 'object') return JSON.stringify(v)
  return String(v)
}
</script>

<style scoped>
.field-changes {
  margin-top: 8px;
}
.old-value {
  color: #909399;
}
.new-value {
  color: #409eff;
  font-weight: 500;
}
</style>
