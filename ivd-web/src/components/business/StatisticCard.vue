<template>
  <button
    class="statistic-card"
    :class="`tone-${tone}`"
    @click="emit('select')"
  >
    <span class="stat-icon"
      ><el-icon :size="31"><component :is="icons[icon] || Money" /></el-icon
    ></span>
    <span class="stat-main"
      ><span class="stat-title">{{ title }}</span>
      <span class="stat-value"
        ><small v-if="prefix">{{ prefix }}</small
        >{{ formattedValue }}<small v-if="!prefix">{{ unit }}</small></span
      >
      <span class="stat-comparison"
        >{{ comparison }}<el-icon><Right /></el-icon
      ></span>
    </span>
  </button>
</template>
<script setup>
import { computed } from 'vue'
import {
  Money,
  ShoppingCart,
  Download,
  Upload,
  Right,
} from '@element-plus/icons-vue'
const props = defineProps({
  title: String,
  value: { type: Number, default: null },
  precision: { type: Number, default: 0 },
  prefix: String,
  unit: String,
  tone: { type: String, default: 'teal' },
  icon: String,
  comparison: String,
})
const emit = defineEmits(['select'])
const icons = {
  money: Money,
  cart: ShoppingCart,
  inbound: Download,
  outbound: Upload,
}
const formattedValue = computed(() =>
  props.value == null
    ? '—'
    : props.value.toLocaleString('zh-CN', {
        minimumFractionDigits: props.precision,
        maximumFractionDigits: props.precision,
      }),
)
</script>
<style scoped>
.statistic-card {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 18px;
  border: 0;
  border-radius: var(--scm-radius);
  padding: 21px 20px;
  text-align: left;
  color: white;
  cursor: pointer;
  min-height: 128px;
  background: var(--tone);
  transition: filter 0.15s;
}
.statistic-card:hover {
  filter: brightness(0.96);
}
.tone-orange {
  --tone: var(--scm-orange);
}
.tone-teal {
  --tone: var(--scm-teal);
}
.tone-purple {
  --tone: var(--scm-purple);
}
.tone-blue {
  --tone: var(--scm-blue);
}
.stat-icon {
  width: 55px;
  height: 55px;
  flex-shrink: 0;
  border: 1px solid #ffffff45;
  background: #ffffff15;
  border-radius: 50%;
  display: grid;
  place-items: center;
}
.stat-main {
  flex: 1;
  min-width: 0;
}
.stat-title {
  font-size: 13px;
}
.stat-value {
  display: block;
  font-size: 29px;
  font-weight: 600;
  line-height: 1.6;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.5px;
}
.stat-value small {
  font-size: 12px;
  font-weight: 400;
  margin: 0 4px;
}
.stat-comparison {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 11px;
  color: #ffffffeb;
}
@media (max-width: 1279px) {
  .statistic-card {
    padding: 18px 14px;
    gap: 10px;
  }
  .stat-icon {
    width: 42px;
    height: 42px;
  }
  .stat-value {
    font-size: 24px;
  }
}
</style>
