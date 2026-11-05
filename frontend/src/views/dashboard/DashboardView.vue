<script setup>
/**
 * 仪表盘
 *
 * <p><b>数据从哪来</b>：
 * 后端没有专门的统计接口，这里直接复用三个已有的分页接口
 * （反馈 / 问题 / 任务），取最近 200 条在前端做聚合。
 * 为什么不新增 /stats 接口：演示数据量只有几十条，
 * 前端聚合的成本远低于"再加一套 SQL + VO + 接口 + 权限"的维护成本；
 * 数据量真上来了再补聚合接口，届时这个页面的取数函数是唯一改动点。
 *
 * <p><b>为什么要 Promise.allSettled 而不是 all</b>：
 * 任务接口失败不该让整个仪表盘白屏——反馈卡片能出来就先给用户看，
 * 失败的那部分降级成 0 并在日志里留痕。
 */
import { computed, h, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NDataTable, NSelect, NButton, NSpin } from 'naive-ui'
import dayjs from 'dayjs'

import AppIcon from '@/components/AppIcon.vue'
import CountUp from '@/components/CountUp.vue'
import DonutChart from '@/components/charts/DonutChart.vue'
import BarChart from '@/components/charts/BarChart.vue'
import { fetchFeedbackPage } from '@/api/feedback'
import { fetchTaskPage } from '@/api/task'
import {
  FEEDBACK_STATUSES,
  TASK_STATUSES,
  moduleLabel,
  feedbackStatusLabel
} from '@/constants/enums'

const router = useRouter()

// ==================== 状态 ====================

/** 是否加载中 */
const loading = ref(false)

/** 加载失败信息 */
const loadError = ref('')

/** 反馈原始数据（最近 200 条） */
const feedbackRows = ref([])

/** 反馈总数（后端返回的 total，比本地条数更准） */
const feedbackTotal = ref(0)

/** 任务原始数据（最近 200 条） */
const taskRows = ref([])

/** 统计范围：7 / 30 / 0 天（0 表示全部） */
const range = ref(0)

/**
 * 默认展示「全部时间」而不是「近 7 天」
 *
 * <p>演示数据集中在几天里，默认按 7 天过滤会让四块卡几乎全是 0，
 * 第一眼看起来像"没数据"。先给全量，用户想收窄再自己选。
 */
const rangeOptions = [
  { label: '全部时间', value: 0 },
  { label: '近 30 天', value: 30 },
  { label: '近 7 天', value: 7 }
]

/** 状态 → 柔和色（低饱和马卡龙色系，白底可读） */
const FEEDBACK_STATUS_COLORS = {
  NEW: '#8b9bfb',
  LINKED: '#7cc2f8',
  CLOSED: '#8fd9c4',
  IGNORED: '#cbd2e6'
}

/** 任务状态 → 柔和色 */
const TASK_STATUS_COLORS = {
  TODO: '#b9c3fd',
  IN_PROGRESS: '#8b9bfb',
  PENDING_VERIFY: '#f6cf9a',
  DONE: '#8fd9c4',
  WONT_DO: '#d8dcea'
}

/** 表格状态徽标：柔和底色 + 同色系深字，比纯灰 tag 更易扫读 */
const FEEDBACK_STATUS_CHIPS = {
  NEW: { bg: 'rgba(139, 155, 251, 0.16)', color: '#5462d6' },
  LINKED: { bg: 'rgba(124, 194, 248, 0.2)', color: '#2f7fba' },
  CLOSED: { bg: 'rgba(143, 217, 196, 0.24)', color: '#2f8f6d' },
  IGNORED: { bg: 'rgba(203, 210, 230, 0.32)', color: '#6b7285' }
}

// ==================== 数据加载 ====================

/**
 * 加载仪表盘数据
 *
 * <p>三个接口并行，各自容错：任何一路失败都只影响对应的卡片。
 */
async function loadData() {
  loading.value = true
  loadError.value = ''

  const [feedbackRes, taskRes] = await Promise.allSettled([
    fetchFeedbackPage({ pageNo: 1, pageSize: 200 }),
    fetchTaskPage({ pageNo: 1, pageSize: 200 })
  ])

  if (feedbackRes.status === 'fulfilled') {
    feedbackRows.value = feedbackRes.value?.records || []
    feedbackTotal.value = feedbackRes.value?.total || 0
  } else {
    feedbackRows.value = []
    feedbackTotal.value = 0
    loadError.value = feedbackRes.reason?.message || '加载反馈数据失败'
  }

  taskRows.value = taskRes.status === 'fulfilled' ? taskRes.value?.records || [] : []
  loading.value = false
}

onMounted(loadData)

// ==================== 派生数据 ====================

/** 按统计范围过滤后的反馈 */
const scopedFeedbacks = computed(() => {
  if (!range.value) return feedbackRows.value
  const from = dayjs().subtract(range.value - 1, 'day').startOf('day')
  return feedbackRows.value.filter(
    (row) => row.createTime && dayjs(row.createTime).isAfter(from)
  )
})

/** 环形图：反馈状态分布 */
const statusSegments = computed(() =>
  FEEDBACK_STATUSES.map((item) => ({
    label: item.label,
    value: scopedFeedbacks.value.filter((row) => row.status === item.value).length,
    color: FEEDBACK_STATUS_COLORS[item.value]
  })).filter((item) => item.value > 0)
)

/** 环形图中心数字：范围内实际条数 */
const scopedTotal = computed(() => scopedFeedbacks.value.length)

/** 柱状图：功能模块分布 */
const moduleBars = computed(() => {
  const counter = new Map()
  scopedFeedbacks.value.forEach((row) => {
    const key = row.module || 'OTHER'
    counter.set(key, (counter.get(key) || 0) + 1)
  })

  return [...counter.entries()]
    .sort((a, b) => b[1] - a[1])
    // 只取前 4 个：再多每列就窄到 36px，模块名会被拆成一字一行
    .slice(0, 4)
    .map(([code, value]) => ({ label: moduleLabel(code), value }))
})

/** 折线图：近 7 日反馈量 */
const trendPoints = computed(() => {
  const days = Array.from({ length: 7 }, (_, index) =>
    dayjs().subtract(6 - index, 'day').startOf('day')
  )

  return days.map((day) => ({
    label: day.format('MM/DD'),
    value: feedbackRows.value.filter(
      (row) => row.createTime && dayjs(row.createTime).isSame(day, 'day')
    ).length
  }))
})

/** 任务状态统计 */
const taskStats = computed(() => {
  const total = taskRows.value.length || 1
  return TASK_STATUSES.map((item) => {
    const value = taskRows.value.filter((row) => row.status === item.value).length
    return {
      label: item.label,
      value,
      percent: Math.round((value / total) * 100),
      color: TASK_STATUS_COLORS[item.value]
    }
  })
})

/** 任务总数（卡片标题右侧） */
const taskTotal = computed(() => taskRows.value.length)

/** 处于开发中的任务数 */
const inProgressCount = computed(
  () => taskRows.value.filter((row) => row.status === 'IN_PROGRESS').length
)

/** 表格数据：按创建时间倒序取前 8 条 */
const tableRows = computed(() =>
  [...scopedFeedbacks.value]
    .sort((a, b) => String(b.createTime).localeCompare(String(a.createTime)))
    .slice(0, 8)
)

// ==================== 顶部关键指标 ====================
//
// 四张图说明「结构」，四张数说明「规模」。管理者第一眼要的是后者：
// 多少条待处理、多少任务在跑。所以把数字放最上面，且用 count-up
// 让"数据刚刚刷新过"这件事有感知。

/** 待关联反馈数（状态 NEW：还没进任何问题） */
const pendingCount = computed(
  () => scopedFeedbacks.value.filter((row) => row.status === 'NEW').length
)

/** 已完成任务数 */
const doneTaskCount = computed(
  () => taskRows.value.filter((row) => row.status === 'DONE').length
)

/** 今日新增反馈数 */
const todayCount = computed(
  () =>
    feedbackRows.value.filter(
      (row) => row.createTime && dayjs(row.createTime).isSame(dayjs(), 'day')
    ).length
)

/** 近 7 日反馈量的峰值，用于柱高归一化（至少 1，避免全 0 时除零） */
const trendMax = computed(() =>
  Math.max(1, ...trendPoints.value.map((point) => point.value))
)

/**
 * 柱高百分比
 *
 * <p>最低 6%：值为 0 的那天也要留一条可见的底座，
 * 否则柱状图会出现"某天消失了"的错觉。
 *
 * @param {number} value 当日条数
 * @returns {string} CSS 高度
 */
function barHeight(value) {
  return `${Math.max(6, (value / trendMax.value) * 100)}%`
}


// ==================== 表格列 ====================

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

const columns = [
  {
    title: '反馈编号',
    key: 'feedbackNo',
    width: 140,
    render: (row) =>
      h('span', { class: 'text-mono', style: 'color: var(--c-text-3); font-size: 12.5px' }, row.feedbackNo)
  },
  {
    title: '反馈摘要',
    key: 'content',
    minWidth: 260,
    ellipsis: { tooltip: true }
  },
  {
    title: '功能模块',
    key: 'module',
    width: 120,
    render: (row) => row.moduleLabel || moduleLabel(row.module)
  },
  {
    title: '来源',
    key: 'source',
    width: 100,
    render: (row) => row.sourceLabel || '-'
  },
  {
    title: '录入人',
    key: 'createdByName',
    width: 100,
    render: (row) => row.createdByName || '-'
  },
  {
    title: '状态',
    key: 'status',
    width: 96,
    render: (row) => {
      const chip = FEEDBACK_STATUS_CHIPS[row.status] || FEEDBACK_STATUS_CHIPS.NEW
      return h(
        'span',
        {
          class: 'status-chip',
          style: `background:${chip.bg};color:${chip.color}`
        },
        row.statusLabel || feedbackStatusLabel(row.status)
      )
    }
  },
  {
    title: '录入时间',
    key: 'createTime',
    width: 140,
    render: (row) => formatTime(row.createTime)
  },
  {
    title: '操作',
    key: 'actions',
    width: 90,
    fixed: 'right',
    render: (row) =>
      h(
        NButton,
        {
          size: 'small',
          quaternary: true,
          type: 'primary',
          onClick: () => router.push({ name: 'feedback-detail', params: { id: row.id } })
        },
        { default: () => '查看' }
      )
  }
]
</script>

<template>
  <div class="page-container">
    <!-- ==================== 概览标题栏 ==================== -->
    <div class="board-head">
      <div class="board-head__text">
        <h2 class="board-head__title">反馈健康度概览</h2>
        <p class="board-head__desc">
          共 {{ feedbackTotal }} 条反馈 · 其中 {{ taskTotal }} 项改进任务在跟进
        </p>
      </div>

      <div class="board-head__actions">
        <n-select
          v-model:value="range"
          :options="rangeOptions"
          class="range-select"
          size="medium"
        />
        <button class="refresh-btn" type="button" :disabled="loading" @click="loadData">
          <AppIcon name="refresh" :size="16" :class="{ spinning: loading }" />
        </button>
      </div>
    </div>

    <!-- 失败态：明确告知 + 可重试，不留白板 -->
    <div v-if="loadError" class="board-error glass">
      <AppIcon name="alert-circle" :size="18" />
      <span>{{ loadError }}</span>
      <n-button size="small" type="primary" @click="loadData">重新加载</n-button>
    </div>

    <n-spin :show="loading">
      <!-- ==================== 关键指标（bento 不等高网格） ==================== -->
      <div class="kpi-grid">
        <!-- 主指标：反馈总量 + 近 7 日柱图，占 2×2 -->
        <article class="kpi kpi--trend glass">
          <header class="kpi__head">
            <span class="kpi__label">反馈总量</span>
            <span class="kpi__delta">近 7 天 +{{ trendPoints.reduce((sum, p) => sum + p.value, 0) }}</span>
          </header>

          <div class="kpi__num">
            <CountUp :value="feedbackTotal" />
            <em>条</em>
          </div>
          <p class="kpi__sub">统计范围内 {{ scopedTotal }} 条 · 任务 {{ taskTotal }} 项</p>

          <div class="kpi__bars" aria-hidden="true">
            <span
              v-for="(point, index) in trendPoints"
              :key="point.label"
              class="kpi__bar-col"
              :title="`${point.label}：${point.value} 条`"
            >
              <i
                class="kpi__bar"
                :class="{ 'is-today': index === trendPoints.length - 1 }"
                :style="{ height: barHeight(point.value) }"
              ></i>
            </span>
          </div>
          <div class="kpi__axis">
            <span v-for="point in trendPoints" :key="point.label">{{ point.label }}</span>
          </div>
        </article>

        <article class="kpi glass">
          <header class="kpi__head">
            <span class="kpi__label">待关联</span>
            <span class="kpi__dot kpi__dot--warn"></span>
          </header>
          <div class="kpi__num kpi__num--sm kpi__num--warn">
            <CountUp :value="pendingCount" />
            <em>条</em>
          </div>
          <p class="kpi__sub">尚未归入任何问题</p>
        </article>

        <article class="kpi glass">
          <header class="kpi__head">
            <span class="kpi__label">进行中任务</span>
            <span class="kpi__dot kpi__dot--primary"></span>
          </header>
          <div class="kpi__num kpi__num--sm kpi__num--primary">
            <CountUp :value="inProgressCount" />
            <em>项</em>
          </div>
          <p class="kpi__sub">开发工程师处理中</p>
        </article>

        <article class="kpi glass">
          <header class="kpi__head">
            <span class="kpi__label">已完成任务</span>
            <span class="kpi__dot kpi__dot--ok"></span>
          </header>
          <div class="kpi__num kpi__num--sm kpi__num--ok">
            <CountUp :value="doneTaskCount" />
            <em>项</em>
          </div>
          <p class="kpi__sub">验收通过并关闭</p>
        </article>

        <article class="kpi glass">
          <header class="kpi__head">
            <span class="kpi__label">今日新增</span>
            <span class="kpi__tag">实时</span>
          </header>
          <div class="kpi__num kpi__num--sm kpi__num--accent">
            <CountUp :value="todayCount" />
            <em>条</em>
          </div>
          <p class="kpi__sub">今天录入的反馈</p>
        </article>
      </div>

      <!-- ==================== 三块图表 ==================== -->
      <div class="stat-grid">
        <!-- 1. 反馈状态分布 -->
        <section class="stat-card glass">
          <header class="stat-card__head">
            <span class="stat-card__title">反馈概览</span>
            <AppIcon name="chevron-down" :size="15" class="stat-card__chevron" />
          </header>

          <div class="stat-card__body stat-card__body--split">
            <DonutChart
              :segments="statusSegments"
              :size="118"
              :thickness="12"
              :center-value="scopedTotal"
              center-label="条反馈"
            />
            <ul class="legend">
              <li v-for="item in statusSegments" :key="item.label" class="legend__item">
                <span class="legend__dot" :style="{ background: item.color }"></span>
                <span class="legend__label">{{ item.label }}</span>
                <span class="legend__value">{{ item.value }}</span>
              </li>
            </ul>
          </div>
        </section>

        <!-- 2. 功能模块分布 -->
        <section class="stat-card glass">
          <header class="stat-card__head">
            <span class="stat-card__title">模块分布</span>
            <span class="stat-card__hint">TOP {{ moduleBars.length }}</span>
          </header>

          <div class="stat-card__body">
            <BarChart :data="moduleBars" :height="104" />
            <div class="axis-row">
              <span v-for="item in moduleBars" :key="item.label" class="axis-row__item">
                {{ item.label }}
              </span>
            </div>
          </div>
        </section>

        <!-- 3. 改进任务进度 -->
        <section class="stat-card glass">
          <header class="stat-card__head">
            <span class="stat-card__title">改进任务</span>
            <span class="stat-card__hint">{{ inProgressCount }} 项进行中</span>
          </header>

          <div class="stat-card__body task-list">
            <div v-for="item in taskStats" :key="item.label" class="task-item">
              <div class="task-item__row">
                <span class="task-item__label">{{ item.label }}</span>
                <span class="task-item__value">{{ item.value }}</span>
              </div>
              <div class="task-item__track">
                <span
                  class="task-item__fill"
                  :style="{ width: `${item.percent}%`, background: item.color }"
                ></span>
              </div>
            </div>
          </div>
        </section>
      </div>

      <!-- ==================== 最新反馈表格 ==================== -->
      <section class="table-card glass">
        <header class="table-card__head">
          <div class="table-card__title-wrap">
            <span class="table-card__title">最新反馈</span>
            <span class="table-card__sub">按录入时间倒序 · 展示前 8 条</span>
          </div>

          <button class="more-btn" type="button" @click="router.push({ name: 'feedback-list' })">
            查看全部
            <AppIcon name="arrow-right" :size="15" />
          </button>
        </header>

        <n-data-table
          :columns="columns"
          :data="tableRows"
          :bordered="false"
          :single-line="false"
          size="small"
          :row-key="(row) => row.id"
        />
      </section>
    </n-spin>
  </div>
</template>

<style scoped>
/* ==================== 标题栏 ==================== */
.board-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.board-head__title {
  margin: 0;
  font-size: 19px;
  font-weight: 700;
  color: var(--c-text-1);
  letter-spacing: 0.01em;
}

.board-head__desc {
  margin: 6px 0 0;
  font-size: 12.5px;
  color: var(--c-text-3);
}

.board-head__actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.range-select {
  width: 132px;
}

.refresh-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  color: var(--c-text-2);
  background: rgba(255, 255, 255, 0.62);
  border: 1px solid rgba(255, 255, 255, 0.85);
  border-radius: 12px;
  cursor: pointer;
  transition: color 0.18s ease, background-color 0.18s ease;
}

.refresh-btn:hover:not(:disabled) {
  color: var(--c-primary);
  background-color: #fff;
}

.refresh-btn:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.board-error {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  margin-bottom: 16px;
  font-size: 13px;
  color: var(--c-danger);
  border-radius: 16px;
}

/* ==================== 关键指标 bento ====================
 *
 * 不等高网格：主指标占 2×2，四个从属指标各占 1×1。
 * 权重差异直接写在版式里，比"四张一样大的卡"更快让人抓到重点。
 */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  grid-auto-rows: 138px;
  gap: 14px;
  margin-bottom: 16px;
}

.kpi {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 16px 18px;
  overflow: hidden;
  border-radius: 20px;
  background-color: rgba(255, 255, 255, 0.78);
  transition: transform 0.38s cubic-bezier(0.22, 0.61, 0.36, 1),
    box-shadow 0.38s cubic-bezier(0.22, 0.61, 0.36, 1);
}

/* 悬停微抬升：14px 的位移太夸张，4px 刚好有"浮起来"的实感 */
.kpi:hover {
  transform: translateY(-4px);
  box-shadow: 0 24px 60px -28px rgba(74, 89, 168, 0.38),
    0 4px 14px rgba(74, 89, 168, 0.06), var(--shadow-inset);
}

.kpi--trend {
  grid-column: span 2;
  grid-row: span 2;
  padding: 20px 22px 16px;
}

.kpi__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}

.kpi__label {
  font-size: 12.5px;
  color: var(--c-text-2);
}

.kpi__delta {
  font-size: 11px;
  font-weight: 500;
  color: #2b9563;
  background: rgba(51, 173, 116, 0.12);
  padding: 3px 8px;
  border-radius: 999px;
  font-variant-numeric: tabular-nums;
}

.kpi__tag {
  font-size: 10.5px;
  color: var(--c-text-3);
  background: rgba(122, 136, 190, 0.12);
  padding: 3px 8px;
  border-radius: 999px;
}

.kpi__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

.kpi__dot--warn {
  background: var(--c-warning);
  box-shadow: 0 0 0 3px rgba(224, 144, 47, 0.14);
}

.kpi__dot--primary {
  background: var(--c-primary);
  box-shadow: 0 0 0 3px rgba(108, 123, 245, 0.14);
}

.kpi__dot--ok {
  background: var(--c-success);
  box-shadow: 0 0 0 3px rgba(51, 173, 116, 0.14);
}

.kpi__num {
  display: flex;
  align-items: baseline;
  gap: 5px;
  font-size: 42px;
  font-weight: 600;
  line-height: 1.05;
  letter-spacing: -0.03em;
  color: var(--c-text-1);
  font-variant-numeric: tabular-nums;
}

.kpi__num--sm {
  font-size: 28px;
  letter-spacing: -0.02em;
}

.kpi__num em {
  font-size: 12px;
  font-style: normal;
  font-weight: 400;
  color: var(--c-text-3);
}

.kpi__num--warn { color: var(--c-warning); }
.kpi__num--primary { color: var(--c-primary); }
.kpi__num--ok { color: var(--c-success); }
.kpi__num--accent { color: #8b6fe8; }

.kpi__sub {
  margin: 6px 0 0;
  font-size: 11.5px;
  color: var(--c-text-3);
}

/* 主指标卡内的 7 日柱图 */
.kpi__bars {
  display: flex;
  align-items: flex-end;
  gap: 9px;
  height: 84px;
  margin-top: auto;
  padding-top: 14px;
}

.kpi__bar-col {
  flex: 1;
  display: flex;
  align-items: flex-end;
  height: 100%;
  min-width: 0;
}

.kpi__bar {
  display: block;
  width: 100%;
  border-radius: 7px 7px 4px 4px;
  background: linear-gradient(180deg, rgba(108, 123, 245, 0.42), rgba(108, 123, 245, 0.1));
  transform-origin: bottom;
  animation: kpi-bar-in 0.7s cubic-bezier(0.22, 0.61, 0.36, 1) backwards;
  transition: background 0.25s ease;
}

/* 依次入场，让柱图有"长出来"的过程；延迟按序号叠加 */
.kpi__bar-col:nth-child(2) .kpi__bar { animation-delay: 0.05s; }
.kpi__bar-col:nth-child(3) .kpi__bar { animation-delay: 0.1s; }
.kpi__bar-col:nth-child(4) .kpi__bar { animation-delay: 0.15s; }
.kpi__bar-col:nth-child(5) .kpi__bar { animation-delay: 0.2s; }
.kpi__bar-col:nth-child(6) .kpi__bar { animation-delay: 0.25s; }
.kpi__bar-col:nth-child(7) .kpi__bar { animation-delay: 0.3s; }

.kpi__bar.is-today {
  background: linear-gradient(180deg, var(--c-primary), rgba(167, 139, 250, 0.55));
  box-shadow: 0 8px 20px -10px rgba(108, 123, 245, 0.95);
}

.kpi__bar-col:hover .kpi__bar {
  background: linear-gradient(180deg, var(--c-primary), rgba(108, 123, 245, 0.35));
}

@keyframes kpi-bar-in {
  from {
    transform: scaleY(0.05);
    opacity: 0;
  }
}

.kpi__axis {
  display: flex;
  gap: 9px;
  margin-top: 7px;
}

.kpi__axis span {
  flex: 1;
  min-width: 0;
  font-size: 10px;
  color: var(--c-text-3);
  text-align: center;
  font-variant-numeric: tabular-nums;
}

/* ==================== 指标卡 ==================== */
.stat-grid {
  display: grid;
  grid-template-columns: 1.1fr 1fr 1fr;
  gap: 16px;
}

.stat-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 16px 18px 14px;
  border-radius: 20px;
  background-color: rgba(255, 255, 255, 0.78);
  transition: transform 0.38s cubic-bezier(0.22, 0.61, 0.36, 1),
    box-shadow 0.38s cubic-bezier(0.22, 0.61, 0.36, 1);
}

.stat-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 22px 54px -26px rgba(74, 89, 168, 0.34),
    0 4px 14px rgba(74, 89, 168, 0.06), var(--shadow-inset);
}

.stat-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 12px;
}

.stat-card__title {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--c-text-1);
}

.stat-card__hint {
  font-size: 11px;
  color: var(--c-text-3);
}

.stat-card__chevron {
  color: var(--c-text-3);
}

.stat-card__body {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-height: 118px;
}

.stat-card__body--split {
  flex-direction: row;
  align-items: center;
  gap: 14px;
}

/* 图例 */
.legend {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 7px;
  min-width: 0;
  padding: 0;
  margin: 0;
  list-style: none;
}

.legend__item {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 12px;
}

.legend__dot {
  width: 8px;
  height: 8px;
  border-radius: 3px;
  flex: none;
}

.legend__label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: var(--c-text-2);
}

.legend__value {
  font-weight: 600;
  color: var(--c-text-1);
  font-variant-numeric: tabular-nums;
}

/* 柱状图下方的模块名：允许折成两行，宁可紧凑也不要把「发票上传」截成「发票…」 */
.axis-row {
  display: flex;
  gap: 6px;
  margin-top: 8px;
}

.axis-row__item {
  flex: 1;
  min-width: 0;
  font-size: 10px;
  line-height: 1.3;
  color: var(--c-text-3);
  text-align: center;
  word-break: break-all;
}

/* 任务进度条 */
.task-list {
  display: flex;
  flex-direction: column;
  gap: 9px;
  justify-content: center;
}

.task-item__row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 4px;
}

.task-item__label {
  font-size: 11.5px;
  color: var(--c-text-2);
}

.task-item__value {
  font-size: 11.5px;
  font-weight: 600;
  color: var(--c-text-1);
  font-variant-numeric: tabular-nums;
}

.task-item__track {
  height: 5px;
  background: rgba(122, 136, 190, 0.12);
  border-radius: 999px;
  overflow: hidden;
}

.task-item__fill {
  display: block;
  height: 100%;
  border-radius: 999px;
  transition: width 0.5s cubic-bezier(0.22, 1, 0.36, 1);
}

/* ==================== 表格卡 ==================== */
.table-card {
  padding: 18px 20px 8px;
  margin-top: 16px;
  border-radius: 20px;
  background-color: rgba(255, 255, 255, 0.78);
}

.table-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.table-card__title-wrap {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.table-card__title {
  font-size: 15px;
  font-weight: 700;
  color: var(--c-text-1);
}

.table-card__sub {
  font-size: 11.5px;
  color: var(--c-text-3);
}

.more-btn {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 6px 12px;
  font-family: inherit;
  font-size: 12px;
  color: var(--c-primary);
  background: rgba(108, 123, 245, 0.08);
  border: none;
  border-radius: 10px;
  cursor: pointer;
  transition: background-color 0.18s ease;
}

.more-btn:hover {
  background-color: rgba(108, 123, 245, 0.16);
}

/* 表格：去掉外框，靠分隔线分列，符合参考稿的轻量观感 */
.table-card :deep(.n-data-table) {
  background-color: transparent;
}

.table-card :deep(.n-data-table .n-data-table-th),
.table-card :deep(.n-data-table .n-data-table-td) {
  font-size: 12.5px;
  background-color: transparent;
}

.table-card :deep(.n-data-table .n-data-table-th) {
  font-size: 12px;
  letter-spacing: 0.02em;
}

/* 状态徽标：柔和底色 + 圆角，与参考稿的 badge 观感一致 */
.table-card :deep(.status-chip) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 56px;
  padding: 2px 10px;
  font-size: 11.5px;
  font-weight: 500;
  line-height: 1.6;
  border-radius: 8px;
}

.spinning {
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* 中等宽度：主指标占满一行，四个小卡 2×2 */
@media (max-width: 1240px) {
  .kpi-grid {
    grid-template-columns: 1fr 1fr;
    grid-auto-rows: 132px;
  }

  .kpi--trend {
    grid-column: span 2;
    grid-row: span 2;
  }

  .stat-grid {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 860px) {
  .kpi-grid {
    grid-template-columns: 1fr;
  }

  .kpi--trend {
    grid-column: span 1;
    grid-row: span 1;
    min-height: 260px;
  }

  .stat-grid {
    grid-template-columns: 1fr;
  }

  .board-head {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
