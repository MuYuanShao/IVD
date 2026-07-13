<!--
  RequisitionDetailDialog.vue — 申领明细弹窗（共用）
  被 RequisitionList (查看明细) 和 ShortageList (查看来源申领) 共用
-->
<template>
  <el-dialog :model-value="modelValue" title="申领明细" width="680px" top="5vh" @update:model-value="$emit('update:modelValue', $event)">
    <template v-if="detail">
      <el-descriptions :column="3" border size="small" style="margin-bottom:16px">
        <el-descriptions-item label="申领单号">{{ detail.reqNo }}</el-descriptions-item>
        <el-descriptions-item label="申领科室">{{ detail.fromDeptId }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag size="small">{{ detail.status }}</el-tag>
        </el-descriptions-item>
      </el-descriptions>
      <el-table
        :data="(detail.items && detail.items.length > 0) ? detail.items : [{ reagentName: '—', qty: 0, shippedQty: 0, shortageQty: 0 }]"
        size="small" empty-text="暂无明细"
      >
        <el-table-column prop="reagentName" label="试剂名称" min-width="120" />
        <el-table-column prop="qty" label="申领量" width="80" />
        <el-table-column prop="shippedQty" label="发货量" width="80" />
        <el-table-column prop="shortageQty" label="缺货量" width="80">
          <template #default="{row}">
            <span :style="{ color: (row.shortageQty || 0) > 0 ? 'var(--ivd-danger)' : '' }">
              {{ row.shortageQty || 0 }}
            </span>
          </template>
        </el-table-column>
      </el-table>
    </template>
    <el-skeleton v-else :rows="6" animated />
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
defineProps({
  modelValue: { type: Boolean, default: false },
  detail: { type: Object, default: null }
})

defineEmits(['update:modelValue'])
</script>
