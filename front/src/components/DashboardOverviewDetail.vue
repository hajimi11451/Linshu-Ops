<template>
  <div class="h-full min-h-0 flex flex-col overflow-y-auto custom-scrollbar">
    <div class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-6">
      <el-card
        class="h-full bg-ui-card rounded-lg shadow-sm border border-ui-border hover:shadow-md transition"
        :body-style="{ padding: '24px' }"
      >
        <div class="flex justify-between items-start gap-4">
          <div>
            <p class="text-xs font-medium text-ui-subtext uppercase tracking-wider">系统健康度</p>
            <h3 class="text-3xl font-bold mt-1" :class="healthTone.text">
              {{ healthState.score }}<span class="text-lg ml-1">%</span>
            </h3>
            <p class="text-xs mt-2 leading-5" :class="healthTone.softText">
              {{ healthState.description }}
            </p>
          </div>
          <div class="p-2 rounded-lg border" :class="healthTone.badge">
            <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"
              />
            </svg>
          </div>
        </div>
      </el-card>

      <el-card
        class="h-full bg-ui-card rounded-lg shadow-sm border border-ui-border hover:shadow-md transition"
        :body-style="{ padding: '24px' }"
      >
        <div class="flex justify-between items-start">
          <div>
            <p class="text-xs font-medium text-ui-subtext uppercase tracking-wider">活跃告警</p>
            <h3 class="text-3xl font-bold mt-1 text-ui-warning">{{ healthState.activeAlertCount }}</h3>
            <p class="text-xs text-ui-subtext mt-2">高 / 中 / 低：{{ healthState.highRiskCount }} / {{ healthState.mediumRiskCount }} / {{ healthState.lowRiskCount }}</p>
          </div>
          <div class="p-2 bg-orange-50 rounded-lg border border-orange-100">
            <svg class="w-6 h-6 text-ui-warning" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
              />
            </svg>
          </div>
        </div>
      </el-card>

      <el-card
        class="h-full bg-ui-card rounded-lg shadow-sm border border-ui-border hover:shadow-md transition"
        :body-style="{ padding: '24px' }"
      >
        <div class="flex justify-between items-start">
          <div>
            <p class="text-xs font-medium text-ui-subtext uppercase tracking-wider">已分析日志</p>
            <h3 class="text-3xl font-bold text-brand mt-1">{{ healthState.totalLogsCount }}</h3>
            <p class="text-xs text-ui-subtext mt-2">无风险记录 {{ healthState.normalCount }} 条</p>
          </div>
          <div class="p-2 bg-blue-50 rounded-lg border border-blue-100">
            <svg class="w-6 h-6 text-brand" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
              />
            </svg>
          </div>
        </div>
      </el-card>

      <el-card
        class="h-full bg-ui-card rounded-lg shadow-sm border border-ui-border hover:shadow-md transition"
        :body-style="{ padding: '24px' }"
      >
        <div class="flex justify-between items-start">
          <div>
            <p class="text-xs font-medium text-ui-subtext uppercase tracking-wider">监控服务器</p>
            <h3 class="text-3xl font-bold text-ui-text mt-1">{{ serverList.length }}</h3>
            <p class="text-xs text-ui-subtext mt-2 truncate">当前：{{ selectedServer || '未选择服务器' }}</p>
          </div>
          <div class="p-2 bg-slate-50 rounded-lg border border-slate-200">
            <svg class="w-6 h-6 text-ui-text" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M5 12h14M5 7h14M5 17h14M4 4h16v16H4z"
              />
            </svg>
          </div>
        </div>
      </el-card>
    </div>

    <div class="mt-6 flex flex-1 min-h-0 flex-col xl:flex-row gap-6">
      <el-card
        class="w-full xl:w-2/3 h-full min-h-0 bg-ui-card rounded-lg shadow-sm border border-ui-border"
        :body-style="{ padding: '24px', height: '100%' }"
      >
        <div class="flex h-full min-h-0 flex-col">
          <div class="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between mb-6">
          <div class="flex items-center space-x-4">
            <h3 class="text-lg font-bold text-ui-text">服务器监控控制台</h3>
            <el-select
              v-if="serverList.length > 0"
              :model-value="selectedServer"
              size="small"
              @change="emit('server-change', $event)"
              placeholder="选择服务器"
              class="w-44"
            >
              <el-option v-for="ip in serverList" :key="ip" :label="ip" :value="ip" />
            </el-select>
            <span v-else-if="loadingMonitor" class="text-xs text-gray-400">正在搜索服务器...</span>
            <span v-else class="text-xs text-gray-400">未找到服务器</span>
          </div>

          <div class="flex flex-wrap gap-2">
            <span class="inline-flex items-center px-2.5 py-1 rounded text-xs font-medium border" :class="getUsageBadgeClass(currentInfo.cpuUsage)">
              CPU: {{ toUsageValue(currentInfo.cpuUsage) }}%
            </span>
            <span class="inline-flex items-center px-2.5 py-1 rounded text-xs font-medium border" :class="getUsageBadgeClass(currentInfo.memUsage)">
              Mem: {{ toUsageValue(currentInfo.memUsage) }}%
            </span>
          </div>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-4 mb-4 text-sm text-gray-600 bg-gray-50 p-4 rounded-lg border border-slate-100">
            <div><span class="font-bold">OS:</span> {{ currentInfo.os || 'N/A' }}</div>
            <div><span class="font-bold">运行时间:</span> {{ currentInfo.upTime || 'N/A' }}</div>
            <div><span class="font-bold">处理器:</span> {{ currentInfo.processor || 'N/A' }}</div>
            <div><span class="font-bold">内存:</span> {{ currentInfo.availableMemory || 'N/A' }} / {{ currentInfo.totalMemory || 'N/A' }}</div>
          </div>

          <div class="flex-1 min-h-[320px] relative w-full flex items-center justify-center">
            <canvas v-if="hasChartData" ref="monitorChartRef"></canvas>
            <div v-else class="text-gray-400 text-sm">{{ loadingMonitor ? '正在获取监控数据...' : '暂无监控数据' }}</div>
          </div>
        </div>
      </el-card>

      <el-card
        class="w-full xl:w-1/3 h-full min-h-0 bg-ui-card rounded-lg shadow-sm border border-ui-border"
        :body-style="{ padding: '24px', height: '100%' }"
      >
        <div class="flex h-full min-h-0 flex-col">
          <div class="flex justify-between items-center mb-6">
            <h3 class="text-lg font-bold text-ui-text">最新信息与告警</h3>
            <router-link to="/info-list" class="text-sm text-brand hover:underline">
              查看全部
            </router-link>
          </div>

          <div class="flex-1 min-h-0 space-y-4 overflow-y-auto pr-1 custom-scrollbar">
            <div v-if="loadingInfo" class="text-center text-gray-400 py-4">加载中...</div>

            <div v-else-if="infoList.length === 0" class="text-center text-gray-400 py-4">暂无数据</div>

            <div
              v-else
              v-for="(info, index) in infoList"
              :key="info.id || index"
              class="flex items-start p-3 rounded-lg border-l-4"
              :class="getAlertClass(info.riskLevel)"
            >
              <div class="flex-1 min-w-0">
                <h4 class="text-sm font-bold text-gray-800 truncate">
                  {{ info.component }} - {{ info.riskLevel }}
                </h4>
                <p class="text-xs text-gray-600 mt-1 truncate">服务器: {{ info.serverIp || '未知' }}</p>
                <p class="text-xs text-gray-400 mt-2">{{ formatDate(info.createdAt) }}</p>
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
import { formatDate, getAlertClass } from '../utils/dashboardHealth'

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

const emit = defineEmits(['server-change'])

const monitorChartRef = ref(null)
let monitorChartInstance = null

const healthTone = computed(() => {
  const toneMap = {
    success: {
      text: 'text-ui-success',
      softText: 'text-green-600',
      badge: 'bg-green-50 border-green-100 text-ui-success',
    },
    warning: {
      text: 'text-ui-warning',
      softText: 'text-orange-600',
      badge: 'bg-orange-50 border-orange-100 text-ui-warning',
    },
    error: {
      text: 'text-ui-error',
      softText: 'text-red-600',
      badge: 'bg-red-50 border-red-100 text-ui-error',
    },
  }

  return toneMap[props.healthState.level] || toneMap.success
})

const hasChartData = computed(() => props.historyData.length > 0)

const toUsageValue = value => {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? Number(parsed.toFixed(1)) : 0
}

const getUsageBadgeClass = value => {
  const usage = toUsageValue(value)

  if (usage >= 85) return 'bg-red-50 border-red-100 text-ui-error'
  if (usage >= 70) return 'bg-orange-50 border-orange-100 text-ui-warning'
  return 'bg-green-50 border-green-100 text-ui-success'
}

const destroyChart = () => {
  if (monitorChartInstance) {
    monitorChartInstance.destroy()
    monitorChartInstance = null
  }
}

const renderMonitorChart = async () => {
  await nextTick()

  if (!monitorChartRef.value || !props.historyData.length) {
    destroyChart()
    return
  }

  const labels = props.historyData.map(item => item.time)
  const cpuData = props.historyData.map(item => item.cpuUsage)
  const memData = props.historyData.map(item => item.memUsage)

  if (monitorChartInstance) {
    monitorChartInstance.data.labels = labels
    monitorChartInstance.data.datasets[0].data = cpuData
    monitorChartInstance.data.datasets[1].data = memData
    monitorChartInstance.update()
    return
  }

  monitorChartInstance = new Chart(monitorChartRef.value, {
    type: 'line',
    data: {
      labels,
      datasets: [
        {
          label: 'CPU使用率 (%)',
          data: cpuData,
          borderColor: '#3182ce',
          backgroundColor: 'rgba(49, 130, 206, 0.1)',
          fill: true,
          tension: 0.4,
          yAxisID: 'y',
        },
        {
          label: '内存使用率 (%)',
          data: memData,
          borderColor: '#38a169',
          backgroundColor: 'rgba(56, 161, 105, 0.1)',
          fill: true,
          tension: 0.4,
          yAxisID: 'y',
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      interaction: {
        mode: 'index',
        intersect: false,
      },
      plugins: {
        legend: {
          position: 'top',
        },
        tooltip: {
          callbacks: {
            label(context) {
              return `${context.dataset.label}: ${context.parsed.y.toFixed(1)}%`
            },
          },
        },
      },
      scales: {
        y: {
          beginAtZero: true,
          max: 100,
          grid: { color: '#e2e8f0' },
          title: {
            display: true,
            text: '使用率 (%)',
          },
        },
        x: {
          grid: { display: false },
          ticks: {
            maxTicksLimit: 10,
          },
        },
      },
    },
  })
}

const handleResize = () => {
  if (monitorChartInstance) {
    monitorChartInstance.resize()
  }
}

watch(() => props.historyData, renderMonitorChart, { deep: true })

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
</style>
