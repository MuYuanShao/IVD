<template>
  <section class="page-container clinical-page">
    <el-alert v-if="!enabled" title="临床业务尚未启用或当前角色无权访问" description="完成机构隔离、临床迁移和独立密钥配置后才可使用。此页面不加载模拟患者数据。" type="info" :closable="false" show-icon />
    <template v-else>
      <header class="clinical-header"><div><h2>{{ title }}</h2><p>{{ description }}</p></div><slot name="actions" /></header>
      <el-card shadow="never">
        <div class="clinical-toolbar"><slot name="toolbar" /><el-button :loading="loading" @click="emit('refresh')">刷新</el-button></div>
        <el-alert v-if="error" :title="error" type="error" :closable="false" />
        <el-table v-loading="loading" :data="rows" row-key="id" :empty-text="error ? '读取失败，请重试' : '暂无授权记录'"><slot /></el-table>
        <el-pagination :current-page="page" :page-size="20" :total="total" layout="total, prev, pager, next" @current-change="emit('page-change', $event)" />
      </el-card>
    </template>
  </section>
</template>
<script setup>
defineProps({ title: String, description:{ type:String,default:'机构与科室授权访问 · 列表脱敏 · 详情读取留痕' }, enabled: Boolean, loading: Boolean, error: String, rows: Array, total: Number, page: Number })
const emit = defineEmits(['refresh', 'page-change'])
</script>
<style scoped>
.clinical-header,.clinical-toolbar { display:flex; gap:12px; align-items:center; justify-content:space-between; margin-bottom:16px }
.clinical-header p { color:var(--scm-muted); margin-top:4px }
.clinical-toolbar { justify-content:flex-start; flex-wrap:wrap }
.el-pagination { margin-top:16px; justify-content:flex-end }
</style>
