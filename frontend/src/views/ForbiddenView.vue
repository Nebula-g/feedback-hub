<script setup>
/**
 * 无访问权限页
 *
 * <p><b>为什么单独做一个页面而不是弹个提示</b>：
 * 权限被拒往往意味着用户"走错了地方"，
 * 需要告诉他「你是谁、这里需要什么身份、你可以去哪」三个信息，
 * 一个 toast 装不下这么多内容。
 *
 * <p>这是六态反馈中的「无权限态」。
 */
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NResult, NButton, NSpace, NTag, NText, NCard } from 'naive-ui'

import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

/** 需要的角色（由路由守卫传入） */
const needRole = computed(() => route.query.need || '')

/** 当前角色 */
const currentRole = computed(() => route.query.current || userStore.roleLabel)
</script>

<template>
  <div class="page-container">
    <n-card class="app-card">
      <n-result status="403" title="无访问权限" description="你没有访问该页面所需的角色">
        <template #default>
          <div class="role-info">
            <n-space vertical :size="8" align="center">
              <n-space align="center" :size="8">
                <n-text depth="3" style="font-size: 13px">当前身份</n-text>
                <n-tag size="small" :bordered="false">{{ currentRole }}</n-tag>
              </n-space>
              <n-space align="center" :size="8" v-if="needRole">
                <n-text depth="3" style="font-size: 13px">所需身份</n-text>
                <n-tag size="small" :bordered="false" type="info">{{ needRole }}</n-tag>
              </n-space>
            </n-space>
          </div>
        </template>

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

<style scoped>
.role-info {
  margin-top: 16px;
  padding: 16px;
  border: 1px solid var(--c-border-soft);
  border-radius: 8px;
  background-color: rgba(245, 247, 255, 0.72);
}
</style>
