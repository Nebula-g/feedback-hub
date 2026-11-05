<script setup>
/**
 * 空状态（点阵六边形插画）
 *
 * <p><b>为什么不用 n-empty 的默认图标</b>：
 * 默认图标是一个灰色线框方块，几乎等于"没设计"。空状态是用户
 * 最容易停下来看一眼的界面，一块有质感的几何点阵能明显提升完成度；
 * 而且纯 SVG 生成，零图片请求。
 *
 * <p><b>点阵怎么画</b>：按 8.6 的步长铺满 200×200 画布，
 * 落在六边形内的点保留；半径与透明度按到中心的距离衰减，
 * 中心密、边缘疏，自然形成光晕过渡，不需要额外的渐变遮罩。
 *
 * @param {string} title       主文案
 * @param {string} description 辅助说明
 * @param {number} size        插画边长（px）
 * @param {string} tone        色相：primary（默认）/ muted
 */
import { computed } from 'vue'

const props = defineProps({
  title: { type: String, default: '暂无数据' },
  description: { type: String, default: '' },
  size: { type: Number, default: 132 },
  tone: { type: String, default: 'primary' }
})

const PALETTE = {
  primary: { core: '#6c7bf5', edge: '#a9b2d6', glow: 'rgba(108, 123, 245, 0.16)' },
  muted: { core: '#8b92a7', edge: '#c3cadd', glow: 'rgba(139, 146, 167, 0.12)' }
}

/**
 * clipPath 的 id 必须每个实例唯一
 *
 * <p>同一页面可能同时出现两个空状态（比如「列表为空」+「筛选无结果」），
 * 静态 id 会让第二个实例引用到第一个实例的裁剪路径，
 * 一旦两者尺寸或位置不同就会露出错位。
 */
let instanceSeed = 0
const clipId = `empty-hex-clip-${(instanceSeed += 1)}`

const colors = computed(() => PALETTE[props.tone] || PALETTE.primary)

/**
 * 生成点阵
 *
 * <p>六边形用「到中心距离 + 45° 旋转的切角判断」近似：
 * 直接算出每个点是否落在半径 78 的圆内，再用 clipPath 裁成六边形，
 * 比逐个算多边形内外更省事，视觉上没有区别。
 */
const dots = computed(() => {
  const list = []
  const center = 100
  const radius = 78
  for (let y = 16; y <= 186; y += 8.6) {
    for (let x = 16; x <= 186; x += 8.6) {
      const distance = Math.hypot(x - center, y - center)
      if (distance > radius) continue
      const isCore = distance < radius * 0.55
      list.push({
        key: `${x.toFixed(1)}-${y.toFixed(1)}`,
        cx: x.toFixed(1),
        cy: y.toFixed(1),
        r: isCore ? 1.85 : 1.15,
        fill: isCore ? colors.value.core : colors.value.edge,
        opacity: Math.max(0.18, 1 - distance / radius).toFixed(2)
      })
    }
  }
  return list
})
</script>

<template>
  <div class="empty-state">
    <svg
      class="empty-state__art"
      :style="{ width: `${size}px`, height: `${size}px` }"
      viewBox="0 0 200 200"
      aria-hidden="true"
    >
      <defs>
        <clipPath :id="clipId">
          <polygon points="100,16 173,58 173,142 100,184 27,142 27,58" />
        </clipPath>
      </defs>

      <circle cx="100" cy="100" r="78" :fill="colors.glow" />
      <g :clip-path="`url(#${clipId})`" class="empty-state__dots">
        <circle
          v-for="dot in dots"
          :key="dot.key"
          :cx="dot.cx"
          :cy="dot.cy"
          :r="dot.r"
          :fill="dot.fill"
          :opacity="dot.opacity"
        />
      </g>
    </svg>

    <p class="empty-state__title">{{ title }}</p>
    <p v-if="description" class="empty-state__desc">{{ description }}</p>

    <div v-if="$slots.default" class="empty-state__actions">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 26px 16px 20px;
}

.empty-state__art {
  flex: none;
  margin-bottom: 4px;
}

/* 极缓的呼吸：幅度 3.5%，不抢注意力，但让空页面不至于像卡住 */
.empty-state__dots {
  transform-origin: 100px 100px;
  animation: empty-breathe 6s ease-in-out infinite;
}

@keyframes empty-breathe {
  0%,
  100% {
    transform: scale(1);
    opacity: 1;
  }
  50% {
    transform: scale(1.035);
    opacity: 0.88;
  }
}

.empty-state__title {
  margin: 0 0 6px;
  font-size: 13.5px;
  font-weight: 600;
  color: var(--c-text-1);
}

.empty-state__desc {
  margin: 0;
  max-width: 320px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--c-text-3);
}

.empty-state__actions {
  display: flex;
  gap: 8px;
  margin-top: 14px;
}

@media (prefers-reduced-motion: reduce) {
  .empty-state__dots {
    animation: none;
  }
}
</style>
