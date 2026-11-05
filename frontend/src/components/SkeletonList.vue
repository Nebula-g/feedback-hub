<script setup>
/**
 * 列表骨架屏
 *
 * <p><b>为什么不用 n-skeleton</b>：Naive 的骨架只有"一块灰条"，
 * 我们想让首屏加载的占位和真实列表行结构一致（头像 + 两行文字 + 右侧徽标），
 * 这样数据到达时布局不跳动，观感是"内容填进去了"而不是"整块被替换"。
 *
 * <p><b>可达性</b>：容器标 aria-busy，动画是纯装饰（aria-hidden），
 * 屏幕阅读器不会念一堆空块。
 *
 * @param {number} rows 占位行数
 * @param {boolean} avatar 是否显示左侧方形头像位
 */
const props = defineProps({
  rows: { type: Number, default: 4 },
  avatar: { type: Boolean, default: true }
})

/**
 * 每行文字条的宽度
 *
 * <p>刻意写成不等长的固定序列而不是随机数：
 * 随机宽度在每次渲染时都会变，反而像在抖。
 */
const WIDTHS = [
  ['62%', '38%'],
  ['74%', '46%'],
  ['56%', '32%'],
  ['68%', '40%'],
  ['60%', '36%']
]
</script>

<template>
  <div class="skeleton" aria-busy="true" aria-hidden="true">
    <div v-for="index in props.rows" :key="index" class="skeleton__row">
      <span v-if="props.avatar" class="skeleton__block skeleton__avatar"></span>
      <span class="skeleton__lines">
        <span
          class="skeleton__block skeleton__line"
          :style="{ width: WIDTHS[(index - 1) % WIDTHS.length][0] }"
        ></span>
        <span
          class="skeleton__block skeleton__line skeleton__line--sub"
          :style="{ width: WIDTHS[(index - 1) % WIDTHS.length][1] }"
        ></span>
      </span>
      <span class="skeleton__block skeleton__pill"></span>
    </div>
  </div>
</template>

<style scoped>
.skeleton {
  padding: 4px 0;
}

.skeleton__row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
}

.skeleton__block {
  display: block;
  border-radius: 8px;
  background: linear-gradient(
    100deg,
    rgba(122, 136, 190, 0.14) 28%,
    rgba(122, 136, 190, 0.05) 50%,
    rgba(122, 136, 190, 0.14) 72%
  );
  background-size: 220% 100%;
  animation: skeleton-shimmer 1.5s linear infinite;
}

.skeleton__avatar {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  flex: none;
}

.skeleton__lines {
  flex: 1;
  display: grid;
  gap: 7px;
  min-width: 0;
}

.skeleton__line {
  height: 9px;
}

.skeleton__line--sub {
  height: 8px;
}

.skeleton__pill {
  width: 52px;
  height: 18px;
  border-radius: 999px;
  flex: none;
}

@keyframes skeleton-shimmer {
  from {
    background-position: 140% 0;
  }
  to {
    background-position: -60% 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .skeleton__block {
    animation: none;
  }
}
</style>
