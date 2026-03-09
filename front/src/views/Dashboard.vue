<template>
  <div>
    <!-- 统计卡片区 -->
    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
      <!-- 系统健康度 -->
      <el-card
        class="bg-ui-card rounded-lg shadow-sm border border-ui-border hover:shadow-md transition"
        :body-style="{ padding: '24px' }"
      >
        <div class="flex justify-between items-start">
          <div>
            <p class="text-xs font-medium text-ui-subtext uppercase tracking-wider">系统健康度</p>
            <h3
              class="text-3xl font-bold mt-1"
              :class="healthScore >= 80 ? 'text-ui-success' : (healthScore >= 60 ? 'text-ui-warning' : 'text-ui-error')"
            >
              {{ healthScore }}<span class="text-lg ml-1">分</span>
            </h3>
          </div>
          <div
            class="p-2 rounded-lg"
            :class="healthScore >= 80 ? 'bg-green-50' : (healthScore >= 60 ? 'bg-orange-50' : 'bg-red-50')"
          >
            <svg
              class="w-6 h-6"
              :class="healthScore >= 80 ? 'text-ui-success' : (healthScore >= 60 ? 'text-ui-warning' : 'text-ui-error')"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
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

      <!-- 活跃告警 -->
      <el-card
        class="bg-ui-card rounded-lg shadow-sm border border-ui-border hover:shadow-md transition"
        :body-style="{ padding: '24px' }"
      >
        <div class="flex justify-between items-start">
          <div>
            <p class="text-xs font-medium text-ui-subtext uppercase tracking-wider">活跃告警</p>
            <h3 class="text-3xl font-bold text-ui-warning mt-1">{{ activeAlertCount }}</h3>
          </div>
          <div class="p-2 bg-orange-50 rounded-lg">
            <svg
              class="w-6 h-6 text-ui-warning"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
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

      <!-- 总日志数 -->
      <el-card
        class="bg-ui-card rounded-lg shadow-sm border border-ui-border hover:shadow-md transition"
        :body-style="{ padding: '24px' }"
      >
        <div class="flex justify-between items-start">
          <div>
            <p class="text-xs font-medium text-ui-subtext uppercase tracking-wider">已分析日志</p>
            <h3 class="text-3xl font-bold text-brand mt-1">{{ totalLogsCount }}</h3>
          </div>
          <div class="p-2 bg-blue-50 rounded-lg">
            <svg
              class="w-6 h-6 text-brand"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
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

      <!-- 高危事件 -->
      <el-card
        class="bg-ui-card rounded-lg shadow-sm border border-ui-border hover:shadow-md transition"
        :body-style="{ padding: '24px' }"
      >
        <div class="flex justify-between items-start">
          <div>
            <p class="text-xs font-medium text-ui-subtext uppercase tracking-wider">高危事件</p>
            <h3 class="text-3xl font-bold text-ui-error mt-1">{{ highRiskCount }}</h3>
          </div>
          <div class="p-2 bg-red-50 rounded-lg">
            <svg
              class="w-6 h-6 text-ui-error"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M13 10V3L4 14h7v7l9-11h-7z"
              />
            </svg>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 图表 + 最新告警区 -->
    <div class="flex flex-col lg:flex-row gap-6">
      <!-- 服务器监控控制台 -->
      <el-card
        class="w-full lg:w-2/3 bg-ui-card rounded-lg shadow-sm border border-ui-border"
        :body-style="{ padding: '24px' }"
      >
        <div class="flex justify-between items-center mb-6">
          <div class="flex items-center space-x-4">
            <h3 class="text-lg font-bold text-ui-text">服务器监控控制台</h3>
            <el-select 
              v-if="serverList.length > 0" 
              v-model="selectedServer" 
              size="small" 
              @change="handleServerChange" 
              placeholder="选择服务器" 
              class="w-40"
            >
              <el-option v-for="ip in serverList" :key="ip" :label="ip" :value="ip" />
            </el-select>
            <span v-else-if="loadingMonitor" class="text-xs text-gray-400">正在搜索服务器...</span>
            <span v-else class="text-xs text-gray-400">未找到服务器</span>
          </div>
          <div class="flex space-x-2">
            <span class="inline-flex items-center px-2 py-1 rounded text-xs font-medium bg-blue-50 text-brand">
              CPU: {{ currentInfo.cpuUsage || 0 }}%
            </span>
            <span class="inline-flex items-center px-2 py-1 rounded text-xs font-medium bg-green-50 text-ui-success">
              Mem: {{ currentInfo.memUsage || 0 }}%
            </span>
          </div>
        </div>
        
        <!-- 实时信息展示区域 -->
        <div class="grid grid-cols-2 gap-4 mb-4 text-sm text-gray-600 bg-gray-50 p-4 rounded-lg">
          <div><span class="font-bold">OS:</span> {{ currentInfo.os || 'N/A' }}</div>
          <div><span class="font-bold">运行时间:</span> {{ currentInfo.upTime || 'N/A' }}</div>
          <div><span class="font-bold">处理器:</span> {{ currentInfo.processor || 'N/A' }}</div>
          <div><span class="font-bold">内存:</span> {{ currentInfo.availableMemory }} / {{ currentInfo.totalMemory }}</div>
        </div>

        <div class="h-64 relative w-full flex items-center justify-center">
          <canvas v-if="hasChartData" ref="monitorChartRef"></canvas>
          <div v-else class="text-gray-400">正在获取监控数据...</div>
        </div>
      </el-card>

      <!-- 最新信息与告警 -->
      <el-card
        class="w-full lg:w-1/3 bg-ui-card rounded-lg shadow-sm border border-ui-border"
        :body-style="{ padding: '24px' }"
      >
        <div class="flex justify-between items-center mb-6">
          <h3 class="text-lg font-bold text-ui-text">最新信息与告警</h3>
          <router-link
            to="/info-list"
            class="text-sm text-brand hover:underline"
          >
            查看全部
          </router-link>
        </div>

        <div class="space-y-4 max-h-[400px] overflow-y-auto">
          <div v-if="loadingInfo" class="text-center text-gray-400 py-4">加载中...</div>

          <div v-else-if="infoList.length === 0" class="text-center text-gray-400 py-4">暂无数据</div>

          <div
            v-else
            v-for="(info, index) in infoList"
            :key="index"
            class="flex items-start p-3 rounded-lg border-l-4"
            :class="getAlertClass(info.riskLevel)"
          >
            <div class="flex-1">
              <h4 class="text-sm font-bold text-gray-800">
                {{ info.component }} - {{ info.riskLevel }}
              </h4>
              <p class="text-xs text-gray-600 mt-1">服务器: {{ info.serverIp }}</p>
              <p class="text-xs text-gray-400 mt-2">{{ formatDate(info.createdAt) }}</p>
            </div>
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import Chart from 'chart.js/auto'
import { selectAllInfo } from '../api/info'
import { getSystemDashboard } from '../api/monitor'

const monitorChartRef = ref(null)
let monitorChartInstance = null
const currentInfo = ref({})
const serverList = ref([])
const selectedServer = ref('')
const loadingMonitor = ref(false)

const infoList = ref([])
const activeAlertCount = ref(0)
const totalLogsCount = ref(0)
const highRiskCount = ref(0)
const healthScore = ref(100)
const hasChartData = ref(false)
const loadingInfo = ref(false)

// 获取监控数据
const fetchMonitorData = async (ip) => {
  loadingMonitor.value = true
  try {
    const res = await getSystemDashboard(ip || selectedServer.value)
    if (res) {
      if (res.servers && res.servers.length > 0) {
        serverList.value = res.servers
      }
      
      if (res.selectedIp) {
        selectedServer.value = res.selectedIp
      }

      currentInfo.value = res.current || {}
      
      if (res.history && res.history.length > 0) {
        hasChartData.value = true
        await nextTick()
        updateMonitorChart(res.history)
      } else {
        hasChartData.value = false
        if (monitorChartInstance) {
          monitorChartInstance.destroy()
          monitorChartInstance = null
        }
      }
    }
  } catch (error) {
    console.error('Failed to fetch monitor data:', error)
  } finally {
    loadingMonitor.value = false
  }
}

const handleServerChange = (val) => {
  fetchMonitorData(val)
}

const updateMonitorChart = (history) => {
  if (!monitorChartRef.value) return

  const labels = history.map(item => item.time)
  const cpuData = history.map(item => item.cpuUsage)
  const memData = history.map(item => item.memUsage)

  if (monitorChartInstance) {
    monitorChartInstance.data.labels = labels
    monitorChartInstance.data.datasets[0].data = cpuData
    monitorChartInstance.data.datasets[1].data = memData
    monitorChartInstance.update()
  } else {
    monitorChartInstance = new Chart(monitorChartRef.value, {
      type: 'line',
      data: {
        labels,
        datasets: [
          {
            label: 'CPU使用率 (%)',
            data: cpuData,
            borderColor: '#3182ce', // blue
            backgroundColor: 'rgba(49, 130, 206, 0.1)',
            fill: true,
            tension: 0.4,
            yAxisID: 'y',
          },
          {
            label: '内存使用率 (%)',
            data: memData,
            borderColor: '#38a169', // green
            backgroundColor: 'rgba(56, 161, 105, 0.1)',
            fill: true,
            tension: 0.4,
            yAxisID: 'y',
          }
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
              label: function(context) {
                return context.dataset.label + ': ' + context.parsed.y.toFixed(1) + '%';
              }
            }
          }
        },
        scales: {
          y: {
            beginAtZero: true,
            max: 100,
            grid: { color: '#e2e8f0' },
            title: {
              display: true,
              text: '使用率 (%)'
            }
          },
          x: { 
            grid: { display: false },
            ticks: {
              maxTicksLimit: 10
            }
          },
        },
      },
    })
  }
}

const normalizeRiskLevel = level => {
  const value = String(level || '').trim()
  if (['高', '中', '低', '无'].includes(value)) return value
  const lowered = value.toLowerCase()
  if (lowered.includes('high') || lowered.includes('critical') || lowered.includes('error')) return '高'
  if (lowered.includes('medium') || lowered.includes('warning') || lowered.includes('warn')) return '中'
  if (lowered.includes('low') || lowered.includes('info')) return '低'
  if (lowered.includes('normal') || lowered.includes('none') || lowered.includes('ok') || lowered.includes('safe')) return '无'
  return '中'
}

// 获取后端数据 (Info)
const fetchInfo = async () => {
  loadingInfo.value = true
  try {
    const res = await selectAllInfo()
    if (res && Array.isArray(res)) {
      infoList.value = res.map(item => ({
        ...item,
        riskLevel: normalizeRiskLevel(item?.riskLevel)
      }))

      activeAlertCount.value = infoList.value.filter(i =>
        ['高', '中'].includes(i.riskLevel)
      ).length

      totalLogsCount.value = infoList.value.length

      highRiskCount.value = infoList.value.filter(i => i.riskLevel === '高').length

      const penalty =
        highRiskCount.value * 10 +
        (activeAlertCount.value - highRiskCount.value) * 5
      healthScore.value = Math.max(0, 100 - penalty)
    }
  } catch (error) {
    console.error('Failed to fetch info:', error)
  } finally {
    loadingInfo.value = false
  }
}

const getAlertClass = level => {
  const map = {
    高: 'bg-red-50 border-ui-error',
    中: 'bg-orange-50 border-ui-warning',
    低: 'bg-blue-50 border-brand',
    无: 'bg-gray-50 border-gray-300',
  }
  return map[normalizeRiskLevel(level)] || map.无
}

const formatDate = dateStr => {
  if (!dateStr) return ''
  if (Array.isArray(dateStr)) {
    const [y, m, d, h, min, s] = dateStr
    return new Date(y, (m || 1) - 1, d || 1, h || 0, min || 0, s || 0).toLocaleString()
  }
  return new Date(dateStr).toLocaleString()
}

let timer = null

onMounted(() => {
  fetchInfo()
  fetchMonitorData()
  // 每60秒刷新一次监控数据
  timer = setInterval(() => fetchMonitorData(), 60000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
  if (monitorChartInstance) monitorChartInstance.destroy()
})
</script>

