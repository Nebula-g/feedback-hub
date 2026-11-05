import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

import {
  fetchUnreadCount,
  fetchLatestNotifications,
  markAllNotificationsRead,
  markNotificationRead
} from '@/api/notification'
import { fetchUnreadGroups } from '@/api/message'
import { useUserStore } from '@/stores/user'

/**
 * 通知 / 消息状态管理
 *
 * <p><b>为什么用 30s 轮询而不是 WebSocket</b>：
 * 当前阶段是演示项目，轮询实现简单、部署零依赖（不需要 STOMP 服务），
 * 且 30s 一次的未读数查询对 MySQL 几乎无压力。
 * 接口形态已按「未读数 + 列表」拆好，将来切 WebSocket
 * 只需把 fetchUnreadCount 换成推送订阅，UI 层不用动。
 *
 * <p><b>为什么不合并进 user store</b>：
 * 通知有自己独立的生命周期（登录后启动、登出后停止），
 * 单独成店避免 user store 越来越臃肿。
 */
export const useNoticeStore = defineStore('notice', () => {
  // ==================== 状态 ====================

  /** 通知未读数（角标） */
  const unreadCount = ref(0)

  /** 通知面板列表（最近 10 条） */
  const notifications = ref([])

  /** 未读评论聚合（消息面板） */
  const msgGroups = ref([])

  /** 轮询定时器句柄 */
  let timer = null

  // ==================== 计算属性 ====================

  /** 角标显示文案：0 不显示，99+ 封顶 */
  const badgeText = computed(() => {
    if (unreadCount.value <= 0) return ''
    return unreadCount.value > 99 ? '99+' : String(unreadCount.value)
  })

  // ==================== 动作 ====================

  /**
   * 拉取一次角标 + 消息聚合
   *
   * <p>两个请求独立容错：消息接口挂了不影响角标显示。
   */
  async function refresh() {
    const userStore = useUserStore()
    if (!userStore.isLogin) return

    const [countRes, groupRes] = await Promise.allSettled([
      fetchUnreadCount(),
      fetchUnreadGroups()
    ])
    if (countRes.status === 'fulfilled') {
      unreadCount.value = Number(countRes.value) || 0
    }
    if (groupRes.status === 'fulfilled') {
      msgGroups.value = Array.isArray(groupRes.value) ? groupRes.value : []
    }
  }

  /**
   * 拉取通知面板列表（打开面板时调用）
   */
  async function refreshNotifications() {
    const res = await fetchLatestNotifications(10)
    notifications.value = res?.records ?? []
  }

  /**
   * 启动轮询（登录后 / MainLayout 挂载时调用）
   */
  function startPolling() {
    stopPolling()
    refresh()
    // 30s 与后端注释保持一致；visibilitychange 场景不做特殊处理，演示项目从简
    timer = setInterval(refresh, 30000)
  }

  /**
   * 停止轮询（登出时调用）
   */
  function stopPolling() {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
    unreadCount.value = 0
    notifications.value = []
    msgGroups.value = []
  }

  /**
   * 全部已读后本地同步清零，避免等下一轮轮询
   */
  async function markAllRead() {
    await markAllNotificationsRead()
    unreadCount.value = 0
    notifications.value = notifications.value.map((n) => ({ ...n, isRead: true }))
  }

  /**
   * 单条已读 + 角标减一
   *
   * @param {object} item 通知项
   */
  async function markRead(item) {
    if (item.isRead) return
    await markNotificationRead(item.id)
    item.isRead = true
    unreadCount.value = Math.max(0, unreadCount.value - 1)
  }

  /**
   * 消息面板条目被点击后本地移除该组
   *
   * @param {object} group 聚合条目
   */
  function removeMsgGroup(group) {
    msgGroups.value = msgGroups.value.filter(
      (g) => !(g.bizType === group.bizType && g.bizId === group.bizId)
    )
  }

  return {
    unreadCount,
    badgeText,
    notifications,
    msgGroups,
    refresh,
    refreshNotifications,
    startPolling,
    stopPolling,
    markAllRead,
    markRead,
    removeMsgGroup
  }
})
