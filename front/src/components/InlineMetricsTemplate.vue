<template>
  <div class="space-y-2">
    <div class="text-xs text-ui-subtext">{{ title }}</div>
    <div class="bg-white border border-ui-border rounded p-2">
      <div ref="chartRef" class="h-[260px] w-full"></div>
    </div>
    <div v-if="template === 'health_overview'" class="grid grid-cols-2 gap-2 text-xs">
      <div class="bg-gray-50 rounded p-2">CPU 平均: {{ summary.avgCpu ?? 0 }}%</div>
      <div class="bg-gray-50 rounded p-2">CPU 峰值: {{ summary.maxCpu ?? 0 }}%</div>
      <div class="bg-gray-50 rounded p-2">内存平均: {{ summary.avgMem ?? 0 }}%</div>
      <div class="bg-gray-50 rounded p-2">内存峰值: {{ summary.maxMem ?? 0 }}%</div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  title: { type: String, default: '服务器监控图表' },
  chartData: { type: Object, default: () => ({}) },
})

const template = computed(() => props.chartData?.template || 'health_overview')
const summary = computed(() => props.chartData?.summary || {})
const option = computed(() => props.chartData?.option || {})

const chartRef = ref(null)
let chartInstance = null

const initChart = () => {
  if (!chartRef.value) return null
  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value)
  }
  return chartInstance
}

const renderChart = async () => {
  await nextTick()
  const instance = initChart()
  if (!instance) return
  instance.setOption(option.value, true)
  instance.resize()
}

const handleResize = () => {
  if (chartInstance) {
    chartInstance.resize()
  }
}

onMounted(() => {
  renderChart()
  window.addEventListener('resize', handleResize)
})

watch(() => props.chartData, renderChart, { deep: true })

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
})
</script>

