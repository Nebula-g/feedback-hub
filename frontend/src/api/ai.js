import request from './request'

/**
 * AI 能力相关接口
 *
 * <p>对应后端 AiController（阶段 4 实现）。
 *
 * <p><b>为什么把 AI 接口单独放一个模块</b>：
 * AI 是本项目最可能失败、最需要降级的一环。
 * 单独成模块后，调用方可以统一在这里处理超时、重试与降级提示，
 * 而不必在每个页面重复写一遍。
 *
 * <p><b>契约要点（与后端一致）</b>：
 * <ul>
 *   <li>生成候选卡片时，AI 只输出「现象 / 诉求 / 归类理由 / 置信度」</li>
 *   <li>AI <b>不输出</b>优先级——这是课程明确的红线</li>
 *   <li>每次调用都会写入 ai_generation_log，可回查</li>
 * </ul>
 */

/**
 * 生成 AI 候选问题卡片
 *
 * <p>按模块对未归类的反馈做聚类。
 * 模块是硬分组依据，跨模块不会误合并。
 *
 * @param {{module: string}} data 生成参数
 * @returns {Promise<{batchNo: string, count: number,
 *   candidates: object[]}>} 生成结果
 */
export function generateCandidates(data) {
  return request.post('/ai/candidates/generate', data)
}

/**
 * 分页查询 AI 候选卡片
 *
 * @param {{module?: string, status?: string,
 *   pageNo?: number, pageSize?: number}} params 查询条件
 * @returns {Promise<object>} 分页结果
 */
export function fetchCandidatePage(params) {
  return request.get('/ai/candidates', { params })
}

/**
 * PM 确认候选卡片，生成正式问题
 *
 * <p>若 PM 修改过标题或描述，后端会把状态记为 MODIFIED，
 * 并把 AI 原稿原样存入 issue.ai_draft_content，
 * 实现「AI 建议」与「人工定稿」双份留档。
 *
 * @param {number|string} id 候选卡片 ID
 * @param {{title?: string, description?: string,
 *   finalContent?: string, acceptanceCriteria?: string}} data 确认内容
 * @returns {Promise<{issueId: number}>} 生成的正式问题 ID
 */
export function confirmCandidate(id, data) {
  return request.post(`/ai/candidates/${id}/confirm`, data)
}

/**
 * PM 驳回候选卡片
 *
 * @param {number|string} id 候选卡片 ID
 * @param {{reason: string}} data 驳回原因，必填
 * @returns {Promise<null>}
 */
export function rejectCandidate(id, data) {
  return request.post(`/ai/candidates/${id}/reject`, data)
}

/**
 * PM 手动拆分候选卡片
 *
 * <p>一条反馈里包含多个不同问题、被 AI 归成一张卡片时，
 * 把它拆成多张新草稿卡片分别确认。
 * <b>同一条反馈允许同时挂在多张新卡片上</b>——
 * 混卡的根源往往就是一条反馈里写了多件事，按反馈硬拆拆不开。
 *
 * @param {number|string} id 候选卡片 ID
 * @param {{parts: Array<{title: string, phenomenon?: string,
 *   userDemand?: string, feedbackIds?: number[]}>}} data
 *   拆分内容，至少 2 个部分；feedbackIds 为空则继承原卡片全部关联反馈
 * @returns {Promise<number[]>} 新生成的候选卡片 ID 列表
 */
export function splitCandidate(id, data) {
  return request.post(`/ai/candidates/${id}/split`, data)
}

/**
 * 获取拆分弹窗的 AI 预填建议（只读）
 *
 * <p>对这张卡片的关联反馈重新聚类一次，若识别出多个独立问题，
 * 返回多份可编辑的预填内容作为拆分参考。
 * AI 不可用时返回 { available: false }，不会抛错——
 * 拆分是人工决策，AI 缺席不应阻断操作。
 *
 * @param {number|string} id 候选卡片 ID
 * @returns {Promise<{available: boolean, splitSuggested: boolean,
 *   message: string, provider?: string, fallback?: boolean,
 *   parts: Array<{title: string, phenomenon?: string,
 *     userDemand?: string, feedbackIds?: number[]}>}>}
 */
export function fetchSplitSuggestion(id) {
  return request.get(`/ai/candidates/${id}/split-suggestion`)
}

/**
 * PM 合并候选卡片到已有问题
 *
 * <p>卡片描述的问题已经存在时使用：关联反馈挂到目标问题上，
 * 原卡片转「已合并」留档，不生成新问题。
 *
 * @param {number|string} id 候选卡片 ID
 * @param {{targetIssueId: number, remark?: string}} data
 *   目标问题 ID 与合并说明
 * @returns {Promise<{issueId: number}>} 目标问题 ID
 */
export function mergeCandidate(id, data) {
  return request.post(`/ai/candidates/${id}/merge`, data)
}

/**
 * 获取确认弹窗的 AI 起草建议（只读，AI 作用点 C 的候选卡片入口）
 *
 * <p>对候选卡片提前起草一次，返回验收条件等草稿内容用于预填。
 * AI 不可用时返回 { available: false }，不会抛错——
 * 预填只是参考，不应挡住 PM 的确认操作。
 *
 * @param {number|string} id 候选卡片 ID
 * @returns {Promise<{available: boolean, message: string,
 *   draftContent: string, acceptanceCriteria: string,
 *   provider?: string, fallback?: boolean}>} 起草建议
 */
export function fetchDraftSuggestion(id) {
  return request.get(`/ai/candidates/${id}/draft-suggestion`)
}

/**
 * 分页查询 AI 调用记录
 *
 * @param {{bizType?: string, status?: string,
 *   pageNo?: number, pageSize?: number}} params 查询条件
 * @returns {Promise<object>} 分页结果
 */
export function fetchAiLogPage(params) {
  return request.get('/ai/logs', { params })
}

/**
 * 查询单条 AI 调用详情
 *
 * @param {number|string} id 日志 ID
 * @returns {Promise<object>} 调用详情
 */
export function fetchAiLogDetail(id) {
  return request.get(`/ai/logs/${id}`)
}

/**
 * 为已确认问题起草需求说明与验收条件（AI 作用点 C）
 *
 * <p>返回的只是草稿文本，<b>不会</b>写入正式需求字段；
 * 是否采纳、如何修改由 PM 决定。
 *
 * @param {number|string} issueId 问题 ID
 * @returns {Promise<{draftContent: string,
 *   acceptanceCriteria: string}>} 草稿内容
 */
export function draftRequirement(issueId) {
  return request.post(`/ai/issues/${issueId}/draft`)
}
