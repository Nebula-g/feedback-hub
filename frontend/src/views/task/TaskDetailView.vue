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
 * 验收条件拆成逐条清单
 *
 * <p>后端把验收条件存成一段多行文本（PM 起草时就是一行一条）。
 * 这里按行拆开，用来渲染成可勾选的清单——
 * 一段密排的文字没人会逐条核对，拆成行才有"检查表"的作用。
 */
const acceptanceItems = computed(() => {
  const raw = detail.value?.acceptanceCriteria || ''
  return raw
    .split(/\r?\n/)
    .map((line) => line.replace(/^\s*[-*\d.、)]+\s*/, '').trim())
    .filter(Boolean)
})

/** 任务是否已完成（完成即视为全部验收条件通过） */
const isFinished = computed(() => detail.value?.status === 'DONE')

/**
 * 流转弹窗里的自查勾选状态
 *
 * <p>只在"提交待验证 / 完成"这一步要求勾选：这两步等于工程师声明
 * "我逐条验过了"，没有这份自查记录，PM 复核时只能重新过一遍。
 */
const transitChecks = ref([])

/** 该流转是否需要逐条确认验收条件 */
const transitChecklistRequired = computed(() => {
  if (!acceptanceItems.value.length) return false
  return transitTarget.value === 'PENDING_VERIFY' || transitTarget.value === 'DONE'
})

/** 是否已全部勾选 */
const transitChecksAllDone = computed(
  () => transitChecks.value.length > 0 && transitChecks.value.every(Boolean)
)

/** 已勾选条数 */
const transitChecksDone = computed(
  () => transitChecks.value.filter(Boolean).length
)

/**
 * 打开流转弹窗
 *
 * @param {string} status 目标状态
 */
function openTransitModal(status) {
  transitTarget.value = status
  transitRemark.value = ''
  transitChecks.value = acceptanceItems.value.map(() => false)
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

  // 提交待验证/完成：必须逐条勾掉验收条件，否则"验证过了"是空口无凭
  if (transitChecklistRequired.value && !transitChecksAllDone.value) {
    message.warning('请先逐条确认验收条件')
    return
  }

  // 把自查结果并进流转说明，留档到时间线（后端 remark 是唯一可写字段，不改表结构）
  let remark = transitRemark.value.trim()
  if (transitChecklistRequired.value) {
    const summary = `验收条件自查 ${transitChecksDone.value}/${acceptanceItems.value.length} 条通过`
    remark = remark ? `${remark}｜${summary}` : summary
  }

  transitSubmitting.value = true
  try {
    await transitTaskStatus(route.params.id, {
      status: transitTarget.value,
      remark: remark || null
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
          <div class="acceptance-head">
            <n-text depth="3" style="font-size: 12px">验收条件</n-text>
            <span v-if="acceptanceItems.length" class="acceptance-meta">
              {{ isFinished ? '全部满足' : `共 ${acceptanceItems.length} 条` }}
            </span>
          </div>

          <!-- 逐条清单：密排的一段文字没人会核对，拆成行才能逐项打勾 -->
          <ul v-if="acceptanceItems.length" class="acceptance-list">
            <li
              v-for="(item, index) in acceptanceItems"
              :key="index"
              class="acceptance-item"
              :class="{ 'is-done': isFinished }"
            >
              <span class="acceptance-box">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"
                  stroke-linecap="round" stroke-linejoin="round">
                  <path d="M5 12.5l4.5 4.5L19 7" />
                </svg>
              </span>
              <span class="acceptance-text">{{ item }}</span>
              <em v-if="isFinished" class="acceptance-flag">已验证</em>
            </li>
          </ul>

          <div v-else class="content-box">（未填写）</div>
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
        <!-- 提交待验证 / 完成：先把验收条件逐条勾掉，再写说明 -->
        <div v-if="transitChecklistRequired" class="transit-checks">
          <div class="transit-checks__head">
            <span class="transit-checks__title">验收条件自查</span>
            <span class="transit-checks__count">
              {{ transitChecksDone }} / {{ acceptanceItems.length }}
            </span>
          </div>
          <div class="transit-checks__track">
            <span
              class="transit-checks__fill"
              :style="{ width: `${(transitChecksDone / acceptanceItems.length) * 100}%` }"
            ></span>
          </div>

          <label
            v-for="(item, index) in acceptanceItems"
            :key="index"
            class="transit-check"
            :class="{ 'is-done': transitChecks[index] }"
          >
            <input v-model="transitChecks[index]" type="checkbox" class="transit-check__input" />
            <span class="transit-check__box">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"
                stroke-linecap="round" stroke-linejoin="round">
                <path d="M5 12.5l4.5 4.5L19 7" />
              </svg>
            </span>
            <span class="transit-check__text">{{ item }}</span>
          </label>
        </div>

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

/* ==================== 验收条件清单 ==================== */
.acceptance-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.acceptance-meta {
  font-size: 11.5px;
  color: var(--c-text-3);
  font-variant-numeric: tabular-nums;
}

.acceptance-list {
  margin: 6px 0 0;
  padding: 6px;
  list-style: none;
  border: 1px solid var(--c-border-soft);
  border-radius: 12px;
  background-color: rgba(245, 247, 255, 0.72);
}

.acceptance-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 9px;
  transition: background-color 0.2s ease;
}

.acceptance-item:hover {
  background-color: rgba(255, 255, 255, 0.7);
}

.acceptance-box {
  display: grid;
  place-items: center;
  flex: none;
  width: 18px;
  height: 18px;
  margin-top: 1px;
  color: transparent;
  background: rgba(255, 255, 255, 0.9);
  border: 1.5px solid rgba(122, 136, 190, 0.32);
  border-radius: 6px;
}

.acceptance-box svg {
  width: 11px;
  height: 11px;
}

/* 已完成的任务：整条清单变绿并划线，表示"这些都验过了" */
.acceptance-item.is-done .acceptance-box {
  color: #fff;
  background: linear-gradient(135deg, var(--c-primary), #8494f9);
  border-color: transparent;
}

.acceptance-item.is-done .acceptance-text {
  color: var(--c-text-3);
  text-decoration: line-through;
  text-decoration-color: rgba(139, 146, 167, 0.6);
}

.acceptance-text {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--c-text-1);
}

.acceptance-flag {
  flex: none;
  font-size: 10.5px;
  font-style: normal;
  color: #2b9563;
  background: rgba(51, 173, 116, 0.12);
  padding: 2px 8px;
  border-radius: 999px;
}

/* ==================== 流转弹窗：验收条件自查 ==================== */
.transit-checks {
  margin-bottom: 16px;
  padding: 12px 14px;
  background: rgba(245, 247, 255, 0.8);
  border: 1px solid var(--c-border-soft);
  border-radius: 12px;
}

.transit-checks__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 8px;
}

.transit-checks__title {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--c-text-1);
}

.transit-checks__count {
  font-size: 11.5px;
  color: var(--c-text-3);
  font-variant-numeric: tabular-nums;
}

.transit-checks__track {
  height: 5px;
  margin-bottom: 8px;
  overflow: hidden;
  background: rgba(122, 136, 190, 0.16);
  border-radius: 999px;
}

.transit-checks__fill {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--c-primary), var(--c-accent));
  transition: width 0.4s cubic-bezier(0.22, 0.61, 0.36, 1);
}

.transit-check {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 7px 8px;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.18s ease;
}

.transit-check:hover {
  background-color: rgba(255, 255, 255, 0.85);
}

/* 原生 checkbox 视觉隐藏但保留键盘焦点 */
.transit-check__input {
  position: absolute;
  width: 1px;
  height: 1px;
  margin: -1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
  border: 0;
}

.transit-check__box {
  display: grid;
  place-items: center;
  flex: none;
  width: 18px;
  height: 18px;
  margin-top: 1px;
  color: transparent;
  background: #fff;
  border: 1.5px solid rgba(122, 136, 190, 0.32);
  border-radius: 6px;
  transition: background-color 0.2s ease, border-color 0.2s ease, color 0.2s ease;
}

.transit-check__box svg {
  width: 11px;
  height: 11px;
}

.transit-check.is-done .transit-check__box {
  color: #fff;
  background: linear-gradient(135deg, var(--c-primary), #8494f9);
  border-color: transparent;
}

.transit-check__input:focus-visible + .transit-check__box {
  box-shadow: 0 0 0 3px rgba(108, 123, 245, 0.28);
}

.transit-check__text {
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--c-text-2);
}

.transit-check.is-done .transit-check__text {
  color: var(--c-text-3);
  text-decoration: line-through;
  text-decoration-color: rgba(139, 146, 167, 0.6);
}
</style>
