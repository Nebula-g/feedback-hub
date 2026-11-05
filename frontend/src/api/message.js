import request from './request'

/**
 * 业务评论接口
 *
 * <p>对应后端 MessageController，
 * 评论围绕业务对象（反馈 / 任务）组织。
 */

/**
 * 查询某业务对象的评论时间线（正序）
 *
 * @param {string} bizType 业务对象类型：FEEDBACK / TASK
 * @param {number} bizId 业务对象 ID
 * @returns {Promise<object[]>} 评论列表
 */
export function fetchMessages(bizType, bizId) {
  return request.get('/messages', { params: { bizType, bizId } })
}

/**
 * 发表评论
 *
 * @param {string} bizType 业务对象类型
 * @param {number} bizId 业务对象 ID
 * @param {string} content 评论内容
 * @returns {Promise<number>} 新评论 ID
 */
export function sendMessage(bizType, bizId, content) {
  return request.post('/messages', { bizType, bizId, content })
}

/**
 * 未读评论按业务对象聚合（顶栏消息面板）
 *
 * @returns {Promise<object[]>} 聚合列表
 */
export function fetchUnreadGroups() {
  return request.get('/messages/unread-groups')
}

/**
 * 标记某业务对象的评论已读（打开详情页时调用）
 *
 * @param {string} bizType 业务对象类型
 * @param {number} bizId 业务对象 ID
 * @returns {Promise<void>}
 */
export function markMessagesRead(bizType, bizId) {
  return request.post('/messages/read', null, { params: { bizType, bizId } })
}
