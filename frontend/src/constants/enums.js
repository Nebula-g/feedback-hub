/**
 * 前端枚举常量
 *
 * <p><b>为什么要单独维护一份，而不是从后端动态取</b>：
 * 这些枚举都是稳定的业务字典，变更频率极低。
 * 放在前端常量里，下拉框可以直接 v-for 渲染，
 * 省去一次接口往返，也让页面在后端未启动时仍能渲染骨架。
 *
 * <p><b>重要约束</b>：这里的 code 必须与后端枚举严格一致，
 * 否则筛选参数传过去后端匹配不上，会静默返回空列表。
 * 后端对应文件：
 *   enums/FeedbackModule.java、FeedbackSource.java、FeedbackStatus.java
 *   enums/TaskStatus.java、TaskPriority.java、RoleEnum.java
 */

/** 功能模块（与被反馈的报销软件功能对应） */
export const FEEDBACK_MODULES = [
  { value: 'EXPENSE_FORM', label: '报销单填写' },
  { value: 'INVOICE_UPLOAD', label: '发票上传' },
  { value: 'APPROVAL_PROGRESS', label: '审批进度' },
  { value: 'RETURN_MODIFY', label: '退回修改' },
  { value: 'OTHER', label: '其他' }
]

/** 反馈来源渠道 */
export const FEEDBACK_SOURCES = [
  { value: 'TICKET', label: '客服工单' },
  { value: 'SURVEY', label: '使用调查' },
  { value: 'CALL', label: '客户沟通' }
]

/** 反馈归类状态 */
export const FEEDBACK_STATUSES = [
  { value: 'NEW', label: '未归类' },
  { value: 'LINKED', label: '已关联' },
  { value: 'CLOSED', label: '已关闭' },
  { value: 'IGNORED', label: '无效' }
]

/** 改进任务状态 */
export const TASK_STATUSES = [
  { value: 'TODO', label: '待开发' },
  { value: 'IN_PROGRESS', label: '开发中' },
  { value: 'PENDING_VERIFY', label: '待验证' },
  { value: 'DONE', label: '已完成' },
  { value: 'WONT_DO', label: '已关闭' }
]

/** 任务优先级 */
export const TASK_PRIORITIES = [
  { value: 'P0', label: 'P0 - 阻塞主流程' },
  { value: 'P1', label: 'P1 - 影响体验' },
  { value: 'P2', label: 'P2 - 体验优化' }
]

/**
 * 把 code 转成中文名
 *
 * <p>所有列表接口的 VO 里都已经带有 xxxLabel 字段，
 * 这个函数主要用于代码内部拼接文案（如"变更为：已关联"），
 * 以及处理极端情况下 label 缺失的兜底。
 *
 * @param {Array<{value:string,label:string}>} options 枚举选项列表
 * @param {string} code 枚举代码
 * @returns {string} 中文名，未匹配时返回原 code
 */
export function labelOf(options, code) {
  const hit = options.find((item) => item.value === code)
  return hit ? hit.label : code || '-'
}

/** 功能模块中文名 */
export const moduleLabel = (code) => labelOf(FEEDBACK_MODULES, code)

/** 来源渠道中文名 */
export const sourceLabel = (code) => labelOf(FEEDBACK_SOURCES, code)

/** 反馈状态中文名 */
export const feedbackStatusLabel = (code) => labelOf(FEEDBACK_STATUSES, code)

/** 任务状态中文名 */
export const taskStatusLabel = (code) => labelOf(TASK_STATUSES, code)

/** 优先级中文名 */
export const priorityLabel = (code) => labelOf(TASK_PRIORITIES, code)
