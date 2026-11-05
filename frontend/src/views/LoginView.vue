<script setup>
/**
 * 登录页
 *
 * <p><b>设计要点</b>：
 * <ol>
 *   <li><b>不嵌在主框架里</b>：路由表中它是独立顶层路由，
 *       因此没有侧边栏和顶栏——登录页带导航是常见的多余设计。</li>
 *   <li><b>背景即主角</b>：紫蓝流体光带由 LoginBackdrop 渲染在 z-index 0，
 *       登录卡靠右悬浮在 z-index 1，左侧留白给插画。</li>
 *   <li><b>演示账号</b>：答辩时要频繁切换四个角色看权限差异，
 *       做成左下角四个可点击胶囊，不占用卡片视觉重心。</li>
 *   <li><b>失败态就地展示</b>：错误提示显示在表单上方而不是弹窗，
 *       弹窗需要多点一次才能继续，登录失败是高频操作。</li>
 * </ol>
 */
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NForm, NFormItem, NInput, NButton, NCheckbox } from 'naive-ui'
import { useMessage } from 'naive-ui'

import AppIcon from '@/components/AppIcon.vue'
import LoginBackdrop from '@/components/LoginBackdrop.vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const message = useMessage()

/** 「记住我」在本地保存账号用的键名（只存账号，绝不存密码） */
const REMEMBER_KEY = 'feedback_hub_remember_username'

/** 是否正在提交（按钮 loading 态） */
const submitting = ref(false)

/** 错误提示文案，为空表示无错误 */
const errorMessage = ref('')

/** 是否记住账号 */
const rememberMe = ref(false)

/** 表单数据 */
const form = reactive({
  username: '',
  password: ''
})

/** 表单校验规则 */
const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

/** 表单引用，用于触发校验 */
const formRef = ref(null)

/**
 * 演示账号列表
 *
 * <p>对应 sql/init-data.sql 中初始化的四个账号。
 * 密码均为「账号前缀 + 123456」。
 */
const demoAccounts = [
  { username: 'cs01', password: 'cs123456', name: '客服', role: '张客服' },
  { username: 'pm01', password: 'pm123456', name: '产品', role: '李产品' },
  { username: 'dev01', password: 'dev123456', name: '开发', role: '王开发' },
  { username: 'admin', password: 'admin123456', name: '管理', role: '系统管理员' }
]

// 进入页面时还原上次记住的账号
onMounted(() => {
  const saved = localStorage.getItem(REMEMBER_KEY)
  if (saved) {
    form.username = saved
    rememberMe.value = true
  }
})

/**
 * 填充演示账号
 *
 * @param {{username: string, password: string}} account 账号信息
 */
function fillAccount(account) {
  form.username = account.username
  form.password = account.password
  errorMessage.value = ''
}

/**
 * 忘记密码 / 注册
 *
 * <p>课程项目没有找回与注册流程，这里给明确指引而不是弹一个假表单。
 */
function handleForgot() {
  message.info('请联系系统管理员重置密码')
}

function handleRegister() {
  message.info('请联系系统管理员开通账号')
}

/**
 * 提交登录
 */
async function handleSubmit() {
  // 先做前端校验，避免无效请求
  try {
    await formRef.value?.validate()
  } catch {
    return
  }

  submitting.value = true
  errorMessage.value = ''

  try {
    await userStore.login({ username: form.username, password: form.password })

    // 记住账号（只存账号名，密码永远不落盘）
    if (rememberMe.value) {
      localStorage.setItem(REMEMBER_KEY, form.username)
    } else {
      localStorage.removeItem(REMEMBER_KEY)
    }

    message.success('登录成功')

    // 如果是因为访问某页面被拦截到登录页的，登录后跳回原页面
    const redirect = route.query.redirect
    router.push(redirect ? String(redirect) : '/dashboard')
  } catch (error) {
    // 401 已被拦截器处理并清理了登录态，这里只负责提示
    errorMessage.value = error.message || '登录失败，请检查账号和密码'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <!-- 动态背景：fixed + z-index 0，光带漂移与左侧几何呼吸都在这一层 -->
    <LoginBackdrop />

    <!-- ==================== 登录卡（靠右） ==================== -->
    <div class="login-shell">
      <section class="login-card">
        <header class="login-head">
          <h1 class="login-title anim-tracking-in">用户反馈助手</h1>
          <p class="login-subtitle">统一收集、处理、追踪用户反馈</p>
        </header>

        <!-- 错误提示：就地展示，不打断输入流程 -->
        <div v-if="errorMessage" class="login-error">
          <AppIcon name="alert-circle" :size="15" />
          <span>{{ errorMessage }}</span>
        </div>

        <n-form ref="formRef" :model="form" :rules="rules" :show-label="false" size="large">
          <n-form-item path="username" :show-feedback="false">
            <n-input
              v-model:value="form.username"
              placeholder="账号"
              aria-label="账号"
              class="login-input"
              @keyup.enter="handleSubmit"
            />
          </n-form-item>

          <n-form-item path="password" :show-feedback="false">
            <n-input
              v-model:value="form.password"
              type="password"
              show-password-on="click"
              placeholder="密码"
              aria-label="密码"
              class="login-input"
              @keyup.enter="handleSubmit"
            />
          </n-form-item>
        </n-form>

        <div class="login-options">
          <n-checkbox v-model:checked="rememberMe" size="small">记住我</n-checkbox>
          <button class="link-btn" type="button" @click="handleForgot">忘记密码</button>
        </div>

        <n-button
          class="login-submit"
          type="primary"
          block
          size="large"
          :loading="submitting"
          @click="handleSubmit"
        >
          登录
        </n-button>

        <footer class="login-foot">
          还没有账号？
          <button class="link-btn" type="button" @click="handleRegister">立即注册</button>
        </footer>
      </section>
    </div>

    <!-- ==================== 演示账号（左下角，不抢卡片视线） ==================== -->
    <div class="demo">
      <span class="demo__title">演示账号</span>
      <button
        v-for="account in demoAccounts"
        :key="account.username"
        class="demo__item"
        type="button"
        :title="`${account.role} · ${account.username}`"
        @click="fillAccount(account)"
      >
        {{ account.name }}
      </button>
    </div>
  </div>
</template>

<style scoped>
/* 整页：背景铺满，内容不滚动 */
.login-page {
  position: relative;
  min-height: 100vh;
  min-height: 100dvh;
  background-color: #eef2ff;
}

/* 右侧定位层：把卡片推到画面右侧 2/3 处，左侧完整留给插画。
 * 右内边距比视觉居中更大一些——卡片往左让出位置，既不压住插画，
 * 也不贴在窗口边缘（贴边会显得像抽屉而不是漂浮的玻璃卡）。 */
.login-shell {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  min-height: 100vh;
  min-height: 100dvh;
  padding: 40px clamp(48px, 12.5vw, 220px) 40px 32px;
}

/* 半透明毛玻璃卡：
 * 底色用 0.32~0.17 的半透白渐变而不是纯色——纯色半透会把背后的光带压成一片，
 * 渐变则保留左上更亮、右下更透的层次，看得出背景在流动。
 * 描边是白色低透明度，只负责勾出玻璃的边界厚度，不参与压暗。 */
.login-card {
  position: relative;
  width: clamp(380px, 32vw, 470px);
  padding: 44px 46px 42px;
  /* 两层背景：上面是左上角斜向反光（让"一块玻璃"成立，而不是一块半透明白方块），
   * 下面是玻璃本身的透光底色。都用背景层而不是伪元素盖住——
   * 背景层天然在内容之下，不会把标题和输入框糊一层白。 */
  background:
    linear-gradient(
      140deg,
      rgba(255, 255, 255, 0.34) 0%,
      rgba(255, 255, 255, 0.08) 26%,
      rgba(255, 255, 255, 0) 48%
    ),
    linear-gradient(
      155deg,
      rgba(255, 255, 255, 0.32) 0%,
      rgba(255, 255, 255, 0.19) 58%,
      rgba(238, 242, 255, 0.17) 100%
    );
  border: 1px solid rgba(255, 255, 255, 0.58);
  border-radius: 22px;
  box-shadow: 0 40px 80px -40px rgba(74, 89, 168, 0.34),
    0 12px 28px -20px rgba(74, 89, 168, 0.16),
    inset 0 1px 0 rgba(255, 255, 255, 0.92),
    inset 0 0 0 1px rgba(255, 255, 255, 0.2);
  -webkit-backdrop-filter: blur(34px) saturate(230%) brightness(1.05);
  backdrop-filter: blur(34px) saturate(230%) brightness(1.05);
}

.login-head {
  text-align: center;
}

.login-title {
  margin: 0;
  font-size: 25px;
  font-weight: 700;
  letter-spacing: 0.01em;
  color: #1f2437;
}

.login-subtitle {
  margin: 9px 0 0;
  font-size: 13px;
  color: #6b7186;
}

.login-error {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 9px 12px;
  margin-top: 18px;
  font-size: 12.5px;
  color: var(--c-danger);
  background: rgba(226, 85, 107, 0.08);
  border: 1px solid rgba(226, 85, 107, 0.18);
  border-radius: 10px;
}

/* 输入框：近乎不透明的冷白，聚焦时提到纯白。
 *
 * 为什么不用半透明白（玻璃感更强但会出问题）：浏览器自动填充会往 input
 * 元素上塞一层自己的不透明底色，只能靠一条同名 inset 阴影盖掉。阴影是
 * 叠在容器底色之上的，如果容器本身半透明，input 区域（叠两层）和右侧
 * 眼睛图标区域（叠一层）就会差出 4~6 个色阶——放大后仍能看到一条竖线。
 * 容器底色改成不透明后，两处严格相等，断层从根上消失。
 *
 * ⚠ 每条变量都必须带 !important：Naive UI 把主题变量作为**内联 style**
 * 写在 .n-input 根元素上（`style="--n-color: ..."`），内联变量优先级高于
 * 任何选择器，不加 !important 写的值会被静默忽略。 */
.login-input {
  --n-height: 48px !important;
  --n-color: #fdfdff !important;
  --n-color-focus: #ffffff !important;
  --n-border: 1px solid rgba(154, 165, 199, 0.3) !important;
  --n-border-hover: 1px solid rgba(108, 123, 245, 0.5) !important;
  --n-border-focus: 1px solid var(--c-primary) !important;
  --n-box-shadow-focus: 0 0 0 3px rgba(108, 123, 245, 0.12) !important;
  --n-border-radius: 10px !important;
  --n-padding-left: 16px !important;
  font-size: 14px;
}

.login-input :deep(.n-input__placeholder),
.login-input :deep(.n-input__input-el) {
  font-size: 14px;
}

/* 占位符当标签用，颜色必须够深，否则一眼看不出填什么 */
.login-input :deep(.n-input__placeholder) {
  color: #7d8399;
}

.login-input :deep(.n-input__suffix) {
  color: #9aa0b5;
}

/* 输入框本体的底色统一交给外层容器，input 自己保持透明。
 * 圆角是为了下面那条 inset 阴影不溢出到容器的圆角外面。 */
.login-input :deep(.n-input__input-el) {
  background-color: transparent;
  border-radius: 9px;
}

/* 浏览器自动填充（Chrome/Edge 的账号密码、Safari 的「自动填充」）会往
 * input 元素上直接写一层不透明的底色 rgb(232,240,254)，且优先级高于普通
 * 声明。它只覆盖 input 的矩形区域，左右两端的 padding 和密码框的眼睛图标
 * 仍然用容器底色，于是同一个输入框里出现一条竖向色差断层。
 *
 * 这个底色的 background-color 改不动（浏览器用内部样式强制），唯一稳定的
 * 办法是用足够大扩散的 inset 阴影把它整块盖掉，并固定文字填充色、
 * 阻止浏览器把背景色"动画"回默认值。 */
.login-input :deep(.n-input__input-el:-webkit-autofill),
.login-input :deep(.n-input__input-el:-webkit-autofill:hover) {
  -webkit-text-fill-color: #1f2437;
  caret-color: #1f2437;
  transition: background-color 600000s ease 0s;
  box-shadow: inset 0 0 0 1000px #fdfdff;
}

/* 聚焦时容器底色是纯白，阴影必须同步，否则聚焦瞬间又露一次断层 */
.login-input :deep(.n-input__input-el:-webkit-autofill:focus) {
  box-shadow: inset 0 0 0 1000px #ffffff;
}

/* Firefox 用标准伪类，且靠 filter 上色，一并抹平 */
.login-input :deep(.n-input__input-el:autofill) {
  filter: none;
  -webkit-text-fill-color: #1f2437;
  caret-color: #1f2437;
  box-shadow: inset 0 0 0 1000px #fdfdff;
}

.login-input :deep(.n-input__input-el:autofill:focus) {
  box-shadow: inset 0 0 0 1000px #ffffff;
}

.login-card :deep(.n-form-item) {
  margin-bottom: 16px;
}

/* 表单与副标题之间留出参考稿里的呼吸感 */
.login-card :deep(.n-form) {
  margin-top: 26px;
}

.login-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 4px 0 26px;
}

.login-options :deep(.n-checkbox__label) {
  font-size: 12.5px;
  color: #5d6378;
}

.link-btn {
  padding: 0;
  font-size: 12.5px;
  color: #6577e8;
  background: none;
  border: none;
  cursor: pointer;
  transition: color 0.16s ease;
}

.link-btn:hover {
  color: #4a5ad4;
  text-decoration: underline;
}

/* 主按钮：紫蓝渐变，全页唯一的强视觉元素 */
.login-submit {
  --n-height: 48px !important;
  --n-border-radius: 10px !important;
  --n-font-size: 15px;
  background: linear-gradient(100deg, #7d8ff9 0%, #9a7ef6 100%);
  border: none;
  letter-spacing: 0.02em;
  box-shadow: 0 16px 28px -16px rgba(108, 123, 245, 0.95);
  transition: filter 0.18s ease, transform 0.18s ease, box-shadow 0.18s ease;
}

.login-submit:hover {
  background: linear-gradient(100deg, #8b9bfb 0%, #a68cf8 100%);
  box-shadow: 0 18px 32px -16px rgba(108, 123, 245, 1);
}

.login-submit:active {
  transform: translateY(1px);
}

.login-foot {
  margin-top: 24px;
  font-size: 12.5px;
  color: #7d8399;
  text-align: center;
}

/* ==================== 演示账号 ==================== */
.demo {
  position: fixed;
  z-index: 2;
  bottom: 26px;
  left: clamp(24px, 4vw, 56px);
  display: flex;
  align-items: center;
  gap: 8px;
}

.demo__title {
  margin-right: 2px;
  font-size: 11.5px;
  letter-spacing: 0.04em;
  color: rgba(58, 68, 104, 0.72);
}

.demo__item {
  padding: 6px 12px;
  font-family: inherit;
  font-size: 12px;
  color: #4f5773;
  background: rgba(255, 255, 255, 0.52);
  border: 1px solid rgba(255, 255, 255, 0.85);
  border-radius: 999px;
  cursor: pointer;
  -webkit-backdrop-filter: blur(12px);
  backdrop-filter: blur(12px);
  transition: background-color 0.18s ease, transform 0.18s ease,
    box-shadow 0.18s ease;
}

.demo__item:hover {
  background-color: rgba(255, 255, 255, 0.88);
  box-shadow: 0 8px 18px -12px rgba(74, 89, 168, 0.7);
  transform: translateY(-1px);
}

/* 窄屏：卡片占满，演示账号折行到顶部避免压住卡片 */
@media (max-width: 720px) {
  .login-shell {
    justify-content: center;
    padding: 96px 20px 88px;
  }

  .login-card {
    width: 100%;
    max-width: 420px;
    padding: 32px 26px 34px;
  }

  .demo {
    right: 20px;
    bottom: 20px;
    left: 20px;
    flex-wrap: wrap;
    justify-content: center;
  }
}
</style>
