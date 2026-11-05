<script setup>
/**
 * 通知中心
 *
 * <p>顶栏通知面板只放最近 10 条，历史通知在这里分页翻看。
 * 保持列表页同款「筛选条 + 卡片 + 表格」的玻璃风骨架。
 */
import { h, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NDataTable, NEmpty, NPagination, NTag } from 'naive-ui'

import AppIcon from '@/components/AppIcon.vue'
import {
  fetchNotificationPage,
  markAllNotificationsRead
} from '@/api/notification'
import { useNoticeStore } from '@/stores/notification'

const router = useRouter()
const noticeStore = useNoticeStore()

/** 分页状态 */
const rows = ref([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(15)
const loading = ref(false)

/** 通知类型 → 中文标签（列表里用柔和 badge 呈现） */
const TYPE_LABELS = {
  TASK_ASSIGNED: '任务指派',
  TASK_STATUS: '任务状态',
  FEEDBACK_STATUS: '反馈状态',
  AI_GENERATED: 'AI 分析'
}

const columns = [
  {
    title: '类型',
    key: 'type',
    width: 96,
    render: (row) =>
      h(
        NTag,
        { size: 'small', bordered: false, round: true },
        { default: () => TYPE_LABELS[row.type] || row.type }
      )
  },
  { title: '标题', key: 'title', width: 120 },
  {
    title: '内容',
    key: 'content',
    minWidth: 260,
    ellipsis: { tooltip: true }
  },
  {
    title: '状态',
    key: 'isRead',
    width: 76,
    render: (row) =>
      h(
        'span',
        {
          style: `font-size:12px;color:${row.isRead ? '#9aa0b5' : '#4a56c7'}`
        },
        row.isRead ? '已读' : '未读'
      )
  },
  {
    title: '时间',
    key: 'createTime',
    width: 150,
    render: (row) => formatTime(row.createTime)
  },
  {
    title: '操作',
    key: 'actions',
    width: 88,
    render: (row) =>
      row.link
        ? h(
            NButton,
            {
              size: 'small',
              quaternary: true,
              type: 'primary',
              onClick: () => goDetail(row)
            },
            { default: () => '前往查看' }
          )
        : h('span', { style: 'color:#c3c7d4;font-size:12px' }, '—')
  }
]

/**
 * 跳转详情并顺手标记已读
 *
 * @param {object} row 通知行
 */
async function goDetail(row) {
  if (!row.isRead) {
    await noticeStore.markRead(row)
  }
  router.push(row.link)
}

/**
 * 加载当前页
 */
async function loadData() {
  loading.value = true
  try {
    const res = await fetchNotificationPage(pageNo.value, pageSize.value)
    rows.value = res?.records ?? []
    total.value = res?.total ?? 0
  } finally {
    loading.value = false
  }
}

/**
 * 全部已读：store 内先调接口再本地同步，最后刷新列表
 */
async function handleMarkAll() {
  await noticeStore.markAllRead()
  await loadData()
}

/**
 * 时间格式化：后端 LocalDateTime 序列化带 T，替换为空格
 *
 * @param {string} value 时间串
 * @returns {string} 可读时间
 */
function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

/**
 * 翻页
 *
 * @param {number} page 目标页码
 */
function handlePageChange(page) {
  pageNo.value = page
  loadData()
}

onMounted(loadData)
</script>

<template>
  <div class="page">
    <n-card class="table-card">
      <template #header>
        <div class="page-head">
          <span class="page-head__title">通知中心</span>
          <n-button
            size="small"
            secondary
            type="primary"
            :disabled="noticeStore.unreadCount === 0"
            @click="handleMarkAll"
          >
            <template #icon><AppIcon name="checkmark-done" :size="14" /></template>
            全部已读（{{ noticeStore.unreadCount }}）
          </n-button>
        </div>
      </template>

      <n-empty
        v-if="!loading && rows.length === 0"
        description="还没有任何通知"
        class="page-empty"
      />
      <n-data-table
        v-else
        :columns="columns"
        :data="rows"
        :loading="loading"
        :bordered="false"
        :single-line="false"
        size="small"
      />

      <div class="page-foot">
        <n-pagination
          :page="pageNo"
          :page-size="pageSize"
          :item-count="total"
          @update:page="handlePageChange"
        />
      </div>
    </n-card>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.table-card {
  flex: 1;
  overflow: auto;
}

.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.page-head__title {
  font-size: 15px;
  font-weight: 600;
}

.page-empty {
  padding: 48px 0;
}

.page-foot {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
</style>
