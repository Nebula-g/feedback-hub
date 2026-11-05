<script setup>
/**
 * 反馈详情页
 *
 * <p><b>本页最重要的设计：原文只读</b>
 * <p>页面上没有任何「编辑原文」入口，原文以引用块的形式呈现，
 * 视觉上就传达"这是原始材料，不可改动"的语义。
 * 下方提供的是"变更归类状态"（属于加工动作，非修改原文），
 * 且仅限产品经理操作。
 *
 * <p><b>可回查设计</b>：展示编号、来源、客户标识、录入人、录入时间，
 * 任何一条反馈都能追溯到它是谁在什么时候从哪来的。
 */
import { ref, onMounted, computed, h } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  NCard,
  NSpace,
  NButton,
  NTag,
  NText,
  NDescriptions,
  NDescriptionsItem,
  NImage,
  NImageGroup,
  NResult,
  NSpin,
  NSelect,
  NInput,
  NModal,
  useMessage,
  useDialog
} from 'naive-ui'

import { fetchFeedbackDetail, updateFeedbackStatus } from '@/api/feedback'
import {
  fetchMessages,
  sendMessage,
  markMessagesRead
} from '@/api/message'
import {
  FEEDBACK_STATUSES,
  moduleLabel,
  sourceLabel
} from '@/constants/enums'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const dialog = useDialog()
const userStore = useUserStore()

/** 反馈详情数据 */
const detail = ref(null)

/** 加载中 */
const loading = ref(false)

/** 错误信息 */
const loadError = ref('')

/** 状态变更弹窗是否显示 */
const showStatusModal = ref(false)

/** 目标状态 */
const targetStatus = ref(null)

/** 变更说明 */
const statusRemark = ref('')

/** 状态变更提交中 */
const statusSubmitting = ref(false)

/** 是否为产品经理 */
const canChangeStatus = computed(
  () => userStore.role === 'PM' || userStore.role === 'ADMIN'
)

// ==================== 评论区 ====================

/** 评论时间线（正序） */
const comments = ref([])

/** 评论输入框内容 */
const commentDraft = ref('')

/** 评论提交中 */
const commentSubmitting = ref(false)

/**
 * 加载评论时间线，并顺手把该对象的未读评论标记为已读
 *
 * <p>为什么在加载详情时一起做：打开详情页就等于"看过了"，
 * 顶栏消息角标应当随之消掉。
 */
async function loadComments() {
  try {
    const [list] = await Promise.all([
      fetchMessages('FEEDBACK', route.params.id),
      markMessagesRead('FEEDBACK', route.params.id).catch(() => {})
    ])
    comments.value = Array.isArray(list) ? list : []
  } catch {
    // 评论加载失败不阻塞详情展示
    comments.value = []
  }
}

/**
 * 发表评论
 */
async function submitComment() {
  const content = commentDraft.value.trim()
  if (!content) {
    message.warning('请输入评论内容')
    return
  }
  commentSubmitting.value = true
  try {
    await sendMessage('FEEDBACK', route.params.id, content)
    commentDraft.value = ''
    // 只刷新时间线即可；未读角标等下一轮轮询同步
    comments.value = await fetchMessages('FEEDBACK', route.params.id)
    message.success('评论已发送')
  } catch (error) {
    message.error(error.message || '评论发送失败')
  } finally {
    commentSubmitting.value = false
  }
}

/**
 * 评论时间格式化
 *
 * @param {string} value 时间串
 * @returns {string} MM-DD HH:mm
 */
function commentTime(value) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(5, 16)
}

/**
 * 可选的目标状态
 *
 * <p>与后端 FeedbackStatus 枚举的允许跳转保持一致：
 *   NEW    → LINKED / IGNORED
 *   LINKED → CLOSED / NEW
 *   CLOSED → 无
 *   IGNORED → 无
 * 已关闭和已无效都是终态，不允许再流转。
 */
const nextStatusOptions = computed(() => {
  const current = detail.value?.status
  const allowMap = {
    NEW: ['LINKED', 'IGNORED'],
    LINKED: ['CLOSED', 'NEW'],
    CLOSED: [],
    IGNORED: []
  }
  const allow = allowMap[current] || []
  return FEEDBACK_STATUSES.filter((item) => allow.includes(item.value))
})

/** 是否已到达终态（无法继续流转） */
const isTerminal = computed(() => {
  const current = detail.value?.status
  return current === 'CLOSED' || current === 'IGNORED'
})

/**
 * 加载详情
 */
async function loadDetail() {
  loading.value = true
  loadError.value = ''

  try {
    detail.value = await fetchFeedbackDetail(route.params.id)
  } catch (error) {
    detail.value = null
    loadError.value =
      error.code === 404
        ? '该反馈不存在或已被删除'
        : error.message || '加载反馈详情失败'
  } finally {
    loading.value = false
  }
}

/**
 * 打开状态变更弹窗
 */
function openStatusModal() {
  targetStatus.value = null
  statusRemark.value = ''
  showStatusModal.value = true
}

/**
 * 提交状态变更
 */
async function submitStatusChange() {
  if (!targetStatus.value) {
    message.warning('请选择目标状态')
    return
  }
  // 置为无效时必须填写说明——与后端校验规则一致
  if (targetStatus.value === 'IGNORED' && !statusRemark.value.trim()) {
    message.warning('标记为无效时必须填写原因')
    return
  }

  statusSubmitting.value = true
  try {
    await updateFeedbackStatus(route.params.id, {
      status: targetStatus.value,
      remark: statusRemark.value || null
    })
    message.success('状态已更新')
    showStatusModal.value = false
    await loadDetail()
    // 状态变了，录入人可能想看到最新讨论，评论区同步刷新
    await loadComments()
  } catch (error) {
    // 409 表示状态机不允许此次跳转
    message.error(error.message || '状态变更失败')
  } finally {
    statusSubmitting.value = false
  }
}

onMounted(async () => {
  await loadDetail()
  await loadComments()
})
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
          <n-button @click="router.push({ name: 'feedback-list' })">
            返回列表
          </n-button>
        </template>
      </n-result>
    </n-card>

    <!-- 成功态 -->
    <template v-else-if="detail">
      <!-- ==================== 反馈原文（只读） ==================== -->
      <n-card class="app-card" style="margin-bottom: 16px">
        <template #header>
          <n-space align="center" :size="10">
            <n-text strong style="font-size: 15px">反馈原文</n-text>
            <n-text class="text-mono" depth="3" style="font-size: 12px">
              {{ detail.feedbackNo }}
            </n-text>
            <n-tag size="small" :bordered="false">{{ detail.statusLabel }}</n-tag>
          </n-space>
        </template>

        <template #header-extra>
          <n-button
            v-if="canChangeStatus && !isTerminal"
            size="small"
            secondary
            @click="openStatusModal"
          >
            变更归类状态
          </n-button>
        </template>

        <!-- 引用块：视觉上强调这是原始、不可修改的材料 -->
        <blockquote class="feedback-original">
          {{ detail.content }}
        </blockquote>

        <n-alert
          v-if="isTerminal"
          type="default"
          :bordered="false"
          style="margin-top: 12px"
        >
          该反馈已处于终态（{{ detail.statusLabel }}），不可再变更归类状态。
        </n-alert>
      </n-card>

      <!-- ==================== 基本信息 ==================== -->
      <n-card class="app-card" style="margin-bottom: 16px">
        <template #header>
          <n-text strong style="font-size: 15px">基本信息</n-text>
        </template>

        <n-descriptions bordered :column="3" label-placement="left">
          <n-descriptions-item label="功能模块">
            {{ detail.moduleLabel || moduleLabel(detail.module) }}
          </n-descriptions-item>
          <n-descriptions-item label="来源渠道">
            {{ detail.sourceLabel || sourceLabel(detail.source) }}
          </n-descriptions-item>
          <n-descriptions-item label="客户标识">
            <span class="text-mono">{{ detail.customerTag || '-' }}</span>
          </n-descriptions-item>
          <n-descriptions-item label="录入人">
            {{ detail.createdByName || '-' }}
          </n-descriptions-item>
          <n-descriptions-item label="录入时间">
            {{ (detail.createTime || '').replace('T', ' ').slice(0, 19) || '-' }}
          </n-descriptions-item>
          <n-descriptions-item label="归类状态">
            {{ detail.statusLabel }}
          </n-descriptions-item>
        </n-descriptions>
      </n-card>

      <!-- ==================== 凭证图片 ==================== -->
      <n-card class="app-card">
        <template #header>
          <n-text strong style="font-size: 15px">
            凭证截图
            <n-text depth="3" style="font-weight: 400">
              （{{ detail.imageUrls?.length || 0 }} 张）
            </n-text>
          </n-text>
        </template>

        <n-image-group v-if="detail.imageUrls && detail.imageUrls.length > 0">
          <n-space>
            <n-image
              v-for="(url, index) in detail.imageUrls"
              :key="index"
              :src="url"
              width="120"
              height="120"
              object-fit="cover"
              style="border-radius: 6px; border: 1px solid var(--c-border-soft)"
            />
          </n-space>
        </n-image-group>

        <n-empty v-else description="该反馈没有上传凭证截图" size="small" />
      </n-card>

      <!-- ==================== 评论区 ==================== -->
      <n-card class="app-card" style="margin-top: 16px">
        <template #header>
          <n-text strong style="font-size: 15px">
            沟通讨论
            <n-text depth="3" style="font-weight: 400">
              （{{ comments.length }} 条）
            </n-text>
          </n-text>
        </template>

        <!-- 空态 -->
        <n-empty
          v-if="comments.length === 0"
          description="还没有讨论，说点什么吧"
          size="small"
        />

        <!-- 时间线 -->
        <div v-else class="comment-list">
          <div
            v-for="item in comments"
            :key="item.id"
            class="comment"
            :class="{ 'comment--mine': item.mine }"
          >
            <n-avatar round :size="30" class="comment__avatar">
              {{ (item.senderName || '?').charAt(0) }}
            </n-avatar>
            <div class="comment__bubble">
              <div class="comment__meta">
                <span class="comment__name">{{ item.senderName }}</span>
                <span class="comment__time">{{ commentTime(item.createTime) }}</span>
              </div>
              <div class="comment__content">{{ item.content }}</div>
            </div>
          </div>
        </div>

        <!-- 输入区 -->
        <div class="comment-editor">
          <n-input
            v-model:value="commentDraft"
            type="textarea"
            :rows="2"
            maxlength="1000"
            show-count
            placeholder="参与讨论…（Ctrl + Enter 发送）"
            @keydown.ctrl.enter.prevent="submitComment"
          />
          <n-button
            type="primary"
            :loading="commentSubmitting"
            :disabled="!commentDraft.trim()"
            @click="submitComment"
          >
            发送
          </n-button>
        </div>
      </n-card>
    </template>

    <!-- ==================== 状态变更弹窗 ==================== -->
    <n-modal
      v-model:show="showStatusModal"
      preset="card"
      title="变更归类状态"
      style="width: 480px"
    >
      <n-space vertical :size="16">
        <div>
          <n-text depth="3" style="font-size: 12px">当前状态</n-text>
          <div style="margin-top: 4px">
            <n-tag :bordered="false">{{ detail?.statusLabel }}</n-tag>
          </div>
        </div>

        <div>
          <n-text depth="3" style="font-size: 12px">目标状态</n-text>
          <n-select
            v-model:value="targetStatus"
            :options="nextStatusOptions"
            placeholder="请选择目标状态"
            style="margin-top: 4px"
          />
        </div>

        <div>
          <n-text depth="3" style="font-size: 12px">
            变更说明
            <span v-if="targetStatus === 'IGNORED'" style="color: var(--c-danger)">（必填）</span>
            <span v-else>（选填）</span>
          </n-text>
          <n-input
            v-model:value="statusRemark"
            type="textarea"
            :rows="3"
            placeholder="说明本次变更的原因"
            style="margin-top: 4px"
          />
        </div>
      </n-space>

      <template #footer>
        <n-space justify="end">
          <n-button quaternary @click="showStatusModal = false">取消</n-button>
          <n-button
            type="primary"
            :loading="statusSubmitting"
            @click="submitStatusChange"
          >
            确认变更
          </n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<style scoped>
/* 原文引用块：左侧主色竖条 + 浅底，与可编辑区域形成明显区分 */
.feedback-original {
  margin: 0;
  padding: 16px 18px;
  border-left: 3px solid var(--c-primary);
  border-radius: 0 6px 6px 0;
  background-color: rgba(245, 247, 255, 0.72);
  color: var(--c-text-1);
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

/* ==================== 评论区 ==================== */
.comment-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-bottom: 16px;
}

.comment {
  display: flex;
  gap: 10px;
}

/* 自己的评论头像换主题色，一眼区分发言归属 */
.comment--mine .comment__avatar {
  --n-color: var(--c-primary);
  color: #fff;
  background: var(--c-primary);
}

.comment__avatar {
  flex: none;
  background: rgba(122, 136, 190, 0.18);
  color: #4a56c7;
  font-weight: 600;
}

.comment__bubble {
  flex: 1;
  min-width: 0;
  padding: 10px 14px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid var(--c-border-soft);
  border-radius: 4px 14px 14px 14px;
}

.comment__meta {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.comment__name {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--c-text-1);
}

.comment__time {
  font-size: 11px;
  color: var(--c-text-3);
}

.comment__content {
  margin-top: 4px;
  font-size: 13.5px;
  line-height: 1.65;
  color: var(--c-text-2);
  white-space: pre-wrap;
  word-break: break-word;
}

.comment-editor {
  display: flex;
  align-items: flex-end;
  gap: 10px;
}

.comment-editor .n-button {
  flex: none;
}
</style>
