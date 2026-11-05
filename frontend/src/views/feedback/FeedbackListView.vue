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
import { ref, reactive, computed, onMounted, watch, h } from 'vue'
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
  NResult
} from 'naive-ui'

import FilterChips from '@/components/FilterChips.vue'
import EmptyState from '@/components/EmptyState.vue'
import SkeletonList from '@/components/SkeletonList.vue'
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

/** 各状态条数（胶囊上的计数，null 表示还在加载） */
const statusCounts = ref(null)

/** 首次加载：只有第一次进页面才铺骨架屏，翻页/筛选不铺，避免列表"闪一下" */
const firstLoad = ref(true)

/**
 * 加载各状态的条数
 *
 * <p>列表接口只返回当前筛选下的 total，给不出"待关联 12 条"这种分布。
 * 这里用 4 个 pageSize=1 的轻查询换 total，代价是 4 个小请求，
 * 换来的是用户不用点进去就知道哪个状态有积压——对筛选胶囊来说这是核心信息。
 *
 * <p>计数跟随当前的关键词/模块/来源筛选，否则会出现
 * "筛了模块 A，胶囊上却显示全库条数"的错位。
 */
async function loadStatusCounts() {
  const base = { pageNo: 1, pageSize: 1 }
  if (query.module) base.module = query.module
  if (query.source) base.source = query.source
  if (query.keyword) base.keyword = query.keyword

  const results = await Promise.allSettled(
    FEEDBACK_STATUSES.map((item) =>
      fetchFeedbackPage({ ...base, status: item.value })
    )
  )

  const counts = {}
  results.forEach((result, index) => {
    const status = FEEDBACK_STATUSES[index].value
    counts[status] = result.status === 'fulfilled' ? result.value?.total || 0 : null
  })
  statusCounts.value = counts
}

/** 胶囊选项：把状态枚举与条数拼起来 */
const statusOptions = computed(() =>
  FEEDBACK_STATUSES.map((item) => ({
    value: item.value,
    label: item.label,
    count: statusCounts.value?.[item.value] ?? null
  }))
)

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
    firstLoad.value = false
  }

  // 计数独立加载：失败也不该让列表进失败态，胶囊最多不显示数字
  loadStatusCounts()
}

/** 点击搜索 */
function handleSearch() {
  query.pageNo = 1
  filtered.value = true
  loadData()
}

/**
 * 切换状态胶囊
 *
 * <p>胶囊是"点一下就生效"的控件，不做成"点查询才生效"——
 * 那会让用户怀疑自己点没点上。翻页回到第 1 页。
 *
 * @param {string|null} value 目标状态
 */
function handleStatusChange(value) {
  query.status = value
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
      <!-- 状态胶囊：一行铺开，带各状态条数，点一下即生效 -->
      <div class="status-row">
        <span class="status-row__label">归类状态</span>
        <FilterChips
          :model-value="query.status"
          :options="statusOptions"
          :all-count="total"
          all-label="全部"
          @change="handleStatusChange"
        />
      </div>

      <n-space :wrap="true" align="center" style="margin-top: 14px">
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
        <!-- 首屏：骨架屏与真实行的结构一致，数据到达时布局不跳 -->
        <SkeletonList v-if="loading && firstLoad" :rows="6" />

        <EmptyState
          v-else-if="!loading && rows.length === 0"
          :title="filtered ? '没有符合筛选条件的反馈' : '还没有任何反馈记录'"
          :description="
            filtered
              ? '试试切回「全部」，或放宽模块、来源条件'
              : '客户反馈入库后，会在这里按时间倒序出现'
          "
        >
          <n-button v-if="filtered" size="small" @click="handleReset">清空筛选条件</n-button>
          <n-button
            v-else-if="userStore.isCs || userStore.role === 'ADMIN'"
            size="small"
            type="primary"
            @click="router.push({ name: 'feedback-create' })"
          >
            录入第一条反馈
          </n-button>
        </EmptyState>

        <template v-else>
          <n-data-table
            :columns="columns"
            :data="rows"
            :loading="loading"
            :bordered="false"
            :single-line="false"
            size="small"
          />

          <div class="pagination-wrapper">
            <n-pagination
              v-model:page="query.pageNo"
              :page-count="Math.max(1, Math.ceil(total / query.pageSize))"
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
      </template>
    </n-card>
  </div>
</template>

<style scoped>
.filter-card {
  margin-bottom: 16px;
}

/* 状态胶囊整行：标签 + 胶囊，窄屏时标签换到胶囊上方 */
.status-row {
  display: flex;
  align-items: center;
  gap: 14px;
}

.status-row__label {
  flex: none;
  font-size: 12.5px;
  color: var(--c-text-3);
}

@media (max-width: 720px) {
  .status-row {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
  }
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
