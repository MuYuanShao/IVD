<!--
  ShortageDetailDialog.vue — 缺货明细弹窗（共用）
  展示缺货单详细信息：单号/来源申领/科室/试剂/规格/缺货量/预估金额/建议供应商/状态/关联PO
-->
<template>
  <el-dialog :model-value="modelValue" title="缺货明细" width="680px" top="5vh" @update:model-value="$emit('update:modelValue', $event)">
    <template v-if="detail">
      <el-descriptions :column="3" border size="small" style="margin-bottom:16px">
        <el-descriptions-item label="缺货单号">{{ detail.shortageNo }}</el-descriptions-item>
        <el-descriptions-item label="来源申领">
          <el-button v-if="detail.reqId" link type="primary" size="small" @click="$emit('viewRequisition', detail.reqId)">查看申领单</el-button>
          <span v-else>—</span>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getShStatusType(detail.status)" size="small">{{ detail.status }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="申领科室">{{ detail.deptName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="缺货试剂">{{ detail.reagentName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="规格">{{ detail.spec || '—' }}</el-descriptions-item>
        <el-descriptions-item label="缺货量">{{ detail.shortageQty || 0 }}</el-descriptions-item>
        <el-descriptions-item label="预估金额">¥{{ detail.estimatedAmount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="建议供应商">{{ detail.suggestedSupplier || '—' }}</el-descriptions-item>
        <el-descriptions-item label="关联采购单">
          <el-button v-if="detail.poId" link type="success" size="small" @click="$router.push('/purchase')">查看订单</el-button>
          <span v-else>未生成</span>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.createdAt || '—' }}</el-descriptions-item>
      </el-descriptions>
    </template>
    <el-skeleton v-else :rows="8" animated />
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">关闭</el-button>
      <el-button v-if="detail && detail.status==='PENDING_PROCUREMENT'" type="primary" @click="$emit('generatePO', detail.id)">生成采购单</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { getShStatusType } from '@/utils/procurement'

defineProps({
  modelValue: { type: Boolean, default: false },
  detail: { type: Object, default: null }
})

defineEmits(['update:modelValue', 'viewRequisition', 'generatePO'])
</script>
