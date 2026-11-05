<script setup>
/**
 * 动态极光背景
 *
 * <p><b>层级约束（最容易被做错的地方）</b>：
 * 背景必须"永远在所有 UI 元素下面"。这里用三重保险：
 * <ol>
 *   <li>{@code position: fixed} + {@code z-index: 0}，且容器本身 {@code pointer-events: none}，
 *       不会拦截任何点击</li>
 *   <li>{@code overflow: hidden} + {@code contain: strict}，模糊光晕不会溢出到视口外产生滚动条</li>
 *   <li>所有调用方（登录页、主框架）的内容层都显式 {@code position: relative; z-index: 1}，
 *       即便某个组件的 transform 生成了新的层叠上下文，背景也压不到文字上</li>
 * </ol>
 *
 * <p><b>为什么用普通 div 光斑而不是 canvas</b>：
 * 只有 4 个高斯模糊色块在缓慢漂移，CSS 动画走合成线程，几乎不占 CPU；
 * canvas 方案要自己写 requestAnimationFrame 循环，还得处理 dpr，得不偿失。
 *
 * <p><b>透明度为什么压这么低</b>：背景是氛围装饰，不是内容。
 * 色块本身 alpha 在 0.35~0.55，再经过 80px 模糊摊薄，
 * 落在卡片上的实际影响不到 5%，完全不干扰文字对比度。
 *
 * @param {string} variant 视觉变体：'login'（更浓的丝绸感）| 'app'（更克制的后台底）
 */
import { computed } from 'vue'

const props = defineProps({
  variant: {
    type: String,
    default: 'app',
    validator: (v) => ['login', 'app'].includes(v)
  }
})

const rootClass = computed(() => ['aurora', `aurora--${props.variant}`])
</script>

<template>
  <div :class="rootClass" aria-hidden="true">
    <!-- 底色：极缓的斜向渐变，保证任何时刻整屏都有连续的光感过渡 -->
    <div class="aurora__base"></div>

    <!-- 漂移色块：blur 半径远大于色块本身，柔和到看不出边界 -->
    <span class="aurora__blob aurora__blob--1"></span>
    <span class="aurora__blob aurora__blob--2"></span>
    <span class="aurora__blob aurora__blob--3"></span>
    <span class="aurora__blob aurora__blob--4"></span>

    <!-- 丝绸层：极淡的斜向高光缓慢横扫，制造"流动"的观感 -->
    <div class="aurora__silk"></div>
  </div>
</template>

<style scoped>
.aurora {
  position: fixed;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
  contain: strict;
}

.aurora__base {
  position: absolute;
  inset: 0;
  background: linear-gradient(150deg, #f2f5ff 0%, #e9edfe 42%, #f5efff 100%);
}

.aurora__blob {
  position: absolute;
  display: block;
  border-radius: 50%;
  /* 用 blur 而不是 radial-gradient 的透明停止点：
     前者边缘过渡更接近真实光晕，后者容易出现可见的色带 */
  filter: blur(80px);
  will-change: transform;
}

/* 四块色的分布刻意错开象限，避免出现"中间亮、四角暗"的聚光感 */
.aurora__blob--1 {
  top: -18%;
  left: -10%;
  width: 58vw;
  height: 58vw;
  background: radial-gradient(circle, rgba(158, 176, 255, 0.82), transparent 68%);
  animation: drift-1 34s ease-in-out infinite alternate;
}

.aurora__blob--2 {
  top: -12%;
  right: -14%;
  width: 52vw;
  height: 52vw;
  background: radial-gradient(circle, rgba(196, 172, 255, 0.78), transparent 68%);
  animation: drift-2 42s ease-in-out infinite alternate;
}

.aurora__blob--3 {
  bottom: -24%;
  left: 18%;
  width: 56vw;
  height: 56vw;
  background: radial-gradient(circle, rgba(148, 206, 255, 0.7), transparent 68%);
  animation: drift-3 38s ease-in-out infinite alternate;
}

.aurora__blob--4 {
  right: 6%;
  bottom: -28%;
  width: 44vw;
  height: 44vw;
  background: radial-gradient(circle, rgba(255, 186, 222, 0.62), transparent 68%);
  animation: drift-4 46s ease-in-out infinite alternate;
}

.aurora__silk {
  position: absolute;
  inset: -30%;
  background: linear-gradient(
    115deg,
    transparent 38%,
    rgba(255, 255, 255, 0.6) 50%,
    transparent 62%
  );
  opacity: 0.75;
  animation: silk 26s ease-in-out infinite;
}

/* 后台页面：色块再压淡一档，保证表格、卡片上的文字对比度始终优先 */
.aurora--app .aurora__blob {
  opacity: 0.78;
}

/* 登录页：唯一一屏没有数据要读，颜色可以放开一点，营造丝绸光带的第一眼印象 */
.aurora--login .aurora__blob {
  filter: blur(90px);
  opacity: 1;
}

.aurora--login .aurora__base {
  background: linear-gradient(150deg, #f4f7ff 0%, #e6ebfe 40%, #f7eeff 100%);
}

/* ---------- 位移动画：只用 transform，不触发重排 ---------- */
@keyframes drift-1 {
  from {
    transform: translate3d(0, 0, 0) scale(1);
  }
  to {
    transform: translate3d(6vw, 5vh, 0) scale(1.12);
  }
}

@keyframes drift-2 {
  from {
    transform: translate3d(0, 0, 0) scale(1.05);
  }
  to {
    transform: translate3d(-7vw, 6vh, 0) scale(0.95);
  }
}

@keyframes drift-3 {
  from {
    transform: translate3d(0, 0, 0) scale(0.95);
  }
  to {
    transform: translate3d(8vw, -6vh, 0) scale(1.1);
  }
}

@keyframes drift-4 {
  from {
    transform: translate3d(0, 0, 0) scale(1);
  }
  to {
    transform: translate3d(-5vw, -8vh, 0) scale(1.14);
  }
}

@keyframes silk {
  0% {
    transform: translate3d(-14%, 0, 0) rotate(0deg);
  }
  50% {
    transform: translate3d(12%, 2%, 0) rotate(3deg);
  }
  100% {
    transform: translate3d(-14%, 0, 0) rotate(0deg);
  }
}

/* 尊重系统的"减少动态效果"设置：关掉动画但保留背景色 */
@media (prefers-reduced-motion: reduce) {
  .aurora__blob,
  .aurora__silk {
    animation: none;
  }
}
</style>
