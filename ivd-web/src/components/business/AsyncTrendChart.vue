<template>
  <div class="async-trend-chart" :style="{ minHeight: `${height}px` }">
    <chart-component v-bind="$attrs" :height="height" />
  </div>
</template>
<script setup>
import { defineAsyncComponent, h } from 'vue'
defineOptions({ inheritAttrs: false })
defineProps({ height: { type: Number, default: 240 } })
const chartComponent = defineAsyncComponent({
  loader: () => import('./TrendChart.vue'),
  delay: 0,
  timeout: 15000,
  suspensible: false,
  loadingComponent: {
    render: () => h('div', { class: 'chart-load-status', role: 'status' }, '图表加载中…'),
  },
  errorComponent: {
    render: () => h('div', { class: 'chart-load-status', role: 'alert' }, '图表加载失败，请刷新重试'),
  },
  onError(error, retry, fail, attempts) {
    if (attempts < 2) retry()
    else fail()
  },
})
</script>
<style scoped>
.async-trend-chart { width: 100%; min-width: 0; }
.async-trend-chart :deep(.chart-load-status) {
  padding: 32px 16px;
  color: var(--scm-muted, #7b898e);
  font-size: 12px;
  text-align: center;
}
</style>
