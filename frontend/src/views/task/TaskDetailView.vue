<script setup>
/**
 * 改进任务详情页
 *
 * <p><b>本页承载两条关键设计</b>：
 *
 * <p><b>一、状态机不可跳步</b>
 * 流转按钮由后端返回的 nextStatuses 动态生成——
 * 前端不硬编码"下一个状态是什么"，后端说能走哪就显示哪。
 * 这样状态规则只有一处定义，不会出现前后端规则不一致导致的
 * "按钮能点但提交报错"这种尴尬。
 *
 * <p><b>二、AI 建议与人工决策分离（本页最核心）</b>
 * 优先级区域同时展示：
 * <ul>
 *   <li><b>AI 建议优先级</b>（aiSuggestedPriority）——仅供参数，灰色弱化显示</li>
 *   <li><b>AI 提供的客观依据</b>（aiEvidence）——关联条数、影响客户数等</li>
 *   <li><b>PM 最终设定的优先级</b>（priority）——真正生效的字段</li>
 * </ul>
 * 三者在视觉上明确分层，任何人一眼能看出：
 * AI 只提供统计事实，定级是人做的决定。
 *
 * <p>页面底部是<b>时间线</b>，记录每次流转的操作人与角色快照，
 * 实现完整可追溯。
 */
import { ref, reactive, computed, onMounted, h } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  NCard,
  NSpace,
  NButton,
  NTag,
  NText,
  NDescriptions,
  NDescriptionsItem,
  NEmpty,
  NResult,
  NSpin,
  NModal,
  NForm,
  NFormItem,
  NInput,
  NSelect,
  NDivider,
  NAlert,
  NTimeline,
  NTimelineItem,
  useMessage
} from 'naive-ui'

import {
  fetchTaskDetail,
  transitTaskStatus,
  updateTaskPriority,
  assignTask,
  fetchTaskEvents
} from '@/api/task'
import { fetchUserOptions } from '@/api/user'
import {
  TASK_PRIORITIES,
  moduleLabel,
  taskStatusLabel
} from '@/constants/enums'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const userStore = useUserStore()

/** 任务详情 */
const detail = ref(null)

/** 时间线 */
const events = ref([])

/** 加载中 */
const loading = ref(false)

/** 错误 */
const loadError = ref('')

/**
 * 当前角色是否有资格执行某个目标状态的流转
 *
 * <p><b>与后端 TaskServiceImpl.transit() 的角色分支严格对齐</b>：
 * <ul>
 *   <li>DEV —— 推进开发状态（IN_PROGRESS / PENDING_VERIFY / DONE）</li>
 *   <li>PM  —— 只能关闭任务（WONT_DO），这是产品决策</li>
 *   <li>CS / 其他 —— 无任何流转权限</li>
 * </ul>
 *
 * <p>【修复说明 · 阶段 5 权限 Review】
 * 原先这里写死 `role === 'DEV'`，PM 连"关闭任务"的按钮都看不到；
 * 而状态机设计里 WONT_DO 本就是留给 PM 的出口，
 * 前后端两侧各自堵死，导致该分支完全不可用。
 *
 * <p>【修复说明 · DEV 误见「已关闭」】
 * 上一版对 DEV 无条件返回 true，于是"待开发"阶段 DEV 也能看到
 * 「变更为：已关闭」，点下去必然被后端拦成 403（关闭属产品决策）。
 * 现按出口归属拆开：DEV 只看开发类出口，WONT_DO 只留给 PM / ADMIN。
 *
 * @param {string} status 目标状态代码
 * @returns {boolean} 可执行返回 true
 */
function canTransitTo(status) {
  // ADMIN 与后端一致：不区分角色分支，全部出口可用（运维 / 代操作视角）
  if (userStore.role === 'ADMIN') {
    return true
  }
  if (userStore.role === 'DEV') {
    // 关闭任务是产品决策，DEV 不应看到该出口（后端同样只放行 PM / ADMIN）
    return status !== 'WONT_DO'
  }
  if (userStore.role === 'PM') {
    return status === 'WONT_DO'
  }
  return false
}

/** 当前角色在本任务上可见的流转出口（已按角色过滤） */
const visibleNextStatuses = computed(() =>
  (detail.value?.nextStatuses || []).filter(canTransitTo)
)

/** 是否可推进开发状态（DEV 与 ADMIN；用于终态提示判断） */
const canTransit = computed(
  () => userStore.role === 'DEV' || userStore.role === 'ADMIN'
)

/** 是否完全无权流转（用于决定提示文案） */
const noTransitPermission = computed(
  () => visibleNextStatuses.value.length === 0
)

/** 是否为 PM（可定优先级、指派） */
const canManage = computed(
  () => userStore.role === 'PM' || userStore.role === 'ADMIN'
)

/** 优先级的 Naive UI 配色 */
const priorityType = {
  P0: 'error',
  P1: 'warning',
  P2: 'default'
}

/**
 * 允许的下一步状态
 *
 * <p>直接取后端返回的 nextStatuses。
 * 若后端未返回（比如老数据），回退为空数组——
 * 宁可不显示按钮，也不显示一个会报错的按钮。
 */
const nextStatuses = computed(() => detail.value?.nextStatuses || [])

/** 时间格式化 */
function fmt(value) {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '—'
}

/**
 * 加载详情与时间线
 *
 * <p>时间线拉取失败不阻断整页：详情是主信息，
 * 时间线是辅助信息，不该因为辅助信息失败就让整页不可用。
 */
async function loadDetail() {
  loading.value = true
  loadError.value = ''

  try {
    detail.value = await fetchTaskDetail(route.params.id)
    try {
      events.value = (await fetchTaskEvents(route.params.id)) || []
    } catch {
      events.value = []
    }
  } catch (error) {
    detail.value = null
    events.value = []
    loadError.value =
      error.code === 404
        ? '该任务不存在或已被删除'
        : error.message || '加载任务详情失败'
  } finally {
    loading.value = false
  }
}

// ==================== 状态流转 ====================

const showTransitModal = ref(false)
const transitTarget = ref('')
const transitRemark = ref('')
const transitSubmitting = ref(false)

/**
 * 流转说明是否必填
 *
 * <p>与后端规则一致：退回重新开发（IN_PROGRESS）或标记不做（WONT_DO）必须写清原因。
 * 这两类动作会让已有工作作废或终止，没有理由就无法复盘。
 */
const transitRemarkRequired = computed(
  () => transitTarget.value === 'IN_PROGRESS' || transitTarget.value === 'WONT_DO'
)

/**
 * 打开流转弹窗
 *
 * @param {string} status 目标状态
 */
function openTransitModal(status) {
  transitTarget.value = status
  transitRemark.value = ''
  showTransitModal.value = true
}

/**
 * 提交流转
 */
async function submitTransit() {
  if (transitRemarkRequired.value && !transitRemark.value.trim()) {
    message.warning('该流转必须填写说明')
    return
  }

  transitSubmitting.value = true
  try {
    await transitTaskStatus(route.params.id, {
      status: transitTarget.value,
      remark: transitRemark.value || null
    })
    message.success('状态已更新')
    showTransitModal.value = false
    await loadDetail()
  } catch (error) {
    // 409 = 状态机不允许；403 = 越权；两者提示信息由后端给出
    message.error(error.message || '状态流转失败')
  } finally {
    transitSubmitting.value = false
  }
}

// ==================== 设定优先级 ====================

const showPriorityModal = ref(false)
const prioritySubmitting = ref(false)
const priorityForm = reactive({
  priority: 'P1',
  priorityReason: ''
})
const priorityFormRef = ref(null)

const priorityRules = {
  priority: [{ required: true, message: '请选择优先级', trigger: 'change' }],
  priorityReason: [
    { required: true, message: '请填写定级理由', trigger: 'blur' }
  ]
}

/**
 * 打开优先级弹窗
 *
 * <p>默认值取 AI 建议的优先级（如果有的话），
 * 但只是预填，PM 必须显式确认并填写理由才算定级。
 */
function openPriorityModal() {
  priorityForm.priority =
    detail.value?.aiSuggestedPriority || detail.value?.priority || 'P1'
  priorityForm.priorityReason = ''
  showPriorityModal.value = true
}

/**
 * 提交优先级
 */
async function submitPriority() {
  try {
    await priorityFormRef.value?.validate()
  } catch {
    return
  }

  prioritySubmitting.value = true
  try {
    await updateTaskPriority(route.params.id, { ...priorityForm })
    message.success('优先级已更新')
    showPriorityModal.value = false
    await loadDetail()
  } catch (error) {
    message.error(error.message || '优先级设定失败')
  } finally {
    prioritySubmitting.value = false
  }
}

// ==================== 指派负责人 ====================

const showAssignModal = ref(false)
const assignSubmitting = ref(false)
const assigneeId = ref(null)
const devOptions = ref([])

/**
 * 打开指派弹窗并加载开发列表
 */
async function openAssignModal() {
  assigneeId.value = detail.value?.assigneeId || null
  showAssignModal.value = true

  try {
    const list = await fetchUserOptions('DEV')
    devOptions.value = (list || []).map((item) => ({
      label: `${item.displayName}（${item.dept || '研发部'}）`,
      value: item.id
    }))
  } catch {
    devOptions.value = []
  }
}

/**
 * 提交指派
 */
async function submitAssign() {
  if (!assigneeId.value) {
    message.warning('请选择负责人')
    return
  }

  assignSubmitting.value = true
  try {
    await assignTask(route.params.id, { assigneeId: assigneeId.value })
    message.success('已指派负责人')
    showAssignModal.value = false
    await loadDetail()
  } catch (error) {
    message.error(error.message || '指派失败')
  } finally {
    assignSubmitting.value = false
  }
}

/** 状态标签的 Naive UI 配色 */
function statusType(status) {
  const map = {
    TODO: 'default',
    IN_PROGRESS: 'info',
    PENDING_VERIFY: 'warning',
    DONE: 'success',
    WONT_DO: 'default'
  }
  return map[status] || 'default'
}

onMounted(loadDetail)
</script>

<template>
  <div class="page-container">
    <!-- 加载态 -->
    <n-card v-if="loading" class="app-card">
      <n-spin size="large" style="display: block; margin: 60px auto" />
    </n-card>

    <!-- 失败态 -->
    <n-card v-else-if="loadError" class="app-card">
      <n-result status="error" title="加载失败" :description="loadError">
        <template #footer>
          <n-button @click="router.push({ name: 'task-board' })">
            返回看板
          </n-button>
        </template>
      </n-result>
    </n-card>

    <template v-else-if="detail">
      <!-- ==================== 头部 ==================== -->
      <n-card class="app-card" style="margin-bottom: 16px">
        <template #header>
          <n-space align="center" :size="10">
            <n-text strong style="font-size: 16px">{{ detail.title }}</n-text>
            <n-text class="text-mono" depth="3" style="font-size: 12px">
              {{ detail.taskNo }}
            </n-text>
            <n-tag size="small" :bordered="false" :type="statusType(detail.status)">
              {{ detail.statusLabel }}
            </n-tag>
            <n-tag
              size="small"
              :bordered="false"
              :type="priorityType[detail.priority] || 'default'"
            >
              {{ detail.priorityLabel || detail.priority }}
            </n-tag>
          </n-space>
        </template>

        <n-descriptions bordered :column="3" label-placement="left">
          <n-descriptions-item label="来源问题">
            <n-text
              v-if="detail.issueId"
              class="text-mono"
              style="cursor: pointer; color: var(--c-primary)"
              @click="
                router.push({ name: 'issue-detail', params: { id: detail.issueId } })
              "
            >
              {{ detail.issueNo }}
            </n-text>
            <span v-else>—</span>
          </n-descriptions-item>
          <n-descriptions-item label="负责人">
            {{ detail.assigneeName || '未指派' }}
          </n-descriptions-item>
          <n-descriptions-item label="创建人">
            {{ detail.createdByName || '—' }}
          </n-descriptions-item>
          <n-descriptions-item label="创建时间">
            {{ fmt(detail.createTime) }}
          </n-descriptions-item>
          <n-descriptions-item label="更新时间">
            {{ fmt(detail.updateTime) }}
          </n-descriptions-item>
        </n-descriptions>
      </n-card>

      <!-- ==================== 优先级：AI 建议 vs PM 决定 ==================== -->
      <n-card class="app-card" style="margin-bottom: 16px">
        <template #header>
          <n-space align="center" :size="8">
            <n-text strong style="font-size: 15px">优先级</n-text>
            <n-text depth="3" style="font-size: 12px">
              AI 只提供统计依据，最终定级由产品经理决定
            </n-text>
          </n-space>
        </template>

        <div class="priority-grid">
          <!-- 左：AI 的建议（弱化显示，强调仅供参考） -->
          <div class="priority-cell ai-cell">
            <div class="cell-head">
              <n-text depth="3" style="font-size: 12px">AI 建议（仅供参考）</n-text>
              <n-tag size="tiny" :bordered="false">不生效</n-tag>
            </div>
            <div class="cell-value">
              <n-text v-if="detail.aiSuggestedPriority" strong style="font-size: 20px">
                {{ detail.aiSuggestedPriority }}
              </n-text>
              <n-text v-else depth="3">无建议</n-text>
            </div>
            <div v-if="detail.aiEvidence" class="cell-evidence">
              {{ detail.aiEvidence }}
            </div>
          </div>

          <!-- 右：PM 的决定（强调显示，这是生效值） -->
          <div class="priority-cell final-cell">
            <div class="cell-head">
              <n-text depth="3" style="font-size: 12px">产品经理定级（生效）</n-text>
            </div>
            <div class="cell-value">
              <n-text strong style="font-size: 20px">
                {{ detail.priority }}
              </n-text>
            </div>
            <div class="cell-evidence">{{ detail.priorityReason || '（未填写定级理由）' }}</div>
          </div>
        </div>

        <template #footer>
          <n-space justify="end">
            <n-button
              v-if="canManage"
              size="small"
              secondary
              @click="openPriorityModal"
            >
              设定优先级
            </n-button>
            <n-button
              v-if="canManage"
              size="small"
              secondary
              @click="openAssignModal"
            >
              指派负责人
            </n-button>
          </n-space>
        </template>
      </n-card>

      <!-- ==================== 需求说明与验收条件 ==================== -->
      <n-card class="app-card" style="margin-bottom: 16px">
        <template #header>
          <n-text strong style="font-size: 15px">需求说明</n-text>
        </template>

        <div>
          <n-text depth="3" style="font-size: 12px">需求内容</n-text>
          <div class="content-box">
            {{ detail.requirement || '（未填写）' }}
          </div>
        </div>

        <n-divider />

        <div>
          <n-text depth="3" style="font-size: 12px">验收条件</n-text>
          <div class="content-box">
            {{ detail.acceptanceCriteria || '（未填写）' }}
          </div>
        </div>
      </n-card>

      <!-- ==================== 状态流转操作区 ==================== -->
      <n-card class="app-card" style="margin-bottom: 16px">
        <template #header>
          <n-text strong style="font-size: 15px">状态流转</n-text>
        </template>

        <!-- 有可用流转出口：按角色过滤后的按钮 -->
        <n-space v-if="visibleNextStatuses.length > 0">
          <n-button
            v-for="status in visibleNextStatuses"
            :key="status"
            secondary
            :type="status === 'WONT_DO' ? 'default' : 'primary'"
            @click="openTransitModal(status)"
          >
            变更为：{{ taskStatusLabel(status) }}
          </n-button>
        </n-space>

        <!-- DEV 但已到终态 -->
        <n-alert v-else-if="canTransit" type="default" :bordered="false">
          该任务已处于终态（{{ detail.statusLabel }}），无需继续流转。
        </n-alert>

        <!-- PM 但本状态不能关闭（例如待验证只能验证通过/打回） -->
        <n-alert v-else-if="userStore.role === 'PM'" type="default" :bordered="false">
          当前状态（{{ detail.statusLabel }}）下没有需要你执行的操作。关闭（不做）只能在「待开发」或「开发中」阶段执行。
        </n-alert>

        <!-- 非 DEV 非 PM：说明权限归属 -->
        <n-alert v-else type="default" :bordered="false">
          状态流转由开发工程师执行，关闭任务由产品经理决定。当前你的角色是「{{
            userStore.roleLabel
          }}」，只能查看。
        </n-alert>
      </n-card>

      <!-- ==================== 时间线 ==================== -->
      <n-card class="app-card">
        <template #header>
          <n-text strong style="font-size: 15px">流转记录</n-text>
        </template>

        <n-empty v-if="events.length === 0" description="暂无流转记录" size="small" />

        <n-timeline v-else>
          <n-timeline-item
            v-for="event in events"
            :key="event.id"
            :type="event.toStatus === 'DONE' ? 'success' : 'default'"
            :title="`${event.fromStatusLabel || '创建'} → ${event.toStatusLabel}`"
            :content="event.remark || ''"
            :time="fmt(event.createTime)"
          >
            <template #footer>
              <n-text depth="3" style="font-size: 11px">
                {{ event.operatorName }}（{{ event.operatorRole }}）
              </n-text>
            </template>
          </n-timeline-item>
        </n-timeline>
      </n-card>
    </template>

    <!-- ==================== 状态流转弹窗 ==================== -->
    <n-modal
      v-model:show="showTransitModal"
      preset="card"
      :title="`变更为：${taskStatusLabel(transitTarget)}`"
      style="width: 520px"
    >
      <n-form label-placement="top">
        <n-form-item
          label="流转说明"
          :required="transitRemarkRequired"
        >
          <n-input
            v-model:value="transitRemark"
            type="textarea"
            :rows="4"
            :placeholder="
              transitRemarkRequired
                ? '请说明原因（必填）'
                : '补充说明，可留空'
            "
          />
        </n-form-item>
      </n-form>

      <template #footer>
        <n-space justify="end">
          <n-button quaternary @click="showTransitModal = false">取消</n-button>
          <n-button
            type="primary"
            :loading="transitSubmitting"
            @click="submitTransit"
          >
            确认流转
          </n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- ==================== 设定优先级弹窗 ==================== -->
    <n-modal
      v-model:show="showPriorityModal"
      preset="card"
      title="设定优先级"
      style="width: 560px"
    >
      <n-alert type="warning" :bordered="false" style="margin-bottom: 16px">
        优先级是人工决策项。AI 只能提供关联条数、影响客户数等<b>客观统计</b>，
        不能代替你定级。
      </n-alert>

      <n-form
        ref="priorityFormRef"
        :model="priorityForm"
        :rules="priorityRules"
        label-placement="top"
      >
        <n-form-item label="优先级" path="priority">
          <n-select
            v-model:value="priorityForm.priority"
            :options="TASK_PRIORITIES"
          />
        </n-form-item>

        <n-form-item label="定级理由" path="priorityReason">
          <n-input
            v-model:value="priorityForm.priorityReason"
            type="textarea"
            :rows="4"
            placeholder="请填写客观依据，如：关联 7 条反馈，涉及 4 家客户，阻塞报销提交主流程"
          />
        </n-form-item>
      </n-form>

      <template #footer>
        <n-space justify="end">
          <n-button quaternary @click="showPriorityModal = false">取消</n-button>
          <n-button
            type="primary"
            :loading="prioritySubmitting"
            @click="submitPriority"
          >
            确认定级
          </n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- ==================== 指派负责人弹窗 ==================== -->
    <n-modal
      v-model:show="showAssignModal"
      preset="card"
      title="指派负责人"
      style="width: 480px"
    >
      <n-form label-placement="top">
        <n-form-item label="开发工程师">
          <n-select
            v-model:value="assigneeId"
            :options="devOptions"
            placeholder="选择负责人"
          />
        </n-form-item>
      </n-form>

      <template #footer>
        <n-space justify="end">
          <n-button quaternary @click="showAssignModal = false">取消</n-button>
          <n-button type="primary" :loading="assignSubmitting" @click="submitAssign">
            确认指派
          </n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<style scoped>
/* 优先级左右对照 */
.priority-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

@media (max-width: 900px) {
  .priority-grid {
    grid-template-columns: 1fr;
  }
}

.priority-cell {
  padding: 16px;
  border: 1px solid var(--c-border-soft);
  border-radius: 8px;
}

/* AI 建议：灰色底 + 虚线边框，视觉上"退后一步" */
.ai-cell {
  background-color: rgba(244, 246, 255, 0.82);
  border-style: dashed;
}

/* PM 定级：主色边框，视觉上"这是生效值" */
.final-cell {
  background-color: rgba(245, 247, 255, 0.72);
  border-color: var(--c-primary);
}

.cell-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.cell-value {
  margin-bottom: 10px;
}

.cell-evidence {
  color: var(--c-text-2);
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
}

.content-box {
  margin-top: 6px;
  padding: 14px 16px;
  border: 1px solid var(--c-border-soft);
  border-radius: 6px;
  background-color: rgba(245, 247, 255, 0.72);
  color: var(--c-text-1);
  font-size: 13px;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
  min-height: 60px;
}
</style>
