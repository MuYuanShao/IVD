<template>
  <el-main class="scm-main">
    <div class="page-context">
      <el-breadcrumb separator="/" aria-label="面包屑">
        <el-breadcrumb-item :to="{ path: homePath }"
          ><el-icon><HomeFilled /></el-icon> 工作台</el-breadcrumb-item
        >
        <el-breadcrumb-item
          v-for="item in breadcrumbs"
          :key="item.path"
          :to="item.path"
          >{{ item.title }}</el-breadcrumb-item
        >
      </el-breadcrumb>
      <span class="scm-muted"
        >{{ dateLabel }}
        <span class="demo-label">{{
          isDemo || route.meta.mock ? '演示数据' : '业务工作区'
        }}</span></span
      >
    </div>
    <router-view />
    <footer>
      Copyright © {{ new Date().getFullYear() }} IVD 智慧实验室管理系统
      <span>·</span> 让每一份医用耗材物尽其用
    </footer>
  </el-main>
</template>
<script setup>
import { HomeFilled } from '@element-plus/icons-vue'
import { useRoute } from 'vue-router'
import { useWorkspaceNavigation } from '@/composables/useWorkspaceNavigation'
const route = useRoute()
const { breadcrumbs, isDemo, homePath } = useWorkspaceNavigation()
const dateLabel = new Intl.DateTimeFormat('zh-CN', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
  weekday: 'long',
}).format(new Date())
</script>
<style scoped>
.scm-main {
  width: 100%;
  max-width: calc(var(--scm-content-width) + 32px);
  margin: 0 auto;
  padding: 0 16px;
  overflow: visible;
}
.page-context {
  min-height: 51px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.el-breadcrumb {
  font-size: 12px;
}
.demo-label {
  margin-left: 12px;
  padding: 2px 7px;
  border: 1px solid #dce5e5;
  border-radius: 3px;
}
footer {
  text-align: center;
  color: var(--scm-muted);
  font-size: 11px;
  padding: 25px 0;
}
footer span {
  margin: 0 10px;
}
@media (max-width: 650px) {
  .page-context {
    flex-wrap: wrap;
    padding: 12px 0;
  }
}
</style>
