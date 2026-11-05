import request from './request'

/**
 * 站内通知接口
 *
 * <p>对应后端 NotificationController，
 * 全部接口基于当前登录用户，无需传 userId。
 */

/**
 * 查询未读通知数（顶栏角标）
 *
 * @returns {Promise<number>} 未读数
 */
export function fetchUnreadCount() {
  return request.get('/notifications/unread-count')
}

/**
 * 查询最新通知（顶栏面板）
 *
 * @param {number} [limit=10] 条数
 * @returns {Promise<{records: object[]}>} 通知列表
 */
export function fetchLatestNotifications(limit = 10) {
  return request.get('/notifications/latest', { params: { limit } })
}

/**
 * 分页查询全部通知（通知中心页）
 *
 * @param {number} pageNo 页码
 * @param {number} pageSize 每页条数
 * @returns {Promise<object>} 分页结果
 */
export function fetchNotificationPage(pageNo, pageSize) {
  return request.get('/notifications', { params: { pageNo, pageSize } })
}

/**
 * 全部标记已读
 *
 * @returns {Promise<void>}
 */
export function markAllNotificationsRead() {
  return request.post('/notifications/read-all')
}

/**
 * 单条标记已读（点击条目跳转前调用）
 *
 * @param {number} id 通知 ID
 * @returns {Promise<void>}
 */
export function markNotificationRead(id) {
  return request.post(`/notifications/${id}/read`)
}
