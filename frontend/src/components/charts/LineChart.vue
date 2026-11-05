<script setup>
/**
 * 折线 / 面积图
 *
 * <p><b>为什么要在组件内测量容器宽度</b>：
 * 折线图必须按真实像素计算坐标，才能保证描边粗细、圆点大小不被拉伸。
 * 若用 {@code preserveAspectRatio="none"} 让 SVG 硬适应容器，
 * 圆点会变成椭圆、线宽会随窗口变化——这是最常见的"图表糊掉"原因。
 *
 * <p>曲线用 Catmull-Rom 转三次贝塞尔做平滑：
 * 直接连直线太硬，而基于整组数据拟合会过冲（超过实际最大值），
 * 局部插值既能柔和过渡又不会画出不存在的峰。
 *
 * @param {Array<{label: string, value: number}>} data 数据点
 * @param {number} height 图表区高度（px）
 * @param {string} color  描边主色
 * @param {boolean} area  是否填充渐变面积
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

const props = defineProps({
  data: { type: Array, default: () => [] },
  height: { type: Number, default: 112 },
  color: { type: String, default: '#7b8cf7' },
  area: { type: Boolean, default: true }
})

/** 渐变 id 必须全局唯一，否则同页多个图表会互相串色 */
let seed = 0
const gradientId = `line-grad-${(seed += 1)}-${Math.random().toString(36).slice(2, 7)}`

const wrapRef = ref(null)
const width = ref(320)
let observer = null

onMounted(() => {
  if (!wrapRef.value) return
  width.value = wrapRef.value.clientWidth || 320

  // 侧边栏折叠、窗口缩放都会改变宽度，只监听 window.resize 会漏掉前一种
  observer = new ResizeObserver((entries) => {
    const next = entries[0]?.contentRect?.width
    if (next && next > 0) width.value = next
  })
  observer.observe(wrapRef.value)
})

onBeforeUnmount(() => {
  observer?.disconnect()
  observer = null
})

const PAD_X = 8
const PAD_TOP = 16
const PAD_BOTTOM = 10

/** 数据最大值（至少为 1，避免除零） */
const max = computed(() => Math.max(1, ...props.data.map((item) => Number(item.value) || 0)))

/** 坐标点 */
const points = computed(() => {
  const list = props.data
  if (!list.length) return []

  const usableW = Math.max(width.value - PAD_X * 2, 1)
  const usableH = Math.max(props.height - PAD_TOP - PAD_BOTTOM, 1)
  const step = list.length > 1 ? usableW / (list.length - 1) : 0
  const startX = list.length > 1 ? PAD_X : width.value / 2

  return list.map((item, index) => ({
    label: item.label,
    value: Number(item.value) || 0,
    x: startX + step * index,
    y: PAD_TOP + usableH - ((Number(item.value) || 0) / max.value) * usableH
  }))
})

/** 平滑曲线路径 */
const linePath = computed(() => {
  const pts = points.value
  if (pts.length === 0) return ''
  if (pts.length === 1) return `M ${pts[0].x} ${pts[0].y}`

  let d = `M ${pts[0].x} ${pts[0].y}`
  for (let i = 0; i < pts.length - 1; i += 1) {
    const p0 = pts[i - 1] || pts[i]
    const p1 = pts[i]
    const p2 = pts[i + 1]
    const p3 = pts[i + 2] || p2

    const c1x = p1.x + (p2.x - p0.x) / 6
    const c1y = p1.y + (p2.y - p0.y) / 6
    const c2x = p2.x - (p3.x - p1.x) / 6
    const c2y = p2.y - (p3.y - p1.y) / 6

    d += ` C ${c1x} ${c1y}, ${c2x} ${c2y}, ${p2.x} ${p2.y}`
  }
  return d
})

/** 面积路径：曲线 + 底边闭合 */
const areaPath = computed(() => {
  const pts = points.value
  if (pts.length < 2 || !props.area) return ''
  const bottom = props.height - PAD_BOTTOM
  return `${linePath.value} L ${pts[pts.length - 1].x} ${bottom} L ${pts[0].x} ${bottom} Z`
})

/** 最后一个点：视觉终点，额外加光晕 */
const lastPoint = computed(() => points.value[points.value.length - 1] || null)
</script>

<template>
  <div class="line-chart">
    <div ref="wrapRef" class="line-chart__canvas" :style="{ height: `${height}px` }">
      <svg :width="width" :height="height" class="line-chart__svg">
        <defs>
          <linearGradient :id="gradientId" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" :stop-color="color" stop-opacity="0.28" />
            <stop offset="100%" :stop-color="color" stop-opacity="0" />
          </linearGradient>
        </defs>

        <!-- 参考线 -->
        <line
          v-for="n in 3"
          :key="`grid-${n}`"
          x1="0"
          :x2="width"
          :y1="PAD_TOP + ((height - PAD_TOP - PAD_BOTTOM) / 3) * (n - 1)"
          :y2="PAD_TOP + ((height - PAD_TOP - PAD_BOTTOM) / 3) * (n - 1)"
          stroke="rgba(122, 136, 190, 0.1)"
          stroke-width="1"
        />

        <path v-if="areaPath" :d="areaPath" :fill="`url(#${gradientId})`" stroke="none" />
        <path
          v-if="linePath"
          :d="linePath"
          fill="none"
          :stroke="color"
          stroke-width="2.4"
          stroke-linecap="round"
          stroke-linejoin="round"
        />

        <circle
          v-for="point in points"
          :key="point.label"
          :cx="point.x"
          :cy="point.y"
          r="3"
          fill="#ffffff"
          :stroke="color"
          stroke-width="2"
        />

        <!-- 终点光晕 -->
        <circle
          v-if="lastPoint"
          :cx="lastPoint.x"
          :cy="lastPoint.y"
          r="7"
          :fill="color"
          fill-opacity="0.16"
        />
      </svg>
    </div>

    <!-- 横轴标签：按真实 x 坐标定位，保证和折点严格对齐 -->
    <div class="line-chart__axis">
      <span
        v-for="point in points"
        :key="point.label"
        class="line-chart__tick"
        :style="{ left: `${point.x}px` }"
      >
        {{ point.label }}
      </span>
    </div>
  </div>
</template>

<style scoped>
.line-chart {
  width: 100%;
}

.line-chart__canvas {
  position: relative;
  width: 100%;
}

.line-chart__svg {
  display: block;
  overflow: visible;
}

.line-chart__axis {
  position: relative;
  height: 16px;
  margin-top: 2px;
  font-size: 10px;
  color: var(--c-text-3);
}

.line-chart__tick {
  position: absolute;
  top: 0;
  transform: translateX(-50%);
  white-space: nowrap;
}
</style>
