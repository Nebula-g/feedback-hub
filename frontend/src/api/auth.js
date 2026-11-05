import request from './request'

/**
 * 认证相关接口
 *
 * <p>对应后端 AuthController。
 */

/**
 * 登录
 *
 * @param {{username: string, password: string}} data 登录参数
 * @returns {Promise<{token: string, tokenType: string, expiresIn: number,
 *   userId: number, username: string, displayName: string,
 *   role: string, roleLabel: string, permissions: string[]}>} 登录结果
 */
export function login(data) {
  return request.post('/auth/login', data)
}

/**
 * 获取当前登录用户信息
 *
 * <p>用于刷新页面后恢复登录态，避免用户被莫名踢回登录页。
 *
 * @returns {Promise<object>} 当前用户信息
 */
export function fetchCurrentUser() {
  return request.get('/auth/me')
}

/**
 * 校验 Token 是否有效
 *
 * @returns {Promise<{userId: number, role: string}>} 校验结果
 */
export function ping() {
  return request.get('/feedbacks/ping')
}
