import request from '@/utils/request'

/**
 * 认证 API
 */

/** 用户名+密码登录 */
export function login(data) {
  return request({ url: '/auth/login', method: 'post', data })
}

/** 刷新 Token */
export function refreshToken(data) {
  return request({ url: '/auth/refresh', method: 'post', data })
}

/** 获取当前用户信息 */
export function getUserInfo() {
  return request({ url: '/auth/userinfo', method: 'get' })
}
