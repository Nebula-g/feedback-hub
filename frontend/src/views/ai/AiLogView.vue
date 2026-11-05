<script setup>
/**
 * AI 调用记录页
 *
 * <p><b>本页是「可回查」这条要求的直接证据</b>。
 *
 * <p>每一次 AI 调用都会落库：谁在什么时候、用了什么模型、
 * 输入了什么材料、AI 返回了什么原文、耗时多久、成功还是失败。
 * 这样当有人质疑"这条结论是不是 AI 编的"时，
 * 可以调出当次调用的完整输入输出逐一核对。
 *
 * <p>失败的调用同样会记录，并且会标注降级原因——
 * 这证明系统确实经历过 AI 故障并正确处理了，
 * 而不是假装 AI 永远可用。
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
  NDescriptions,
  NDescriptionsItem,
  NCode,
  useMessage
} from 'naive-ui'

import { fetchAiLogPage } from '@/api/ai'

const router = useRouter()
const message = useMessage()

/** 日志列表 */
const rows = ref([])

/** 总数 */
const total = ref(0)

/** 加载中 */
const loading = ref(false)

/** 错误 */
const loadError = ref('')

/** 查询条件 */
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  bizType: null,
  status: null
})

/**
 * 业务类型选项（与后端 AiBizType 枚举严格一致）
 *
 * <p>三个取值分别对应 AI 在本系统中的三个作用点。
 */
const bizTypeOptions = [
  { label: '单条提取', value: 'EXTRACT' },
  { label: '聚类成卡片', value: 'CLUSTER' },
  { label: '起草需求', value: 'DRAFT' }
]

/** 状态选项 */
const statusOptions = [
  { label: '成功', value: 'SUCCESS' },
  { label: '失败', value: 'FAILED' },
  { label: '超时', value: 'TIMEOUT' },
  { label: '已降级', value: 'FALLBACK' }
]

/** 状态配色 */
function statusType(status) {
  const map = {
    SUCCESS: 'success',
    FAILED: 'error',
    TIMEOUT: 'warning',
    FALLBACK: 'info'
  }
  return map[status] || 'default'
}

/** 状态中文名 */
function statusLabel(status) {
  const map = {
    SUCCESS: '成功',
    FAILED: '失败',
    TIMEOUT: '超时',
    FALLBACK: '已降级'
  }
  return map[status] || status
}

/** 时间格式化 */
function fmt(value) {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '—'
}

/** 详情弹窗 */
const showDetailModal = ref(false)
const currentLog = ref(null)

/**
 * 打开详情弹窗
 *
 * @param {object} row 日志记录
 */
function openDetail(row) {
  currentLog.value = row
  showDetailModal.value = true
}

/**
 * 加载日志列表
 */
async function loadData() {
  loading.value = true
  loadError.value = ''

  try {
    const params = { pageNo: query.pageNo, pageSize: query.pageSize }
    if (query.bizType) params.bizType = query.bizType
    if (query.status) params.status = query.status

    const data = await fetchAiLogPage(params)
    rows.value = data?.records || []
    total.value = data?.total || 0
  } catch (error) {
    rows.value = []
    total.value = 0
    loadError.value = error.message || '加载 AI 调用记录失败'
  } finally {
    loading.value = false
  }
}

function handlePageChange(page) {
  query.pageNo = page
  loadData()
}

/** 表格列定义 */
const columns = [
  {
    title: '时间',
    key: 'createTime',
    width: 170,
    render: (row) => fmt(row.createTime)
  },
  {
    title: '业务类型',
    key: 'bizType',
    width: 130,
    render: (row) => row.bizTypeLabel || row.bizType || '—'
  },
  {
    title: '模型',
    key: 'model',
    minWidth: 160,
    ellipsis: { tooltip: true }
  },
  {
    title: '提供方',
    key: 'provider',
    width: 100,
    render: (row) =>
      h(
        NTag,
        {
          size: 'small',
          bordered: false,
          type: row.provider === 'LLM' ? 'success' : 'default'
        },
        { default: () => row.providerLabel || row.provider || '—' }
      )
  },
  {
    title: '耗时',
    key: 'costMs',
    width: 90,
    align: 'right',
    render: (row) => (row.costMs != null ? `${row.costMs} ms` : '—')
  },
  {
    title: '结果',
    key: 'status',
    width: 90,
    render: (row) =>
      h(
        NTag,
        { size: 'small', bordered: false, type: statusType(row.status) },
        { default: () => statusLabel(row.status) }
      )
  },
  {
    title: '操作人',
    key: 'operatorName',
    width: 100
  },
  {
    title: '操作',
    key: 'actions',
    width: 90,
    fixed: 'right',
    render: (row) =>
      h(
        NButton,
        { size: 'small', quaternary: true, onClick: () => openDetail(row) },
        { default: () => '查看原文' }
      )
  }
]

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <!-- ==================== 说明 ==================== -->
    <n-card class="app-card" style="margin-bottom: 16px">
      <n-text depth="3" style="font-size: 12px">
        本页记录每一次 AI 调用的完整输入输出。AI 生成的内容在人工确认前都只是草稿，
        确认后的正式内容会单独保存在问题记录中，两者互不影响、均可回查。
      </n-text>
    </n-card>

    <!-- ==================== 筛选区 ==================== -->
    <n-card class="app-card filter-card">
      <n-space :wrap="true" align="center">
        <n-select
          v-model:value="query.bizType"
          placeholder="业务类型"
          clearable
          :options="bizTypeOptions"
          style="width: 160px"
          @update:value="
            () => {
              query.pageNo = 1
              loadData()
            }
          "
        />
        <n-select
          v-model:value="query.status"
          placeholder="调用结果"
          clearable
          :options="statusOptions"
          style="width: 140px"
          @update:value="
            () => {
              query.pageNo = 1
              loadData()
            }
          "
        />
        <n-button
          quaternary
          @click="
            () => {
              query.bizType = null
              query.status = null
              query.pageNo = 1
              loadData()
            }
          "
        >
          重置
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

    <!-- 列表 -->
    <n-card v-else class="app-card table-card">
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
        description="还没有 AI 调用记录"
        style="margin-top: 32px"
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
    </n-card>

    <!-- ==================== 详情弹窗 ==================== -->
    <n-modal
      v-model:show="showDetailModal"
      preset="card"
      title="AI 调用详情"
      style="width: 780px"
    >
      <n-space vertical :size="16">
        <n-descriptions bordered :column="2" label-placement="left">
          <n-descriptions-item label="业务类型">
            {{ currentLog?.bizType }}
          </n-descriptions-item>
          <n-descriptions-item label="调用结果">
            <n-tag
              size="small"
              :bordered="false"
              :type="statusType(currentLog?.status)"
            >
              {{ statusLabel(currentLog?.status) }}
            </n-tag>
          </n-descriptions-item>
          <n-descriptions-item label="模型">
            {{ currentLog?.model }}
          </n-descriptions-item>
          <n-descriptions-item label="提供方">
            {{ currentLog?.provider }}
          </n-descriptions-item>
          <n-descriptions-item label="耗时">
            {{ currentLog?.costMs != null ? currentLog.costMs + ' ms' : '—' }}
          </n-descriptions-item>
          <n-descriptions-item label="操作人">
            {{ currentLog?.operatorName }}（{{ currentLog?.operatorRole }}）
          </n-descriptions-item>
          <n-descriptions-item label="调用时间">
            {{ fmt(currentLog?.createTime) }}
          </n-descriptions-item>
          <n-descriptions-item label="链路ID">
            <span class="text-mono">{{ currentLog?.traceId }}</span>
          </n-descriptions-item>
        </n-descriptions>

        <div v-if="currentLog?.errorMsg">
          <n-text depth="3" style="font-size: 12px">错误信息</n-text>
          <n-code :code="currentLog.errorMsg" language="text" word-wrap />
        </div>

        <div>
          <n-text depth="3" style="font-size: 12px">输入材料</n-text>
          <n-code
            :code="currentLog?.inputSnapshot || '—'"
            language="json"
            word-wrap
          />
        </div>

        <div>
          <n-text depth="3" style="font-size: 12px">AI 返回原文</n-text>
          <n-code
            :code="currentLog?.outputRaw || '—'"
            language="json"
            word-wrap
          />
        </div>
      </n-space>
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
