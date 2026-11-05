import request from './request'

/**
 * 改进任务相关接口
 *
 * <p>对应后端 TaskController。
 *
 * <p><b>权限分工是这里最值得注意的设计</b>：
 * <ul>
 *   <li>创建任务、设定优先级、指派负责人 —— 只有 PM</li>
 *   <li>推进任务状态（开始开发 / 提交验证 / 完成 / 打回）—— 只有 DEV</li>
 * </ul>
 * 这条边界直接对应课程里「AI 不能决定优先级」那条约束：
 * 优先级的人为决策权被明确锁在 PM 手里。
 */

/**
 * 分页查询任务列表
 *
 * @param {{pageNo?: number, pageSize?: number, status?: string,
 *   priority?: string, module?: string, assigneeId?: string,
 *   keyword?: string}} params 查询条件
 * @returns {Promise<{pageNo: number, pageSize: number, total: number,
 *   records: object[]}>} 分页结果
 */
export function fetchTaskPage(params) {
  return request.get('/tasks', { params })
}

/**
 * 查询任务详情
 *
 * <p>含 aiSuggestedPriority（AI 建议，仅供参考）与
 * aiEvidence（AI 提供的客观统计依据）。
 *
 * @param {number|string} id 任务 ID
 * @returns {Promise<object>} 任务详情
 */
export function fetchTaskDetail(id) {
  return request.get(`/tasks/${id}`)
}

/**
 * 创建任务（仅 PM）
 *
 * @param {object} data 任务信息，priority 与 priorityReason 必填
 * @returns {Promise<{id: number}>} 新建任务 ID
 */
export function createTask(data) {
  return request.post('/tasks', data)
}

/**
 * 推进任务状态（仅 DEV）
 *
 * <p>非法跳转（如 TODO 直接跳 DONE）后端会返回 409。
 *
 * @param {number|string} id 任务 ID
 * @param {{status: string, remark?: string}} data 目标状态与说明
 * @returns {Promise<null>}
 */
export function transitTaskStatus(id, data) {
  return request.put(`/tasks/${id}/status`, data)
}

/**
 * 设定任务优先级（仅 PM）
 *
 * @param {number|string} id 任务 ID
 * @param {{priority: string, priorityReason: string}} data 优先级与理由
 * @returns {Promise<null>}
 */
export function updateTaskPriority(id, data) {
  return request.put(`/tasks/${id}/priority`, data)
}

/**
 * 指派负责人（仅 PM）
 *
 * @param {number|string} id 任务 ID
 * @param {{assigneeId: number}} data 负责人 ID
 * @returns {Promise<null>}
 */
export function assignTask(id, data) {
  return request.put(`/tasks/${id}/assignee`, data)
}

/**
 * 查询任务时间线
 *
 * @param {number|string} id 任务 ID
 * @returns {Promise<object[]>} 事件列表
 */
export function fetchTaskEvents(id) {
  return request.get(`/tasks/${id}/events`)
}
