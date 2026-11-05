import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as apiLogin, fetchCurrentUser } from '@/api/auth'

/**
 * 用户状态管理
 *
 * <p><b>为什么用 localStorage 而不是 sessionStorage</b>：
 * 演示答辩时会频繁刷新页面，sessionStorage 关掉标签页就丢。
 * 这里存的是 JWT，本身有过期时间兜底，
 * 且项目是课程演示性质，不涉及真实敏感数据。
 *
 * <p><b>安全边界说明</b>（答辩可能被问到）：
 * 生产环境更稳妥的做法是 httpOnly Cookie 存 refresh token +
 * 内存存 access token，避免 XSS 直接读取。
 * 本项目选择 localStorage 是权衡后的结果——
 * 实现简单、刷新不丢登录态，适合演示场景。
 */
const TOKEN_KEY = 'feedback_hub_token'
const USER_KEY = 'feedback_hub_user'

export const useUserStore = defineStore('user', () => {
  // ==================== 状态 ====================

  /** JWT 令牌 */
  const token = ref(localStorage.getItem(TOKEN_KEY) || '')

  /** 当前用户信息（不含密码） */
  const userInfo = ref(readUser())

  /**
   * 从本地存储读取用户信息
   *
   * <p>JSON.parse 可能抛异常（数据被手工改坏），
   * 这里吃掉异常回退到 null，避免整个应用白屏。
   *
   * @returns {object|null} 用户信息
   */
  function readUser() {
    try {
      const raw = localStorage.getItem(USER_KEY)
      return raw ? JSON.parse(raw) : null
    } catch {
      return null
    }
  }

  // ==================== 计算属性 ====================

  /** 是否已登录（有 token 且有用户信息） */
  const isLogin = computed(() => !!token.value && !!userInfo.value)

  /** 当前角色代码 */
  const role = computed(() => userInfo.value?.role || '')

  /** 当前角色中文名 */
  const roleLabel = computed(() => userInfo.value?.roleLabel || '')

  /** 显示名称 */
  const displayName = computed(() => userInfo.value?.displayName || '未登录')

  /**
   * 是否具备某角色
   *
   * @param {...string} roles 允许的角色代码
   * @returns {boolean} 是否匹配
   */
  const hasRole = computed(() => (...roles) => roles.includes(role.value))

  /** 是否为产品经理 */
  const isPm = computed(() => role.value === 'PM')

  /** 是否为开发工程师 */
  const isDev = computed(() => role.value === 'DEV')

  /** 是否为客服 */
  const isCs = computed(() => role.value === 'CS')

  // ==================== 动作 ====================

  /**
   * 登录并保存会话
   *
   * @param {{username: string, password: string}} payload 登录参数
   * @returns {Promise<object>} 登录结果
   */
  async function login(payload) {
    const data = await apiLogin(payload)
    token.value = data.token
    userInfo.value = {
      userId: data.userId,
      username: data.username,
      displayName: data.displayName,
      role: data.role,
      roleLabel: data.roleLabel,
      permissions: data.permissions || []
    }
    localStorage.setItem(TOKEN_KEY, data.token)
    localStorage.setItem(USER_KEY, JSON.stringify(userInfo.value))
    return data
  }

  /**
   * 刷新页面后恢复登录态
   *
   * <p>只用本地缓存的 token 去后端换一次真实用户信息，
   * 这样即使本地缓存被篡改，也拿不到错误的身份。
   *
   * @returns {Promise<boolean>} 是否恢复成功
   */
  async function restoreSession() {
    if (!token.value) return false
    try {
      const data = await fetchCurrentUser()
      userInfo.value = data
      localStorage.setItem(USER_KEY, JSON.stringify(data))
      return true
    } catch {
      // token 失效，清理干净
      logout()
      return false
    }
  }

  /**
   * 退出登录
   *
   * @param {boolean} silent 是否静默退出（不跳转）。
   *        Token 过期被拦截器调用时传 true，由拦截器自己控制跳转，
   *        避免出现"先跳登录页再跳一次"的抖动。
   */
  function logout(silent = false) {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
    if (!silent) {
      // 动态引入避免循环依赖：router → store → router
      import('@/router').then(({ default: router }) => {
        router.push({ name: 'login' })
      })
    }
  }

  return {
    token,
    userInfo,
    isLogin,
    role,
    roleLabel,
    displayName,
    hasRole,
    isPm,
    isDev,
    isCs,
    login,
    restoreSession,
    logout
  }
})
