<script setup>
/**
 * 雷达扫描加载动画（ReactBits「Radar」的 Vue 移植版）
 *
 * <p><b>为什么不用 loading 库 / lottie</b>：雷达本质是「同心圆 + 旋转锥形扇叶」，
 * Canvas 2D 几十行就能画出来，引库要多背 100KB+ 体积，
 * 与项目「零依赖手写视觉件」的惯例一致（参见 charts/ 手写 SVG、CountUp 手写 rAF）。
 *
 * <p><b>设计要点</b>：
 * <ul>
 *   <li>色值运行时读取 global.css 的 --c-primary，改主题这里自动跟随；</li>
 *   <li>onBeforeUnmount 清理 rAF，visibilitychange 切后台时暂停，不白耗帧；</li>
 *   <li>prefers-reduced-motion 时只画一帧静态雷达，不做扫描动画。</li>
 * </ul>
 */
import { ref, onMounted, onBeforeUnmount } from 'vue'

const props = defineProps({
  /** 画布直径（CSS 像素） */
  size: { type: Number, default: 180 },
  /** 扫描角速度（弧度/帧），0.016 ≈ 4 秒一圈 */
  speed: { type: Number, default: 0.016 }
})

const canvasRef = ref(null)
let ctx = null
let rafId = null
let angle = 0
let primaryColor = '#6c7bf5'

/** 读取全局主题色（global.css :root --c-primary） */
function readThemeColors() {
  const value = getComputedStyle(document.documentElement)
    .getPropertyValue('--c-primary')
    .trim()
  if (value) primaryColor = value
}

/** hex → rgba，用于扇叶渐变的透明档位 */
function hexToRgba(hex, alpha) {
  const raw = hex.replace('#', '')
  const full = raw.length === 3
    ? raw.split('').map((c) => c + c).join('')
    : raw
  const n = parseInt(full, 16)
  return `rgba(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255}, ${alpha})`
}

function draw() {
  const C = props.size / 2
  const R = C - 5

  ctx.clearRect(0, 0, props.size, props.size)
  ctx.lineWidth = 1

  // 同心圆 + 十字准线
  ctx.strokeStyle = hexToRgba(primaryColor, 0.28)
  ;[0.28, 0.52, 0.76, 1].forEach((k) => {
    ctx.beginPath()
    ctx.arc(C, C, R * k, 0, Math.PI * 2)
    ctx.stroke()
  })
  ctx.beginPath()
  ctx.moveTo(C - R, C)
  ctx.lineTo(C + R, C)
  ctx.moveTo(C, C - R)
  ctx.lineTo(C, C + R)
  ctx.stroke()

  // 锥形渐变扇叶（扫描拖尾），createConicGradient 不支持时退化为纯扫描线
  try {
    const g = ctx.createConicGradient(angle, C, C)
    g.addColorStop(0, hexToRgba(primaryColor, 0.5))
    g.addColorStop(0.14, hexToRgba(primaryColor, 0))
    g.addColorStop(1, hexToRgba(primaryColor, 0))
    ctx.fillStyle = g
    ctx.beginPath()
    ctx.arc(C, C, R, 0, Math.PI * 2)
    ctx.fill()
  } catch (e) {
    /* 老浏览器无锥形渐变，扇叶缺席不影响可用性 */
  }

  // 扫描线
  ctx.strokeStyle = hexToRgba(primaryColor, 0.9)
  ctx.lineWidth = 1.6
  ctx.beginPath()
  ctx.moveTo(C, C)
  ctx.lineTo(C + Math.cos(angle) * R, C + Math.sin(angle) * R)
  ctx.stroke()

  angle = (angle + props.speed) % (Math.PI * 2)
  rafId = requestAnimationFrame(draw)
}

function onVisibilityChange() {
  if (document.hidden) {
    if (rafId) cancelAnimationFrame(rafId)
    rafId = null
  } else if (!rafId && !prefersReducedMotion()) {
    rafId = requestAnimationFrame(draw)
  }
}

function prefersReducedMotion() {
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

onMounted(() => {
  readThemeColors()
  const dpr = window.devicePixelRatio || 1
  const canvas = canvasRef.value
  canvas.width = props.size * dpr
  canvas.height = props.size * dpr
  ctx = canvas.getContext('2d')
  ctx.scale(dpr, dpr)

  // 减少动效偏好：画一帧静态雷达即可
  if (prefersReducedMotion()) {
    angle = 0.9
    draw()
    cancelAnimationFrame(rafId)
    rafId = null
    return
  }
  rafId = requestAnimationFrame(draw)
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onBeforeUnmount(() => {
  if (rafId) cancelAnimationFrame(rafId)
  rafId = null
  document.removeEventListener('visibilitychange', onVisibilityChange)
})
</script>

<template>
  <div
    class="radar-loading"
    :style="{ width: size + 'px', height: size + 'px' }"
    aria-hidden="true"
  >
    <canvas ref="canvasRef" :style="{ width: size + 'px', height: size + 'px' }"></canvas>
  </div>
</template>

<style scoped>
.radar-loading {
  flex: none;
}
.radar-loading canvas {
  display: block;
}
</style>
