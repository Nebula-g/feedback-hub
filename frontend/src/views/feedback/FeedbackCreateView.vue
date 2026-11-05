<script setup>
/**
 * 反馈录入页
 *
 * <p><b>两种录入方式</b>：
 * <ol>
 *   <li><b>单条录入</b>：客服接到一个电话/工单后逐条填写。</li>
 *   <li><b>批量导入</b>：先粘贴多行文本解析成待导入列表（也可以直接粘贴截图，
 *       每张截图生成一条独立反馈），再逐条补充/修改内容、模块、来源与凭证截图，
 *       图片与反馈严格一一对应。这是解决"一周 60 条"这个核心痛点的入口。</li>
 * </ol>
 *
 * <p><b>关于"脱敏"提示</b>：录入界面刻意放了明确的脱敏说明，
 * 因为系统的定位就是"只存脱敏后的内容"，
 * 在入口处提醒比事后靠人自觉可靠。
 */
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import {
  NCard,
  NForm,
  NFormItem,
  NInput,
  NSelect,
  NButton,
  NRadioGroup,
  NRadioButton,
  NSpace,
  NAlert,
  NText,
  NUpload,
  NDivider,
  useMessage
} from 'naive-ui'

import { createFeedback, batchImportFeedback } from '@/api/feedback'
import { uploadImage } from '@/api/file'
import { FEEDBACK_MODULES, FEEDBACK_SOURCES } from '@/constants/enums'

const router = useRouter()
const message = useMessage()

/** 录入模式：single=单条，batch=批量 */
const mode = ref('single')

/** 是否提交中 */
const submitting = ref(false)

/** 已上传图片的访问地址列表 */
const imageUrls = ref([])

/** 单条录入表单 */
const form = reactive({
  content: '',
  module: 'EXPENSE_FORM',
  source: 'TICKET',
  customerTag: ''
})

/** 批量导入：待解析的原始文本 */
const batchText = ref('')

/**
 * 批量导入：解析后的待导入列表
 *
 * <p>每条独立维护内容 / 模块 / 来源 / 凭证截图，
 * 图片与反馈在这里建立一一对应关系。
 */
const batchItems = ref([])

/** 批量条目自增 key（作 v-for 的 key，避免用索引导致编辑状态错位） */
let batchItemKey = 0

/** 校验规则 */
const rules = {
  content: [
    { required: true, message: '请填写反馈内容', trigger: 'blur' },
    { max: 2000, message: '反馈内容不能超过 2000 字', trigger: 'blur' }
  ],
  module: [{ required: true, message: '请选择功能模块', trigger: 'change' }],
  source: [{ required: true, message: '请选择来源渠道', trigger: 'change' }]
}

const formRef = ref(null)

/**
 * 批量文本解析出的条数
 *
 * <p>过滤空行后再计数，让用户看到实际会导入多少条，
 * 避免包含空行时的数字困惑。
 */
const batchCount = computed(() => {
  return batchText.value
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line.length > 0).length
})

/**
 * 自定义上传实现
 *
 * <p>为什么不用 NUpload 自带的 action：
 * 需要携带 Authorization 头，而原生 action 模式无法自定义请求头。
 * 所以这里接管上传过程，手动调用接口并把返回的 URL 存下来。
 *
 * @param {object} options 上传选项
 */
async function customUpload({ file, onFinish, onError }) {
  try {
    const data = await uploadImage(file.file)
    if (!imageUrls.value.includes(data.url)) {
      imageUrls.value.push(data.url)
    }
    message.success(`图片已上传：${data.filename}`)
    onFinish()
  } catch (error) {
    message.error(error.message || '图片上传失败')
    onError()
  }
}

/**
 * 提交单条反馈
 */
async function submitSingle() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }

  submitting.value = true
  try {
    const data = await createFeedback({
      content: form.content,
      module: form.module,
      source: form.source,
      customerTag: form.customerTag || null,
      imageUrls: imageUrls.value
    })
    message.success('反馈录入成功')
    // 录入后跳到详情页，方便立刻确认内容无误
    router.push({ name: 'feedback-detail', params: { id: data.id } })
  } catch (error) {
    message.error(error.message || '录入失败')
  } finally {
    submitting.value = false
  }
}

/**
 * 提交批量导入
 *
 * <p>逐条提交解析列表里的内容与图片；
 * 前端先做完整性校验，避免后端 @NotBlank 整批拒绝。
 */
async function submitBatch() {
  const items = batchItems.value

  if (items.length === 0) {
    message.warning('请先粘贴反馈并解析，或直接粘贴截图')
    return
  }
  if (items.length > 200) {
    message.warning(`单次最多导入 200 条，当前 ${items.length} 条`)
    return
  }

  const emptyIdx = items
    .map((it, i) => (it.content.trim() ? -1 : i + 1))
    .filter((i) => i > 0)
  if (emptyIdx.length > 0) {
    message.warning(`第 ${emptyIdx.join('、')} 条反馈内容为空，请补齐文字描述或删除该条`)
    return
  }

  submitting.value = true
  try {
    const data = await batchImportFeedback({
      items: items.map((it) => ({
        content: it.content.trim(),
        module: it.module,
        source: it.source,
        customerTag: null,
        imageUrls: imagesOf(it)
      }))
    })
    message.success(`成功导入 ${data.count} 条反馈`)
    router.push({ name: 'feedback-list' })
  } catch (error) {
    message.error(error.message || '批量导入失败')
  } finally {
    submitting.value = false
  }
}

/** 根据当前模式分发提交 */
function handleSubmit() {
  if (mode.value === 'single') {
    submitSingle()
  } else {
    submitBatch()
  }
}

/**
 * 移除已上传图片
 *
 * @param {number} index 图片索引
 */
function removeImage(index) {
  imageUrls.value.splice(index, 1)
}

// ==================================================================
// 批量导入：解析 + 逐条编辑
// ==================================================================

/** 新建一条批量条目（模块/来源继承顶部默认值） */
function createBatchItem(content = '') {
  return {
    key: ++batchItemKey,
    content,
    module: form.module,
    source: form.source,
    fileList: []
  }
}

/**
 * 把粘贴文本按行解析成待导入列表
 *
 * <p>解析后清空文本框并追加到已有列表（可分多次粘贴），
 * 每条仍可继续编辑，不是"贴了就不能改"。
 */
function parseBatchText() {
  const lines = batchText.value
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line.length > 0)

  if (lines.length === 0) {
    message.warning('请先粘贴反馈内容')
    return
  }

  const room = 200 - batchItems.value.length
  if (room <= 0) {
    message.warning('已达单次 200 条上限')
    return
  }
  const accepted = lines.slice(0, room)
  accepted.forEach((line) => batchItems.value.push(createBatchItem(line)))
  batchText.value = ''
  if (accepted.length < lines.length) {
    message.warning(`已解析 ${accepted.length} 条，超出 200 条上限的 ${lines.length - accepted.length} 条未导入`)
  } else {
    message.success(`已解析 ${accepted.length} 条，可逐条修改或补充截图`)
  }
}

/** 手动追加一条空白条目 */
function appendBatchItem() {
  if (batchItems.value.length >= 200) {
    message.warning('已达单次 200 条上限')
    return
  }
  batchItems.value.push(createBatchItem())
}

/** 删除一条批量条目 */
function removeBatchItem(index) {
  batchItems.value.splice(index, 1)
}

/**
 * 收集某条已上传完成的图片 URL
 *
 * <p>两种上传路径统一约定：完成后把 url 写回 fileList 条目上，
 * 这里只做一次过滤，不再维护平行的 URL 数组（避免两处状态不同步）。
 */
function imagesOf(item) {
  return item.fileList.filter((f) => f.url).map((f) => f.url)
}

/**
 * 上传一张图片并把结果挂到指定条目
 *
 * <p>供"粘贴截图"路径使用；点击上传路径走 itemUpload。
 *
 * @param {object} item 批量条目
 * @param {File} file 图片文件
 */
async function uploadToItem(item, file) {
  if (item.fileList.length >= 5) {
    message.warning('每条反馈最多 5 张截图')
    return
  }
  try {
    const data = await uploadImage(file)
    item.fileList.push({
      id: `paste-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      name: data.filename,
      status: 'finished',
      url: data.url
    })
  } catch (error) {
    message.error(error.message || '图片上传失败')
  }
}

/**
 * 某条条目的自定义上传实现（点击/拖拽上传路径）
 *
 * <p>与单条录入的 customUpload 同一机制，区别是结果写回对应条目；
 * 用工厂函数把 item 绑进闭包，避免在模板里传额外参数。
 *
 * @param {object} item 批量条目
 */
function itemUpload(item) {
  return async ({ file, onFinish, onError }) => {
    try {
      const data = await uploadImage(file.file)
      file.url = data.url
      message.success(`图片已上传：${data.filename}`)
      onFinish()
    } catch (error) {
      message.error(error.message || '图片上传失败')
      onError()
    }
  }
}

/**
 * 批量条目内容区粘贴：截图直接附加到这一条
 *
 * <p>这是"图片对应不同反馈"的核心交互——
 * 光标在哪条反馈里，粘贴的截图就归哪条。
 */
function onItemPaste(item, event) {
  const files = Array.from(event.clipboardData?.files || []).filter((f) =>
    f.type.startsWith('image/')
  )
  if (files.length === 0) return
  event.preventDefault()
  files.forEach((f) => uploadToItem(item, f))
}

/**
 * 解析文本框粘贴：截图生成独立反馈条目
 *
 * <p>客服常从聊天工具直接复制截图，此时没有文字，
 * 先建条目占位，提示用户补一句文字描述（后端要求内容非空）。
 */
function onBatchTextPaste(event) {
  const files = Array.from(event.clipboardData?.files || []).filter((f) =>
    f.type.startsWith('image/')
  )
  if (files.length === 0) return
  event.preventDefault()
  if (batchItems.value.length >= 200) {
    message.warning('已达单次 200 条上限')
    return
  }
  files.forEach((f) => {
    const item = createBatchItem('')
    batchItems.value.push(item)
    uploadToItem(item, f)
  })
  message.info('截图已生成独立反馈条目，请为每条补充一句问题描述')
}
</script>

<template>
  <div class="page-container">
    <div class="form-wrapper">
      <n-card class="app-card">
        <template #header>
          <div class="card-header">
            <n-text strong style="font-size: 15px">录入用户反馈</n-text>
            <n-radio-group v-model:value="mode" size="small">
              <n-radio-button value="single">单条录入</n-radio-button>
              <n-radio-button value="batch">批量粘贴</n-radio-button>
            </n-radio-group>
          </div>
        </template>

        <n-alert type="info" :bordered="false" class="tip-alert">
          请录入<b>已脱敏</b>的反馈内容，不要包含客户姓名、手机号、公司全称等可识别信息。
          客户标识请使用内部编号（如 C-0017）。
        </n-alert>

        <n-form ref="formRef" :model="form" :rules="rules" label-placement="top">
          <!-- 公共字段：模块与来源 -->
          <n-space :wrap="false" align="start">
            <n-form-item label="功能模块" path="module" style="width: 200px">
              <n-select v-model:value="form.module" :options="FEEDBACK_MODULES" />
            </n-form-item>

            <n-form-item label="来源渠道" path="source" style="width: 200px">
              <n-select v-model:value="form.source" :options="FEEDBACK_SOURCES" />
            </n-form-item>

            <n-form-item v-if="mode === 'single'" label="客户编号" style="width: 200px">
              <n-input
                v-model:value="form.customerTag"
                placeholder="如 C-0017，可留空"
                maxlength="32"
              />
            </n-form-item>
          </n-space>

          <!-- 单条录入：反馈原文 -->
          <n-form-item v-if="mode === 'single'" label="反馈原文" path="content">
            <n-input
              v-model:value="form.content"
              type="textarea"
              placeholder="请用用户自己的话记录问题，尽量保留原始表述"
              :rows="8"
              show-count
              maxlength="2000"
            />
          </n-form-item>

          <!-- 批量导入：第一步粘贴解析 -->
          <n-form-item v-else label="粘贴反馈文本（每行一条）">
            <n-input
              v-model:value="batchText"
              type="textarea"
              placeholder="每行一条反馈，换行分隔。例如：&#10;报销单提交后一直转圈，最后提示超时失败&#10;发票上传超过5M就失败，也没提示原因&#10;&#10;也可以直接粘贴截图，每张截图会成为一条独立反馈"
              :rows="6"
              @paste="onBatchTextPaste"
            />
            <template #feedback>
              已识别 {{ batchCount }} 条文本；解析后每条可单独修改模块、来源并附带截图
            </template>
          </n-form-item>

          <!-- 批量导入：第二步逐条编辑 -->
          <template v-if="mode === 'batch'">
            <n-space justify="start" style="margin-bottom: 8px">
              <n-button
                size="small"
                type="primary"
                secondary
                :disabled="batchCount === 0"
                @click="parseBatchText"
              >
                解析为待导入列表（{{ batchCount }} 条）
              </n-button>
              <n-button size="small" dashed @click="appendBatchItem">+ 添加一条</n-button>
            </n-space>

            <div v-if="batchItems.length > 0" class="batch-list">
              <div
                v-for="(item, index) in batchItems"
                :key="item.key"
                class="batch-item"
                :class="{ 'batch-item--invalid': !item.content.trim() }"
              >
                <div class="batch-item__head">
                  <span class="batch-item__index">#{{ index + 1 }}</span>
                  <n-select
                    v-model:value="item.module"
                    :options="FEEDBACK_MODULES"
                    size="small"
                    style="width: 150px"
                  />
                  <n-select
                    v-model:value="item.source"
                    :options="FEEDBACK_SOURCES"
                    size="small"
                    style="width: 150px"
                  />
                  <span
                    v-if="!item.content.trim()"
                    class="batch-item__warn"
                  >
                    需补充文字描述
                  </span>
                  <n-button
                    quaternary
                    size="tiny"
                    type="error"
                    style="margin-left: auto"
                    @click="removeBatchItem(index)"
                  >
                    删除
                  </n-button>
                </div>

                <n-input
                  v-model:value="item.content"
                  type="textarea"
                  size="small"
                  :rows="2"
                  :autosize="{ minRows: 2, maxRows: 6 }"
                  maxlength="2000"
                  show-count
                  placeholder="反馈内容（可直接粘贴截图附加到本条）"
                  @paste="onItemPaste(item, $event)"
                />

                <n-upload
                  v-model:file-list="item.fileList"
                  list-type="image-card"
                  :max="5"
                  accept="image/png,image/jpeg,image/webp,image/gif"
                  :custom-request="itemUpload(item)"
                >
                  <template #default>
                    <span style="font-size: 12px">+ 截图</span>
                  </template>
                </n-upload>
              </div>
            </div>
          </template>

          <!-- 图片上传：仅单条模式 -->
          <n-form-item v-if="mode === 'single'" label="凭证截图（选填）">
            <n-upload
              list-type="image-card"
              :max="5"
              accept="image/png,image/jpeg,image/webp,image/gif"
              :custom-request="customUpload"
            >
              点击上传
            </n-upload>
          </n-form-item>
        </n-form>

        <n-divider />

        <n-space justify="end">
          <n-button quaternary @click="router.push({ name: 'feedback-list' })">
            取消
          </n-button>
          <n-button type="primary" :loading="submitting" @click="handleSubmit">
            {{ mode === 'single' ? '提交录入' : `导入 ${batchItems.length} 条` }}
          </n-button>
        </n-space>
      </n-card>
    </div>
  </div>
</template>

<style scoped>
/* 表单区宽度跟随 .page-container 统一限宽。
 * 不再单独限 860px——之前只有这页限宽，
 * 与其他页面切换时内容宽度跳变，是"页面大小来回变"的直接原因。 */
.form-wrapper {
  width: 100%;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.tip-alert {
  margin-bottom: 20px;
}

/* 批量条目列表：浅色卡片，与页面玻璃风格一致 */
.batch-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 12px;
}

.batch-item {
  padding: 12px 14px;
  border: 1px solid var(--c-border, rgba(108, 123, 245, 0.16));
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.55);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.batch-item--invalid {
  border-color: rgba(208, 48, 80, 0.45);
}

.batch-item__head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.batch-item__index {
  font-weight: 600;
  font-size: 13px;
  color: var(--c-primary, #6c7bf5);
  min-width: 28px;
}

.batch-item__warn {
  font-size: 12px;
  color: #d03050;
}

</style>
