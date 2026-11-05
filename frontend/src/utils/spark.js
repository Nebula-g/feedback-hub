/**
 * 全局点击火花（ReactBits「Click Spark」的 Vue 移植版）
 *
 * <p><b>为什么做成全局监听而不是指令</b>：需求是「页面内任何点击都出火花，
 * 包括背景空白处」，document 级 pointerdown 一次监听即可覆盖全部场景，
 * 无需在每个可点元素上逐个挂指令，也不存在遗漏。原 v-spark 指令已移除，
 * 避免全局 + 指令双触发造成按钮处火花叠加。
 *
 * <p><b>实现方式</b>：pointerdown 时在光标坐标创建一个 fixed 容器，
 * 向容器塞 8 根放射短线（CSS 变量 --a 控制角度），动画播完自毁。
 * 样式（.click-spark / @keyframes spark-fly）统一定义在 global.css，
 * 因为监听器创建的 DOM 不在任何组件树内，scoped 样式管不到。
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

/**
 * 安装全局点击火花监听（在 main.js 里 app.mount 之前调用一次即可）
 */
export function installGlobalSpark() {
  document.addEventListener('pointerdown', (e) => createSparkAt(e.clientX, e.clientY))
}
