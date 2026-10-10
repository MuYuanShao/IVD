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
  const deptId = computed(() => user.value?.departmentId || user.value?.deptId || null)
  const deptName = computed(() => user.value?.deptName || '')
  const roleLabel = computed(() => {
    const map = {
      SYSTEM_ADMIN: '系统管理员',
      STOREKEEPER: '库管员',
      DEPT_ADMIN: '科室管理员',
      LAB_TECH: '检验技师',
      AUDITOR: '审核员',
      ADMIN: '系统管理员',
      USER: '操作员'
    }
    return map[role.value] || role.value || '检验科主任'
  })

  // ── Actions ──

  /** 登录 */
  async function login(username, password) {
    loading.value = true
    console.log('[Auth Store] 开始登录:', username)
    try {
      const data = await loginApi({ username, password })
      if (!data || !data.accessToken) {
        console.error('[Auth Store] 服务器返回数据异常:', data)
        throw new Error('服务器返回数据异常')
      }
      console.log('[Auth Store] 登录成功，保存 token:', data.accessToken.substring(0, 30) + '...')
      token.value = data.accessToken
      refreshTokenVal.value = data.refreshToken
      user.value = {
        userId: data.userId,
        username: data.username,
        role: data.role,
        realName: data.realName,
        institutionId: data.institutionId,
        departmentId: data.departmentId,
        features: data.features || {}
      }
      // 持久化
      localStorage.setItem('ivd-token', data.accessToken)
      localStorage.setItem('ivd-refresh-token', data.refreshToken)
      return data
    } catch (err) {
      console.error('[Auth Store] 登录失败:', err.message || err)
      throw err
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
    isLoggedIn, role, realName, deptId, deptName, roleLabel,
    login, refresh, fetchUserInfo, logout
  }
})
