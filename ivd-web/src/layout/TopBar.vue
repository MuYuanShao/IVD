<template>
  <el-header class="scm-topbar" height="66px">
    <div class="topbar-inner">
      <router-link :to="homePath" class="brand" aria-label="IVD 首页">
        <span class="brand-mark">IVD</span>
        <span class="brand-name"
          >检验试剂精益管理平台<small
            >LEAN LABORATORY REAGENT MANAGEMENT</small
          ></span
        >
      </router-link>
      <el-menu
        :default-active="route.path"
        mode="horizontal"
        router
        class="main-navigation"
        :ellipsis="false"
        aria-label="主导航"
      >
        <template v-for="item in menus" :key="item.path">
          <el-sub-menu
            v-if="item.children"
            :index="item.path"
            popper-class="scm-theme"
            :class="{ 'current-module': activeGroup?.path === item.path }"
          >
            <template #title>{{ item.title }}</template>
            <el-menu-item
              v-for="child in item.children"
              :key="child.path"
              :index="child.path"
              >{{ child.title }}</el-menu-item
            >
          </el-sub-menu>
          <el-menu-item v-else :index="item.path">{{
            item.title
          }}</el-menu-item>
        </template>
      </el-menu>
      <el-dropdown
        trigger="click"
        popper-class="scm-theme"
        @command="emit('user-action', $event)"
      >
        <button class="user-menu" aria-label="用户菜单">
          <el-avatar :size="30" :icon="UserFilled" />
          <span
            >{{ user.department }}<small>{{ user.name }}</small></span
          >
          <el-icon><ArrowDown /></el-icon>
        </button>
        <template #dropdown
          ><el-dropdown-menu>
            <el-dropdown-item command="profile">个人信息</el-dropdown-item>
            <el-dropdown-item command="preferences"
              >工作台设置</el-dropdown-item
            >
            <el-dropdown-item command="reset" divided
              >重置演示偏好</el-dropdown-item
            >
            <el-dropdown-item v-if="!isDemo" command="logout" divided
              >退出登录</el-dropdown-item
            >
          </el-dropdown-menu></template
        >
      </el-dropdown>
    </div>
  </el-header>
</template>
<script setup>
import { useRoute } from 'vue-router'
import { UserFilled, ArrowDown } from '@element-plus/icons-vue'
import { useWorkspaceNavigation } from '@/composables/useWorkspaceNavigation'
defineProps({ user: { type: Object, required: true } })
const emit = defineEmits(['user-action'])
const route = useRoute()
const { menus, activeGroup, isDemo, homePath } = useWorkspaceNavigation()
</script>
<style scoped>
.scm-topbar {
  padding: 0 24px;
  background: white;
  border-bottom: 1px solid var(--scm-border);
}
.topbar-inner {
  height: 100%;
  display: flex;
  align-items: center;
  gap: 22px;
  max-width: 1480px;
  margin: auto;
}
.brand {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  gap: 11px;
}
.brand-mark {
  font:
    italic 800 31px/1 Arial,
    sans-serif;
  color: var(--scm-primary);
  letter-spacing: -2px;
}
.brand-name {
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 1px;
}
.brand-name small {
  display: block;
  font-size: 7px;
  font-weight: 400;
  letter-spacing: 0.6px;
  margin-top: 3px;
  color: var(--scm-muted);
}
.main-navigation {
  flex: 1;
  min-width: 0;
  border: none;
  --el-menu-horizontal-height: 66px;
  --el-menu-hover-bg-color: var(--scm-primary-soft);
}
.main-navigation :deep(.el-menu-item),
.main-navigation :deep(.el-sub-menu__title) {
  padding: 0 12px;
  font-size: 13px;
}
.main-navigation :deep(.el-sub-menu__title) {
  padding-right: 27px;
}
.main-navigation :deep(.el-sub-menu__icon-arrow) {
  right: 9px;
}
.main-navigation :deep(.current-module > .el-sub-menu__title) {
  color: var(--scm-primary);
  border-bottom: 2px solid var(--scm-primary);
}
.user-menu {
  display: flex;
  align-items: center;
  gap: 8px;
  border: 0;
  background: transparent;
  cursor: pointer;
  color: var(--scm-text);
  text-align: left;
  padding: 4px;
  white-space: nowrap;
}
.user-menu small {
  display: block;
  font-size: 11px;
  color: var(--scm-muted);
}
.user-menu .el-avatar {
  background: #e8f4f2;
  color: var(--scm-primary);
}
@media (max-width: 1279px) {
  .scm-topbar {
    padding: 0 16px;
  }
  .topbar-inner {
    gap: 12px;
  }
  .brand-name {
    font-size: 11px;
    letter-spacing: 0;
  }
  .brand-name small {
    display: none;
  }
  .main-navigation :deep(.el-menu-item),
  .main-navigation :deep(.el-sub-menu__title) {
    font-size: 12px;
  }
}
@media (max-width: 850px) {
  .scm-topbar {
    height: auto;
  }
  .topbar-inner {
    flex-wrap: wrap;
    padding-top: 8px;
  }
  .main-navigation {
    order: 3;
    flex-basis: 100%;
    overflow-x: auto;
    --el-menu-horizontal-height: 46px;
  }
  .user-menu {
    margin-left: auto;
  }
  .topbar-inner > .el-dropdown {
    margin-left: auto;
  }
}
</style>
