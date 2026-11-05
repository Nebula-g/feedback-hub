<script setup>
/**
 * 全局命令面板（Ctrl / ⌘ + K）
 *
 * <p><b>解决什么问题</b>：反馈、问题、任务分在三个页面，
 * 用户记得"发票上传失败"却不记得它在哪个模块。
 * 顶栏的普通搜索只能查反馈（跳反馈列表带 keyword），
 * 这里做成跨模块一次检索，命中后直跳详情。
 *
 * <p><b>数据来源</b>：不新增后端接口，直接复用三个已有的分页接口，
 * 各取前 5 条。演示数据量小，三次轻量查询足够；
 * 以后要真正的全文检索，只需替换 <code>search()</code> 里的实现。
 *
 * <p><b>空关键词时</b>：不显示"没结果"，而是给快捷入口（跳转到各模块，
 * 以及权限允许时直接新建）。命令面板的第一价值是"少点两下"，
 * 其次才是搜索。
 *
 * @param {boolean} show 是否显示（v-model）
 */
import { computed, nextTick, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import AppIcon from '@/components/AppIcon.vue'
import { fetchFeedbackPage } from '@/api/feedback'
import { fetchIssuePage } from '@/api/issue'
import { fetchTaskPage } from '@/api/task'
import { useUserStore } from '@/stores/user'

const props = defineProps({
  show: { type: Boolean, default: false }
})

const emit = defineEmits(['update:show'])

const router = useRouter()
const userStore = useUserStore()

const keyword = ref('')
const loading = ref(false)
const results = ref([])
const activeIndex = ref(0)
const inputRef = ref(null)
const listRef = ref(null)

/** 防抖定时器：连续输入只发最后一次请求 */
let searchTimer = 0
/** 请求序号：慢请求回来时如果已有更新的请求，直接丢弃结果 */
let requestSeq = 0

/** 快捷入口（无关键词时展示） */
const shortcuts = computed(() => {
  const list = [
    { type: '页面', title: '仪表盘', meta: '反馈健康度概览', to: { name: 'dashboard' } },
    { type: '页面', title: '反馈列表', meta: '全部原始反馈', to: { name: 'feedback-list' } },
    { type: '页面', title: '问题列表', meta: '已归类的问题', to: { name: 'issue-list' } },
    { type: '页面', title: '任务看板', meta: '改进任务流转', to: { name: 'task-board' } }
  ]
  if (userStore.role === 'PM' || userStore.role === 'ADMIN') {
    list.push({ type: '页面', title: 'AI 候选问题', meta: '待确认的聚类结果', to: { name: 'ai-candidate' } })
  }
  if (userStore.isCs || userStore.role === 'ADMIN') {
    list.push({ type: '新建', title: '录入反馈', meta: '粘贴客户原话', to: { name: 'feedback-create' } })
  }
  return list
})

/** 平铺后的可选项，键盘上下移动时按这个顺序走 */
const flatItems = computed(() => results.value.flatMap((group) => group.items))

watch(
  () => props.show,
  (visible) => {
    if (!visible) return
    keyword.value = ''
    results.value = []
    activeIndex.value = 0
    nextTick(() => inputRef.value?.focus())
  }
)

watch(keyword, (value) => {
  clearTimeout(searchTimer)
  const trimmed = value.trim()
  if (!trimmed) {
    results.value = []
    activeIndex.value = 0
    loading.value = false
    return
  }
  // 输入即搜会让每个字符都打一次后端，260ms 是"感觉不到延迟"与"少发请求"的平衡点
  searchTimer = setTimeout(() => search(trimmed), 260)
})

/**
 * 跨模块检索
 *
 * <p>三路并行 + allSettled：某个模块接口失败不该让整个面板空掉，
 * 命中的其他结果照常展示。
 *
 * @param {string} text 关键词
 */
async function search(text) {
  const seq = (requestSeq += 1)
  loading.value = true
  try {
    const [feedbackRes, issueRes, taskRes] = await Promise.allSettled([
      fetchFeedbackPage({ pageNo: 1, pageSize: 5, keyword: text }),
      fetchIssuePage({ pageNo: 1, pageSize: 5, keyword: text }),
      fetchTaskPage({ pageNo: 1, pageSize: 5, keyword: text })
    ])

    // 慢请求后到：说明用户已经输入了更新的关键词，这批结果作废
    if (seq !== requestSeq) return

    const groups = []

    const feedbacks = feedbackRes.status === 'fulfilled' ? feedbackRes.value?.records || [] : []
    if (feedbacks.length) {
      groups.push({
        label: '反馈',
        items: feedbacks.map((row) => ({
          type: '反馈',
          title: row.content,
          meta: row.feedbackNo,
          to: { name: 'feedback-detail', params: { id: row.id } }
        }))
      })
    }

    const issues = issueRes.status === 'fulfilled' ? issueRes.value?.records || [] : []
    if (issues.length) {
      groups.push({
        label: '问题',
        items: issues.map((row) => ({
          type: '问题',
          title: row.title,
          meta: row.issueNo || `#${row.id}`,
          to: { name: 'issue-detail', params: { id: row.id } }
        }))
      })
    }

    const tasks = taskRes.status === 'fulfilled' ? taskRes.value?.records || [] : []
    if (tasks.length) {
      groups.push({
        label: '任务',
        items: tasks.map((row) => ({
          type: '任务',
          title: row.title,
          meta: row.statusLabel || '任务',
          to: { name: 'task-detail', params: { id: row.id } }
        }))
      })
    }

    results.value = groups
    activeIndex.value = 0
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}

function close() {
  emit('update:show', false)
}

/**
 * 执行选中项
 *
 * @param {object} item 结果项（含 to 路由）
 */
function run(item) {
  if (!item) return
  close()
  router.push(item.to)
}

/**
 * 移动高亮项并保证它可见
 *
 * @param {number} index 目标下标（会自动取模）
 */
function moveActive(index) {
  const total = flatItems.value.length
  if (!total) return
  activeIndex.value = (index + total) % total
  nextTick(() => {
    const el = listRef.value?.querySelector('.palette__item.is-active')
    el?.scrollIntoView({ block: 'nearest' })
  })
}

function onKeydown(event) {
  if (event.key === 'Escape') {
    event.preventDefault()
    close()
    return
  }
  if (!flatItems.value.length) return
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    moveActive(activeIndex.value + 1)
  } else if (event.key === 'ArrowUp') {
    event.preventDefault()
    moveActive(activeIndex.value - 1)
  } else if (event.key === 'Enter') {
    event.preventDefault()
    run(flatItems.value[activeIndex.value])
  }
}

/**
 * 计算某个条目在平铺数组里的下标
 *
 * <p>模板里按分组渲染，但高亮是按平铺顺序算的，
 * 渲染时需要反查分组内序号对应的全局下标。
 *
 * @param {number} groupIndex 分组下标
 * @param {number} itemIndex  组内下标
 * @returns {number} 全局下标
 */
function globalIndex(groupIndex, itemIndex) {
  let offset = 0
  for (let i = 0; i < groupIndex; i += 1) {
    offset += results.value[i].items.length
  }
  return offset + itemIndex
}
</script>

<template>
  <Teleport to="body">
    <div v-if="show" class="palette-backdrop" @click.self="close">
      <div class="palette" role="dialog" aria-modal="true" aria-label="全局搜索">
        <div class="palette__input">
          <AppIcon name="search" :size="16" />
          <input
            ref="inputRef"
            v-model="keyword"
            placeholder="搜索反馈、问题、任务…"
            autocomplete="off"
            @keydown="onKeydown"
          />
          <span v-if="loading" class="palette__loading">检索中</span>
          <kbd v-else>Esc</kbd>
        </div>

        <div ref="listRef" class="palette__list">
          <!-- 无关键词：快捷入口 -->
          <template v-if="!keyword.trim()">
            <div class="palette__group">快捷入口</div>
            <button
              v-for="(item, index) in shortcuts"
              :key="item.title"
              type="button"
              class="palette__item"
              :class="{ 'is-active': index === 0 }"
              @click="run(item)"
            >
              <span class="palette__type">{{ item.type }}</span>
              <span class="palette__title">{{ item.title }}</span>
              <span class="palette__meta">{{ item.meta }}</span>
            </button>
          </template>

          <!-- 有关键词但没命中 -->
          <div v-else-if="!loading && results.length === 0" class="palette__empty">
            没有匹配「{{ keyword.trim() }}」的结果
          </div>

          <!-- 命中结果 -->
          <template v-for="(group, groupIndex) in results" :key="group.label">
            <div class="palette__group">{{ group.label }}</div>
            <button
              v-for="(item, itemIndex) in group.items"
              :key="`${group.label}-${itemIndex}`"
              type="button"
              class="palette__item"
              :class="{ 'is-active': globalIndex(groupIndex, itemIndex) === activeIndex }"
              @mouseenter="activeIndex = globalIndex(groupIndex, itemIndex)"
              @click="run(item)"
            >
              <span class="palette__type">{{ item.type }}</span>
              <span class="palette__title">{{ item.title }}</span>
              <span class="palette__meta">{{ item.meta }}</span>
            </button>
          </template>
        </div>

        <div class="palette__foot">
          <span>↑↓ 选择</span>
          <span>↵ 打开</span>
          <span>Esc 关闭</span>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.palette-backdrop {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 14vh 20px 20px;
  background: rgba(37, 42, 65, 0.2);
  -webkit-backdrop-filter: blur(6px);
  backdrop-filter: blur(6px);
  animation: palette-fade 0.18s ease;
}

.palette {
  width: min(560px, 100%);
  overflow: hidden;
  background: rgba(255, 255, 255, 0.95);
  border: 1px solid rgba(255, 255, 255, 0.9);
  border-radius: 18px;
  box-shadow: 0 40px 90px -36px rgba(37, 42, 65, 0.5);
  -webkit-backdrop-filter: blur(18px) saturate(150%);
  backdrop-filter: blur(18px) saturate(150%);
  animation: palette-pop 0.22s cubic-bezier(0.22, 0.61, 0.36, 1);
}

.palette__input {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  color: var(--c-text-3);
  border-bottom: 1px solid var(--c-divider);
}

.palette__input input {
  flex: 1;
  min-width: 0;
  font-family: inherit;
  font-size: 14px;
  color: var(--c-text-1);
  background: transparent;
  border: none;
  outline: none;
}

.palette__input input::placeholder {
  color: var(--c-text-3);
}

.palette__loading {
  font-size: 11px;
  color: var(--c-text-3);
}

kbd {
  font-family: inherit;
  font-size: 10.5px;
  color: var(--c-text-2);
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid var(--c-border-soft);
  border-bottom-width: 2px;
  border-radius: 6px;
  padding: 2px 6px;
}

.palette__list {
  max-height: 300px;
  overflow-y: auto;
  padding: 6px;
}

.palette__group {
  padding: 10px 12px 5px;
  font-size: 10.5px;
  letter-spacing: 0.08em;
  color: var(--c-text-3);
}

.palette__item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 9px 12px;
  font-family: inherit;
  font-size: 13px;
  color: var(--c-text-2);
  text-align: left;
  background: transparent;
  border: none;
  border-radius: 10px;
  cursor: pointer;
}

.palette__item.is-active {
  color: var(--c-text-1);
  background: var(--c-primary-soft);
}

.palette__type {
  flex: none;
  padding: 2px 7px;
  font-size: 10.5px;
  color: var(--c-text-3);
  background: rgba(122, 136, 190, 0.14);
  border-radius: 6px;
}

.palette__title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.palette__meta {
  flex: none;
  font-size: 11px;
  color: var(--c-text-3);
  font-variant-numeric: tabular-nums;
}

.palette__empty {
  padding: 30px 16px;
  font-size: 12.5px;
  color: var(--c-text-3);
  text-align: center;
}

.palette__foot {
  display: flex;
  gap: 16px;
  padding: 9px 16px;
  font-size: 11px;
  color: var(--c-text-3);
  border-top: 1px solid var(--c-divider);
  background: rgba(248, 249, 255, 0.7);
}

@keyframes palette-fade {
  from {
    opacity: 0;
  }
}

@keyframes palette-pop {
  from {
    opacity: 0;
    transform: translateY(-8px) scale(0.985);
  }
}

@media (prefers-reduced-motion: reduce) {
  .palette-backdrop,
  .palette {
    animation: none;
  }
}
</style>
