<!--
  RequisitionDetailDialog.vue — 申领明细弹窗（共用）
  被 RequisitionList (查看明细) 和 ShortageList (查看来源申领) 共用

  重构：明细数据从独立的 /requisitions/{reqId}/items 接口获取，
  直接查询 purchase_requisition_item 表，不与申领总表混合。
-->
<template>
  <el-dialog :model-value="modelValue" title="申领明细" width="1100px" align-center @update:model-value="$emit('update:modelValue', $event)">
    <!-- 申领头信息（来自列表行数据） -->
    <el-descriptions :column="3" border size="small" style="margin-bottom:16px">
      <el-descriptions-item label="申领单号">{{ reqNo }}</el-descriptions-item>
      <el-descriptions-item label="申领科室">{{ deptName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="状态">
        <el-tag size="small">{{ status }}</el-tag>
      </el-descriptions-item>
    </el-descriptions>

    <!-- 明细表（独立查询 purchase_requisition_item） -->
    <el-table
      v-loading="loading"
      :data="items"
      size="small" empty-text="暂无明细" border
    >
      <el-table-column prop="reagentName" label="试剂名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="genericName" label="通用名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="specification" label="规格型号" min-width="100" show-overflow-tooltip />
      <el-table-column prop="manufacturer" label="厂家" min-width="120" show-overflow-tooltip />
      <el-table-column prop="unit" label="单位" width="60" />
      <el-table-column prop="unitPrice" label="单价" width="80" align="right">
        <template #default="{row}">{{ row.unitPrice != null ? '¥' + Number(row.unitPrice).toFixed(2) : '-' }}</template>
      </el-table-column>
      <el-table-column prop="qtyRequested" label="申领数量" width="80" align="right" />
      <el-table-column label="申请金额" width="100" align="right">
        <template #default="{row}">{{ row.amount != null ? '¥' + Number(row.amount).toFixed(2) : '-' }}</template>
      </el-table-column>
      <el-table-column prop="qtyFulfilled" label="发货数量" width="80" align="right" />
      <el-table-column prop="qtyShortage" label="缺货数量" width="80" align="right">
        <template #default="{row}">
          <span :style="{ color: (row.qtyShortage || 0) > 0 ? 'var(--ivd-danger)' : '', fontWeight: (row.qtyShortage || 0) > 0 ? '600' : '' }">
            {{ row.qtyShortage || 0 }}
          </span>
        </template>
      </el-table-column>
    </el-table>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { getRequisitionItems } from '@/api/procurement'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  reqId:      { type: [String, Number], default: null },
  reqNo:      { type: String, default: '-' },
  deptName:   { type: String, default: '-' },
  status:     { type: String, default: '-' }
})

defineEmits(['update:modelValue'])

const items = ref([])
const loading = ref(false)

watch(() => props.modelValue, async (open) => {
  if (open && props.reqId) {
    loading.value = true
    items.value = []
    try {
      items.value = await getRequisitionItems(props.reqId) || []
    } catch (e) {
      console.error('获取申领明细失败:', e)
    } finally {
      loading.value = false
    }
  }
})
</script>
