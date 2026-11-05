<script setup>
/**
 * AI 候选问题工作台
 *
 * <p><b>本页是挑战赛道的核心页面</b>，把「AI 做归纳、人做判断」这条主线可视化。
 *
 * <p><b>页面要解决的业务问题</b>：
 * 客服一周收到 60 条反馈，人工归并耗时 3–4 小时，且口径不一。
 * 这里让 AI 先做一轮聚类，PM 只需在候选卡片上做「采纳 / 修改 / 驳回」的判断题，
 * 把 3 小时的整理压缩到 30 分钟的判断。
 *
 * <p><b>三条设计约束在这里的落地</b>：
 * <ol>
 *   <li><b>AI 结果只是草稿</b>：卡片状态是 DRAFT，不直接产生任何业务事实</li>
 *   <li><b>人必须确认</b>：确认弹窗左右并排展示「AI 原稿」与「PM 定稿」，
 *       原稿只读，定稿可编辑；只要改过，状态就记为 MODIFIED</li>
 *   <li><b>AI 挂了业务照走</b>：生成失败时展示降级提示，
 *       并明确告诉 PM「可以跳过 AI，手工新建问题」</li>
 * </ol>
 *
 * <p><b>刻意不做的事</b>：本页没有任何「AI 自动定优先级」的入口，
 * 优先级始终由 PM 在问题转任务时手动设定。
 */
import { ref, reactive, computed, onMounted, h } from 'vue'
import { useRouter } from 'vue-router'
import {
  NCard,
  NSpace,
  NButton,
  NTag,
  NText,
  NSelect,
  NInput,
  NEmpty,
  NResult,
  NSpin,
  NModal,
  NForm,
  NFormItem,
  NAlert,
  NDivider,
  NProgress,
  NThing,
  NTooltip,
  useMessage
} from 'naive-ui'

import {
  generateCandidates,
  fetchCandidatePage,
  confirmCandidate,
  rejectCandidate,
  splitCandidate,
  fetchSplitSuggestion,
  mergeCandidate,
  fetchDraftSuggestion
} from '@/api/ai'
import { fetchIssuePage } from '@/api/issue'
import { FEEDBACK_MODULES, moduleLabel } from '@/constants/enums'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const message = useMessage()
const userStore = useUserStore()

// ==================== 状态 ====================

/** 候选卡片列表 */
const cards = ref([])

/** 加载中 */
const loading = ref(false)

/** 生成中 */
const generating = ref(false)

/** 列表错误 */
const loadError = ref('')

/** 生成失败提示（降级提示） */
const generateError = ref('')

/** 当前批次号 */
const currentBatch = ref('')

/** 已处理的卡片数量统计 */
const handledCount = computed(
  () => cards.value.filter((item) => item.status !== 'DRAFT').length
)

/** 待处理数量 */
const pendingCount = computed(
  () => cards.value.filter((item) => item.status === 'DRAFT').length
)

/** 查询条件 */
const query = reactive({
  module: null,
  status: null,
  pageNo: 1,
  pageSize: 20
})

/** 状态筛选选项 */
const statusOptions = [
  { label: '待处理', value: 'DRAFT' },
  { label: '已确认', value: 'CONFIRMED' },
  { label: '修改后确认', value: 'MODIFIED' },
  { label: '已合并', value: 'MERGED' },
  { label: '已拆分', value: 'SPLITTED' },
  { label: '已驳回', value: 'REJECTED' }
]

/** 状态标签配色 */
function statusType(status) {
  const map = {
    DRAFT: 'info',
    CONFIRMED: 'success',
    MODIFIED: 'warning',
    MERGED: 'default',
    SPLITTED: 'info',
    REJECTED: 'error'
  }
  return map[status] || 'default'
}

/**
 * 「AI 归纳结果」区块的身份标签
 *
 * <p><b>为什么不能写死成「未确认草稿」</b>：
 * 这段文字永远是 AI 生成时的原话，即使 PM 已确认也不会被改写——确认的是
 * 「这个问题成立」，不是「这段归纳字字准确」。但卡片一旦变成已确认，
 * 标题旁挂着「已确认」、正文旁还挂着「未确认草稿」，同一张卡上两个标签
 * 直接打架，看的人会以为数据不一致。
 *
 * <p>所以标签随状态走：待处理时提示"这是草稿，还没人判断"；
 * 处理完之后说明"这是留档的 AI 原稿，正式结论在生成的 issue 里"。
 *
 * @param {string} status 卡片状态代码
 * @returns {{text: string, type: string, tip: string}}
 */
function draftBadge(status) {
  const map = {
    DRAFT: {
      text: '未确认草稿',
      type: 'warning',
      tip: 'AI 生成的归纳建议，还没经过 PM 判断。确认前不产生任何业务数据，也不进入统计。'
    },
    CONFIRMED: {
      text: 'AI 原稿 · 已留档',
      type: 'default',
      tip: 'AI 生成时的原始归纳，确认后原样留档、永不改写。正式结论以生成的那个问题为准。'
    },
    MODIFIED: {
      text: 'AI 原稿 · 已定稿',
      type: 'default',
      tip: 'AI 生成时的原始归纳，原样留档。PM 确认时改过内容，定稿见生成的那个问题。'
    },
    MERGED: {
      text: 'AI 原稿 · 已并入',
      type: 'default',
      tip: 'AI 生成时的原始归纳，原样留档。该建议已并入已有问题，没有单独建单。'
    },
    REJECTED: {
      text: 'AI 原稿 · 未采纳',
      type: 'default',
      tip: 'AI 生成时的原始归纳，原样留档。该建议被判为不成立，未采纳。'
    },
    SPLITTED: {
      text: 'AI 原稿 · 已拆分',
      type: 'info',
      tip: 'PM 判定这张卡片把多个不同问题混在了一起，已手动拆分为多张新候选卡片分别处理；AI 原稿在此留档。'
    }
  }
  return map[status] || map.DRAFT
}

/** 是否为 PM */const canHandle = computed(
  () => userStore.role === 'PM' || userStore.role === 'ADMIN'
)

// ==================== 数据加载 ====================

/**
 * 加载候选卡片列表
 */
async function loadData() {
  loading.value = true
  loadError.value = ''

  try {
    const params = { pageNo: query.pageNo, pageSize: query.pageSize }
    if (query.module) params.module = query.module
    if (query.status) params.status = query.status

    const data = await fetchCandidatePage(params)
    cards.value = data?.records || []
  } catch (error) {
    cards.value = []
    // 503 表示 AI 服务不可用，但列表查询本身不受影响
    loadError.value = error.message || '加载候选卡片失败'
  } finally {
    loading.value = false
  }
}

/**
 * 调用 AI 生成候选卡片
 *
 * <p><b>降级设计</b>：失败不抛错、不阻塞页面，
 * 只在顶部显示一条明确的降级提示，
 * 并引导 PM 走手工创建的路径。
 * 这保证「AI 不是必经之路」这条约束成立。
 */
async function handleGenerate() {
  generating.value = true
  generateError.value = ''

  try {
    // module 为空表示处理所有模块；跨模块合并始终是禁止的
    const data = await generateCandidates({ module: query.module || null })
    currentBatch.value = data?.batchNo || ''

    if (data?.fallback) {
      // 发生降级时如实告知，而不是假装结果来自大模型
      generateError.value =
        data.message || 'AI 服务不可用，已自动降级为本地规则实现。'
    } else {
      message.success(`AI 已生成 ${data?.count || 0} 张候选卡片，请逐张确认`)
    }

    await loadData()
  } catch (error) {
    if (error.code === 503) {
      generateError.value =
        'AI 服务暂时不可用（未启用自动降级）。你可以跳过 AI，'
        + '直接在「问题列表」中手工新建问题。'
    } else {
      generateError.value = error.message || 'AI 生成失败，请稍后重试'
    }
  } finally {
    generating.value = false
  }
}

function handleReset() {
  query.module = null
  query.status = null
  query.pageNo = 1
  loadData()
}

// ==================== 确认卡片 ====================

const showConfirmModal = ref(false)
const confirmSubmitting = ref(false)
const currentCard = ref(null)
const confirmFormRef = ref(null)

/** AI 起草建议识别中 */
const confirmAnalyzing = ref(false)

/** AI 起草建议结果（null 表示还没识别/识别失败） */
const confirmSuggestion = ref(null)

/**
 * 预填来源标签
 *
 * <p>区分「真实大模型」与「本地规则兜底」，口径与拆分弹窗一致：
 * 规则实现给出的预填同样是参考，但结论可靠度不同，
 * PM 需要知道自己在看谁的结果。
 *
 * @returns {{text: string, type: string}}
 */
const confirmSourceTag = computed(() => {
  const result = confirmSuggestion.value
  if (!result?.available) {
    return { text: '未获得建议', type: 'default' }
  }
  const byRule = result.fallback || result.provider === 'RULE'
  return byRule
    ? { text: '本地规则预填', type: 'warning' }
    : { text: 'AI 预填', type: 'success' }
})

/** 确认表单：初始值与 AI 草稿一致，PM 可改 */
const confirmForm = reactive({
  title: '',
  description: '',
  finalContent: '',
  acceptanceCriteria: ''
})

const confirmRules = {
  title: [{ required: true, message: '请填写问题标题', trigger: 'blur' }]
}

/**
 * 打开确认弹窗
 *
 * <p>把 AI 草稿原样填入可编辑表单，
 * PM 不改就是原样采纳（CONFIRMED），改了就是修改后确认（MODIFIED）。
 * 判定在后端的 confirm 逻辑里做，前端不重复实现规则。
 *
 * <p>验收条件原稿里没有现成内容，打开弹窗后异步请求
 * AI 起草建议（复用 DRAFT 作用点）预填；失败时静默处理，
 * 不打断确认操作。
 *
 * @param {object} card 候选卡片
 */
function openConfirmModal(card) {
  currentCard.value = card
  confirmForm.title = card.title || ''
  confirmForm.description = buildDescription(card)
  confirmForm.finalContent = buildFinalContent(card)
  confirmForm.acceptanceCriteria = ''
  confirmSuggestion.value = null
  showConfirmModal.value = true
  analyzeDraft()
}

/**
 * 请求 AI 起草建议并预填验收条件
 *
 * 与拆分弹窗的 analyzeSplit 同一套约定：
 * 识别中不弹错误、失败只更新提示文案；
 * AI 给出建议时覆盖验收条件字段（ PM 仍可继续修改）。
 */
async function analyzeDraft() {
  if (!currentCard.value?.id) return

  confirmAnalyzing.value = true
  try {
    const result = await fetchDraftSuggestion(currentCard.value.id)
    confirmSuggestion.value = result

    if (result?.available && result.acceptanceCriteria) {
      confirmForm.acceptanceCriteria = result.acceptanceCriteria
    }
  } catch (error) {
    confirmSuggestion.value = {
      available: false,
      message: error.message || 'AI 起草失败，请手动填写验收条件。'
    }
  } finally {
    confirmAnalyzing.value = false
  }
}

/**
 * 把 AI 草稿拼成问题描述
 *
 * @param {object} card 候选卡片
 * @returns {string} 问题描述
 */
function buildDescription(card) {
  const parts = []
  if (card.phenomenon) parts.push(`【现象】${card.phenomenon}`)
  if (card.userDemand) parts.push(`【诉求】${card.userDemand}`)
  return parts.join('\n\n')
}

/**
 * 把 AI 草稿拼成需求说明初稿
 *
 * @param {object} card 候选卡片
 * @returns {string} 需求说明
 */
function buildFinalContent(card) {
  return card.userDemand ? `用户期望：${card.userDemand}` : ''
}

/**
 * 提交确认
 */
async function submitConfirm() {
  try {
    await confirmFormRef.value?.validate()
  } catch {
    return
  }

  confirmSubmitting.value = true
  try {
    const data = await confirmCandidate(currentCard.value.id, {
      title: confirmForm.title,
      description: confirmForm.description,
      finalContent: confirmForm.finalContent,
      acceptanceCriteria: confirmForm.acceptanceCriteria
    })
    message.success('已确认为正式问题')
    showConfirmModal.value = false
    router.push({ name: 'issue-detail', params: { id: data.issueId } })
  } catch (error) {
    message.error(error.message || '确认失败')
  } finally {
    confirmSubmitting.value = false
  }
}

// ==================== 合并卡片 ====================
//
// 与拆分互补：拆分处理"一张卡片混了多个问题"，
// 合并处理"这张卡片描述的问题已经存在"。
// 合并不建新单，把关联反馈挂到目标问题上，原卡片转「已合并」留档。

const showMergeModal = ref(false)
const mergeSubmitting = ref(false)
const mergeLoadingIssues = ref(false)

/** 合并表单：目标问题 + 可选说明 */
const mergeForm = reactive({ targetIssueId: null, remark: '' })

/** 目标问题下拉选项 */
const mergeIssueOptions = ref([])

/**
 * 打开合并弹窗并加载可选问题列表
 *
 * @param {object} card 候选卡片
 */
async function openMergeModal(card) {
  currentCard.value = card
  mergeForm.targetIssueId = null
  mergeForm.remark = ''
  showMergeModal.value = true

  mergeLoadingIssues.value = true
  try {
    const page = await fetchIssuePage({ pageNo: 1, pageSize: 100 })
    mergeIssueOptions.value = (page?.records || []).map((issue) => ({
      label: `#${issue.id} ${issue.issueNo} ${issue.title}`,
      value: issue.id
    }))
  } catch (error) {
    message.error(error.message || '问题列表加载失败')
  } finally {
    mergeLoadingIssues.value = false
  }
}

/**
 * 提交合并
 */
async function submitMerge() {
  if (!mergeForm.targetIssueId) {
    message.warning('必须选择要并入的目标问题')
    return
  }

  mergeSubmitting.value = true
  try {
    await mergeCandidate(currentCard.value.id, {
      targetIssueId: mergeForm.targetIssueId,
      remark: mergeForm.remark || undefined
    })
    message.success('已并入目标问题，关联反馈已挂到该问题上')
    showMergeModal.value = false
    await loadData()
  } catch (error) {
    message.error(error.message || '合并失败')
  } finally {
    mergeSubmitting.value = false
  }
}

// ==================== 驳回卡片 ====================

const showRejectModal = ref(false)
const rejectSubmitting = ref(false)
const rejectReason = ref('')

/**
 * 打开驳回弹窗
 *
 * @param {object} card 候选卡片
 */
function openRejectModal(card) {
  currentCard.value = card
  rejectReason.value = ''
  showRejectModal.value = true
}

/**
 * 提交驳回
 */
async function submitReject() {
  if (!rejectReason.value.trim()) {
    message.warning('驳回时必须填写原因')
    return
  }

  rejectSubmitting.value = true
  try {
    await rejectCandidate(currentCard.value.id, { reason: rejectReason.value })
    message.success('已驳回该候选卡片，关联反馈已回到未归类')
    showRejectModal.value = false
    await loadData()
  } catch (error) {
    message.error(error.message || '驳回失败')
  } finally {
    rejectSubmitting.value = false
  }
}

// ==================== 拆分卡片 ====================
//
// 验收场景：一条反馈里同时写了"上传失败"和"看不到退回原因"，
// AI 把两件事归成了一张卡片。拆分让 PM 把它们分成两张候选卡片，
// 之后各自走确认流程、各自生成正式问题。
//
// 关键点：同一条反馈允许同时勾进多个部分——因为混卡的根源
// 往往就是一条反馈里写了多件事，按反馈硬拆是拆不开的。

const showSplitModal = ref(false)
const splitSubmitting = ref(false)

/** 拆分后的各部分（动态数组，至少 2 个） */
const splitParts = ref([])

/** AI 预填识别中 */
const splitAnalyzing = ref(false)

/** AI 识别结果说明（null 表示还没识别/识别失败） */
const splitSuggestion = ref(null)

/**
 * 预填来源标签
 *
 * <p>区分「真实大模型」与「本地规则兜底」：规则实现给出的预填同样是参考，
 * 但结论可靠度不同，PM 需要知道自己在看谁的结果——
 * 这与 AI 调用记录里始终记录 provider 的口径保持一致。
 *
 * @returns {{text: string, type: string}}
 */
const splitSourceTag = computed(() => {
  const result = splitSuggestion.value
  if (!result?.available) {
    return { text: '未获得建议', type: 'default' }
  }
  const byRule = result.fallback || result.provider === 'RULE'
  return byRule
    ? { text: '本地规则预填', type: 'warning' }
    : { text: 'AI 预填', type: 'success' }
})

/** 拆分部分的反馈选项（所有部分共享同一批关联反馈） */
const splitFeedbackOptions = computed(() =>
  (currentCard.value?.relatedFeedbackIds || []).map((id) => ({
    label: `反馈 #${id}`,
    value: id
  }))
)

/**
 * 打开拆分弹窗
 *
 * 先给一个最小可用的两手准备：第一部分预填 AI 原稿、第二部分留空，
 * 然后立即向 AI 请求拆分建议。AI 识别出多个问题时覆盖成预填方案，
 * 识别失败或认为只有一个问题时保留下面的手填模板——
 * 拆分是人工决策，AI 只是参考，不能因为 AI 缺席就挡住操作。
 *
 * @param {object} card 候选卡片
 */
function openSplitModal(card) {
  currentCard.value = card
  const ids = [...(card.relatedFeedbackIds || [])]
  splitParts.value = [
    {
      title: card.title || '',
      phenomenon: card.phenomenon || '',
      userDemand: card.userDemand || '',
      feedbackIds: ids
    },
    { title: '', phenomenon: '', userDemand: '', feedbackIds: ids }
  ]
  splitSuggestion.value = null
  showSplitModal.value = true
  analyzeSplit()
}

/**
 * 请求 AI 拆分建议并预填
 *
 * 识别中会先把模板留在界面上（不清空），避免出现"空白等待"。
 * 失败时静默处理，只更新提示文案，不打断 PM 的填写。
 */
async function analyzeSplit() {
  if (!currentCard.value?.id) return

  splitAnalyzing.value = true
  try {
    const result = await fetchSplitSuggestion(currentCard.value.id)
    splitSuggestion.value = result

    const parts = result?.parts || []
    if (!result?.available || parts.length === 0) {
      // AI 不可用/没归纳出内容：保留手填模板
      return
    }

    const fallbackIds = [...(currentCard.value.relatedFeedbackIds || [])]
    splitParts.value = parts.map((part) => ({
      title: part.title || '',
      phenomenon: part.phenomenon || '',
      userDemand: part.userDemand || '',
      feedbackIds: part.feedbackIds?.length ? [...part.feedbackIds] : fallbackIds
    }))

    // AI 认为只有一个问题时，补一个空部分，否则弹窗只有一段、
    // 与"拆分"的语义不符（拆分至少要 2 个部分才能提交）
    if (splitParts.value.length === 1) {
      splitParts.value.push({
        title: '',
        phenomenon: '',
        userDemand: '',
        feedbackIds: fallbackIds
      })
    }
  } catch (error) {
    splitSuggestion.value = {
      available: false,
      splitSuggested: false,
      message: error.message || 'AI 识别失败，请手动填写各部分内容。'
    }
  } finally {
    splitAnalyzing.value = false
  }
}

/** 追加一个拆分部分 */
function addSplitPart() {
  splitParts.value.push({
    title: '',
    phenomenon: '',
    userDemand: '',
    feedbackIds: [...(currentCard.value?.relatedFeedbackIds || [])]
  })
}

/** 删除一个拆分部分（保底保留 2 个） */
function removeSplitPart(index) {
  if (splitParts.value.length <= 2) return
  splitParts.value.splice(index, 1)
}

/**
 * 提交拆分
 *
 * 前端先做完整性校验（每部分有标题、反馈全覆盖），
 * 后端会再做一次同样的校验——双保险，但不依赖前端。
 */
async function submitSplit() {
  const parts = splitParts.value

  if (parts.length < 2) {
    message.warning('拆分至少要分成 2 张候选卡片')
    return
  }
  if (parts.some((part) => !part.title.trim())) {
    message.warning('每张拆分后的卡片都必须填写标题')
    return
  }
  // 每个部分至少要挂一条反馈：拆分后的卡片仍然是"由反馈归纳出的问题"，
  // 没有反馈依据的卡片不应当存在。也避免"清空选择"被后端理解成"继承全部"
  if (parts.some((part) => !part.feedbackIds?.length)) {
    message.warning('每个部分至少要选择一条关联反馈')
    return
  }

  const originalIds = currentCard.value?.relatedFeedbackIds || []
  const covered = new Set()
  for (const part of parts) {
    for (const id of part.feedbackIds || []) covered.add(id)
  }
  const missing = originalIds.filter((id) => !covered.has(id))
  if (missing.length > 0) {
    message.warning(
      `反馈 ${missing.map((id) => '#' + id).join('、')} 没有被任何部分接收，请为它指定去向`
    )
    return
  }

  splitSubmitting.value = true
  try {
    const newIds = await splitCandidate(currentCard.value.id, {
      parts: parts.map((part) => ({
        title: part.title,
        phenomenon: part.phenomenon || null,
        userDemand: part.userDemand || null,
        feedbackIds: part.feedbackIds?.length ? part.feedbackIds : null
      }))
    })
    message.success(
      `已拆分为 ${newIds?.length || parts.length} 张候选卡片，原卡片已留档，请逐张确认新卡片`
    )
    showSplitModal.value = false
    await loadData()
  } catch (error) {
    message.error(error.message || '拆分失败')
  } finally {
    splitSubmitting.value = false
  }
}

/** 当前卡片是否被修改过（用于界面提示） */
function isCardModified(card) {
  if (currentCard.value?.id !== card.id) return false
  return confirmForm.title !== (card.title || '')
}

/** 时间格式化 */
function fmt(value) {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '—'
}

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <!-- ==================== 操作区 ==================== -->
    <n-card class="app-card filter-card">
      <n-space vertical :size="12" style="width: 100%">
        <n-space :wrap="true" align="center">
          <n-text style="font-size: 13px">功能模块</n-text>
          <n-select
            v-model:value="query.module"
            placeholder="全部模块（不选择则处理所有）"
            :options="FEEDBACK_MODULES"
            clearable
            style="width: 220px"
          />
          <n-button
            type="primary"
            :loading="generating"
            @click="handleGenerate"
          >
            生成候选问题
          </n-button>

          <n-divider vertical />

          <n-text style="font-size: 13px">状态</n-text>
          <n-select
            v-model:value="query.status"
            placeholder="全部状态"
            :options="statusOptions"
            clearable
            style="width: 150px"
            @update:value="
              () => {
                query.pageNo = 1
                loadData()
              }
            "
          />
          <n-button quaternary @click="handleReset">重置</n-button>
        </n-space>

        <!-- AI 降级提示：明确告知可绕过 AI -->
        <n-alert
          v-if="generateError"
          type="warning"
          :bordered="false"
          closable
          @close="generateError = ''"
        >
          {{ generateError }}
        </n-alert>

        <!-- 统计条 -->
        <n-text depth="3" style="font-size: 12px">
          共 {{ cards.length }} 张卡片 · 待处理 {{ pendingCount }} 张 · 已处理
          {{ handledCount }} 张
          <template v-if="currentBatch">
            · 当前批次 {{ currentBatch }}
          </template>
        </n-text>
      </n-space>
    </n-card>

    <!-- 加载态 -->
    <n-card v-if="loading" class="app-card">
      <n-spin size="large" style="display: block; margin: 60px auto" />
    </n-card>

    <!-- 失败态 -->
    <n-card v-else-if="loadError" class="app-card">
      <n-result status="error" title="加载失败" :description="loadError">
        <template #footer>
          <n-button type="primary" @click="loadData">重新加载</n-button>
      </template>
      </n-result>
    </n-card>

    <!-- 空态 -->
    <n-card v-else-if="cards.length === 0" class="app-card">
      <n-empty description="还没有 AI 候选卡片，选择模块后点击「生成候选问题」">
        <template #extra>
          <n-button size="small" @click="router.push({ name: 'issue-list' })">
            或去手工新建问题
          </n-button>
        </template>
      </n-empty>
    </n-card>

    <!-- 卡片列表 -->
    <n-space v-else vertical :size="12" style="width: 100%">
      <n-card
        v-for="card in cards"
        :key="card.id"
        class="app-card candidate-card"
        :class="{ 'is-handled': card.status !== 'DRAFT' }"
      >
        <!-- 卡片头 -->
        <template #header>
          <n-space align="center" :size="10" style="flex: 1">
            <n-text strong style="font-size: 15px">{{ card.title }}</n-text>
            <n-tag size="small" :bordered="false" :type="statusType(card.status)">
              {{ card.statusLabel }}
            </n-tag>
            <n-tag size="small" :bordered="false">
              {{ card.moduleLabel || moduleLabel(card.module) }}
            </n-tag>
          </n-space>
        </template>

        <!-- 操作按钮：仅 PM 可见，且只有草稿态可操作 -->
        <template #header-extra>
          <n-space v-if="canHandle && card.status === 'DRAFT'" :size="8">
            <n-button size="small" secondary type="primary" @click="openConfirmModal(card)">
              确认并创建问题
            </n-button>
            <n-tooltip trigger="hover" placement="top">
              <template #trigger>
                <n-button size="small" secondary type="warning" @click="openSplitModal(card)">
                  拆分
                </n-button>
              </template>
              这张卡片混进了多个不同的问题？把它拆成多张候选卡片分别确认
            </n-tooltip>
            <n-tooltip trigger="hover" placement="top">
              <template #trigger>
                <n-button size="small" secondary type="info" @click="openMergeModal(card)">
                  合并
                </n-button>
              </template>
              这张卡片描述的问题已经存在？把关联反馈并入那个问题，不重复建单
            </n-tooltip>
            <n-button size="small" secondary type="error" @click="openRejectModal(card)">
              驳回
            </n-button>
          </n-space>
        </template>

        <!-- AI 输出内容：草稿态提示待确认，已处理态说明是留档原文 -->
        <div class="ai-section">
          <div class="section-label">
            <n-text depth="3" style="font-size: 12px">AI 归纳结果</n-text>
            <n-tooltip trigger="hover" placement="top-start" style="max-width: 300px">
              <template #trigger>
                <n-tag
                  size="tiny"
                  :bordered="false"
                  :type="draftBadge(card.status).type"
                  style="cursor: help"
                >
                  {{ draftBadge(card.status).text }}
                </n-tag>
              </template>
              {{ draftBadge(card.status).tip }}
            </n-tooltip>
          </div>

          <div class="field-row">
            <div class="field-label">问题现象</div>
            <div class="field-value">{{ card.phenomenon || '（AI 未给出）' }}</div>
          </div>

          <div class="field-row">
            <div class="field-label">用户诉求</div>
            <div class="field-value">{{ card.userDemand || '（AI 未给出）' }}</div>
          </div>

          <div class="field-row">
            <div class="field-label">归类理由</div>
            <div class="field-value">{{ card.aiReason || '（AI 未给出）' }}</div>
          </div>

          <div v-if="card.aiConfidence != null" class="confidence-row">
            <n-text depth="3" style="font-size: 12px">置信度</n-text>
            <n-progress
              type="line"
              :percentage="Math.round(card.aiConfidence * 100)"
              :height="6"
              style="width: 160px"
              :color="card.aiConfidence >= 0.8 ? 'var(--c-success)' : 'var(--c-warning)'"
            />
          </div>
        </div>

        <n-divider />

        <!-- 关联反馈：可回查原始材料 -->
        <div class="related-row">
          <n-text depth="3" style="font-size: 12px">
            关联反馈 {{ card.relatedFeedbackIds?.length || 0 }} 条
          </n-text>

          <n-space :size="6" :wrap="true">
            <n-button
              v-for="fbId in card.relatedFeedbackIds || []"
              :key="fbId"
              size="tiny"
              quaternary
              @click="router.push({ name: 'feedback-detail', params: { id: fbId } })"
            >
              #{{ fbId }}
            </n-button>
          </n-space>
        </div>

        <!-- 已处理的卡片：展示处理结果 -->
        <div v-if="card.status !== 'DRAFT'" class="handle-result">
          <n-text depth="3" style="font-size: 12px">
            处理人：{{ card.handledByName || '—' }} · {{ fmt(card.handleTime) }}
          </n-text>
          <div v-if="card.handleRemark" class="handle-remark">
            {{ card.handleRemark }}
          </div>
        </div>
      </n-card>
    </n-space>

    <!-- ==================== 确认弹窗 ==================== -->
    <n-modal
      v-model:show="showConfirmModal"
      preset="card"
      title="确认候选问题"
      class="confirm-modal"
      style="width: 900px"
    >
      <n-alert type="info" :bordered="false" style="margin-bottom: 16px">
        左侧是 AI 原稿（不可修改，确认后原样留档）；右侧是你最终确认的内容。
        <b>只要修改过，系统就会记录为「修改后确认」</b>，便于事后区分 AI 产出与人工决策。
      </n-alert>

      <div class="confirm-grid">
        <!-- 左：AI 原稿（只读） -->
        <div class="panel">
          <div class="panel-head">
            <n-text strong>AI 原稿</n-text>
            <n-tag size="tiny" :bordered="false" type="info">只读</n-tag>
          </div>

          <div class="readonly-box">
            <div class="readonly-item">
              <span class="readonly-label">标题</span>
              <span>{{ currentCard?.title || '—' }}</span>
            </div>
            <div class="readonly-item">
              <span class="readonly-label">现象</span>
              <span>{{ currentCard?.phenomenon || '—' }}</span>
            </div>
            <div class="readonly-item">
              <span class="readonly-label">诉求</span>
              <span>{{ currentCard?.userDemand || '—' }}</span>
            </div>
          </div>
        </div>

        <!-- 右：PM 定稿（可编辑） -->
        <div class="panel">
          <div class="panel-head">
            <n-text strong>确认后的内容</n-text>
            <n-tag
              v-if="currentCard && confirmForm.title !== (currentCard.title || '')"
              size="tiny"
              :bordered="false"
              type="warning"
            >
              已修改
            </n-tag>
            <n-tag v-else size="tiny" :bordered="false" type="success">
              原样采纳
            </n-tag>
          </div>

          <div class="split-ai-hint confirm-ai-hint">
            <div class="split-ai-head">
              <n-space align="center" :size="8" :wrap="false">
                <n-spin v-if="confirmAnalyzing" :size="13" />
                <n-text v-if="confirmAnalyzing" depth="3" class="split-ai-title">
                  AI 正在根据关联反馈起草验收条件…
                </n-text>
                <n-tag
                  v-else-if="confirmSuggestion"
                  size="tiny"
                  :bordered="false"
                  :type="confirmSourceTag.type"
                >
                  {{ confirmSourceTag.text }}
                </n-tag>
              </n-space>
              <n-button
                v-if="!confirmAnalyzing"
                size="tiny"
                quaternary
                type="primary"
                @click="analyzeDraft"
              >
                重新生成
              </n-button>
            </div>
            <n-text
              v-if="!confirmAnalyzing && confirmSuggestion?.message"
              depth="3"
              class="split-ai-msg"
            >
              {{ confirmSuggestion.message }}
            </n-text>
            <n-text v-else-if="confirmAnalyzing" depth="3" class="split-ai-msg">
              起草期间可以手动填写，建议生成后会覆盖验收条件字段。
            </n-text>
          </div>

          <n-form
            ref="confirmFormRef"
            :model="confirmForm"
            :rules="confirmRules"
            label-placement="top"
          >
            <n-form-item label="问题标题" path="title">
              <n-input v-model:value="confirmForm.title" maxlength="200" show-count />
            </n-form-item>

            <n-form-item label="问题描述">
              <n-input
                v-model:value="confirmForm.description"
                type="textarea"
                :rows="5"
              />
            </n-form-item>

            <n-form-item label="需求说明">
              <n-input
                v-model:value="confirmForm.finalContent"
                type="textarea"
                :rows="4"
              />
            </n-form-item>

            <n-form-item label="验收条件">
              <n-input
                v-model:value="confirmForm.acceptanceCriteria"
                type="textarea"
                :rows="3"
                placeholder="如何判断这个问题已解决"
              />
            </n-form-item>
          </n-form>
        </div>
      </div>

      <template #footer>
        <n-space justify="end">
          <n-button quaternary @click="showConfirmModal = false">取消</n-button>
          <n-button
            type="primary"
            :loading="confirmSubmitting"
            @click="submitConfirm"
          >
            确认并生成问题
          </n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- ==================== 拆分弹窗 ==================== -->
    <n-modal
      v-model:show="showSplitModal"
      preset="card"
      title="拆分候选卡片"
      class="split-modal"
      style="width: 760px"
    >
      <n-alert type="info" :bordered="false" style="margin-bottom: 14px">
        当一张卡片把<b>多个不同的问题</b>混在一起（典型：一条反馈里同时写了
        「上传失败」和「看不到退回原因」）时，把它拆成多张候选卡片分别确认。
        <b>同一条反馈可以同时勾进多个部分</b>——这正是"一条反馈拆出多个问题"的用法。
        拆分后原卡片转为「已拆分」留档，新卡片按普通草稿走确认流程。
      </n-alert>

      <div class="split-ai-hint">
        <div class="split-ai-head">
          <n-space align="center" :size="8" :wrap="false">
            <n-spin v-if="splitAnalyzing" :size="13" />
            <n-text v-if="splitAnalyzing" depth="3" class="split-ai-title">
              AI 正在识别这张卡片里包含几个问题…
            </n-text>
            <n-tag
              v-else-if="splitSuggestion"
              size="tiny"
              :bordered="false"
              :type="splitSourceTag.type"
            >
              {{ splitSourceTag.text }}
            </n-tag>
          </n-space>
          <n-button
            v-if="!splitAnalyzing"
            size="tiny"
            quaternary
            type="primary"
            @click="analyzeSplit"
          >
            重新识别
          </n-button>
        </div>
        <n-text
          v-if="!splitAnalyzing && splitSuggestion?.message"
          depth="3"
          class="split-ai-msg"
        >
          {{ splitSuggestion.message }}
        </n-text>
        <n-text
          v-else-if="splitAnalyzing"
          depth="3"
          class="split-ai-msg"
        >
          识别期间可以先按下方模板手动填写，识别结果会覆盖已填写内容。
        </n-text>
      </div>

      <div class="split-parts">
        <div
          v-for="(part, index) in splitParts"
          :key="index"
          class="split-part"
        >
          <div class="split-part-head">
            <n-text strong>问题 {{ index + 1 }}</n-text>
            <n-button
              v-if="splitParts.length > 2"
              size="tiny"
              quaternary
              type="error"
              @click="removeSplitPart(index)"
            >
              移除
            </n-button>
          </div>

          <n-form label-placement="top" size="small">
            <n-form-item label="问题标题" required>
              <n-input
                v-model:value="part.title"
                maxlength="200"
                placeholder="这个问题单独叫什么"
              />
            </n-form-item>
            <n-form-item label="问题现象">
              <n-input
                v-model:value="part.phenomenon"
                type="textarea"
                :rows="2"
                placeholder="这个问题的现象描述"
              />
            </n-form-item>
            <n-form-item label="用户诉求">
              <n-input
                v-model:value="part.userDemand"
                type="textarea"
                :rows="2"
                placeholder="用户希望怎么解决"
              />
            </n-form-item>
            <n-form-item label="关联反馈（可多选，同一条反馈可归入多个问题）">
              <n-select
                v-model:value="part.feedbackIds"
                :options="splitFeedbackOptions"
                multiple
                clearable
                placeholder="选择归入该问题的反馈"
              />
            </n-form-item>
          </n-form>
        </div>
      </div>

      <n-button
        size="small"
        dashed
        block
        style="margin-top: 12px"
        @click="addSplitPart"
      >
        + 再拆出一个问题
      </n-button>

      <template #footer>
        <n-space justify="end">
          <n-button quaternary @click="showSplitModal = false">取消</n-button>
          <n-button
            type="primary"
            :loading="splitSubmitting"
            @click="submitSplit"
          >
            确认拆分
          </n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- ==================== 合并弹窗 ==================== -->
    <n-modal
      v-model:show="showMergeModal"
      preset="card"
      title="合并候选卡片"
      style="width: 560px"
    >
      <n-alert type="info" :bordered="false" style="margin-bottom: 14px">
        当这张卡片描述的问题<b>已经存在</b>（如历史上确认过的同一问题）时，
        把它的关联反馈并入那个问题——<b>不重复建单</b>。
        原卡片转为「已合并」留档，AI 原稿仍可追溯。
      </n-alert>

      <n-form label-placement="top">
        <n-form-item label="并入的目标问题" required>
          <n-select
            v-model:value="mergeForm.targetIssueId"
            :options="mergeIssueOptions"
            :loading="mergeLoadingIssues"
            filterable
            placeholder="搜索问题编号或标题"
          />
        </n-form-item>
        <n-form-item label="合并说明（可选）">
          <n-input
            v-model:value="mergeForm.remark"
            type="textarea"
            :rows="3"
            maxlength="500"
            placeholder="如：与 IS-0008 是同一问题，反馈已并入该单"
          />
        </n-form-item>
      </n-form>

      <template #footer>
        <n-space justify="end">
          <n-button quaternary @click="showMergeModal = false">取消</n-button>
          <n-button
            type="primary"
            :loading="mergeSubmitting"
            @click="submitMerge"
          >
            确认合并
          </n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- ==================== 驳回弹窗 ==================== -->
    <n-modal
      v-model:show="showRejectModal"
      preset="card"
      title="驳回候选问题"
      style="width: 520px"
    >
      <n-form label-placement="top">
        <n-form-item label="驳回原因" required>
          <n-input
            v-model:value="rejectReason"
            type="textarea"
            :rows="4"
            placeholder="说明为什么该候选不成立，如：现象描述过于笼统，无法定位"
          />
        </n-form-item>
      </n-form>

      <template #footer>
        <n-space justify="end">
          <n-button quaternary @click="showRejectModal = false">取消</n-button>
          <n-button
            type="error"
            :loading="rejectSubmitting"
            @click="submitReject"
          >
            确认驳回
          </n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<style scoped>
.filter-card {
  margin-bottom: 16px;
}

/* 已处理的卡片：降低视觉权重，让待处理项更突出 */
.candidate-card.is-handled {
  opacity: 0.72;
}

.ai-section {
  padding: 14px 16px;
  border: 1px dashed rgba(122, 136, 190, 0.35);
  border-radius: 6px;
  background-color: rgba(244, 246, 255, 0.82);
}

.section-label {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.field-row {
  display: flex;
  gap: 12px;
  margin-bottom: 10px;
  align-items: flex-start;
}

.field-label {
  flex: 0 0 68px;
  color: var(--c-text-3);
  font-size: 12px;
  line-height: 1.7;
}

.field-value {
  flex: 1;
  color: var(--c-text-1);
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.confidence-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
}

.related-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.handle-result {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--c-border-soft);
}

.handle-remark {
  margin-top: 6px;
  color: var(--c-text-2);
  font-size: 12px;
  line-height: 1.6;
}

/* 确认弹窗：左右并排 */
.confirm-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

@media (max-width: 820px) {
  .confirm-grid {
    grid-template-columns: 1fr;
  }
}

.panel-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.readonly-box {
  padding: 12px 14px;
  border: 1px solid var(--c-border-soft);
  border-radius: 6px;
  background-color: rgba(255, 255, 255, 0.86);
  font-size: 13px;
  line-height: 1.7;
}

.readonly-item {
  display: flex;
  gap: 10px;
  margin-bottom: 8px;
}

.readonly-label {
  flex: 0 0 40px;
  color: var(--c-text-3);
  font-size: 12px;
}

/* 确认弹窗：AI 起草建议状态条（复用 split-ai-hint 基础样式，只改外边距） */
.confirm-ai-hint {
  margin: 0 0 14px;
}

/* 拆分弹窗：AI 预填状态条 */
.split-ai-hint {
  margin: -2px 0 14px;
  padding: 8px 12px;
  border: 1px solid rgba(122, 136, 190, 0.22);
  border-radius: 6px;
  background-color: rgba(240, 243, 255, 0.72);
}

.split-ai-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.split-ai-msg {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
}

.split-ai-title {
  font-size: 12px;
}

/* 拆分弹窗：各部分纵向排列，用虚线框分隔 */
.split-part {
  padding: 12px 14px 2px;
  border: 1px dashed rgba(122, 136, 190, 0.35);
  border-radius: 6px;
  background-color: rgba(244, 246, 255, 0.82);
}

.split-part + .split-part {
  margin-top: 12px;
}

.split-part-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
</style>
