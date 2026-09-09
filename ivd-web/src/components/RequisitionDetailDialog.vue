<!--
  RequisitionDetailDialog.vue — 申领明细弹窗（共用）
  被 RequisitionList (查看明细) 和 ShortageList (查看来源申领) 共用

  从 /requisitions/{reqId} 详情接口读取明细和科研项目名称快照。
-->
<template>
  <el-dialog :model-value="modelValue" title="申领明细" width="min(1100px, 94vw)" align-center @update:model-value="$emit('update:modelValue', $event)">
    <!-- 申领头信息（来自列表行数据） -->
    <el-descriptions :column="3" border size="small" style="margin-bottom:16px">
      <el-descriptions-item label="申领单号">{{ reqNo }}</el-descriptions-item>
      <el-descriptions-item label="申领科室">{{ deptName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="状态">
        <el-tag size="small">{{ status }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="科研项目" :span="3">{{ loading ? '加载中…' : error ? '读取失败' : detail?.researchProjectName || '无' }}</el-descriptions-item>
    </el-descriptions>

    <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button link type="primary" @click="loadDetail">重试</el-button></el-alert>
    <!-- 通过详情接口同时读取项目快照及明细，避免列表属性过期。 -->
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
import { ref, watch, onBeforeUnmount } from 'vue'
import { getRequisitionDetail } from '@/api/procurement'

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
const detail = ref(null), error = ref('')
let loadVersion = 0

async function loadDetail() {
  const current = ++loadVersion
  loading.value = true; items.value = []; detail.value = null; error.value = ''
  try {
    const data = await getRequisitionDetail(props.reqId)
    if (current === loadVersion) { detail.value = data; items.value = data.items || [] }
  } catch (e) { if (current === loadVersion) error.value = e.message || '读取申领详情失败' }
  finally { if (current === loadVersion) loading.value = false }
}
watch(() => [props.modelValue, props.reqId], ([open, id]) => {
  if (open && id) loadDetail()
  else { ++loadVersion; loading.value = false }
})
onBeforeUnmount(() => { ++loadVersion })
</script>
