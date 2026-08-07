import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * Axios 请求封装
 * - 自动携带 JWT Token
 * - 统一错误处理
 * - 401 自动跳转登录页
 */
const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 15000
})

// 请求拦截器
service.interceptors.request.use(
  config => {
    const token = localStorage.getItem('ivd-token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => Promise.reject(error)
)

// 响应拦截器
service.interceptors.response.use(
  response => {
    // 二进制/Blob 响应直接返回原始数据，不做 JSON 解包
    if (response.config.responseType === 'blob' || response.config.responseType === 'arraybuffer') {
      return response.data
    }
    // 防御：非 JSON 响应（如 500 错误页 HTML）直接 reject
    if (!response.data || typeof response.data !== 'object') {
      ElMessage.error('服务器响应格式异常')
      return Promise.reject(new Error('Invalid response format'))
    }
    const { code, message, data } = response.data
    if (code === 200) {
      return data
    }
    // 业务错误码
    if (code === 40100) {
      // 登录接口本身的 40100（用户名或密码错误）直接透传错误消息
      const isLoginRequest = response.config.url?.includes('/auth/login')
      if (isLoginRequest) {
        return Promise.reject(new Error(message || '用户名或密码错误'))
      }
      // 其他接口 40100：token 过期/无效，清除 token 并跳转登录页
      console.warn('[Request] 收到 40100，清除 token 并重定向到登录页')
      localStorage.removeItem('ivd-token')
      window.location.href = '/login'
      return Promise.reject(new Error(message || '登录已过期，请重新登录'))
    }
    console.error('[Request] 业务错误:', code, message)
    ElMessage.error(message || '请求失败')
    return Promise.reject(new Error(message))
  },
  error => {
    console.error('[Request] HTTP错误:', error.response?.status, error.message)
    if (error.response?.status === 401 || error.response?.status === 403) {
      console.warn('[Request] HTTP ' + error.response.status + '，清除 token 并重定向到登录页')
      localStorage.removeItem('ivd-token')
      window.location.href = '/login'
      return Promise.reject(error)
    }
    ElMessage.error(error.message || '网络异常')
    return Promise.reject(error)
  }
)

export default service
