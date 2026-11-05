<script setup>
/**
 * 环形图
 *
 * <p><b>为什么手写 SVG 而不引 ECharts</b>：
 * 仪表盘只需要"一个环 + 中心数字"，引图表库要多吃 300KB+ 产物，
 * 还得为它单独调一套主题色，收益远小于成本。
 * 这里用 {@code pathLength="100"} 把圆周长归一化成 100，
 * 描边虚线长度直接等于百分比，省掉一堆三角函数。
 *
 * @param {Array<{label: string, value: number, color: string}>} segments 分段数据
 * @param {number} size      像素直径
 * @param {number} thickness 环宽（视框单位，100 为整圆直径）
 * @param {string} centerValue 中心主数字
 * @param {string} centerLabel 中心副标题
 */
import { computed } from 'vue'

const props = defineProps({
  segments: { type: Array, default: () => [] },
  size: { type: Number, default: 132 },
  thickness: { type: Number, default: 13 },
  centerValue: { type: [String, Number], default: '' },
  centerLabel: { type: String, default: '' }
})

/** 总数值 */
const total = computed(() =>
  props.segments.reduce((sum, item) => sum + (Number(item.value) || 0), 0)
)

/**
 * 计算每段的虚线参数
 *
 * <p>段与段之间留 1.6 个单位的缝隙，环看起来才不像一整块彩色；
 * 单段（或只有一段有值）时不留缝，避免出现一个缺口。
 */
const arcs = computed(() => {
  const sum = total.value
  if (!sum) return []

  const visible = props.segments.filter((item) => Number(item.value) > 0)
  const gap = visible.length > 1 ? 1.6 : 0

  let cursor = 0
  return visible.map((item) => {
    const percent = (Number(item.value) / sum) * 100
    const length = Math.max(percent - gap, 0.6)
    const arc = {
      key: item.label,
      color: item.color,
      dash: `${length} ${100 - length}`,
      // 负偏移让每段从上一段结束的位置接着画
      offset: -cursor
    }
    cursor += percent
    return arc
  })
})
</script>

<template>
  <div class="donut" :style="{ width: `${size}px`, height: `${size}px` }">
    <svg viewBox="0 0 100 100" :width="size" :height="size" class="donut__svg">
      <!-- 底环：给空数据留一个可读的轮廓 -->
      <circle
        cx="50"
        cy="50"
        r="42"
        fill="none"
        stroke="rgba(148, 163, 214, 0.16)"
        :stroke-width="thickness"
      />
      <circle
        v-for="arc in arcs"
        :key="arc.key"
        cx="50"
        cy="50"
        r="42"
        fill="none"
        :stroke="arc.color"
        :stroke-width="thickness"
        :stroke-dasharray="arc.dash"
        :stroke-dashoffset="arc.offset"
        path-length="100"
        transform="rotate(-90 50 50)"
      />
    </svg>

    <div class="donut__center">
      <div class="donut__value">{{ centerValue }}</div>
      <div v-if="centerLabel" class="donut__label">{{ centerLabel }}</div>
    </div>
  </div>
</template>

<style scoped>
.donut {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
}

.donut__svg {
  transform: rotate(0deg);
}

.donut__center {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  pointer-events: none;
}

.donut__value {
  font-size: 22px;
  font-weight: 700;
  line-height: 1.1;
  color: var(--c-text-1);
  font-variant-numeric: tabular-nums;
}

.donut__label {
  font-size: 11px;
  color: var(--c-text-3);
}
</style>
