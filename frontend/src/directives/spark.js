/**
 * v-spark —— 点击火花指令（ReactBits「Click Spark」的 Vue 移植版）
 *
 * <p><b>为什么做成指令而不是组件</b>：火花只有一个「挂到按钮上」的动作，
 * 指令一行 `v-spark` 即可复用，不需要包裹/改写按钮结构，
 * 对现有 n-button 模板零侵入（符合「优先迭代现有代码」的约束）。
 *
 * <p><b>实现方式</b>：pointerdown 时在光标坐标创建一个 fixed 容器，
 * 向容器塞 8 根放射短线（CSS 变量 --a 控制角度），动画播完自毁。
 * 样式（.click-spark / @keyframes spark-fly）统一定义在 global.css，
 * 因为指令创建的 DOM 不在任何组件树内，scoped 样式管不到。
 *
 * <p><b>用在哪</b>：只挂关键决策按钮（确认立项 / 驳回 / 指派），
 * 普通查询、翻页按钮不挂——火花是「我做了个决定」的仪式感，不是装饰。
 *
 * <p><b>无障碍</b>：prefers-reduced-motion 时容器整体 display:none（见 global.css）。
 */

/** 火花根数（360° 均分） */
const SPARK_COUNT = 8

/** 与 CSS 动画时长（.55s）保持一致后再移除 DOM */
const CLEANUP_DELAY_MS = 620

/** 读取 global.css 的主题变量，带兜底色 */
function readThemeVar(name, fallback) {
  const value = getComputedStyle(document.documentElement)
    .getPropertyValue(name)
    .trim()
  return value || fallback
}

function createSparkAt(x, y) {
  const host = document.createElement('div')
  host.className = 'click-spark'
  host.style.left = `${x}px`
  host.style.top = `${y}px`
  host.setAttribute('aria-hidden', 'true')

  const primary = readThemeVar('--c-primary', '#6c7bf5')
  const accent = readThemeVar('--c-accent', '#a78bfa')

  for (let i = 0; i < SPARK_COUNT; i++) {
    const ray = document.createElement('i')
    ray.style.setProperty('--a', `${(i * 360) / SPARK_COUNT}deg`)
    // 主色 / 强调色交替，与项目双色视觉一致
    ray.style.background = i % 2 === 0 ? primary : accent
    host.appendChild(ray)
  }

  document.body.appendChild(host)
  setTimeout(() => host.remove(), CLEANUP_DELAY_MS)
}

export default {
  mounted(el) {
    el.__sparkHandler = (e) => {
      // 禁用态按钮不出火花（如提交中的 loading 按钮）
      if (el.disabled || el.getAttribute('aria-disabled') === 'true') return
      createSparkAt(e.clientX, e.clientY)
    }
    el.addEventListener('pointerdown', el.__sparkHandler)
  },
  unmounted(el) {
    if (el.__sparkHandler) {
      el.removeEventListener('pointerdown', el.__sparkHandler)
      delete el.__sparkHandler
    }
  }
}
