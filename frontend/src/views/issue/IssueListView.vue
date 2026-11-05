<script setup>
/**
 * 问题列表页
 *
 * <p>问题是「反馈」与「任务」之间的中间层：
 * 多条相似反馈归为一个问题，一个问题产出一个改进任务。
 *
 * <p><b>关键字段：是否被人工修改过</b>
 * 列表里用标签直接标出「AI 原样采纳」与「PM 已修改」，
 * 这是验收场景 5 的一眼可见证据——
 * 不用点进详情、不用查数据库，列表上就能看出 AI 产出和人工决策是分开的。
 */
import { ref, reactive, onMounted, computed, h } from 'vue'
import { useRouter } from 'vue-router'
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
  NModal,
  NForm,
  NFormItem,
  useMessage
} from 'naive-ui'

import { fetchIssuePage, createIssue } from '@/api/issue'
import { FEEDBACK_MODULES, moduleLabel } from '@/constants/enums'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const message = useMessage()
const userStore = useUserStore()

// ==================== 列表状态 ====================

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
const filtered = ref(false)

const query = reactive({
  pageNo: 1,
  pageSize: 10,
  module: null,
  sourceType: null,
  keyword: ''
})

/** 来源类型筛选选项 */
const sourceTypeOptions = [
  { label: 'AI 生成', value: 'AI' },
  { label: '手工创建', value: 'MANUAL' }
]

// ==================== 表格列 ====================

const columns = [
  {
    title: '编号',
    key: 'issueNo',
    width: 100,
    render: (row) =>
      h('span', { class: 'text-mono', style: 'color:var(--c-text-2)' }, row.issueNo)
  },
  {
    title: '问题标题',
    key: 'title',
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
    key: 'sourceType',
    width: 92,
    render: (row) =>
      h(
        NTag,
        {
          size: 'small',
          bordered: false,
          type: row.sourceType === 'AI' ? 'info' : 'default'
        },
        { default: () => (row.sourceType === 'AI' ? 'AI 生成' : '手工创建') }
      )
  },
  {
    title: '关联反馈',
    key: 'feedbackIds',
    width: 88,
    align: 'right',
    render: (row) => `${row.feedbackIds?.length || 0} 条`
  },
  {
    title: '人工修改',
    key: 'modifiedFromAi',
    width: 100,
    render: (row) => {
      // 手工创建的问题没有"AI 建议"可比对，显示占位符
      if (row.sourceType !== 'AI') {
        return h('span', { style: 'color:var(--c-text-3)' }, '—')
      }
      return h(
        NTag,
        {
          size: 'small',
          bordered: false,
          type: row.modifiedFromAi ? 'warning' : 'success'
        },
        { default: () => (row.modifiedFromAi ? 'PM 已修改' : '原样采纳') }
      )
    }
  },
  {
    title: '确认人',
    key: 'confirmedByName',
    width: 84
  },
  {
    title: '确认时间',
    key: 'confirmTime',
    width: 120,
    render: (row) =>
      (row.confirmTime || '').replace('T', ' ').slice(5, 16) || '-'
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
          onClick: () =>
            router.push({ name: 'issue-detail', params: { id: row.id } })
        },
        { default: () => '查看详情' }
      )
  }
]

// ==================== 新建问题 ====================

/** 弹窗是否显示 */
const showCreateModal = ref(false)

/** 提交中 */
const createSubmitting = ref(false)

/** 表单数据 */
const createForm = reactive({
  title: '',
  description: '',
  module: 'EXPENSE_FORM',
  finalContent: '',
  acceptanceCriteria: ''
})

const createFormRef = ref(null)

const createRules = {
  title: [
    { required: true, message: '请填写问题标题', trigger: 'blur' },
    { max: 200, message: '标题不能超过 200 字', trigger: 'blur' }
  ],
  module: [{ required: true, message: '请选择功能模块', trigger: 'change' }]
}

/** 只有产品经理能创建问题 */
const canCreate = computed(
  () => userStore.role === 'PM' || userStore.role === 'ADMIN'
)

/**
 * 打开新建弹窗并重置表单
 */
function openCreateModal() {
  Object.assign(createForm, {
    title: '',
    description: '',
    module: 'EXPENSE_FORM',
    finalContent: '',
    acceptanceCriteria: ''
  })
  showCreateModal.value = true
}

/**
 * 提交新建问题
 */
async function submitCreate() {
  try {
    await createFormRef.value?.validate()
  } catch {
    return
  }

  createSubmitting.value = true
  try {
    const data = await createIssue({ ...createForm })
    message.success('问题创建成功')
    showCreateModal.value = false
    router.push({ name: 'issue-detail', params: { id: data.id } })
  } catch (error) {
    message.error(error.message || '创建失败')
  } finally {
    createSubmitting.value = false
  }
}

// ==================== 数据加载 ====================

async function loadData() {
  loading.value = true
  loadError.value = ''

  try {
    const params = {
      pageNo: query.pageNo,
      pageSize: query.pageSize
    }
    if (query.module) params.module = query.module
    if (query.sourceType) params.sourceType = query.sourceType
    if (query.keyword) params.keyword = query.keyword

    const data = await fetchIssuePage(params)
    rows.value = data?.records || []
    total.value = data?.total || 0
  } catch (error) {
    rows.value = []
    total.value = 0
    loadError.value = error.message || '加载问题列表失败'
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNo = 1
  filtered.value = true
  loadData()
}

function handleReset() {
  query.module = null
  query.sourceType = null
  query.keyword = ''
  query.pageNo = 1
  filtered.value = false
  loadData()
}

function handlePageChange(page) {
  query.pageNo = page
  loadData()
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
          placeholder="搜索标题、描述或编号"
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
          v-model:value="query.sourceType"
          placeholder="来源"
          clearable
          :options="sourceTypeOptions"
          style="width: 130px"
        />
        <n-button type="primary" @click="handleSearch">查询</n-button>
        <n-button quaternary @click="handleReset">重置</n-button>

        <n-button v-if="canCreate" type="primary" secondary @click="openCreateModal">
          手工新建问题
        </n-button>
      </n-space>
    </n-card>

    <!-- ==================== 列表区 ==================== -->
    <n-card class="app-card table-card">
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

        <n-empty
          v-if="!loading && rows.length === 0"
          :description="
            filtered ? '没有符合筛选条件的问题，试试放宽条件' : '还没有确认任何问题'
          "
          style="margin-top: 32px"
        >
          <template #extra>
            <n-button v-if="filtered" size="small" @click="handleReset">
              清空筛选条件
            </n-button>
          </template>
        </n-empty>

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
    </n-card>

    <!-- ==================== 新建问题弹窗 ==================== -->
    <n-modal
      v-model:show="showCreateModal"
      preset="card"
      title="手工新建问题"
      style="width: 620px"
    >
      <n-form
        ref="createFormRef"
        :model="createForm"
        :rules="createRules"
        label-placement="top"
      >
        <n-form-item label="问题标题" path="title">
          <n-input
            v-model:value="createForm.title"
            placeholder="用一句话描述问题，如：发票上传大文件时必然失败"
            maxlength="200"
            show-count
          />
        </n-form-item>

        <n-form-item label="功能模块" path="module">
          <n-select v-model:value="createForm.module" :options="FEEDBACK_MODULES" />
        </n-form-item>

        <n-form-item label="问题描述">
          <n-input
            v-model:value="createForm.description"
            type="textarea"
            :rows="4"
            placeholder="补充说明问题现象、复现步骤等"
            maxlength="5000"
            show-count
          />
        </n-form-item>

        <n-form-item label="需求说明">
          <n-input
            v-model:value="createForm.finalContent"
            type="textarea"
            :rows="4"
            placeholder="你期望改进后的效果"
          />
        </n-form-item>

        <n-form-item label="验收条件">
          <n-input
            v-model:value="createForm.acceptanceCriteria"
            type="textarea"
            :rows="3"
            placeholder="如何判断这个问题已解决，如：5MB 以上 PDF 可在 10 秒内上传成功"
          />
        </n-form-item>
      </n-form>

      <template #footer>
        <n-space justify="end">
          <n-button quaternary @click="showCreateModal = false">取消</n-button>
          <n-button type="primary" :loading="createSubmitting" @click="submitCreate">
            创建问题
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

.table-card {
  min-height: 420px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
