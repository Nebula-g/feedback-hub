<script setup>
/**
 * Before / After 拖拽对比
 *
 * <p><b>为什么用 clip-path 而不是改宽度</b>：
 * 如果给上层容器设 width: 50%，里面的文字会按新宽度重新折行，
 * 拖动时整段文字在"跳"，完全失去对比的意义。
 * clip-path 只裁切视觉，布局宽度不变，文字位置绝对稳定。
 *
 * <p><b>两层内容为什么一个左对齐、一个右对齐</b>：
 * 文字都是从左边开始排的，如果两层都左对齐，上层一旦盖住左侧，
 * 下层的文字就全被压在下面看不见了。让 after 层靠右排，
 * 左右各占一半，拖动分割线时两边内容同时可见。
 *
 * <p><b>指针事件</b>：用 pointerdown/move/up + setPointerCapture，
 * 鼠标、触屏、触控笔一套逻辑；拖动过整个容器都不会丢事件。
 *
 * @param {string} beforeLabel 左侧（原稿）标题
 * @param {string} afterLabel  右侧（定稿）标题
 * @param {number} height      容器高度（px）
 */
import { computed, ref } from 'vue'

const props = defineProps({
  beforeLabel: { type: String, default: '原稿' },
  afterLabel: { type: String, default: '定稿' },
  height: { type: Number, default: 300 },
  /** 初始分割位置（百分比） */
  initial: { type: Number, default: 52 }
})

const root = ref(null)
const pos = ref(props.initial)
const dragging = ref(false)

/** 两侧内容面板：滚动需要互相同步 */
const beforeContent = ref(null)
const afterContent = ref(null)

/** 防止 scrollTop 互相赋值触发 scroll 事件造成死循环 */
let syncingScroll = false

/**
 * 同步另一侧内容的滚动位置
 *
 * <p>两层展示的是同一组字段，一侧滚到底另一侧也要跟到底，
 * 否则分割线两边对不上号。
 *
 * @param {'before'|'after'} source 发起滚动的一侧
 */
function syncScroll(source) {
  if (syncingScroll) return
  const from = source === 'before' ? beforeContent.value : afterContent.value
  const to = source === 'before' ? afterContent.value : beforeContent.value
  if (!from || !to) return
  syncingScroll = true
  to.scrollTop = from.scrollTop
  requestAnimationFrame(() => {
    syncingScroll = false
  })
}

const styleVars = computed(() => ({
  '--pos': `${pos.value}%`,
  '--ba-height': `${props.height}px`
}))

/**
 * 把指针位置换算成分割百分比
 *
 * <p>两侧各留 6% 的余量：拖到底时标签还看得见，
 * 否则用户会以为组件坏了（整块变成空白色）。
 *
 * @param {number} clientX 指针横坐标
 */
function moveTo(clientX) {
  const rect = root.value?.getBoundingClientRect()
  if (!rect || rect.width === 0) return
  const percent = ((clientX - rect.left) / rect.width) * 100
  pos.value = Math.min(94, Math.max(6, percent))
}

function onPointerDown(event) {
  /* 正文区域要留给滚动/选中文本，点按不再移动分割线 */
  if (event.target.closest?.('.ba__content')) return
  dragging.value = true
  root.value?.setPointerCapture?.(event.pointerId)
  moveTo(event.clientX)
}

function onPointerMove(event) {
  if (!dragging.value) return
  moveTo(event.clientX)
}

function stopDrag() {
  dragging.value = false
}

/**
 * 键盘微调
 *
 * <p>拖拽控件对键盘用户是不可用的，方向键是必须提供的等价路径；
 * 每次 4%，按住不放能连续调整。
 *
 * @param {KeyboardEvent} event 键盘事件
 */
function onKeydown(event) {
  if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return
  event.preventDefault()
  const step = event.key === 'ArrowRight' ? 4 : -4
  pos.value = Math.min(94, Math.max(6, pos.value + step))
}
</script>

<template>
  <div
    ref="root"
    class="ba"
    :class="{ 'is-dragging': dragging }"
    :style="styleVars"
    @pointerdown="onPointerDown"
    @pointermove="onPointerMove"
    @pointerup="stopDrag"
    @pointercancel="stopDrag"
    @pointerleave="stopDrag"
  >
    <!-- 底层：定稿（右侧完整可见，左侧被上层裁掉） -->
    <div class="ba__layer ba__layer--after">
      <span class="ba__badge ba__badge--after">{{ afterLabel }}</span>
      <div
        ref="afterContent"
        class="ba__content ba__content--after"
        @scroll="syncScroll('after')"
      >
        <slot name="after" />
      </div>
    </div>

    <!-- 上层：原稿（按 --pos 裁切，只露出左侧部分） -->
    <div class="ba__layer ba__layer--before">
      <span class="ba__badge ba__badge--before">{{ beforeLabel }}</span>
      <div
        ref="beforeContent"
        class="ba__content"
        @scroll="syncScroll('before')"
      >
        <slot name="before" />
      </div>
    </div>

    <!-- 分割线 + 拖拽钮 -->
    <div class="ba__divider">
      <button
        type="button"
        class="ba__handle"
        aria-label="拖动对比原稿与定稿，可用左右方向键调整"
        @keydown="onKeydown"
        @pointerdown.stop="onPointerDown"
      >
        <svg
          width="18"
          height="18"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.8"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path d="M9 6l-4 6 4 6M15 6l4 6-4 6" />
        </svg>
      </button>
    </div>
  </div>
</template>

<style scoped>
.ba {
  position: relative;
  height: var(--ba-height);
  overflow: hidden;
  border-radius: 16px;
  border: 1px solid var(--c-border-soft);
  background: rgba(255, 255, 255, 0.55);
  cursor: ew-resize;
  user-select: none;
  touch-action: none;
}

.ba__layer {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px 18px;
}

.ba__layer--after {
  align-items: flex-end;
  text-align: right;
}

.ba__layer--before {
  /* 近不透明：必须完全盖住底层文字，否则两份内容会叠成重影 */
  background: linear-gradient(135deg, #f7f2ff 0%, #eef2ff 100%);
  clip-path: inset(0 calc(100% - var(--pos)) 0 0);
}

.ba__badge {
  flex: none;
  font-size: 11px;
  font-weight: 600;
  line-height: 1.5;
  padding: 3px 10px;
  border-radius: 999px;
}

.ba__badge--before {
  color: #7a5ad4;
  background: rgba(167, 139, 250, 0.18);
}

.ba__badge--after {
  color: #2b9563;
  background: rgba(51, 173, 116, 0.15);
}

.ba__content {
  flex: 1;
  min-height: 0;
  /* 内容可滚动：长文本不截断，两侧滚动位置由 syncScroll 保持一致 */
  overflow-y: auto;
  width: 100%;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--c-text-2);
  cursor: default;
  user-select: text;
  scrollbar-width: thin;
  scrollbar-color: rgba(108, 123, 245, 0.35) transparent;
}

.ba__content::-webkit-scrollbar {
  width: 6px;
}

.ba__content::-webkit-scrollbar-thumb {
  background: rgba(108, 123, 245, 0.35);
  border-radius: 999px;
}

.ba__content::-webkit-scrollbar-track {
  background: transparent;
}

.ba__content--after {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.ba__divider {
  position: absolute;
  top: 0;
  bottom: 0;
  left: var(--pos);
  width: 2px;
  margin-left: -1px;
  background: linear-gradient(
    180deg,
    transparent,
    rgba(108, 123, 245, 0.8) 14%,
    rgba(108, 123, 245, 0.8) 86%,
    transparent
  );
  pointer-events: none;
}

.ba__handle {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  color: var(--c-primary);
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid rgba(108, 123, 245, 0.28);
  border-radius: 50%;
  box-shadow: 0 12px 26px -12px rgba(74, 89, 168, 0.6);
  cursor: ew-resize;
  pointer-events: auto;
  transition: box-shadow 0.2s ease, transform 0.2s ease;
}

.ba__handle:hover {
  transform: translate(-50%, -50%) scale(1.06);
}

.ba__handle:focus-visible {
  outline: none;
  box-shadow: 0 0 0 4px rgba(108, 123, 245, 0.24);
}

.ba.is-dragging .ba__handle {
  transform: translate(-50%, -50%) scale(1.1);
  box-shadow: 0 14px 30px -12px rgba(74, 89, 168, 0.75);
}

@media (prefers-reduced-motion: reduce) {
  .ba__handle {
    transition: none;
  }
}
</style>
