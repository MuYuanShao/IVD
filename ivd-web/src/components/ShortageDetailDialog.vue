<!--
  ShortageDetailDialog.vue — 缺货明细弹窗（共用）
  明细数据从独立的 /shortages/{id}/items 接口获取，直接查询 shortage_order_item 表
-->
<template>
  <el-dialog :model-value="modelValue" title="缺货明细" width="1000px" align-center @update:model-value="$emit('update:modelValue', $event)">
    <!-- 缺货头信息 -->
    <el-descriptions :column="3" border size="small" style="margin-bottom:16px">
      <el-descriptions-item label="缺货单号">{{ header.shortageNo }}</el-descriptions-item>
      <el-descriptions-item label="来源申领">
        <el-button v-if="header.reqId" link type="primary" size="small" @click="$emit('viewRequisition', header.reqId)">查看申领单</el-button>
        <span v-else>—</span>
      </el-descriptions-item>
      <el-descriptions-item label="状态">
        <el-tag :type="getShStatusType(header.status)" size="small">{{ header.status }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="申领科室">{{ header.deptName || '—' }}</el-descriptions-item>
      <el-descriptions-item label="关联采购单">
        <el-button v-if="header.poId" link type="success" size="small" @click="$router.push('/purchase')">查看订单</el-button>
        <span v-else>未生成</span>
      </el-descriptions-item>
      <el-descriptions-item label="创建时间">{{ header.createdAt || '—' }}</el-descriptions-item>
    </el-descriptions>

    <!-- 缺货明细表（独立查询 shortage_order_item） -->
    <el-table v-loading="loading" :data="items" size="small" empty-text="暂无缺货明细" border>
      <el-table-column prop="reagentName" label="试剂名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="genericName" label="通用名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="specification" label="规格型号" min-width="100" show-overflow-tooltip />
      <el-table-column prop="manufacturer" label="厂家" min-width="100" show-overflow-tooltip />
      <el-table-column prop="unit" label="单位" width="60" />
      <el-table-column prop="qtyShortage" label="缺货数量" width="80" align="right" />
      <el-table-column prop="estUnitPrice" label="预估单价" width="90" align="right">
        <template #default="{row}">{{ row.estUnitPrice != null ? '¥' + Number(row.estUnitPrice).toFixed(2) : '-' }}</template>
      </el-table-column>
      <el-table-column label="预估金额" width="100" align="right">
        <template #default="{row}">{{ row.estAmount != null ? '¥' + Number(row.estAmount).toFixed(2) : '-' }}</template>
      </el-table-column>
    </el-table>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">关闭</el-button>
      <el-button v-if="canGeneratePO" type="primary" @click="$emit('generatePO', header.id)">生成采购单</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { getShortageItems } from '@/api/procurement'
import { getShStatusType } from '@/utils/procurement'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  header: { type: Object, default: () => ({}) }
})

defineEmits(['update:modelValue', 'viewRequisition', 'generatePO'])

const items = ref([])
const loading = ref(false)

const canGeneratePO = computed(() => props.header.status === 'PENDING_PROCUREMENT')

watch(() => props.modelValue, async (open) => {
  if (open && props.header.id) {
    loading.value = true
    items.value = []
    try {
      items.value = await getShortageItems(props.header.id) || []
    } catch (e) {
      console.error('获取缺货明细失败:', e)
    } finally {
      loading.value = false
    }
  }
})
</script>
