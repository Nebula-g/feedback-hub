<script setup>
/**
 * 改进任务看板
 *
 * <p><b>为什么用看板而不是表格</b>：
 * 任务的核心信息是"它卡在哪一步"。
 * 看板按状态分列，一眼能看出 bottlenecks——
 * 这比在表格里读 status 列直观得多。
 *
 * <p><b>权限设计</b>：
 * <ul>
 *   <li>PM 可以新建任务、改优先级、指派负责人</li>
 *   <li>DEV 只能推进状态（在看板里拖拽/点击流转）</li>
 * </ul>
 * 看板上的操作按钮按角色显示，避免点了才被后端拒绝。
 *
 * <p><b>筛选</b>：支持按状态、优先级、模块筛选，
 * 以及 DEV 最关心的「只看我的」。
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
  useMessage
} from 'naive-ui'

import { fetchTaskPage, createTask } from '@/api/task'
import { fetchIssuePage } from '@/api/issue'
import {
  FEEDBACK_MODULES,
  TASK_STATUSES,
  TASK_PRIORITIES,
  moduleLabel
} from '@/constants/enums'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const message = useMessage()
const userStore = useUserStore()

/** 全部任务 */
const rows = ref([])

/** 加载中 */
const loading = ref(false)

/** 错误 */
const loadError = ref('')

/** 查询条件 */
const query = reactive({
  pageNo: 1,
  pageSize: 100, // 看板一次取全量，不再分页
  status: null,
  priority: null,
  module: null,
  assigneeId: null,
  keyword: ''
})

/** 只看我的（DEV 快捷筛选） */
const onlyMine = ref(false)

// 后端 TaskController.create 为 hasRole('PM')，严格只放行 PM，
// ADMIN 点了也会被 403，因此入口只对 PM 显示
const canCreate = computed(() => userStore.role === 'PM')

/** 优先级 → Naive UI 标签配色 */
const priorityType = {
  P0: 'error',
  P1: 'warning',
  P2: 'default'
}

/** 状态圆点配色（看板分列用） */
const statusColor = {
  TODO: 'var(--c-text-2)',
  IN_PROGRESS: 'var(--c-primary)',
  PENDING_VERIFY: 'var(--c-warning)',
  DONE: 'var(--c-success)',
  WONT_DO: 'var(--c-text-3)'
}

/**
 * 按状态分列
 *
 * <p>用 TASK_STATUSES 的顺序保证列顺序稳定。
 */
const columns = computed(() => {
  return TASK_STATUSES.map((status) => ({
    status,
    items: rows.value.filter((item) => item.status === status.value)
  }))
})

/** 时间格式化 */
function fmt(value) {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '—'
}

/**
 * 加载任务
 */
async function loadData() {
  loading.value = true
  loadError.value = ''

  try {
    const params = {
      pageNo: query.pageNo,
      pageSize: query.pageSize
    }
    if (query.status) params.status = query.status
    if (query.priority) params.priority = query.priority
    if (query.module) params.module = query.module
    if (query.keyword) params.keyword = query.keyword
    // 「只看我的」映射为后端约定的 myself
    if (onlyMine.value) params.assigneeId = 'myself'

    const data = await fetchTaskPage(params)
    rows.value = data?.records || []
  } catch (error) {
    rows.value = []
    loadError.value = error.message || '加载任务列表失败'
  } finally {
    loading.value = false
  }
}

function handleReset() {
  query.status = null
  query.priority = null
  query.module = null
  query.keyword = ''
  onlyMine.value = false
  loadData()
}

// ==================== 新建任务 ====================

const showCreateModal = ref(false)
const createSubmitting = ref(false)
const issueOptions = ref([])
const createFormRef = ref(null)

const createForm = reactive({
  issueId: null,
  title: '',
  requirement: '',
  acceptanceCriteria: '',
  priority: 'P1',
  priorityReason: '',
  assigneeId: null
})

const createRules = {
  issueId: [{ required: true, message: '请选择来源问题', trigger: 'change' }],
  title: [{ required: true, message: '请填写任务标题', trigger: 'blur' }],
  priority: [{ required: true, message: '请选择优先级', trigger: 'change' }],
  priorityReason: [
    { required: true, message: '请填写定级理由', trigger: 'blur' }
  ]
}

/**
 * 打开新建弹窗
 *
 * <p>同时加载可选的问题列表（全部，不限分页大小）。
 */
async function openCreateModal() {
  Object.assign(createForm, {
    issueId: null,
    title: '',
    requirement: '',
    acceptanceCriteria: '',
    priority: 'P1',
    priorityReason: '',
    assigneeId: null
  })
  showCreateModal.value = true

  try {
    const data = await fetchIssuePage({ pageNo: 1, pageSize: 100 })
    issueOptions.value = (data?.records || []).map((item) => ({
      label: `${item.issueNo} · ${item.title}`,
      value: item.id
    }))
  } catch {
    issueOptions.value = []
  }
}

/**
 * 提交新建任务
 */
async function submitCreate() {
  try {
    await createFormRef.value?.validate()
  } catch {
    return
  }

  createSubmitting.value = true
  try {
    const data = await createTask({ ...createForm })
    message.success('任务创建成功')
    showCreateModal.value = false
    await loadData()
    router.push({ name: 'task-detail', params: { id: data.id } })
  } catch (error) {
    message.error(error.message || '创建失败')
  } finally {
    createSubmitting.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <!-- ==================== 筛选区 ==================== -->
    <n-card class="app-card filter-card">
      <n-space :wrap="true" align="center">
        <n-input
          v-model:value="query.keyword"
          placeholder="搜索编号、标题或需求说明"
          clearable
          style="width: 240px"
          @keyup.enter="loadData"
        />
        <n-select
          v-model:value="query.status"
          placeholder="状态"
          clearable
          :options="TASK_STATUSES"
          style="width: 130px"
          @update:value="loadData"
        />
        <n-select
          v-model:value="query.priority"
          placeholder="优先级"
          clearable
          :options="TASK_PRIORITIES"
          style="width: 170px"
          @update:value="loadData"
        />
        <n-select
          v-model:value="query.module"
          placeholder="功能模块"
          clearable
          :options="FEEDBACK_MODULES"
          style="width: 150px"
          @update:value="loadData"
        />
        <n-button @click="loadData">查询</n-button>
        <n-button quaternary @click="handleReset">重置</n-button>

        <n-button
          v-if="canCreate"
          type="primary"
          secondary
          @click="openCreateModal"
        >
          新建改进任务
        </n-button>
      </n-space>
    </n-card>

    <!-- 失败态 -->
    <n-card v-if="loadError" class="app-card">
      <n-result status="error" title="加载失败" :description="loadError">
        <template #footer>
          <n-button type="primary" @click="loadData">重新加载</n-button>
      </template>
      </n-result>
    </n-card>

    <!-- 加载态 -->
    <div v-else-if="loading" class="board">
      <n-card v-for="i in 5" :key="i" class="app-card column">
        <n-spin size="medium" style="display: block; margin: 40px auto" />
      </n-card>
    </div>

    <!-- 看板 -->
    <div v-else class="board">
      <div v-for="col in columns" :key="col.status.value" class="column">
        <div class="column-head">
          <n-space align="center" :size="6">
            <span
              class="dot"
              :style="{ background: statusColor[col.status.value] || 'var(--c-text-3)' }"
            />
            <n-text strong style="font-size: 13px">
              {{ col.status.label }}
            </n-text>
          </n-space>
          <n-text depth="3" style="font-size: 12px">
            {{ col.items.length }}
          </n-text>
        </div>

        <div class="column-body">
          <n-empty
            v-if="col.items.length === 0"
            description="暂无任务"
            size="small"
            style="margin-top: 24px"
          />

          <n-card
            v-for="task in col.items"
            :key="task.id"
            size="small"
            class="task-card"
            @click="router.push({ name: 'task-detail', params: { id: task.id } })"
          >
            <div class="task-top">
              <n-tag
                size="tiny"
                :bordered="false"
                :type="priorityType[task.priority] || 'default'"
              >
                {{ task.priority }}
              </n-tag>
              <n-text class="text-mono" depth="3" style="font-size: 11px">
                {{ task.taskNo }}
              </n-text>
            </div>

            <div class="task-title">{{ task.title }}</div>

            <n-space justify="space-between" align="center" style="margin-top: 10px">
              <n-text depth="3" style="font-size: 11px">
                {{ task.issueNo ? `来源 ${task.issueNo}` : '无来源问题' }}
              </n-text>
              <n-text depth="3" style="font-size: 11px">
                {{ task.assigneeName || '未指派' }}
              </n-text>
            </n-space>
          </n-card>
        </div>
      </div>
    </div>

    <!-- ==================== 新建任务弹窗 ==================== -->
    <n-modal
      v-model:show="showCreateModal"
      preset="card"
      title="新建改进任务"
      style="width: 680px"
    >
      <n-form
        ref="createFormRef"
        :model="createForm"
        :rules="createRules"
        label-placement="top"
      >
        <n-form-item label="来源问题" path="issueId">
          <n-select
            v-model:value="createForm.issueId"
            :options="issueOptions"
            placeholder="选择该任务要解决的问题"
            filterable
          />
        </n-form-item>

        <n-form-item label="任务标题" path="title">
          <n-input
            v-model:value="createForm.title"
            placeholder="如：修复发票上传大文件超时问题"
            maxlength="200"
            show-count
          />
        </n-form-item>

        <n-form-item label="需求说明">
          <n-input
            v-model:value="createForm.requirement"
            type="textarea"
            :rows="4"
            placeholder="需要开发做什么"
          />
        </n-form-item>

        <n-form-item label="验收条件">
          <n-input
            v-model:value="createForm.acceptanceCriteria"
            type="textarea"
            :rows="3"
            placeholder="如何判断这个任务已完成"
          />
        </n-form-item>

        <n-space :wrap="false" align="start">
          <n-form-item label="优先级" path="priority" style="width: 180px">
            <n-select
              v-model:value="createForm.priority"
              :options="TASK_PRIORITIES"
            />
          </n-form-item>

          <n-form-item label="定级理由" path="priorityReason" style="flex: 1">
            <n-input
              v-model:value="createForm.priorityReason"
              placeholder="为什么定这个级别？请填写客观依据"
            />
          </n-form-item>
        </n-space>
      </n-form>

      <template #footer>
        <n-space justify="end">
          <n-button quaternary @click="showCreateModal = false">取消</n-button>
          <n-button
            type="primary"
            :loading="createSubmitting"
            @click="submitCreate"
          >
            创建任务
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

/* 看板列宽可伸缩：五列在常规宽度下正好铺满，
 * 窗口再窄才降级为横向滚动，避免最后一列被切掉一半 */
.board {
  display: flex;
  gap: 12px;
  overflow-x: auto;
  padding-bottom: 12px;
  align-items: flex-start;
}

.column {
  flex: 1 1 180px;
  min-width: 180px;
  background-color: rgba(244, 246, 255, 0.82);
  border: 1px solid var(--c-border-soft);
}

.column-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 10px;
  margin-bottom: 10px;
  border-bottom: 1px solid var(--c-border-soft);
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  display: inline-block;
}

.column-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 120px;
}

.task-card {
  background-color: rgba(245, 247, 255, 0.72);
  border: 1px solid var(--c-border-soft);
  cursor: pointer;
  transition: border-color 0.15s ease, transform 0.15s ease;
}

.task-card:hover {
  border-color: var(--c-primary);
  transform: translateY(-1px);
}

.task-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.task-title {
  color: var(--c-text-1);
  font-size: 13px;
  line-height: 1.5;
}
</style>
