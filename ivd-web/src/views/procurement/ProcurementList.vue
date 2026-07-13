<!--
  ProcurementList.vue — 旧路由重定向页
  v2 重构后 4 个子页面已拆分为独立路由：
    /requisition（申领管理）/shortage（缺货单）/purchase（采购订单）/inbound（扫码验收）
  此处保留兼容：如通过旧链接访问 /procurement，直接跳转到 /requisition
-->
<template>
  <div class="page-container" style="display:flex;align-items:center;justify-content:center;min-height:400px">
    <el-result icon="info" title="页面已迁移" sub-title="采购管理已拆分为独立子页面，正在为您跳转…">
      <template #extra>
        <el-button type="primary" @click="$router.push('/requisition')">前往申领管理</el-button>
        <el-button @click="$router.push('/purchase')">前往采购订单</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
const router = useRouter()
onMounted(() => router.replace('/requisition'))
</script>
