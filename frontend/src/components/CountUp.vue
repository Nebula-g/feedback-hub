<script setup>
/**
 * 数字滚动（count-up）
 *
 * <p><b>为什么自己写而不是用动画库</b>：
 * 只需要「数值变化时从旧值滚到新值」这一件事，用 rAF 二十行就够；
 * 引一个动画库反而要为 tree-shaking 和首屏体积买单。
 *
 * <p><b>两个细节</b>：
 * <ol>
 *   <li>进入视口才开始滚：仪表盘首屏就能看到，但用户从别处滚回来时
 *       如果数字早就跑完了，会像"没动过"，失去提示数据更新的作用</li>
 *   <li>尊重 prefers-reduced-motion：关闭动效时直接显示终值，不做过渡</li>
 * </ol>
 *
 * @param {number} value    目标值
 * @param {number} duration 动画时长（ms）
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({
  value: { type: Number, default: 0 },
  duration: { type: Number, default: 900 }
})

/** 当前显示值（动画过程中不断被改写） */
const display = ref(0)

const root = ref(null)
let rafId = 0
let observer = null
/** 是否已经播过首轮动画；首轮由 IntersectionObserver 触发，之后由 watch 触发 */
let started = false

const reduceMotion =
  typeof window !== 'undefined' &&
  window.matchMedia &&
  window.matchMedia('(prefers-reduced-motion: reduce)').matches

const formatted = computed(() => display.value.toLocaleString('en-US'))

const easeOutCubic = (t) => 1 - Math.pow(1 - t, 3)

/**
 * 从当前显示值滚到目标值
 *
 * <p>从 display 而不是 0 起滚，这样数据刷新（比如切换时间范围）时
 * 数字是"增量滚动"而不是每次都从头数，观感更连续。
 *
 * @param {number} to 目标值
 */
function roll(to) {
  cancelAnimationFrame(rafId)
  if (reduceMotion) {
    display.value = to
    return
  }
  const from = display.value
  const start = performance.now()
  const step = (now) => {
    const progress = Math.min(1, (now - start) / props.duration)
    display.value = Math.round(from + (to - from) * easeOutCubic(progress))
    if (progress < 1) rafId = requestAnimationFrame(step)
  }
  rafId = requestAnimationFrame(step)
}

onMounted(() => {
  if (reduceMotion || typeof IntersectionObserver === 'undefined') {
    display.value = props.value
    started = true
    return
  }
  observer = new IntersectionObserver(
    (entries) => {
      if (!entries[0].isIntersecting) return
      started = true
      observer.disconnect()
      observer = null
      roll(props.value)
    },
    { threshold: 0.35 }
  )
  observer.observe(root.value)
})

watch(
  () => props.value,
  (next) => {
    if (!started) return
    roll(next)
  }
)

onBeforeUnmount(() => {
  cancelAnimationFrame(rafId)
  observer?.disconnect()
})
</script>

<template>
  <span ref="root" class="count-up">{{ formatted }}</span>
</template>

<style scoped>
/* 等宽数字：滚动过程中宽度不变，右侧单位不会抖 */
.count-up {
  font-variant-numeric: tabular-nums;
}
</style>
