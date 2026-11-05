import { createRouter, createWebHistory } from 'vue-router'

import { useUserStore } from '@/stores/user'

/**
 * 路由表
 *
 * <p><b>meta 字段说明</b>：
 * <ul>
 *   <li>{@code title}    —— 页面标题，用于浏览器标签与面包屑</li>
 *   <li>{@code roles}    —— 允许访问的角色，留空表示登录即可访问</li>
 *   <li>{@code menu}     —— 是否在侧边栏显示，false 表示隐藏（如详情页）</li>
 *   <li>{@code icon}     —— 侧边栏图标名</li>
 * </ul>
 *
 * <p><b>为什么详情页要隐藏菜单</b>：
 * 详情页必须带 id 参数进入，在侧边栏放一个点不开的入口是误导。
 */
const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { title: '登录', public: true }
  },
  {
    path: '/',
    component: () => import('@/layout/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      // ---------- 仪表盘 ----------
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/views/dashboard/DashboardView.vue'),
        meta: { title: '仪表盘', menu: true, icon: 'dashboard' }
      },

      // ---------- 反馈 ----------
      {
        path: 'feedbacks',
        name: 'feedback-list',
        component: () => import('@/views/feedback/FeedbackListView.vue'),
        meta: { title: '反馈列表', menu: true, icon: 'chatbubbles' }
      },
      {
        path: 'feedbacks/new',
        name: 'feedback-create',
        component: () => import('@/views/feedback/FeedbackCreateView.vue'),
        meta: { title: '录入反馈', menu: true, icon: 'add-circle', roles: ['CS', 'ADMIN'] }
      },
      {
        path: 'feedbacks/:id',
        name: 'feedback-detail',
        component: () => import('@/views/feedback/FeedbackDetailView.vue'),
        meta: { title: '反馈详情', menu: false }
      },

      // ---------- AI 候选问题 ----------
      {
        path: 'ai/candidates',
        name: 'ai-candidate',
        component: () => import('@/views/ai/AiCandidateView.vue'),
        meta: {
          title: 'AI 候选问题',
          menu: true,
          icon: 'sparkles',
          roles: ['PM', 'ADMIN']
        }
      },

      // ---------- 问题 ----------
      {
        path: 'issues',
        name: 'issue-list',
        component: () => import('@/views/issue/IssueListView.vue'),
        meta: { title: '问题列表', menu: true, icon: 'alert-circle' }
      },
      {
        path: 'issues/:id',
        name: 'issue-detail',
        component: () => import('@/views/issue/IssueDetailView.vue'),
        meta: { title: '问题详情', menu: false }
      },

      // ---------- 任务 ----------
      {
        path: 'tasks',
        name: 'task-board',
        component: () => import('@/views/task/TaskBoardView.vue'),
        meta: { title: '任务看板', menu: true, icon: 'checkmark-done' }
      },
      {
        path: 'tasks/:id',
        name: 'task-detail',
        component: () => import('@/views/task/TaskDetailView.vue'),
        meta: { title: '任务详情', menu: false }
      },

      // ---------- 通知中心 ----------
      {
        path: 'notifications',
        name: 'notification-center',
        component: () => import('@/views/notification/NotificationListView.vue'),
        meta: { title: '通知中心', menu: false }
      },

      // ---------- 其他 ----------
      {
        path: 'forbidden',
        name: 'forbidden',
        component: () => import('@/views/ForbiddenView.vue'),
        meta: { title: '无访问权限', menu: false }
      },
      {
        path: 'ai/logs',
        name: 'ai-logs',
        component: () => import('@/views/ai/AiLogView.vue'),
        meta: { title: 'AI 调用记录', menu: true, icon: 'file-tray-full', roles: ['PM', 'ADMIN'] }
      },
      {
        path: ':pathMatch(.*)*',
        name: 'not-found',
        component: () => import('@/views/NotFoundView.vue'),
        meta: { title: '页面不存在', menu: false }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/**
 * 全局前置守卫
 *
 * <p><b>这里是「无权限」这一态的落点</b>：
 * 与其让用户点了按钮才被后端 403 拒绝，
 * 不如在路由层就拦掉并给出明确说明——
 * 「你的角色是客服，这个页面只有产品经理能用」，
 * 比一句冷冰冰的"无权限"有用得多。
 *
 * <p><b>还原登录态</b>：刷新页面时 store 会被清空，
 * 这里用本地 token 重新拉取一次用户信息，避免用户被莫名登出。
 */
router.beforeEach(async (to, from, next) => {
  // 设置浏览器标签标题
  document.title = to.meta.title ? `${to.meta.title} · 反馈改进助手` : '反馈改进助手'

  const userStore = useUserStore()

  // 公开页面（登录页）直接放行
  if (to.meta.public) {
    return next()
  }

  // 未登录：先尝试从本地 token 恢复，恢复失败才跳登录
  if (!userStore.isLogin) {
    const restored = await userStore.restoreSession()
    if (!restored) {
      return next({ name: 'login', query: { redirect: to.fullPath } })
    }
  }

  // 角色校验：roles 为空表示所有登录用户都可访问
  const allowRoles = to.meta.roles
  if (allowRoles && allowRoles.length > 0 && !allowRoles.includes(userStore.role)) {
    return next({
      name: 'forbidden',
      query: { need: allowRoles.join('/'), current: userStore.roleLabel }
    })
  }

  next()
})

export default router
