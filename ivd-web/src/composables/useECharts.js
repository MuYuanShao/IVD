/**
 * useECharts — ECharts 生命周期封装
 *
 * 用法：
 *   const { chartInstance, refresh } = useECharts(chartRef, () => ({ ...option }))
 *
 * 自动处理：init / setOption / window.resize / dispose
 * 支持单页多图表（多次调用，每个 chartRef 独立管理）
 */
import { ref, watch, onMounted, onUnmounted, nextTick, shallowRef } from 'vue'
import * as echarts from 'echarts'

export function useECharts(chartRef, optionsFn) {
  const chartInstance = shallowRef(null)
  let resizeHandler = null

  function initChart() {
    if (!chartRef.value) return
    if (chartInstance.value) {
      chartInstance.value.dispose()
    }
    const instance = echarts.init(chartRef.value)
    instance.setOption(optionsFn() || {})
    chartInstance.value = instance

    resizeHandler = () => instance.resize()
    window.addEventListener('resize', resizeHandler)
  }

  function refresh() {
    if (chartInstance.value && optionsFn) {
      chartInstance.value.setOption(optionsFn(), true)
    }
  }

  function dispose() {
    if (resizeHandler) {
      window.removeEventListener('resize', resizeHandler)
      resizeHandler = null
    }
    if (chartInstance.value) {
      chartInstance.value.dispose()
      chartInstance.value = null
    }
  }

  onMounted(() => {
    nextTick(() => initChart())
  })

  onUnmounted(() => {
    dispose()
  })

  return { chartInstance, refresh }
}
