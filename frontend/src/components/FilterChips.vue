<script setup>
/**
 * 状态筛选胶囊
 *
 * <p><b>为什么不用 n-select / n-tabs</b>：
 * 反馈、问题、任务的状态都是 4~5 个固定枚举，用户切换频率很高。
 * 下拉框要多点一次、标签页占一整行且视觉重量太大；
 * 胶囊一行铺开，一眼能看到"哪些状态、各有几条"，点击成本最低。
 *
 * <p><b>计数从哪来</b>：调用方传入（通常是列表接口返回的 total 或聚合结果）。
 * 组件本身不发请求，保持纯展示——这样它既能用于反馈列表，
 * 也能用于 AI 候选、任务看板，不绑定任何业务接口。
 *
 * @param {string|null} modelValue 当前选中值，null 表示「全部」
 * @param {Array}       options    [{ value, label, count }]
 * @param {string}      allLabel   「全部」的文案
 * @param {number}      allCount   「全部」的计数，不传则不显示
 */
import { computed } from 'vue'

const props = defineProps({
  modelValue: { type: [String, Number], default: null },
  options: { type: Array, default: () => [] },
  allLabel: { type: String, default: '全部' },
  allCount: { type: [Number, String], default: null }
})

const emit = defineEmits(['update:modelValue', 'change'])

/** 选中项统一成字符串比较，避免 1 与 '1' 这种类型差异导致高亮丢失 */
const current = computed(() => (props.modelValue === null || props.modelValue === undefined
  ? null
  : String(props.modelValue)))

/**
 * 点击切换
 *
 * <p>点已选中的胶囊不会取消选择——回到「全部」有单独的胶囊，
 * 这样不用记"再点一次是取消"，降低误操作。
 *
 * @param {string|null} value 目标值
 */
function select(value) {
  const next = value === null ? null : String(value)
  if (next === current.value) return
  emit('update:modelValue', value)
  emit('change', value)
}
</script>

<template>
  <div class="chips" role="tablist">
    <button
      type="button"
      class="chip"
      :class="{ 'is-active': current === null }"
      role="tab"
      :aria-selected="current === null"
      @click="select(null)"
    >
      {{ allLabel }}
      <b v-if="allCount !== null">{{ allCount }}</b>
    </button>

    <button
      v-for="item in options"
      :key="item.value"
      type="button"
      class="chip"
      :class="{ 'is-active': current === String(item.value) }"
      role="tab"
      :aria-selected="current === String(item.value)"
      @click="select(item.value)"
    >
      {{ item.label }}
      <b v-if="item.count !== undefined && item.count !== null">{{ item.count }}</b>
    </button>
  </div>
</template>

<style scoped>
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 6px 14px;
  font-family: inherit;
  font-size: 12.5px;
  line-height: 1.5;
  color: var(--c-text-2);
  background: rgba(255, 255, 255, 0.62);
  border: 1px solid var(--c-border-soft);
  border-radius: 999px;
  cursor: pointer;
  transition: color 0.22s ease, background-color 0.22s ease, border-color 0.22s ease,
    transform 0.22s cubic-bezier(0.22, 0.61, 0.36, 1), box-shadow 0.22s ease;
}

.chip b {
  font-size: 11.5px;
  font-weight: 600;
  color: var(--c-text-3);
  font-variant-numeric: tabular-nums;
  transition: color 0.22s ease;
}

.chip:hover {
  color: var(--c-text-1);
  border-color: rgba(108, 123, 245, 0.35);
  transform: translateY(-1px);
}

.chip:active {
  transform: translateY(0) scale(0.985);
}

.chip.is-active {
  color: #fff;
  background: linear-gradient(135deg, var(--c-primary), #8494f9);
  border-color: transparent;
  box-shadow: 0 10px 22px -12px rgba(108, 123, 245, 0.95);
}

.chip.is-active b {
  color: rgba(255, 255, 255, 0.84);
}

.chip:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px rgba(108, 123, 245, 0.25);
}

@media (prefers-reduced-motion: reduce) {
  .chip {
    transition: none;
  }
}
</style>
