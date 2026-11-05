<script setup>
/**
 * 柱状图
 *
 * <p>用 flex + 百分比高度实现，不使用 SVG：
 * 柱子的宽度需要随容器自适应、标签要跟着柱子居中，
 * 纯 CSS 方案天然做到，SVG 里反而要手算坐标。
 *
 * @param {Array<{label: string, value: number}>} data 数据点
 * @param {number} height 图表区高度（px）
 */
import { computed } from 'vue'

const props = defineProps({
  data: { type: Array, default: () => [] },
  height: { type: Number, default: 112 },
  /** 柱子渐变起始色 */
  colorFrom: { type: String, default: '#b9c3fd' },
  /** 柱子渐变结束色 */
  colorTo: { type: String, default: '#7b8cf7' }
})

/** 最大值：至少为 1，避免全 0 时除零 */
const max = computed(() => Math.max(1, ...props.data.map((item) => Number(item.value) || 0)))

/**
 * 柱子高度百分比
 *
 * <p>下限 6%：数值为 0 时也留一小截，
 * 否则整张图会出现"柱子凭空消失"的空洞感。
 */
function barHeight(value) {
  const ratio = (Number(value) || 0) / max.value
  return `${Math.max(ratio * 100, 6)}%`
}
</script>

<template>
  <div class="bar-chart" :style="{ height: `${height}px` }">
    <!-- 三条参考线，弱到几乎看不见，只用来提示刻度 -->
    <div class="bar-chart__grid">
      <span v-for="n in 3" :key="n"></span>
    </div>

    <div class="bar-chart__cols">
      <div v-for="item in data" :key="item.label" class="bar-chart__col">
        <span class="bar-chart__value">{{ item.value }}</span>
        <div
          class="bar-chart__bar"
          :style="{
            height: barHeight(item.value),
            background: `linear-gradient(180deg, ${colorFrom}, ${colorTo})`
          }"
        ></div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.bar-chart {
  position: relative;
  display: flex;
  align-items: flex-end;
  width: 100%;
}

.bar-chart__grid {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  pointer-events: none;
}

.bar-chart__grid span {
  height: 1px;
  background: rgba(122, 136, 190, 0.1);
}

.bar-chart__cols {
  position: relative;
  display: flex;
  align-items: flex-end;
  justify-content: space-around;
  gap: 6px;
  width: 100%;
  height: 100%;
}

.bar-chart__col {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  height: 100%;
  gap: 6px;
}

.bar-chart__value {
  font-size: 11px;
  font-weight: 600;
  color: var(--c-text-2);
  font-variant-numeric: tabular-nums;
}

.bar-chart__bar {
  width: 60%;
  max-width: 22px;
  min-height: 4px;
  border-radius: 8px 8px 4px 4px;
  box-shadow: 0 6px 14px -8px rgba(108, 123, 245, 0.8);
  transition: height 0.45s cubic-bezier(0.22, 1, 0.36, 1);
}
</style>
