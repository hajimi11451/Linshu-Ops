<template>
  <div class="flex h-full min-h-0 flex-col overflow-hidden">
    <div class="flex flex-1 min-h-0 flex-col gap-4 xl:flex-row">
      <el-card
        class="glass-card flex-1 min-h-0 rounded-[30px]"
        :body-style="{ padding: '20px', height: '100%' }"
      >
        <div class="flex h-full min-h-0 flex-col">
          <div class="mb-3 flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
            <div class="min-w-0">
              <h3 class="mt-0 text-lg font-bold text-ui-text">监控趋势</h3>
              <p class="mt-0.5 text-sm" :class="healthTone.softText">
                {{ healthState.description }}
              </p>
            </div>

            <div class="flex flex-wrap gap-1.5 lg:max-w-[420px] lg:justify-end">
             <span class="glass-chip px-2.5 py-0.5 text-xs font-medium" :class="healthTone.chip">
                  健康度 {{ healthState.score }}%
                </span>
            </div>
          </div>

          <div class="glass-subcard mb-3 flex flex-col gap-3 p-3 text-sm text-ui-subtext">
            <!-- <div class="flex flex-wrap gap-1.5">
              <span
                v-for="item in enabledMetricLabels"
                :key="item"
                class="glass-chip border-white/24 bg-white/12 px-2 py-0.5 text-xs font-medium text-ui-text"
              >
                {{ item }}
              </span>
              <span
                v-if="!enabledMetricLabels.length"
                class="glass-chip border-amber-200/30 bg-amber-400/10 px-2 py-0.5 text-xs font-medium text-ui-warning"
              >
                当前未启用任何系统指标采集
              </span>
            </div> -->

            <div class="grid grid-cols-1 gap-3 md:grid-cols-2">
              <div><span class="font-bold">OS:</span> {{ currentInfo.os || 'N/A' }}</div>
              <div><span class="font-bold">运行:</span> {{ currentInfo.upTime || 'N/A' }}</div>
              <div><span class="font-bold">节点:</span> {{ currentInfo.processor || 'N/A' }}</div>
              <div><span class="font-bold">内存:</span> {{ currentInfo.availableMemory || 'N/A' }} / {{ currentInfo.totalMemory || 'N/A' }}</div>
              <div><span class="font-bold">网卡接收(RX):</span> {{ formatRate(currentInfo.netRxBytesPerSec) }}</div>
              <div><span class="font-bold">网卡发送(TX):</span> {{ formatRate(currentInfo.netTxBytesPerSec) }}</div>
              <div><span class="font-bold">磁盘读取:</span> {{ formatRate(currentInfo.diskReadBytesPerSec) }}</div>
              <div><span class="font-bold">磁盘写入:</span> {{ formatRate(currentInfo.diskWriteBytesPerSec) }}</div>
            </div>
          </div>

          <div class="glass-subcard flex-1 min-h-0 overflow-hidden p-2.5 lg:p-3" @wheel.prevent="handleTrendSliderWheel">
            <div class="flex h-full min-h-[280px] flex-col gap-3 lg:min-h-[300px]">
              <div class="relative min-h-0 flex-1 w-full">
                <canvas v-if="hasChartData" ref="monitorChartRef" class="h-full w-full"></canvas>
                <div v-else class="flex h-full items-center justify-center text-sm text-ui-subtext">
                  {{ loadingMonitor ? '加载监控数据...' : emptyChartLabel }}
                </div>
              </div>

              <div
                v-if="showTrendSlider"
                class="rounded-[18px] border border-white/18 bg-white/8 px-3 py-2.5"
              >
                
                <div class="mb-2 flex items-center justify-between text-xs text-ui-subtext">
                  <span>时间范围</span>
                  <span>{{ trendWindowRangeLabel }} / {{ props.historyData.length }}</span>
                </div>
                <input
                  v-model="trendWindowStart"
                  class="trend-slider w-full"
                  type="range"
                  min="0"
                  :max="maxTrendWindowStart"
                  step="1"
                />
              </div>
            </div>
          </div>
        </div>
      </el-card>

      <el-card
        class="glass-card w-full min-h-0 rounded-[30px] xl:basis-[32%] xl:max-w-[32%]"
        :body-style="{ padding: '24px', height: '100%' }"
      >
        <div class="flex h-full min-h-0 flex-col">
          <div class="mb-4 flex items-center justify-between gap-3">
            <div class="min-w-0">
              <h3 class="text-lg font-bold text-ui-text">最新告警</h3>
              <p class="mt-1 text-xs text-ui-subtext">当前服务器相关告警与诊断记录</p>
            </div>
            <router-link to="/info-list" class="text-sm text-brand hover:underline">
              全部
            </router-link>
          </div>

          <div class="glass-subcard flex-1 min-h-0 overflow-hidden p-3">
            <div class="custom-scrollbar h-full min-h-0 space-y-2 overflow-y-auto pr-1">
              <div v-if="loadingInfo" class="flex h-full items-center justify-center text-ui-subtext">加载中</div>

              <div v-else-if="infoList.length === 0" class="flex h-full items-center justify-center text-ui-subtext">暂无记录</div>

              <div
                v-else
                v-for="(info, index) in infoList"
                :key="info.id || index"
                class="glass-soft flex items-start gap-2.5 px-3 py-2.5"
                :class="getAlertClass(info.riskLevel)"
              >
                <span class="mt-1 inline-flex h-2 w-2 shrink-0 rounded-full" :class="getAlertDotClass(info.riskLevel)"></span>
                <div class="min-w-0 flex-1">
                  <div class="flex items-center justify-between gap-3">
                    <h4 class="truncate text-[13px] font-semibold text-ui-text">
                      {{ getCompactAlertTitle(info) }}
                    </h4>
                    <span class="glass-chip shrink-0 px-2 py-0.5 text-[11px] font-medium" :class="getAlertChipClass(info.riskLevel)">
                      {{ normalizeRiskLevel(info.riskLevel) }}
                    </span>
                  </div>
                  <p class="mt-1 truncate text-xs leading-5 text-ui-subtext">
                    {{ getCompactAlertSummary(info.errorSummary) }}
                  </p>
                  <div class="mt-1 flex items-center justify-between gap-2 text-[11px] text-ui-subtext/75">
                    <span class="truncate">服务器: {{ info.serverIp || '未知' }}</span>
                    <span class="shrink-0">{{ formatDate(info.createdAt) }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import Chart from 'chart.js/auto'
import {
  formatDate,
  getAlertClass,
  normalizeMonitorSettings,
  normalizeRiskLevel,
} from '../utils/dashboardHealth'

const props = defineProps({
  healthState: {
    type: Object,
    default: () => ({
      score: 100,
      description: '',
      activeAlertCount: 0,
      highRiskCount: 0,
      mediumRiskCount: 0,
      lowRiskCount: 0,
      normalCount: 0,
      totalLogsCount: 0,
      level: 'success',
    }),
  },
  infoList: {
    type: Array,
    default: () => [],
  },
  loadingInfo: {
    type: Boolean,
    default: false,
  },
  serverList: {
    type: Array,
    default: () => [],
  },
  selectedServer: {
    type: String,
    default: '',
  },
  loadingMonitor: {
    type: Boolean,
    default: false,
  },
  currentInfo: {
    type: Object,
    default: () => ({}),
  },
  historyData: {
    type: Array,
    default: () => [],
  },
})

const totalHistoryDuration = computed(() => {
  if (props.historyData.length < 2) {
    return 'N/A'
  }
  const firstTime = new Date(props.historyData[0].time)
  const lastTime = new Date(props.historyData[props.historyData.length - 1].time)
  const diffMs = lastTime.getTime() - firstTime.getTime()

  // Convert milliseconds to a human-readable format
  const seconds = Math.floor(diffMs / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)
  const days = Math.floor(hours / 24)

  if (days > 0) return `${days} 天`
  if (hours > 0) return `${hours} 小时`
  if (minutes > 0) return `${minutes} 分钟`
  return `${seconds} 秒`
})

const monitorChartRef = ref(null)
let monitorChartInstance = null
const trendWindowSize = 240
const trendWindowStart = ref(0)

const monitorSettings = computed(() => normalizeMonitorSettings(props.currentInfo?.monitorSettings))

const healthTone = computed(() => {
  const toneMap = {
    success: {
      chip: 'border-emerald-200/30 bg-emerald-400/10 text-ui-success',
      softText: 'text-green-600',
    },
    warning: {
      chip: 'border-amber-200/30 bg-amber-400/10 text-ui-warning',
      softText: 'text-orange-600',
    },
    error: {
      chip: 'border-red-200/30 bg-red-400/10 text-ui-error',
      softText: 'text-red-600',
    },
  }

  return toneMap[props.healthState.level] || toneMap.success
})

const enabledMetricLabels = computed(() => {
  const items = []
  if (monitorSettings.value.cpuEnabled) items.push('CPU 使用率')
  if (monitorSettings.value.memEnabled) items.push('内存使用率')
  if (monitorSettings.value.netRxEnabled) items.push('网卡接收速率')
  if (monitorSettings.value.netTxEnabled) items.push('网卡发送速率')
  if (monitorSettings.value.diskReadEnabled) items.push('磁盘读取速率')
  if (monitorSettings.value.diskWriteEnabled) items.push('磁盘写入速率')
  return items
})

const formatUsageValue = value => {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? `${Number(parsed.toFixed(1))}%` : '--'
}

const formatRate = value => {
  const parsed = Number(value)
  if (!Number.isFinite(parsed) || parsed < 0) return '--'

  if (parsed >= 1024 ** 3) return `${(parsed / 1024 ** 3).toFixed(1)} GB/s`
  if (parsed >= 1024 ** 2) return `${(parsed / 1024 ** 2).toFixed(1)} MB/s`
  if (parsed >= 1024) return `${(parsed / 1024).toFixed(1)} KB/s`
  return `${parsed.toFixed(1)} B/s`
}

const getMetricBadgeClass = value => {
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) {
    return 'border-white/24 bg-white/12 text-ui-subtext'
  }
  return 'border-white/24 bg-white/12 text-ui-text'
}

const monitorMetricBadges = computed(() => {
  const badges = []

  if (monitorSettings.value.cpuEnabled) {
    badges.push({
      key: 'cpuUsage',
      label: 'CPU',
      value: formatUsageValue(props.currentInfo?.cpuUsage),
      badgeClass: getMetricBadgeClass(props.currentInfo?.cpuUsage),
    })
  }
  if (monitorSettings.value.memEnabled) {
    badges.push({
      key: 'memUsage',
      label: 'Mem',
      value: formatUsageValue(props.currentInfo?.memUsage),
      badgeClass: getMetricBadgeClass(props.currentInfo?.memUsage),
    })
  }
  if (monitorSettings.value.netRxEnabled) {
    badges.push({
      key: 'netRxBytesPerSec',
      label: 'RX',
      value: formatRate(props.currentInfo?.netRxBytesPerSec),
      badgeClass: getMetricBadgeClass(props.currentInfo?.netRxBytesPerSec),
    })
  }
  if (monitorSettings.value.netTxEnabled) {
    badges.push({
      key: 'netTxBytesPerSec',
      label: 'TX',
      value: formatRate(props.currentInfo?.netTxBytesPerSec),
      badgeClass: getMetricBadgeClass(props.currentInfo?.netTxBytesPerSec),
    })
  }
  if (monitorSettings.value.diskReadEnabled) {
    badges.push({
      key: 'diskReadBytesPerSec',
      label: 'Disk Read',
      value: formatRate(props.currentInfo?.diskReadBytesPerSec),
      badgeClass: getMetricBadgeClass(props.currentInfo?.diskReadBytesPerSec),
    })
  }
  if (monitorSettings.value.diskWriteEnabled) {
    badges.push({
      key: 'diskWriteBytesPerSec',
      label: 'Disk Write',
      value: formatRate(props.currentInfo?.diskWriteBytesPerSec),
      badgeClass: getMetricBadgeClass(props.currentInfo?.diskWriteBytesPerSec),
    })
  }

  return badges
})

const datasetBlueprints = computed(() => {
  const blueprints = []

  if (monitorSettings.value.cpuEnabled) {
    blueprints.push({
      key: 'cpuUsage',
      label: 'CPU使用率 (%)',
      borderColor: '#3182ce',
      backgroundColor: 'rgba(49, 130, 206, 0.12)',
      yAxisID: 'usage',
      formatter: 'usage',
    })
  }
  if (monitorSettings.value.memEnabled) {
    blueprints.push({
      key: 'memUsage',
      label: '内存使用率 (%)',
      borderColor: '#38a169',
      backgroundColor: 'rgba(56, 161, 105, 0.12)',
      yAxisID: 'usage',
      formatter: 'usage',
    })
  }
  if (monitorSettings.value.netRxEnabled) {
    blueprints.push({
      key: 'netRxBytesPerSec',
      label: '网卡接收速率',
      borderColor: '#7c3aed',
      backgroundColor: 'rgba(124, 58, 237, 0.08)',
      yAxisID: 'rate',
      formatter: 'rate',
    })
  }
  if (monitorSettings.value.netTxEnabled) {
    blueprints.push({
      key: 'netTxBytesPerSec',
      label: '网卡发送速率',
      borderColor: '#d97706',
      backgroundColor: 'rgba(217, 119, 6, 0.08)',
      yAxisID: 'rate',
      formatter: 'rate',
    })
  }
  if (monitorSettings.value.diskReadEnabled) {
    blueprints.push({
      key: 'diskReadBytesPerSec',
      label: '磁盘读取速率',
      borderColor: '#0f766e',
      backgroundColor: 'rgba(15, 118, 110, 0.08)',
      yAxisID: 'rate',
      formatter: 'rate',
    })
  }
  if (monitorSettings.value.diskWriteEnabled) {
    blueprints.push({
      key: 'diskWriteBytesPerSec',
      label: '磁盘写入速率',
      borderColor: '#dc2626',
      backgroundColor: 'rgba(220, 38, 38, 0.08)',
      yAxisID: 'rate',
      formatter: 'rate',
    })
  }

  return blueprints
})

const maxTrendWindowStart = computed(() => Math.max(props.historyData.length - trendWindowSize, 0))

const visibleHistoryData = computed(() => {
  const safeStart = Math.max(0, Math.min(Number(trendWindowStart.value) || 0, maxTrendWindowStart.value))
  return props.historyData.slice(safeStart, safeStart + trendWindowSize)
})

const showTrendSlider = computed(() => props.historyData.length > trendWindowSize)

const trendWindowRangeLabel = computed(() => {
  const startIdx = Math.max(0, Math.min(Number(trendWindowStart.value) || 0, maxTrendWindowStart.value))
  const endIdx = Math.min(startIdx + trendWindowSize, props.historyData.length)

  if (visibleHistoryData.value.length === 0) {
    return 'N/A'
  }

  const startTime = visibleHistoryData.value[0]?.time || 'N/A'
  const endTime = visibleHistoryData.value[visibleHistoryData.value.length - 1]?.time || 'N/A'

  return `${startTime} - ${endTime}`
})

const chartDatasets = computed(() => datasetBlueprints.value
  .map(blueprint => ({
    ...blueprint,
    data: visibleHistoryData.value.map(item => {
      const parsed = Number(item?.[blueprint.key])
      return Number.isFinite(parsed) ? Number(parsed.toFixed(1)) : null
    }),
  }))
  .filter(dataset => dataset.data.some(value => value !== null)))

const hasChartData = computed(() => visibleHistoryData.value.length > 0 && chartDatasets.value.length > 0)

const emptyChartLabel = computed(() => {
  if (!enabledMetricLabels.value.length) return '当前未启用任何可绘制的监控项'
  return '暂无监控数据'
})

const getCompactAlertTitle = info => {
  const component = String(info?.component || '未命名组件').trim()
  const level = normalizeRiskLevel(info?.riskLevel)
  return `${component} · ${level}`
}

const getCompactAlertSummary = value => {
  const text = String(value || '').replace(/\s+/g, ' ').trim()
  if (!text) return '暂无摘要'
  return text.length > 34 ? `${text.slice(0, 34)}...` : text
}

const getAlertChipClass = level => {
  const normalized = normalizeRiskLevel(level)

  if (normalized === '高') return 'border-red-200/30 bg-red-400/10 text-ui-error'
  if (normalized === '中') return 'border-amber-200/30 bg-amber-400/10 text-ui-warning'
  if (normalized === '低') return 'border-sky-200/30 bg-sky-400/10 text-brand'
  return 'border-white/22 bg-white/10 text-ui-subtext'
}

const getAlertDotClass = level => {
  const normalized = normalizeRiskLevel(level)

  if (normalized === '高') return 'bg-ui-error'
  if (normalized === '中') return 'bg-ui-warning'
  if (normalized === '低') return 'bg-brand'
  return 'bg-white/50'
}

const destroyChart = () => {
  if (monitorChartInstance) {
    monitorChartInstance.destroy()
    monitorChartInstance = null
  }
}

const resolveXAxisLabelStep = labels => {
  const total = Array.isArray(labels) ? labels.length : 0
  if (total <= 8) return 1
  if (total <= 16) return 2
  if (total <= 24) return 3
  return Math.ceil(total / 8)
}

const buildMonitorChartConfig = labels => {
  const hasUsageAxis = chartDatasets.value.some(dataset => dataset.yAxisID === 'usage')
  const hasRateAxis = chartDatasets.value.some(dataset => dataset.yAxisID === 'rate')
  const xAxisLabelStep = resolveXAxisLabelStep(labels)
  const denseData = labels.length > 18

  return {
    type: 'line',
    data: {
      labels,
      datasets: chartDatasets.value.map(dataset => ({
        label: dataset.label,
        data: dataset.data,
        borderColor: dataset.borderColor,
        backgroundColor: dataset.backgroundColor,
        fill: false,
        tension: 0.35,
        borderWidth: 2,
        pointRadius: denseData ? 0 : 1.5,
        pointHoverRadius: 4,
        pointHitRadius: 10,
        yAxisID: dataset.yAxisID,
        metricFormatter: dataset.formatter,
      })),
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      //数据图刷新动画
      animation: false,
      interaction: {
        mode: 'index',
        intersect: false,
      },
      plugins: {
        legend: {
          position: 'top',
          labels: {
            color: '#5f6f87',
            usePointStyle: true,
            boxWidth: 8,
          },
        },
        tooltip: {
          backgroundColor: 'rgba(15, 23, 42, 0.82)',
          borderColor: 'rgba(255, 255, 255, 0.12)',
          borderWidth: 1,
          padding: 12,
          callbacks: {
            label(context) {
              const formatter = context.dataset.metricFormatter
              if (formatter === 'rate') {
                return `${context.dataset.label}: ${formatRate(context.parsed.y)}`
              }
              return `${context.dataset.label}: ${Number(context.parsed.y || 0).toFixed(1)}%`
            },
          },
        },
      },
      scales: {
        usage: {
          display: hasUsageAxis,
          beginAtZero: true,
          max: 100,
          grid: { color: 'rgba(148, 163, 184, 0.16)' },
          ticks: {
            color: '#5f6f87',
            callback(value) {
              return `${value}%`
            },
          },
          title: {
            display: hasUsageAxis,
            text: '使用率 (%)',
            color: '#5f6f87',
          },
        },
        rate: {
          display: hasRateAxis,
          position: 'right',
          beginAtZero: true,
          grid: { drawOnChartArea: false },
          ticks: {
            color: '#5f6f87',
            callback(value) {
              return formatRate(Number(value))
            },
          },
          title: {
            display: hasRateAxis,
            text: '速率',
            color: '#5f6f87',
          },
        },
        x: {
          grid: { display: false },
          ticks: {
            color: '#5f6f87',
            autoSkip: false,
            maxRotation: 0,
            minRotation: 0,
            padding: 6,
            callback(value, index) {
              if (index === 0 || index === labels.length - 1 || index % xAxisLabelStep === 0) {
                return labels[index]
              }
              return ''
            },
          },
        },
      },
    },
  }
}

const renderMonitorChart = async () => {
  await nextTick()

  if (!monitorChartRef.value || !hasChartData.value) {
    destroyChart()
    return
  }

  const labels = visibleHistoryData.value.map(item => item.time)
  const config = buildMonitorChartConfig(labels)

  if (monitorChartInstance) {
    monitorChartInstance.data = config.data
    monitorChartInstance.options = config.options
    monitorChartInstance.update()
    return
  }

  monitorChartInstance = new Chart(monitorChartRef.value, config)
}

const handleTrendSliderWheel = event => {
  if (!showTrendSlider.value) return

  const direction = Number(event?.deltaY) || 0
  if (!direction) return

  const currentStart = Number(trendWindowStart.value) || 0
  //步长控制
  const nextStart = direction < 0 ? currentStart - 20 : currentStart + 20
  trendWindowStart.value = Math.max(0, Math.min(nextStart, maxTrendWindowStart.value))
}

const handleResize = () => {
  if (monitorChartInstance) {
    monitorChartInstance.resize()
  }
}

watch(
  () => props.historyData.length,
  (nextLength, previousLength) => {
    const previousMax = Math.max(previousLength - trendWindowSize, 0)
    const nextMax = Math.max(nextLength - trendWindowSize, 0)
    const currentStart = Number(trendWindowStart.value) || 0
    const pinnedToLatest = currentStart >= previousMax

    if (pinnedToLatest) {
      trendWindowStart.value = nextMax
      return
    }

    trendWindowStart.value = Math.max(0, Math.min(currentStart, nextMax))
  },
  { immediate: true }
)

watch(trendWindowStart, value => {
  const safeValue = Math.max(0, Math.min(Number(value) || 0, maxTrendWindowStart.value))
  if (safeValue !== value) {
    trendWindowStart.value = safeValue
  }
})

watch(() => props.historyData, renderMonitorChart, { deep: true })
watch(monitorSettings, renderMonitorChart, { deep: true })
watch(visibleHistoryData, renderMonitorChart, { deep: true })

onMounted(() => {
  renderMonitorChart()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  destroyChart()
})
</script>

<style scoped>
.custom-scrollbar::-webkit-scrollbar {
  width: 6px;
}

.custom-scrollbar::-webkit-scrollbar-track {
  background: transparent;
}

.custom-scrollbar::-webkit-scrollbar-thumb {
  background-color: #cbd5e0;
  border-radius: 3px;
}

.trend-slider {
  appearance: none;
  height: 6px;
  border-radius: 999px;
  background: rgba(37, 99, 235, 0.15); /* More subtle blue */
  outline: none;
}

.trend-slider::-webkit-slider-thumb {
  appearance: none;
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.7); /* Slightly less opaque border */
  border-radius: 999px;
  background: rgba(37, 99, 235, 0.7); /* More translucent blue */
  box-shadow: 0 2px 8px rgba(37, 99, 235, 0.2); /* Softer shadow */
  cursor: ew-resize;
}

.trend-slider::-moz-range-thumb {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.7);
  border-radius: 999px;
  background: rgba(37, 99, 235, 0.7);
  box-shadow: 0 2px 8px rgba(37, 99, 235, 0.2);
  cursor: ew-resize;
}
</style>

