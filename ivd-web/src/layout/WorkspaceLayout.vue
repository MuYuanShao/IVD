<template>
  <el-container
    direction="vertical"
    class="scm-theme scm-workspace"
    :class="{ 'scm-business': business }"
  >
    <top-bar :user="displayUser" @user-action="onUserAction" />
    <sub-menu />
    <app-main />
    <el-dialog
      v-model="profileOpen"
      title="当前用户"
      width="min(480px, 92vw)"
      class="scm-theme"
    >
      <el-descriptions :column="1" border>
        <el-descriptions-item label="用户名">{{
          displayUser.name
        }}</el-descriptions-item>
        <el-descriptions-item label="科室">{{
          displayUser.department
        }}</el-descriptions-item>
        <el-descriptions-item label="角色">{{
          displayUser.role
        }}</el-descriptions-item>
        <el-descriptions-item label="数据环境">{{
          business ? '已登录业务系统' : '本地演示'
        }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
    <el-dialog
      v-model="settingsOpen"
      title="工作台设置"
      width="min(520px, 92vw)"
      class="scm-theme"
    >
      <p class="settings-hint">选择显示在首页的常用功能</p>
      <el-checkbox-group v-model="store.selectedFunctions">
        <el-checkbox
          v-for="item in store.functions"
          :key="item.path"
          :value="item.path"
          >{{ item.title }}</el-checkbox
        >
      </el-checkbox-group>
    </el-dialog>
  </el-container>
</template>
<script setup>
import { computed, onMounted, provide, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import TopBar from './TopBar.vue'
import SubMenu from './SubMenu.vue'
import AppMain from './AppMain.vue'
import { useWorkspaceStore } from '@/stores/workspace'
import { useAuthStore } from '@/stores/auth'
import '@/styles/tokens.scss'
import '@/styles/business.scss'
const props = defineProps({ business: { type: Boolean, default: false } })
const store = useWorkspaceStore()
const auth = useAuthStore()
const router = useRouter()
const displayUser = computed(() =>
  props.business
    ? {
        name: auth.realName || '当前用户',
        department: auth.deptName || '未分配科室',
        role: auth.roleLabel,
        hospital: 'IVD 智慧实验室',
      }
    : store.user,
)
provide('workspaceUser', displayUser)
onMounted(() => {
  if (props.business && auth.isLoggedIn) auth.fetchUserInfo()
})
const profileOpen = ref(false)
const settingsOpen = ref(false)
async function onUserAction(action) {
  if (action === 'profile') profileOpen.value = true
  if (action === 'preferences') settingsOpen.value = true
  if (action === 'reset') {
    store.resetPreferences()
    ElMessage.success('演示偏好已重置')
  }
  if (action === 'logout') {
    await auth.logout()
    await router.replace('/login')
  }
}
</script>
<style>
body:has(.scm-workspace) {
  margin: 0;
  background: #f3f6f7;
}
.scm-workspace {
  --layout-aside-width: 0px;
  min-height: 100vh;
  background: var(--scm-background);
}
.settings-hint {
  margin-bottom: 16px !important;
  color: #7b898e;
}
</style>
