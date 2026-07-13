<!--
  InventoryView.vue — 旧路由重定向页
  v2 重构后 4 个子页面已拆分为独立路由：
    /central（中心库）/dept（科室库）/expiry（效期预警）/warehouse（组库三级）
  此处保留兼容：如通过旧链接访问 /inventory，直接跳转到 /central
-->
<template>
  <div class="page-container" style="display:flex;align-items:center;justify-content:center;min-height:400px">
    <el-result icon="info" title="页面已迁移" sub-title="三级库管理已拆分为独立子页面，正在为您跳转…">
      <template #extra>
        <el-button type="primary" @click="$router.push('/central')">前往中心库</el-button>
        <el-button @click="$router.push('/expiry')">前往效期预警</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
const router = useRouter()
onMounted(() => router.replace('/central'))
</script>
