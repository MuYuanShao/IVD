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
    const { code, message, data } = response.data
    if (code === 200) {
      return data
    }
    // 业务错误码
    if (code === 40100) {
      localStorage.removeItem('ivd-token')
      window.location.href = '/login'
      return Promise.reject(new Error('未认证'))
    }
    ElMessage.error(message || '请求失败')
    return Promise.reject(new Error(message))
  },
  error => {
    if (error.response?.status === 401 || error.response?.status === 403) {
      localStorage.removeItem('ivd-token')
      window.location.href = '/login'
    }
    ElMessage.error(error.message || '网络异常')
    return Promise.reject(error)
  }
)

export default service
