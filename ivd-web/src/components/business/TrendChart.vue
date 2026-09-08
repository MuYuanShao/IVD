<template>
  <div
    v-if="labels.length"
    ref="chartElement"
    class="trend-chart"
    role="img"
    :aria-label="summary || title"
    :style="{ height: `${height}px` }"
  />
  <el-empty
    v-else
    description="暂无趋势数据"
    :image-size="60"
    :style="{ height: `${height}px` }"
  />
</template>
<script setup>
import { ref, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
  AriaComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
echarts.use([
  BarChart,
  LineChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  AriaComponent,
  CanvasRenderer,
])
const props = defineProps({
  title: String,
  summary: String,
  labels: { type: Array, default: () => [] },
  series: { type: Array, default: () => [] },
  height: { type: Number, default: 240 },
  unit: { type: String, default: '万元' },
  variant: { type: String, default: 'line' },
})
const chartElement = ref(null)
let chart
let observer
function render() {
  if (!chartElement.value) {
    chart?.dispose()
    chart = undefined
    return
  }
  chart ||= echarts.init(chartElement.value)
  chart.setOption(
    {
      color: ['#5470C6', '#91CC75', '#FAC858', '#EE6666', '#73C0DE'],
      animation: !window.matchMedia('(prefers-reduced-motion: reduce)').matches,
      aria: {
        enabled: true,
        label: { description: props.summary || props.title },
      },
      tooltip: { trigger: 'axis', confine: true },
      legend: {
        bottom: 4,
        icon: 'roundRect',
        itemWidth: 10,
        itemHeight: 5,
        textStyle: { color: '#7b898e', fontSize: 10 },
      },
      grid: { left: 12, right: 18, top: 36, bottom: 39, containLabel: true },
      xAxis: {
        type: 'category',
        data: props.labels,
        axisTick: { show: false },
        axisLine: { lineStyle: { color: '#e8edef' } },
        axisLabel: { color: '#8b999f', fontSize: 10, hideOverlap: true },
      },
      yAxis: {
        type: 'value',
        name: props.unit,
        nameTextStyle: { fontSize: 10, color: '#8b999f' },
        splitNumber: 3,
        axisLabel: { fontSize: 10, color: '#8b999f' },
        splitLine: { lineStyle: { color: '#eef2f4', type: 'dashed' } },
      },
      series: props.series.map((item) => ({
        name: item.name,
        data: item.data,
        type: props.variant,
        smooth: props.variant === 'line',
        showSymbol: false,
        barMaxWidth: 7,
        lineStyle: { width: 2 },
        areaStyle: props.variant === 'line' ? { opacity: 0.07 } : undefined,
      })),
    },
    true,
  )
  chart.resize()
}
onMounted(() => {
  render()
  observer = new ResizeObserver(() => chart?.resize())
  if (chartElement.value) observer.observe(chartElement.value)
})
watch(
  () => [props.labels, props.series, props.variant, props.height],
  async () => {
    await nextTick()
    render()
    observer?.disconnect()
    if (chartElement.value) observer?.observe(chartElement.value)
  },
  { deep: true },
)
onBeforeUnmount(() => {
  observer?.disconnect()
  chart?.dispose()
})
</script>
<style scoped>
.trend-chart {
  width: 100%;
  min-width: 0;
  padding: 0 5px;
}
</style>
