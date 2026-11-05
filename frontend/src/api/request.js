import axios from 'axios'
import { useUserStore } from '@/stores/user'

/**
 * HTTP 请求层
 *
 * <p><b>本文件是整个前端与后端对接的唯一出口</b>，
 * 所有接口都走这里创建的实例，好处是：
 * <ul>
 *   <li>Token 只在一处注入，不用每个请求手写 Header</li>
 *   <li>错误只在一处处理，六态反馈中的「失败/冲突/无权限」统一在这里判定</li>
 *   <li>将来要加请求重试、取消、埋点，改一个文件就够</li>
 * </ul>
 */

/**
 * 创建 axios 实例
 *
 * <p>baseURL 用相对路径 '/api'：
 * 开发环境由 Vite dev server 代理到 8081，
 * 生产环境由 Nginx 反向代理到后端。
 * 写相对路径的好处是同一个前端包，
 * 部署到任何域名都不需要重新打包。
 */
const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json;charset=UTF-8'
  }
})

// ==================== 请求拦截器 ====================

/**
 * 请求拦截器：注入 JWT
 *
 * <p>为什么不在登录时把 token 写死：
 * token 有过期时间，且用户可能切换账号，
 * 每次请求时动态从 store 读取能保证总是拿到最新的。
 */
request.interceptors.request.use(
  (config) => {
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers.Authorization = `Bearer ${userStore.token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// ==================== 响应拦截器 ====================

/**
 * 响应拦截器：统一剥离包装、统一错误处理
 *
 * <p>后端统一返回 { code, message, data, traceId, timestamp }，
 * 这里做两件事：
 * <ol>
 *   <li>code 为 2xx 时，直接返回 data，业务组件拿到的就是纯数据，
 *       不用每次写 response.data.data 这种嵌套。</li>
 *   <li>其余情况抛出规范化错误对象，交由调用方决定展示方式。</li>
 * </ol>
 *
 * <p><b>六态反馈的落点就在这里</b>：
 * 401 → 跳转登录；403 → 无权限态；409 → 冲突态；
 * 503 → AI 降级提示（不阻断业务）；其余 → 通用失败态。
 */
request.interceptors.response.use(
  (response) => {
    // 先读取外层结构，这里刻意用 response.data 而非解构，
    // 因为后端可能对文件下载等场景返回非 JSON
    const body = response.data

    // 后端统一包装：有 code 字段就按统一结构处理
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code >= 200 && body.code < 300) {
        return body.data
      }
      // 业务失败，规范化后抛出
      return Promise.reject(
        normalizeError({
          code: body.code,
          message: body.message,
          traceId: body.traceId
        })
      )
    }

    // 非统一结构，原样返回
    return body
  },
  (error) => {
    // HTTP 层就失败了（网络不通、超时、4xx、5xx）
    const status = error.response?.status
    const data = error.response?.data

    // 401：Token 过期或失效，清理登录态并跳登录页
    if (status === 401) {
      const userStore = useUserStore()
      userStore.logout(true)
      return Promise.reject(
        normalizeError({
          code: 401,
          message: data?.message || '登录已过期，请重新登录',
          traceId: data?.traceId
        })
      )
    }

    return Promise.reject(
      normalizeError({
        code: status || -1,
        message: data?.message || error.message || '网络异常，请稍后重试',
        traceId: data?.traceId
      })
    )
  }
)

/**
 * 规范化错误对象
 *
 * <p>为什么要多此一举：
 * 原始 error 对象结构不稳定（HTTP 错误、业务错误、网络错误三种来源），
 * 统一成 { code, message, traceId, kind } 之后，
 * 页面里就能用统一的 kind 去决定展示哪种 UI 状态。
 *
 * @param {object} raw 原始错误信息
 * @returns {object} 规范化错误对象
 */
function normalizeError({ code, message, traceId }) {
  let kind = 'error'
  if (code === 401) kind = 'unauthorized'
  else if (code === 403) kind = 'forbidden'
  else if (code === 409) kind = 'conflict'
  else if (code === 404) kind = 'notfound'
  else if (code === 503) kind = 'degraded' // AI 不可用，但业务可继续

  return { code, message, traceId, kind }
}

export default request
