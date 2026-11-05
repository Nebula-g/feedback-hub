<script setup>
/**
 * 反馈列表页
 *
 * <p><b>六态反馈的落点</b>：
 * <ul>
 *   <li><b>加载态</b>：表格 loading</li>
 *   <li><b>空态</b>：无筛选结果时展示引导，区分「库里没数据」和「筛选没匹配」</li>
 *   <li><b>成功态</b>：正常渲染列表</li>
 *   <li><b>失败态</b>：接口报错时展示错误卡片 + 重试按钮，而不是空白</li>
 * </ul>
 *
 * <p><b>只读设计</b>：本页只有「查看详情」，没有任何编辑/删除入口。
 * 反馈原文一旦入库不可修改，这是整个项目可信度的基础。
 */
import { ref, reactive, onMounted, watch, h } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  NCard,
  NSpace,
  NButton,
  NDataTable,
  NPagination,
  NSelect,
  NInput,
  NTag,
  NEmpty,
  NResult,
  NText,
  NIcon
} from 'naive-ui'

import { fetchFeedbackPage } from '@/api/feedback'
import {
  FEEDBACK_MODULES,
  FEEDBACK_SOURCES,
  FEEDBACK_STATUSES,
  moduleLabel,
  sourceLabel,
  feedbackStatusLabel
} from '@/constants/enums'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// ==================== 状态 ====================

/** 表格数据 */
const rows = ref([])

/** 总记录数 */
const total = ref(0)

/** 是否加载中 */
const loading = ref(false)

/** 接口错误信息，非空表示处于失败态 */
const loadError = ref('')

/** 查询条件 */
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  module: null,
  status: null,
  source: null,
  // 顶栏全局搜索会带 keyword 跳到这里，初始值从地址栏取
  keyword: typeof route.query.keyword === 'string' ? route.query.keyword : ''
})

/** 是否执行过至少一次筛选（用于区分「无数据」与「筛选无结果」） */
const filtered = ref(!!query.keyword)

// ==================== 表格列定义 ====================

/** 状态标签配色 */
function statusTagType(status) {
  const map = {
    NEW: 'default',
    LINKED: 'info',
    CLOSED: 'success',
    IGNORED: 'warning'
  }
  return map[status] || 'default'
}

const columns = [
  {
    title: '编号',
    key: 'feedbackNo',
    width: 132,
    render: (row) =>
      h('span', { class: 'text-mono', style: 'color:var(--c-text-2); font-size:12px' }, row.feedbackNo)
  },
  {
    title: '反馈原文',
    key: 'content',
    minWidth: 200,
    ellipsis: { tooltip: true }
  },
  {
    title: '功能模块',
    key: 'module',
    width: 100,
    render: (row) => row.moduleLabel || moduleLabel(row.module)
  },
  {
    title: '来源',
    key: 'source',
    width: 92,
    render: (row) => row.sourceLabel || sourceLabel(row.source)
  },
  {
    title: '客户',
    key: 'customerTag',
    width: 88,
    render: (row) =>
      h('span', { class: 'text-mono', style: 'color:var(--c-text-3)' }, row.customerTag || '-')
  },
  {
    title: '状态',
    key: 'status',
    width: 84,
    render: (row) =>
      h(
        NTag,
        { size: 'small', type: statusTagType(row.status), bordered: false },
        { default: () => row.statusLabel || feedbackStatusLabel(row.status) }
      )
  },
  {
    title: '录入人',
    key: 'createdByName',
    width: 96,
    ellipsis: { tooltip: true }
  },
  {
    title: '录入时间',
    key: 'createTime',
    width: 120,
    render: (row) => formatTime(row.createTime)
  },
  {
    title: '操作',
    key: 'actions',
    width: 82,
    render: (row) =>
      h(
        NButton,
        {
          size: 'small',
          quaternary: true,
          type: 'primary',
          onClick: () => router.push({ name: 'feedback-detail', params: { id: row.id } })
        },
        { default: () => '查看详情' }
      )
  }
]

/**
 * 格式化时间
 *
 * <p>后端返回 LocalDateTime，序列化为 "2026-10-26T15:40:08"。
 * 列表里省掉年份和秒：同一屏内年份基本一致，秒对排查问题没意义，
 * 省下来的宽度留给反馈原文。
 *
 * @param {string} value 时间字符串
 * @returns {string} 格式化后的时间（MM-DD HH:mm）
 */
function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(5, 16)
}

// ==================== 数据加载 ====================

/**
 * 加载反馈列表
 *
 * <p>失败时设置 loadError 让页面进入失败态，
 * 而不是让表格空着——空白会被误认为"没有数据"。
 */
async function loadData() {
  loading.value = true
  loadError.value = ''

  try {
    // 过滤掉空值，避免把 null 当查询条件传给后端
    const params = {
      pageNo: query.pageNo,
      pageSize: query.pageSize
    }
    if (query.module) params.module = query.module
    if (query.status) params.status = query.status
    if (query.source) params.source = query.source
    if (query.keyword) params.keyword = query.keyword

    const data = await fetchFeedbackPage(params)
    rows.value = data?.records || []
    total.value = data?.total || 0
  } catch (error) {
    rows.value = []
    total.value = 0
    loadError.value = error.message || '加载反馈列表失败'
  } finally {
    loading.value = false
  }
}

/** 点击搜索 */
function handleSearch() {
  query.pageNo = 1
  filtered.value = true
  loadData()
}

/** 重置筛选条件 */
function handleReset() {
  query.module = null
  query.status = null
  query.source = null
  query.keyword = ''
  query.pageNo = 1
  filtered.value = false
  loadData()
}

/** 切换页码 */
function handlePageChange(page) {
  query.pageNo = page
  loadData()
}

// 进入页面即加载
onMounted(loadData)

/**
 * 已经在列表页时再次用顶栏搜索
 *
 * <p>同一个路由只变 query，组件不会重新挂载，
 * 必须显式响应参数变化，否则用户会觉得"搜索没反应"。
 */
watch(
  () => route.query.keyword,
  (value) => {
    const next = typeof value === 'string' ? value : ''
    if (next === query.keyword) return
    query.keyword = next
    query.pageNo = 1
    filtered.value = !!next
    loadData()
  }
)
</script>

<template>
  <div class="page-container">
    <!-- ==================== 筛选区 ==================== -->
    <n-card class="app-card filter-card">
      <n-space :wrap="true" align="center">
        <n-input
          v-model:value="query.keyword"
          placeholder="搜索反馈原文或编号"
          clearable
          style="width: 240px"
          @keyup.enter="handleSearch"
        />

        <n-select
          v-model:value="query.module"
          placeholder="功能模块"
          clearable
          :options="FEEDBACK_MODULES"
          style="width: 150px"
        />

        <n-select
          v-model:value="query.status"
          placeholder="归类状态"
          clearable
          :options="FEEDBACK_STATUSES"
          style="width: 130px"
        />

        <n-select
          v-model:value="query.source"
          placeholder="来源渠道"
          clearable
          :options="FEEDBACK_SOURCES"
          style="width: 130px"
        />

        <n-button type="primary" @click="handleSearch">查询</n-button>
        <n-button quaternary @click="handleReset">重置</n-button>
      </n-space>
    </n-card>

    <!-- ==================== 列表区 ==================== -->
    <n-card class="app-card table-card">
      <!-- 失败态：明确告知 + 提供重试，避免白屏被误读为"没数据" -->
      <n-result
        v-if="loadError"
        status="error"
        title="加载失败"
        :description="loadError"
      >
        <template #footer>
          <n-button type="primary" @click="loadData">重新加载</n-button>
        </template>
      </n-result>

      <template v-else>
        <n-data-table
          :columns="columns"
          :data="rows"
          :loading="loading"
          :bordered="false"
          :single-line="false"
          size="small"
        />

        <!-- 空态：区分"筛选无结果"与"库里无数据"，两者处理动作不同 -->
        <n-empty
          v-if="!loading && rows.length === 0"
          :description="filtered ? '没有符合筛选条件的反馈，试试放宽条件' : '还没有任何反馈记录'"
          style="margin-top: 32px"
        >
          <template #extra>
            <n-button v-if="filtered" size="small" @click="handleReset">
              清空筛选条件
            </n-button>
            <n-button
              v-else-if="userStore.isCs || userStore.role === 'ADMIN'"
              size="small"
              type="primary"
              @click="router.push({ name: 'feedback-create' })"
            >
              录入第一条反馈
            </n-button>
          </template>
        </n-empty>

        <div class="pagination-wrapper">
          <n-pagination
            v-model:page="query.pageNo"
            :page-count="
              Math.max(1, Math.ceil(total / query.pageSize))
            "
            :page-size="query.pageSize"
            show-size-picker
            :page-sizes="[10, 20, 50]"
            @update:page="handlePageChange"
            @update:page-size="
              (size) => {
                query.pageSize = size
                query.pageNo = 1
                loadData()
              }
            "
          />
        </div>
      </template>
    </n-card>
  </div>
</template>

<style scoped>
.filter-card {
  margin-bottom: 16px;
}

.table-card {
  min-height: 420px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
