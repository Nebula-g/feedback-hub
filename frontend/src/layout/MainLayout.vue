<script setup>
/**
 * 主框架布局
 *
 * <p>结构：左侧悬浮玻璃侧边栏 + 右侧（顶部搜索栏 + 内容区），
 * 整体叠在动态极光背景之上。
 *
 * <p><b>为什么不再用 n-layout 而改普通 div</b>：
 * n-layout 的侧栏/顶栏是铺满屏幕的直角块，无法做出参考稿里
 * "四角圆润、带外边距、浮在背景上"的卡片感；
 * 强行用 :deep() 覆盖它的内部结构，反而比直接写 flex 更脆弱。
 *
 * <p><b>侧边栏菜单为什么从路由表动态生成</b>：
 * 菜单项与路由配置写在同一个 meta 里，新增页面只需改路由表一处，
 * 不用在菜单组件里再维护一份平行配置——
 * 那种"两处配置"的模式迟早会出现菜单和权限不一致的 bug。
 *
 * <p><b>角色过滤</b>：meta.roles 决定菜单是否显示。
 * 注意这只是展示层过滤，真正的权限校验始终在后端
 * （@PreAuthorize + Service 内的数据归属判断）。
 * 前端隐藏只是体验优化，不能当作安全手段。
 */
import { computed, h, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NMenu, NDropdown, NInput, NAvatar, NPopover, NEmpty, NButton } from 'naive-ui'

import AppIcon from '@/components/AppIcon.vue'
import AuroraBackground from '@/components/AuroraBackground.vue'
import { useUserStore } from '@/stores/user'
import { useNoticeStore } from '@/stores/notification'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const noticeStore = useNoticeStore()

/** 通知 / 消息两个面板的显隐 */
const showNoticePanel = ref(false)
const showMessagePanel = ref(false)

// 登录后启动未读数轮询，离开布局时停止
onMounted(() => {
  noticeStore.startPolling()
})
onUnmounted(() => {
  noticeStore.stopPolling()
})

/** 通知类型 → 面板图标与主题色 */
const NOTICE_TYPE_META = {
  TASK_ASSIGNED: { icon: 'checkmark-done', color: '#7a8cf8' },
  TASK_STATUS: { icon: 'alert-circle', color: '#f0a35e' },
  FEEDBACK_STATUS: { icon: 'chatbubbles', color: '#59c3ad' },
  AI_GENERATED: { icon: 'sparkles', color: '#b48cf2' }
}

/**
 * 相对时间格式化（面板里"5 分钟前"比完整时间戳易读）
 *
 * @param {string} value ISO 时间串
 * @returns {string} 相对时间
 */
function timeAgo(value) {
  if (!value) return ''
  const diff = Date.now() - new Date(value).getTime()
  const min = Math.floor(diff / 60000)
  if (min < 1) return '刚刚'
  if (min < 60) return `${min} 分钟前`
  const hour = Math.floor(min / 60)
  if (hour < 24) return `${hour} 小时前`
  const day = Math.floor(hour / 24)
  if (day < 7) return `${day} 天前`
  return String(value).replace('T', ' ').slice(5, 16)
}

/**
 * 打开通知面板：每次都拉最新列表
 */
async function openNoticePanel() {
  showMessagePanel.value = false
  showNoticePanel.value = true
  await noticeStore.refreshNotifications()
}

/**
 * 点击通知条目：标已读 + 跳转
 *
 * @param {object} item 通知项
 */
async function handleNoticeClick(item) {
  await noticeStore.markRead(item)
  showNoticePanel.value = false
  if (item.link) router.push(item.link)
}

/**
 * 点击消息聚合条目：标记该对象已读 + 跳详情页
 *
 * @param {object} group 聚合条目
 */
async function handleMessageClick(group) {
  const name = group.bizType === 'TASK' ? 'task-detail' : 'feedback-detail'
  showNoticePanel.value = false
  showMessagePanel.value = false
  noticeStore.removeMsgGroup(group)
  await router.push({ name, params: { id: group.bizId } })
}

/** 顶栏搜索关键字 */
const keyword = ref('')

/**
 * 根据路由表生成侧边栏菜单
 *
 * <p>只保留 meta.menu === true 的路由，并按当前角色过滤。
 */
const menuOptions = computed(() => {
  const layoutRoute = router.options.routes.find((r) => r.path === '/')
  if (!layoutRoute) return []

  return layoutRoute.children
    .filter((child) => child.meta?.menu === true)
    .filter((child) => {
      const roles = child.meta?.roles
      return !roles || roles.length === 0 || roles.includes(userStore.role)
    })
    .map((child) => ({
      label: child.meta.title,
      key: child.name,
      icon: renderIcon(child.meta.icon || 'ellipse')
    }))
})

/**
 * 图标渲染函数
 *
 * <p>Naive UI 的 menu 需要返回一个渲染函数，这里复用 AppIcon，
 * 保证菜单图标与顶栏图标是同一套描边风格。
 *
 * @param {string} name 图标名
 * @returns {Function} 渲染函数
 */
function renderIcon(name) {
  return () => h(AppIcon, { name, size: 18 })
}

/** 当前选中的菜单项 */
const activeKey = computed(() => route.name)

/** 当前页面标题（面包屑位置） */
const pageTitle = computed(() => route.meta.title || '')

/**
 * 是否显示顶栏返回按钮
 *
 * <p>仪表盘是登录后的落地首页，没有"上一页"语义，其余页面都显示。
 */
const showBack = computed(() => route.name !== 'dashboard')

/**
 * 顶栏返回按钮：回到上一页
 *
 * <p>优先走浏览器历史（history.state.back 由 vue-router 维护，
 * 直接进页面/新开标签时为空），此时兜底回仪表盘，保证不会点出登录页。
 */
function goBack() {
  if (window.history.state?.back) {
    router.back()
  } else {
    router.push({ name: 'dashboard' })
  }
}

// ==================== 页面停留位置记忆 ====================

/** 内容区滚动容器（.app-content 是唯一滚动层，window 不滚） */
const contentRef = ref(null)

/**
 * 停留位置缓存：key = 路由 fullPath（含 query，搜索结果各自记忆），value = scrollTop
 *
 * <p>为什么不用 vue-router 的 scrollBehavior：
 * 它只能控制 window 滚动或锚点元素，够不到布局内部的滚动容器，
 * 所以在布局层自己记录/恢复。
 */
const scrollMemories = new Map()

/**
 * 恢复期间的抑制开关
 *
 * <p>路由切换瞬间旧内容卸载、容器高度骤减，浏览器会钳小 scrollTop
 * 并触发一次 scroll 事件 —— 这个值是被动的"垃圾数据"，若不屏蔽，
 * 会把目标页真实的停留位置覆盖掉。
 */
let restorePending = false

/** 滚动时持续记录，保证离开前的最后停留点一定被记到 */
function handleContentScroll() {
  if (restorePending || !route.fullPath) return
  scrollMemories.set(route.fullPath, contentRef.value.scrollTop)
}

// 路由变化时恢复目标页上次的停留位置（首次进入无记录，落到顶部）
watch(
  () => route.fullPath,
  (to, from) => {
    if (from) restoreScroll(to)
  }
)

/**
 * 恢复指定页的停留位置
 *
 * <p><b>为什么不能一次设置就完事</b>：路由过渡是 mode="out-in"（旧页先淡出
 * 再插入新页），且列表页内容高度会波动（骨架屏→数据行→图片加载，实测
 * 801 → 562 → 739 反复横跳），恢复瞬间高度不够会被浏览器钳制，之后高度
 * 变化又会把位置挤掉 —— 一次性设置必然失效。
 *
 * <p>策略：每 80ms 尽力恢复一次；当内容高度连续约 0.5s 不再变化
 * （或累计超过 3s）时做最终定位并结束。期间用户滚轮介入则立即让位。
 *
 * @param {string} fullPath 目标路由 fullPath
 */
function restoreScroll(fullPath) {
  const seq = ++scrollRestoreSeq
  const saved = scrollMemories.get(fullPath) || 0
  const el0 = contentRef.value
  if (!el0) return
  if (saved === 0) {
    // 回顶部不依赖内容高度，直接置 0 即可
    el0.scrollTop = 0
    return
  }

  restorePending = true
  let lastHeight = -1
  let stableCount = 0
  let waited = 0

  const cancel = () => { scrollRestoreSeq++ } // 用户滚轮介入，立即让位
  el0.addEventListener('wheel', cancel, { once: true, passive: true })
  const cleanup = () => el0.removeEventListener('wheel', cancel)

  const tick = () => {
    if (seq !== scrollRestoreSeq) { cleanup(); return } // 期间又跳页了，放弃
    const el = contentRef.value
    if (!el) { cleanup(); return }
    el.scrollTop = saved // 每次都尽力恢复；高度未到时会被钳制，下一轮再补
    if (el.scrollHeight === lastHeight) stableCount++
    else { stableCount = 0; lastHeight = el.scrollHeight }
    waited += 80
    if (stableCount >= 6 || waited >= 3000) { // 高度稳定约 0.5s，或超时 3s
      cleanup()
      restorePending = false
      return
    }
    setTimeout(tick, 80)
  }
  tick()
}

/** 恢复轮次计数：防止上一轮的重试串到新路由上 */
let scrollRestoreSeq = 0

/** 用户下拉菜单 */
const userOptions = [
  { label: '个人信息', key: 'profile' },
  { label: '退出登录', key: 'logout' }
]

/**
 * 处理用户菜单点击
 *
 * @param {string} key 菜单项 key
 */
function handleUserSelect(key) {
  if (key === 'logout') {
    userStore.logout()
  }
  // profile 暂不实现，课程项目范围内无意义
}

/**
 * 点击菜单跳转
 *
 * @param {string} key 路由 name
 */
function handleMenuUpdate(key) {
  if (key !== route.name) router.push({ name: key })
}

/**
 * 顶栏搜索
 *
 * <p>直接复用反馈列表已有的 keyword 查询参数，
 * 不新增后端接口——搜索能力本来就存在于列表页。
 */
function handleSearch() {
  const value = keyword.value.trim()
  if (!value) return
  router.push({ name: 'feedback-list', query: { keyword: value } })
}
</script>

<template>
  <div class="app-shell">
    <!-- 动态背景：固定在最底层，z-index 0，所有内容层在 z-index 1 之上 -->
    <AuroraBackground variant="app" />

    <div class="app-shell__body">
      <!-- ==================== 侧边栏 ==================== -->
      <aside class="sider glass">
        <div class="sider__brand">
          <div class="brand-logo">
            <AppIcon name="chatbubbles" :size="19" />
          </div>
          <div class="brand-text">
            <div class="brand-title">反馈改进助手</div>
            <div class="brand-sub">Feedback Hub</div>
          </div>
        </div>

        <n-menu
          class="sider__menu"
          :value="activeKey"
          :options="menuOptions"
          :item-height="42"
          @update:value="handleMenuUpdate"
        />

        <div class="sider__footer">
          <n-dropdown :options="userOptions" trigger="click" @select="handleUserSelect">
            <div class="user-card">
              <n-avatar round :size="34" class="user-avatar">
                {{ userStore.displayName.charAt(0) }}
              </n-avatar>
              <div class="user-meta">
                <div class="user-name">{{ userStore.displayName }}</div>
                <div class="user-role">{{ userStore.roleLabel || '未登录' }}</div>
              </div>
              <AppIcon name="chevron-down" :size="16" class="user-arrow" />
            </div>
          </n-dropdown>
        </div>
      </aside>

      <!-- ==================== 右侧主区域 ==================== -->
      <div class="app-main">
        <header class="topbar glass">
          <div class="topbar__title">
            <!-- 全局返回按钮：圆形图标，dashboard 落地页隐藏 -->
            <button
              v-if="showBack"
              class="back-btn"
              type="button"
              title="返回上一页"
              @click="goBack"
            >
              <AppIcon name="chevron-left" :size="18" />
            </button>
            <span class="topbar__page">{{ pageTitle }}</span>
          </div>

          <div class="topbar__search">
            <n-input
              v-model:value="keyword"
              round
              placeholder="搜索反馈、问题或任务"
              class="search-input"
              @keyup.enter="handleSearch"
            >
              <template #prefix>
                <AppIcon name="search" :size="16" />
              </template>
            </n-input>
          </div>

          <div class="topbar__actions">
            <!-- ==================== 通知面板 ==================== -->
            <n-popover
              v-model:show="showNoticePanel"
              trigger="click"
              placement="bottom-end"
              :width="360"
              raw
              class="notice-popover"
              @update:show="(v) => v && openNoticePanel()"
            >
              <template #trigger>
                <button class="icon-btn" type="button" title="通知">
                  <AppIcon name="bell" :size="18" />
                  <span v-if="noticeStore.badgeText" class="icon-btn__badge">
                    {{ noticeStore.badgeText }}
                  </span>
                </button>
              </template>

              <div class="panel">
                <div class="panel__head">
                  <span class="panel__title">通知</span>
                  <n-button
                    quaternary
                    size="tiny"
                    type="primary"
                    :disabled="noticeStore.unreadCount === 0"
                    @click="noticeStore.markAllRead()"
                  >
                    全部已读
                  </n-button>
                </div>

                <n-empty
                  v-if="noticeStore.notifications.length === 0"
                  description="暂无通知"
                  size="small"
                  class="panel__empty"
                />
                <div v-else class="panel__list">
                  <div
                    v-for="item in noticeStore.notifications"
                    :key="item.id"
                    class="notice-item"
                    :class="{ 'notice-item--unread': !item.isRead }"
                    @click="handleNoticeClick(item)"
                  >
                    <span
                      class="notice-item__icon"
                      :style="{ background: (NOTICE_TYPE_META[item.type] || NOTICE_TYPE_META.TASK_STATUS).color + '22', color: (NOTICE_TYPE_META[item.type] || NOTICE_TYPE_META.TASK_STATUS).color }"
                    >
                      <AppIcon :name="(NOTICE_TYPE_META[item.type] || NOTICE_TYPE_META.TASK_STATUS).icon" :size="15" />
                    </span>
                    <div class="notice-item__body">
                      <div class="notice-item__title">{{ item.title }}</div>
                      <div class="notice-item__content">{{ item.content }}</div>
                      <div class="notice-item__time">{{ timeAgo(item.createTime) }}</div>
                    </div>
                    <span v-if="!item.isRead" class="notice-item__dot"></span>
                  </div>
                </div>

                <div class="panel__foot">
                  <n-button text size="tiny" type="primary" @click="router.push({ name: 'notification-center' })">
                    查看全部
                  </n-button>
                </div>
              </div>
            </n-popover>

            <!-- ==================== 消息面板 ==================== -->
            <n-popover
              v-model:show="showMessagePanel"
              trigger="click"
              placement="bottom-end"
              :width="360"
              raw
              class="notice-popover"
            >
              <template #trigger>
                <button class="icon-btn" type="button" title="消息">
                  <AppIcon name="mail" :size="18" />
                  <span v-if="noticeStore.msgGroups.length" class="icon-btn__badge">
                    {{ noticeStore.msgGroups.length > 99 ? '99+' : noticeStore.msgGroups.length }}
                  </span>
                </button>
              </template>

              <div class="panel">
                <div class="panel__head">
                  <span class="panel__title">消息</span>
                  <span class="panel__hint">我录入/负责的对象</span>
                </div>

                <n-empty
                  v-if="noticeStore.msgGroups.length === 0"
                  description="暂无新消息"
                  size="small"
                  class="panel__empty"
                />
                <div v-else class="panel__list">
                  <div
                    v-for="group in noticeStore.msgGroups"
                    :key="group.bizType + group.bizId"
                    class="msg-item"
                    @click="handleMessageClick(group)"
                  >
                    <div class="msg-item__row">
                      <span class="msg-item__title">{{ group.title || '（无标题）' }}</span>
                      <span class="msg-item__count">{{ group.count }}</span>
                    </div>
                    <div class="msg-item__content">{{ group.lastContent }}</div>
                    <div class="msg-item__time">
                      {{ group.bizType === 'TASK' ? '任务' : '反馈' }} · {{ timeAgo(group.lastTime) }}
                    </div>
                  </div>
                </div>
              </div>
            </n-popover>

            <n-dropdown :options="userOptions" trigger="click" @select="handleUserSelect">
              <n-avatar round :size="34" class="topbar__avatar">
                {{ userStore.displayName.charAt(0) }}
              </n-avatar>
            </n-dropdown>
          </div>
        </header>

        <main ref="contentRef" class="app-content" @scroll.passive="handleContentScroll">
          <router-view v-slot="{ Component }">
            <transition name="fade" mode="out-in">
              <component :is="Component" />
            </transition>
          </router-view>
        </main>
      </div>
    </div>
  </div>
</template>

<style scoped>
.app-shell {
  position: relative;
  height: 100vh;
  overflow: hidden;
}

/* 内容层统一抬到背景之上，这是"背景永不遮挡内容"的关键约束 */
.app-shell__body {
  position: relative;
  z-index: 1;
  display: flex;
  gap: 16px;
  height: 100%;
  padding: 16px;
}

/* ==================== 侧边栏 ==================== */
.sider {
  display: flex;
  flex-direction: column;
  flex: none;
  width: 218px;
  padding: 16px 10px 12px;
  border-radius: 22px;
}

.sider__brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 10px 16px;
}

.brand-logo {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 11px;
  color: #fff;
  background: linear-gradient(135deg, #8b9bfb, #a78bfa);
  box-shadow: 0 8px 18px -8px rgba(108, 123, 245, 0.9);
}

.brand-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--c-text-1);
  line-height: 1.3;
}

.brand-sub {
  font-size: 10px;
  letter-spacing: 0.06em;
  color: var(--c-text-3);
  line-height: 1.4;
  text-transform: uppercase;
}

.sider__menu {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
}

/* 菜单项做成圆角胶囊，未选中态保持透明底，选中态是实色主色 */
:deep(.n-menu-item-content) {
  padding-left: 12px !important;
}

:deep(.n-menu-item-content::before) {
  border-radius: 12px;
  transition: background-color 0.18s ease;
}

:deep(.n-menu-item-content-header) {
  font-size: 13.5px;
  font-weight: 500;
}

:deep(.n-menu-item) {
  margin: 2px 0;
}

.sider__footer {
  padding-top: 10px;
  margin-top: 8px;
  border-top: 1px solid var(--c-divider);
}

.user-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 14px;
  cursor: pointer;
  transition: background-color 0.18s ease;
}

.user-card:hover {
  background-color: rgba(108, 123, 245, 0.08);
}

.user-avatar {
  color: #fff;
  background: linear-gradient(135deg, #9aa8fc, #bda6fb);
  font-size: 13px;
  font-weight: 600;
}

.user-meta {
  flex: 1;
  min-width: 0;
}

.user-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--c-text-1);
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-role {
  font-size: 11px;
  color: var(--c-text-3);
  line-height: 1.4;
}

.user-arrow {
  color: var(--c-text-3);
}

/* ==================== 右侧主干 ==================== */
.app-main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  gap: 16px;
}

.topbar {
  display: flex;
  align-items: center;
  flex: none;
  gap: 20px;
  height: 64px;
  padding: 0 20px;
  border-radius: 20px;
}

.topbar__title {
  display: flex;
  align-items: center;
  flex: none;
  gap: 10px;
}

/* 圆形返回按钮：与右侧 icon-btn 同一套底色/描边，只是圆角收成整圆 */
.back-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  padding: 0;
  color: var(--c-text-2);
  background: rgba(255, 255, 255, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.8);
  border-radius: 50%;
  cursor: pointer;
  transition: color 0.18s ease, background-color 0.18s ease, transform 0.18s ease;
}

.back-btn:hover {
  color: var(--c-primary);
  background-color: #fff;
  transform: translateX(-2px);
}

.back-btn:active {
  transform: translateX(0);
}

.topbar__page {
  font-size: 15px;
  font-weight: 700;
  color: var(--c-text-1);
  letter-spacing: 0.01em;
}

.topbar__search {
  flex: 1;
  max-width: 420px;
  margin-left: 10px;
}

.search-input :deep(.n-input__border),
.search-input :deep(.n-input__state-border) {
  border-radius: 999px;
}

.search-input :deep(.n-input__prefix) {
  color: var(--c-text-3);
}

.search-input {
  --n-height: 38px !important;
  max-width: 420px;
}

.topbar__actions {
  display: flex;
  align-items: center;
  flex: none;
  gap: 6px;
  margin-left: auto;
}

.icon-btn {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  padding: 0;
  color: var(--c-text-2);
  background: rgba(255, 255, 255, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.8);
  border-radius: 12px;
  cursor: pointer;
  transition: color 0.18s ease, background-color 0.18s ease;
}

.icon-btn:hover {
  color: var(--c-primary);
  background-color: #fff;
}

.icon-btn__dot {
  position: absolute;
  top: 8px;
  right: 9px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #f0738a;
  box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.9);
}

/* 数字角标：有具体未读数时替代小圆点 */
.icon-btn__badge {
  position: absolute;
  top: -5px;
  right: -5px;
  min-width: 17px;
  height: 17px;
  padding: 0 4px;
  font-size: 10.5px;
  font-weight: 600;
  line-height: 17px;
  color: #fff;
  text-align: center;
  background: #f0738a;
  border-radius: 9px;
  box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.95);
}

.topbar__avatar {
  margin-left: 4px;
  color: #fff;
  background: linear-gradient(135deg, #9aa8fc, #bda6fb);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.app-content {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  border-radius: 22px;
  /* 禁用浏览器的 scroll anchoring：列表数据异步渲染时内容高度会变，
     锚定机制会自作主张调整 scrollTop，把恢复的停留位置改掉
     （实测 282 被悄悄调成 105） */
  overflow-anchor: none;
}

/* 路由切换淡入淡出，避免生硬跳变 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.16s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* 窄屏：侧边栏收成图标条，保证内容区不被挤压 */
@media (max-width: 1180px) {
  .sider {
    width: 76px;
    padding: 16px 8px 12px;
  }

  .brand-text,
  .user-meta,
  .user-arrow,
  .sidebar-only {
    display: none;
  }

  .sider__brand {
    justify-content: center;
    padding-left: 0;
    padding-right: 0;
  }

  :deep(.n-menu-item-content) {
    justify-content: center;
    padding-left: 0 !important;
  }

  :deep(.n-menu-item-content-header) {
    display: none;
  }

  .user-card {
    justify-content: center;
    padding: 8px 0;
  }
}
</style>

<!-- 非 scoped：raw popover 内容 teleport 到 body，scoped 选择器够不到 -->
<style>
.notice-popover {
  padding: 0;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(255, 255, 255, 0.9);
  border-radius: 16px;
  box-shadow: 0 12px 40px rgba(96, 112, 190, 0.18);
  -webkit-backdrop-filter: blur(18px);
  backdrop-filter: blur(18px);
  overflow: hidden;
}

.panel {
  display: flex;
  flex-direction: column;
  max-height: 420px;
}

.panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex: none;
  padding: 12px 16px 8px;
}

.panel__title {
  font-size: 14px;
  font-weight: 600;
  color: #2a2f45;
}

.panel__hint {
  font-size: 11.5px;
  color: #9298ad;
}

.panel__empty {
  padding: 28px 0;
}

.panel__list {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px;
}

.panel__foot {
  flex: none;
  padding: 8px 16px 10px;
  text-align: center;
  border-top: 1px solid rgba(122, 136, 190, 0.12);
}

/* ---------- 通知条目 ---------- */
.notice-item {
  position: relative;
  display: flex;
  gap: 10px;
  padding: 10px;
  border-radius: 12px;
  cursor: pointer;
  transition: background-color 0.15s ease;
}

.notice-item:hover {
  background: rgba(122, 136, 190, 0.08);
}

.notice-item__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
  width: 32px;
  height: 32px;
  border-radius: 10px;
}

.notice-item__body {
  flex: 1;
  min-width: 0;
}

.notice-item__title {
  font-size: 13px;
  font-weight: 600;
  color: #2a2f45;
}

.notice-item--unread .notice-item__title {
  color: #4a56c7;
}

.notice-item__content {
  margin-top: 2px;
  font-size: 12.5px;
  line-height: 1.5;
  color: #5d6378;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.notice-item__time {
  margin-top: 3px;
  font-size: 11px;
  color: #9aa0b5;
}

.notice-item__dot {
  position: absolute;
  top: 14px;
  right: 12px;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #f0738a;
}

/* ---------- 消息聚合条目 ---------- */
.msg-item {
  padding: 10px;
  border-radius: 12px;
  cursor: pointer;
  transition: background-color 0.15s ease;
}

.msg-item:hover {
  background: rgba(122, 136, 190, 0.08);
}

.msg-item__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.msg-item__title {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  font-weight: 600;
  color: #2a2f45;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.msg-item__count {
  flex: none;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  font-size: 11px;
  font-weight: 600;
  line-height: 18px;
  color: #fff;
  text-align: center;
  background: #7a8cf8;
  border-radius: 9px;
}

.msg-item__content {
  margin-top: 3px;
  font-size: 12.5px;
  line-height: 1.5;
  color: #5d6378;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.msg-item__time {
  margin-top: 3px;
  font-size: 11px;
  color: #9aa0b5;
}
</style>
