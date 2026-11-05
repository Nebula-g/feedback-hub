<script setup>
/**
 * 404 页面
 *
 * <p>六态反馈中的「空态」在路由层面的体现：
 * 用户可能记错了地址，或者页面已经下线，
 * 需要给出明确的出口而不是一片空白。
 */
import { useRouter } from 'vue-router'
import { NResult, NButton, NSpace, NCard } from 'naive-ui'

const router = useRouter()
</script>

<template>
  <div class="page-container">
    <n-card class="app-card">
      <n-result
        status="404"
        title="页面不存在"
        description="请检查地址是否正确，或返回工作台继续操作"
      >
        <template #footer>
          <n-space justify="center">
            <n-button @click="router.back()">返回上一页</n-button>
            <n-button type="primary" @click="router.push({ name: 'feedback-list' })">
              回到反馈列表
            </n-button>
          </n-space>
        </template>
      </n-result>
    </n-card>
  </div>
</template>
