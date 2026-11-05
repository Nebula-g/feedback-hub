import request from './request'

/**
 * 反馈相关接口
 *
 * <p>对应后端 FeedbackController。
 *
 * <p><b>只读原则</b>：本模块<b>不提供任何修改反馈正文的方法</b>。
 * 这是刻意的——AI 和人都不允许改写原始反馈，
 * 从接口层就断掉这条路，比靠"记得不要调用"可靠得多。
 */

/**
 * 分页查询反馈列表
 *
 * @param {{pageNo?: number, pageSize?: number, module?: string,
 *   status?: string, source?: string, keyword?: string}} params 查询条件
 * @returns {Promise<{pageNo: number, pageSize: number, total: number,
 *   records: object[]}>} 分页结果
 */
export function fetchFeedbackPage(params) {
  return request.get('/feedbacks', { params })
}

/**
 * 查询反馈详情
 *
 * @param {number|string} id 反馈 ID
 * @returns {Promise<object>} 反馈详情
 */
export function fetchFeedbackDetail(id) {
  return request.get(`/feedbacks/${id}`)
}

/**
 * 录入单条反馈（仅客服）
 *
 * @param {object} data 反馈内容
 * @returns {Promise<{id: number}>} 新建反馈 ID
 */
export function createFeedback(data) {
  return request.post('/feedbacks', data)
}

/**
 * 批量导入反馈（仅客服）
 *
 * @param {{items: object[]}} data 反馈列表，单次最多 200 条
 * @returns {Promise<{ids: number[], count: number}>} 导入结果
 */
export function batchImportFeedback(data) {
  return request.post('/feedbacks/batch', data)
}

/**
 * 变更反馈归类状态（仅产品经理）
 *
 * @param {number|string} id 反馈 ID
 * @param {{status: string, remark?: string}} data 目标状态与说明
 * @returns {Promise<null>}
 */
export function updateFeedbackStatus(id, data) {
  return request.put(`/feedbacks/${id}/status`, data)
}
