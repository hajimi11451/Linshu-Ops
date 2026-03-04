<template>
  <div class="space-y-2">
    <div class="text-xs text-ui-subtext">{{ title }}</div>
    <div class="bg-white border border-ui-border rounded p-2">
      <canvas ref="canvasRef" style="max-height: 260px;"></canvas>
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
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import Chart from 'chart.js/auto'

const props = defineProps({
  title: { type: String, default: '服务器监控图表' },
  chartData: { type: Object, default: () => ({}) },
})

const template = computed(() => props.chartData?.template || 'health_overview')
const history = computed(() => (Array.isArray(props.chartData?.history) ? props.chartData.history : []))
const summary = computed(() => props.chartData?.summary || {})
const anomalies = computed(() => (Array.isArray(props.chartData?.anomalies) ? props.chartData.anomalies : []))
const scores = computed(() => props.chartData?.healthScores || {})

const canvasRef = ref(null)
let chartInstance = null

const renderChart = () => {
  if (!canvasRef.value) return
  if (chartInstance) {
    chartInstance.destroy()
    chartInstance = null
  }

  const labels = history.value.map(item => item.time || '')
  const cpu = history.value.map(item => Number(item.cpuUsage || 0))
  const mem = history.value.map(item => Number(item.memUsage || 0))

  const commonOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { position: 'top' } },
  }

  if (template.value === 'cpu_mem_trend') {
    chartInstance = new Chart(canvasRef.value, {
      type: 'line',
      data: {
        labels,
        datasets: [
          { label: 'CPU 使用率(%)', data: cpu, borderColor: '#4299e1', backgroundColor: 'rgba(66,153,225,.15)', tension: 0.3, fill: true },
          { label: '内存使用率(%)', data: mem, borderColor: '#48bb78', backgroundColor: 'rgba(72,187,120,.15)', tension: 0.3, fill: true },
        ],
      },
      options: { ...commonOptions, scales: { y: { min: 0, max: 100 } } },
    })
    return
  }

  if (template.value === 'anomaly_timeline') {
    chartInstance = new Chart(canvasRef.value, {
      type: 'bar',
      data: {
        labels: anomalies.value.map(i => i.time || ''),
        datasets: [
          { label: '异常 CPU(%)', data: anomalies.value.map(i => Number(i.cpuUsage || 0)), backgroundColor: 'rgba(245,101,101,.7)' },
          { label: '异常内存(%)', data: anomalies.value.map(i => Number(i.memUsage || 0)), backgroundColor: 'rgba(237,137,54,.7)' },
        ],
      },
      options: { ...commonOptions, scales: { y: { min: 0, max: 100 } } },
    })
    return
  }

  if (template.value === 'health_score_radar') {
    chartInstance = new Chart(canvasRef.value, {
      type: 'radar',
      data: {
        labels: ['CPU', '内存', '稳定性', '容量'],
        datasets: [
          {
            label: '健康评分',
            data: [
              Number(scores.value.cpuScore || 0),
              Number(scores.value.memScore || 0),
              Number(scores.value.stabilityScore || 0),
              Number(scores.value.capacityScore || 0),
            ],
            borderColor: '#3182ce',
            backgroundColor: 'rgba(49,130,206,.2)',
          },
        ],
      },
      options: { ...commonOptions, scales: { r: { min: 0, max: 100 } } },
    })
    return
  }

  // health_overview 默认模板：双线 + 阈值参考线
  chartInstance = new Chart(canvasRef.value, {
    type: 'line',
    data: {
      labels,
      datasets: [
        { label: 'CPU 使用率(%)', data: cpu, borderColor: '#4299e1', backgroundColor: 'rgba(66,153,225,.15)', tension: 0.3, fill: true },
        { label: '内存使用率(%)', data: mem, borderColor: '#48bb78', backgroundColor: 'rgba(72,187,120,.15)', tension: 0.3, fill: true },
        { label: '85% 阈值', data: labels.map(() => 85), borderColor: '#ed8936', pointRadius: 0, borderDash: [5, 5], tension: 0 },
      ],
    },
    options: { ...commonOptions, scales: { y: { min: 0, max: 100 } } },
  })
}

onMounted(renderChart)
watch(() => props.chartData, renderChart, { deep: true })

onBeforeUnmount(() => {
  if (chartInstance) chartInstance.destroy()
})
</script>

