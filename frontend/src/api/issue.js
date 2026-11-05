import request from './request'

/**
 * 问题（Issue）相关接口
 *
 * <p>对应后端 IssueController。
 *
 * <p><b>注意</b>：创建与关联/解绑接口均限定 PM 角色，
 * 后端用 @PreAuthorize 做了拦截，前端也要同步隐藏入口，
 * 避免客服点了按钮才被拒——那是很差的体验。
 */

/**
 * 分页查询问题列表
 *
 * @param {{pageNo?: number, pageSize?: number, module?: string,
 *   sourceType?: string, keyword?: string}} params 查询条件
 * @returns {Promise<{pageNo: number, pageSize: number, total: number,
 *   records: object[]}>} 分页结果
 */
export function fetchIssuePage(params) {
  return request.get('/issues', { params })
}

/**
 * 查询问题详情
 *
 * <p>返回的 VO 同时含 aiDraftContent（AI 原话）与 finalContent（PM 定稿），
 * 页面据此做并排对比。
 *
 * @param {number|string} id 问题 ID
 * @returns {Promise<object>} 问题详情
 */
export function fetchIssueDetail(id) {
  return request.get(`/issues/${id}`)
}

/**
 * 手工创建问题（仅 PM）
 *
 * @param {object} data 问题信息
 * @returns {Promise<{id: number}>} 新建问题 ID
 */
export function createIssue(data) {
  return request.post('/issues', data)
}

/**
 * 批量关联反馈到问题（仅 PM）
 *
 * <p>重复关联在后端是幂等的，不会插入重复数据。
 *
 * @param {number|string} id 问题 ID
 * @param {{feedbackIds: number[], remark?: string}} data 关联参数
 * @returns {Promise<object>} 关联结果
 */
export function linkFeedbacks(id, data) {
  return request.post(`/issues/${id}/feedbacks`, data)
}

/**
 * 解除反馈关联（仅 PM）
 *
 * @param {number|string} id 问题 ID
 * @param {number|string} feedbackId 反馈 ID
 * @returns {Promise<null>}
 */
export function unlinkFeedback(id, feedbackId) {
  return request.delete(`/issues/${id}/feedbacks/${feedbackId}`)
}
