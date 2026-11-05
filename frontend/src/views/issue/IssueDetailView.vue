<script setup>
/**
 * 问题详情页
 *
 * <p><b>本页是全项目的「证据页」</b>，直接对应验收场景 5：
 * 「AI 生成的内容与人工确认后的结果必须能区分开」。
 *
 * <p>核心设计是<b>左右并排对照</b>：
 * <ul>
 *   <li>左侧：AI 原始建议（aiDraftContent），确认后永不覆盖，视觉上标记只读</li>
 *   <li>右侧：PM 定稿内容（finalContent）</li>
 * </ul>
 * 只要两侧内容不同，页面就会显示「PM 已修改 AI 建议」的醒目标签。
 * 这比把两份内容存在数据库里、靠答辩时口头解释要可信得多。
 *
 * <p>页面底部是<b>关联反馈原文列表</b>，每条都能点进去看原话，
 * 实现「任务 → 问题 → 反馈原文」的完整回溯链条。
 */
import { ref, computed, onMounted, h } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  NCard,
  NSpace,
  NButton,
  NTag,
  NText,
  NDescriptions,
  NDescriptionsItem,
  NDataTable,
  NInput,
  NEmpty,
  NResult,
  NSpin,
  NModal,
  NForm,
  NFormItem,
  NDivider,
  useMessage
} from 'naive-ui'

import { fetchIssueDetail, linkFeedbacks, unlinkFeedback } from '@/api/issue'
import { fetchFeedbackPage, fetchFeedbackDetail } from '@/api/feedback'
import { draftRequirement } from '@/api/ai'
import { moduleLabel } from '@/constants/enums'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const userStore = useUserStore()

// ==================== 状态 ====================

/** 问题详情 */
const detail = ref(null)

/** 关联反馈的详情列表（用于展示原文） */
const linkedFeedbacks = ref([])

/** 加载中 */
const loading = ref(false)

/** 错误信息 */
const loadError = ref('')

/** 是否为产品经理 */
const canEdit = computed(
  () => userStore.role === 'PM' || userStore.role === 'ADMIN'
)

/** 是否来自 AI */
const fromAi = computed(() => detail.value?.sourceType === 'AI')

/** AI 建议是否被 PM 修改过 */
const isModified = computed(
  () => fromAi.value && detail.value?.modifiedFromAi === true
)

// ==================== 数据加载 ====================

/**
 * 加载问题详情
 *
 * <p>详情接口只返回关联的反馈 ID 列表，
 * 这里再并发拉取每条反馈的详情以展示原文。
 * 数量有限（一个问题通常关联几条到几十条），并发拉取可接受。
 */
async function loadDetail() {
  loading.value = true
  loadError.value = ''

  try {
    detail.value = await fetchIssueDetail(route.params.id)

    const ids = detail.value?.feedbackIds || []
    if (ids.length > 0) {
      const list = await Promise.all(
        ids.map((id) =>
          fetchFeedbackDetail(id).catch(() => null)
        )
      )
      // 过滤掉拉取失败的，避免整页因单条数据异常而崩
      linkedFeedbacks.value = list.filter((item) => item !== null)
    } else {
      linkedFeedbacks.value = []
    }
  } catch (error) {
    detail.value = null
    linkedFeedbacks.value = []
    loadError.value =
      error.code === 404
        ? '该问题不存在或已被删除'
        : error.message || '加载问题详情失败'
  } finally {
    loading.value = false
  }
}

// ==================== 关联反馈 ====================

/** 弹窗显示 */
const showLinkModal = ref(false)

/** 已选择的反馈 ID */
const selectedFeedbackIds = ref([])

/** 搜索关键词 */
const feedbackKeyword = ref('')

/** 候选反馈 */
const candidateRows = ref([])

/** 候选加载中 */
const candidateLoading = ref(false)

/** 关联说明 */
const linkRemark = ref('')

/** 提交中 */
const linkSubmitting = ref(false)

/** 当前问题已关联的 ID 集合 */
const linkedIdSet = computed(
  () => new Set(detail.value?.feedbackIds || [])
)

const candidateColumns = [
  { type: 'selection' },
  {
    title: '编号',
    key: 'feedbackNo',
    width: 160,
    render: (row) =>
      h('span', { class: 'text-mono', style: 'color:var(--c-text-2)' }, row.feedbackNo)
  },
  {
    title: '反馈原文',
    key: 'content',
    minWidth: 320,
    ellipsis: { tooltip: true }
  },
  {
    title: '模块',
    key: 'module',
    width: 100,
    render: (row) => row.moduleLabel || moduleLabel(row.module)
  },
  {
    title: '状态',
    key: 'status',
    width: 90,
    render: (row) =>
      h(
        NTag,
        { size: 'tiny', bordered: false },
        { default: () => row.statusLabel }
      )
  }
]

/**
 * 打开关联弹窗
 */
function openLinkModal() {
  selectedFeedbackIds.value = []
  linkRemark.value = ''
  feedbackKeyword.value = ''
  candidateRows.value = []
  showLinkModal.value = true
  loadCandidates()
}

/**
 * 加载候选反馈
 *
 * <p>默认筛选同模块，减少 PM 的翻找成本。
 */
async function loadCandidates() {
  candidateLoading.value = true
  try {
    const params = { pageNo: 1, pageSize: 50 }
    if (feedbackKeyword.value) params.keyword = feedbackKeyword.value
    else if (detail.value?.module) params.module = detail.value.module

    const data = await fetchFeedbackPage(params)
    candidateRows.value = data?.records || []
  } catch (error) {
    message.error(error.message || '加载反馈列表失败')
  } finally {
    candidateLoading.value = false
  }
}

/**
 * 提交关联
 *
 * <p>后端对重复关联是幂等的，所以即便用户误选了已关联的也不会出问题。
 */
async function submitLink() {
  if (selectedFeedbackIds.value.length === 0) {
    message.warning('请至少选择一条反馈')
    return
  }

  linkSubmitting.value = true
  try {
    await linkFeedbacks(route.params.id, {
      feedbackIds: selectedFeedbackIds.value,
      remark: linkRemark.value || null
    })
    message.success(`已关联 ${selectedFeedbackIds.value.length} 条反馈`)
    showLinkModal.value = false
    await loadDetail()
  } catch (error) {
    message.error(error.message || '关联失败')
  } finally {
    linkSubmitting.value = false
  }
}

/**
 * 解除关联
 *
 * @param {object} row 反馈行
 */
async function handleUnlink(row) {
  try {
    await unlinkFeedback(route.params.id, row.id)
    message.success('已解除关联')
    await loadDetail()
  } catch (error) {
    message.error(error.message || '解除关联失败')
  }
}

// ==================== AI 起草需求草稿（作用点 C） ====================

/** 起草中 */
const aiDrafting = ref(false)

/** 草稿弹窗 */
const showDraftModal = ref(false)

/** AI 草稿内容 */
const aiDraft = ref({ draftContent: '', acceptanceCriteria: '' })

/**
 * 调用 AI 起草需求草稿
 *
 * <p><b>这里刻意不写入正式字段</b>：草稿只在弹窗里展示，
 * PM 可以复制、参考、改写，但必须经过自己的判断才会落到正式需求里。
 * 接口返回 503 时给出明确的降级提示，保证 AI 不可用时不阻塞工作。
 */
async function handleAiDraft() {
  aiDrafting.value = true
  try {
    const data = await draftRequirement(route.params.id)
    aiDraft.value = {
      draftContent: data?.draftContent || '',
      acceptanceCriteria: data?.acceptanceCriteria || ''
    }
    showDraftModal.value = true
  } catch (error) {
    if (error.code === 503) {
      message.warning(
        'AI 服务暂不可用，请手工填写需求说明与验收条件。'
      )
    } else {
      message.error(error.message || 'AI 起草失败')
    }
  } finally {
    aiDrafting.value = false
  }
}

/**
 * 复制草稿文本到剪贴板
 *
 * @param {string} text 待复制文本
 */
async function copyText(text) {
  if (!text) {
    message.warning('没有可复制的内容')
    return
  }
  try {
    await navigator.clipboard.writeText(text)
    message.success('已复制')
  } catch (e) {
    message.warning('当前浏览器不支持自动复制，请手动选中复制')
  }
}

/** 表格行 key */
function rowKey(row) {
  return row.id
}

/** 时间格式化 */
function fmt(value) {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '—'
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
          <n-button @click="router.push({ name: 'issue-list' })">
            返回列表
          </n-button>
        </template>
      </n-result>
    </n-card>

    <template v-else-if="detail">
      <!-- ==================== 头部信息 ==================== -->
      <n-card class="app-card" style="margin-bottom: 16px">
        <template #header>
          <n-space align="center" :size="10">
            <n-text strong style="font-size: 16px">{{ detail.title }}</n-text>
            <n-text class="text-mono" depth="3" style="font-size: 12px">
              {{ detail.issueNo }}
            </n-text>
            <n-tag
              size="small"
              :bordered="false"
              :type="detail.sourceType === 'AI' ? 'info' : 'default'"
            >
              {{ detail.sourceType === 'AI' ? 'AI 生成' : '手工创建' }}
            </n-tag>
            <n-tag v-if="isModified" size="small" :bordered="false" type="warning">
              PM 已修改 AI 建议
            </n-tag>
            <n-tag
              v-else-if="fromAi"
              size="small"
              :bordered="false"
              type="success"
            >
              AI 建议被原样采纳
            </n-tag>
          </n-space>
        </template>

        <n-descriptions bordered :column="3" label-placement="left">
          <n-descriptions-item label="功能模块">
            {{ detail.moduleLabel || moduleLabel(detail.module) }}
          </n-descriptions-item>
          <n-descriptions-item label="确认人">
            {{ detail.confirmedByName || '—' }}
          </n-descriptions-item>
          <n-descriptions-item label="确认时间">
            {{ fmt(detail.confirmTime) }}
          </n-descriptions-item>
        </n-descriptions>
      </n-card>

      <!-- ==================== AI 建议 vs PM 定稿 ==================== -->
      <n-card class="app-card" style="margin-bottom: 16px">
        <template #header>
          <n-space align="center" :size="8">
            <n-text strong style="font-size: 15px">需求说明对照</n-text>
            <n-text depth="3" style="font-size: 12px">
            左侧为 AI 原话（不可修改），右侧为产品经理定稿
          </n-text>
          </n-space>
        </template>

        <template #header-extra>
          <n-button
            v-if="canEdit"
            size="small"
            secondary
            :loading="aiDrafting"
            @click="handleAiDraft"
          >
            AI 起草需求草稿
          </n-button>
        </template>

        <!-- 手工创建：无 AI 原稿 -->
        <div v-if="!fromAi">
          <n-alert type="default" :bordered="false">
            该问题由产品经理手工创建，没有 AI 生成的需求草稿。
          </n-alert>
          <div class="content-box" style="margin-top: 12px">
            {{ detail.finalContent || detail.description || '（暂无需求说明）' }}
          </div>
        </div>

        <!-- AI 来源：并排对照 -->
        <div v-else class="compare-panels">
          <div class="panel">
            <div class="panel-head">
              <n-text strong>AI 生成的草稿</n-text>
              <n-tag size="tiny" :bordered="false" type="info">只读</n-tag>
            </div>
            <div class="content-box ai-box">
              {{ detail.aiDraftContent || '（AI 未返回内容）' }}
            </div>
          </div>

          <div class="panel">
            <div class="panel-head">
              <n-text strong>产品经理定稿</n-text>
              <n-tag v-if="isModified" size="tiny" :bordered="false" type="warning">
                已修改
              </n-tag>
              <n-tag v-else size="tiny" :bordered="false" type="success">
                与原稿一致
              </n-tag>
            </div>
            <div class="content-box final-box">
              {{ detail.finalContent || '（尚未定稿）' }}
            </div>
          </div>
        </div>

        <n-divider />

        <div>
          <n-text depth="3" style="font-size: 12px">验收条件</n-text>
          <div class="content-box" style="margin-top: 6px">
            {{ detail.acceptanceCriteria || '（未填写）' }}
          </div>
        </div>
      </n-card>

      <!-- ==================== 关联反馈原文 ==================== -->
      <n-card class="app-card">
        <template #header>
          <n-space align="center" :size="10">
            <n-text strong style="font-size: 15px">关联的用户反馈</n-text>
            <n-text depth="3" style="font-size: 12px">
              {{ linkedFeedbacks.length }} 条
            </n-text>
          </n-space>
        </template>

        <template #header-extra>
          <n-button v-if="canEdit" size="small" secondary @click="openLinkModal">
            关联更多反馈
          </n-button>
        </template>

        <n-empty
          v-if="linkedFeedbacks.length === 0"
          description="还没有关联任何反馈"
          size="small"
        />

        <n-space v-else vertical :size="10" style="width: 100%">
          <n-card
            v-for="fb in linkedFeedbacks"
            :key="fb.id"
            size="small"
            class="linked-item"
          >
            <n-space align="center" :size="8">
              <n-text class="text-mono" depth="3" style="font-size: 12px">
                {{ fb.feedbackNo }}
              </n-text>
              <n-tag size="tiny" :bordered="false">{{ fb.moduleLabel }}</n-tag>
              <n-tag size="tiny" :bordered="false" type="info">
                {{ fb.statusLabel }}
              </n-tag>
            </n-space>

            <div class="linked-content">{{ fb.content }}</div>

            <n-space justify="space-between" align="center" style="margin-top: 8px">
              <n-text depth="3" style="font-size: 11px">
                {{ fb.createdByName }} · {{ fmt(fb.createTime) }}
              </n-text>

              <n-space :size="6">
                <n-button
                  size="tiny"
                  quaternary
                  @click="
                    router.push({ name: 'feedback-detail', params: { id: fb.id } })
                  "
                >
                  查看原文
                </n-button>
                <n-button
                  v-if="canEdit"
                  size="tiny"
                  quaternary
                  type="error"
                  @click="handleUnlink(fb)"
                >
                  解除关联
                </n-button>
              </n-space>
            </n-space>
          </n-card>
        </n-space>
      </n-card>

      <!-- ==================== 关联弹窗 ==================== -->
      <n-modal
        v-model:show="showLinkModal"
        preset="card"
        title="关联用户反馈"
        style="width: 880px"
      >
        <n-space vertical :size="12">
          <n-alert type="info" :bordered="false">
            默认展示同模块的反馈（未归类 / 已关联）。已关闭与已无效的反馈不再参与归并。
          </n-alert>

          <n-space>
            <n-input
              v-model:value="feedbackKeyword"
              placeholder="搜索反馈原文或编号"
              clearable
              style="width: 300px"
              @keyup.enter="loadCandidates"
            />
            <n-button @click="loadCandidates">搜索</n-button>
          </n-space>

          <n-data-table
            :columns="candidateColumns"
            :data="candidateRows"
            :loading="candidateLoading"
            :row-key="rowKey"
            :bordered="false"
            size="small"
            max-height="380px"
            @update:checked-row-keys="(keys) => (selectedFeedbackIds = keys)"
          />
        </n-space>

        <template #footer>
          <n-space justify="space-between" align="center" style="width: 100%">
            <n-text depth="3" style="font-size: 12px">
              已选择 {{ selectedFeedbackIds.length }} 条
            </n-text>
            <n-space>
              <n-button quaternary @click="showLinkModal = false">取消</n-button>
              <n-button
                type="primary"
                :loading="linkSubmitting"
                :disabled="selectedFeedbackIds.length === 0"
                @click="submitLink"
              >
                确认关联
              </n-button>
            </n-space>
          </n-space>
        </template>
      </n-modal>

      <!-- ==================== AI 草稿弹窗 ==================== -->
      <n-modal
        v-model:show="showDraftModal"
        preset="card"
        title="AI 起草的需求草稿"
        style="width: 760px"
      >
        <n-space vertical :size="14">
          <n-alert type="warning" :bordered="false">
            以下内容由 AI 生成，<b>仅为草稿</b>，尚未写入正式需求。
            请自行判断是否采纳、如何修改；AI 不会给出优先级。
          </n-alert>

          <div>
            <n-space justify="space-between" align="center">
              <n-text depth="3" style="font-size: 12px">需求说明草稿</n-text>
              <n-button size="tiny" quaternary @click="copyText(aiDraft.draftContent)">
                复制
              </n-button>
            </n-space>
            <div class="content-box" style="margin-top: 6px">
              {{ aiDraft.draftContent || '（AI 未返回内容）' }}
            </div>
          </div>

          <div>
            <n-space justify="space-between" align="center">
              <n-text depth="3" style="font-size: 12px">验收条件草稿</n-text>
              <n-button
                size="tiny"
                quaternary
                @click="copyText(aiDraft.acceptanceCriteria)"
              >
                复制
              </n-button>
            </n-space>
            <div class="content-box" style="margin-top: 6px">
              {{ aiDraft.acceptanceCriteria || '（AI 未返回内容）' }}
            </div>
          </div>
        </n-space>

        <template #footer>
          <n-space justify="end">
            <n-button quaternary @click="showDraftModal = false">关闭</n-button>
          </n-space>
        </template>
      </n-modal>
    </template>
  </div>
</template>

<style scoped>
/* 并排对照：两栏等宽，窄屏自动堆叠 */
.compare-panels {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

@media (max-width: 900px) {
  .compare-panels {
    grid-template-columns: 1fr;
  }
}

.panel-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.content-box {
  padding: 14px 16px;
  border: 1px solid var(--c-border-soft);
  border-radius: 6px;
  background-color: rgba(245, 247, 255, 0.72);
  color: var(--c-text-1);
  font-size: 13px;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
  min-height: 90px;
}

/* AI 原稿：左侧主色竖条，强调"原始材料"语义 */
.ai-box {
  border-left: 3px solid var(--c-primary);
  background-color: rgba(255, 255, 255, 0.86);
}

/* PM 定稿：绿色竖条表示"这是最终结论" */
.final-box {
  border-left: 3px solid var(--c-success);
}

.linked-item {
  background-color: rgba(245, 247, 255, 0.72);
  border: 1px solid var(--c-border-soft);
}

.linked-content {
  margin-top: 8px;
  color: var(--c-text-1);
  font-size: 13px;
  line-height: 1.7;
}
</style>
