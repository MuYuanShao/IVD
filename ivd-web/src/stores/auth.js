import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi, refreshToken as refreshApi, getUserInfo } from '@/api/auth'

/**
 * 认证 Store — JWT Token 管理 + 用户信息
 */
export const useAuthStore = defineStore('auth', () => {
  // ── State ──
  const token = ref(localStorage.getItem('ivd-token') || '')
  const refreshTokenVal = ref(localStorage.getItem('ivd-refresh-token') || '')
  const user = ref(null)
  const loading = ref(false)

  // ── Getters ──
  const isLoggedIn = computed(() => !!token.value)
  const role = computed(() => user.value?.role || '')
  const realName = computed(() => user.value?.realName || user.value?.username || '')
  const roleLabel = computed(() => {
    const map = { ADMIN: '系统管理员', USER: '操作员', AUDITOR: '审核员' }
    return map[role.value] || role.value || '检验科主任'
  })

  // ── Actions ──

  /** 登录 */
  async function login(username, password) {
    loading.value = true
    try {
      const data = await loginApi({ username, password })
      token.value = data.accessToken
      refreshTokenVal.value = data.refreshToken
      user.value = {
        userId: data.userId,
        username: data.username,
        role: data.role,
        realName: data.realName
      }
      // 持久化
      localStorage.setItem('ivd-token', data.accessToken)
      localStorage.setItem('ivd-refresh-token', data.refreshToken)
      return data
    } finally {
      loading.value = false
    }
  }

  /** 刷新 Token */
  async function refresh() {
    try {
      const data = await refreshApi({ refreshToken: refreshTokenVal.value })
      token.value = data.accessToken
      refreshTokenVal.value = data.refreshToken
      localStorage.setItem('ivd-token', data.accessToken)
      localStorage.setItem('ivd-refresh-token', data.refreshToken)
      return data
    } catch {
      // 刷新失败，强制登出
      await logout()
      throw new Error('Token 刷新失败')
    }
  }

  /** 获取用户信息 */
  async function fetchUserInfo() {
    try {
      const data = await getUserInfo()
      user.value = data
    } catch (e) {
      console.error('获取用户信息失败:', e)
    }
  }

  /** 登出 */
  async function logout() {
    token.value = ''
    refreshTokenVal.value = ''
    user.value = null
    localStorage.removeItem('ivd-token')
    localStorage.removeItem('ivd-refresh-token')
  }

  return {
    token, refreshTokenVal, user, loading,
    isLoggedIn, role, realName, roleLabel,
    login, refresh, fetchUserInfo, logout
  }
})
