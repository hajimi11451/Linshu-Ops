<template>
  <div class="h-full min-h-0">
    <div
      ref="pagerRef"
      class="dashboard-pager h-full min-h-0"
      tabindex="0"
      @scroll.passive="handlePagerScroll"
      @keydown.left.prevent="switchPage(-1)"
      @keydown.right.prevent="switchPage(1)"
    >
      <section class="dashboard-page">
        <div class="dashboard-page-shell h-full" :style="getPageMotionStyle(0)">
          <div class="flex h-full min-h-0 flex-col overflow-y-auto p-6 lg:p-8 custom-scrollbar">
            <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
              <div class="glass-card p-5">
                <div class="text-xs uppercase tracking-[0.22em] text-ui-subtext">已接入</div>
                <div class="mt-3 text-3xl font-bold text-ui-text">{{ dashboardStats.total }}</div>
                <p class="mt-2 text-sm text-ui-subtext">服务器监控数量</p>
              </div>

              <div class="glass-card p-5">
                <div class="text-xs uppercase tracking-[0.22em] text-ui-subtext">健康</div>
                <div class="mt-3 text-3xl font-bold text-ui-text">{{ dashboardStats.success }}</div>
                <p class="mt-2 text-sm text-ui-subtext">核心资源稳定</p>
              </div>

              <div class="glass-card p-5">
                <div class="text-xs uppercase tracking-[0.22em] text-ui-subtext">关注</div>
                <div class="mt-3 text-3xl font-bold text-ui-text">{{ dashboardStats.warning }}</div>
                <p class="mt-2 text-sm text-ui-subtext">资源波动需关注</p>
              </div>

              <div class="glass-card p-5">
                <div class="text-xs uppercase tracking-[0.22em] text-ui-subtext">健康均值</div>
                <div class="mt-3 text-3xl font-bold text-brand">{{ dashboardStats.averageScore }}%</div>
                <p class="mt-2 text-sm text-ui-subtext">更新：{{ lastUpdatedLabel }}</p>
              </div>
            </div>

            <div class="glass-card mt-4 flex flex-1 min-h-0 flex-col p-4 lg:p-5">
              <div class="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
                <div>
                  <h3 class="text-lg font-bold text-ui-text">服务器态势</h3>
                </div>

                <div class="flex flex-wrap items-center gap-3">
                  <div class="glass-chip p-1">
                    <button
                      type="button"
                      class="flex h-9 w-9 items-center justify-center rounded-full text-ui-subtext transition-colors hover:bg-white/10 hover:text-ui-text disabled:cursor-not-allowed disabled:opacity-40"
                      :disabled="activeCardIndex <= 0"
                      @click="switchCarousel(-1)"
                    >
                      <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
                      </svg>
                    </button>
                    <button
                      type="button"
                      class="flex h-9 w-9 items-center justify-center rounded-full text-ui-subtext transition-colors hover:bg-white/10 hover:text-ui-text disabled:cursor-not-allowed disabled:opacity-40"
                      :disabled="activeCardIndex >= carouselItems.length - 1"
                      @click="switchCarousel(1)"
                    >
                      <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
                      </svg>
                    </button>
                  </div>
                </div>
              </div>

              <div class="mt-4 flex flex-1 min-h-0 flex-col">
                <div
                  ref="carouselRef"
                  class="server-carousel flex-1 min-h-0"
                  tabindex="0"
                  @scroll.passive="handleCarouselScroll"
                  @keydown.left.prevent="switchCarousel(-1)"
                  @keydown.right.prevent="switchCarousel(1)"
                  @wheel.prevent="handleCarouselWheel"
                >
                  <article
                    v-for="(item, index) in carouselItems"
                    :key="item.key"
                    :ref="el => setSlideRef(el, index)"
                    class="server-slide"
                    :class="{ 'is-active': activeCardIndex === index, 'server-slide-add': item.type === 'add' }"
                    :style="getSlideStyle(index)"
                    @click="item.type === 'add' ? openAddMonitorDialog() : openServerDetail(item.serverIp)"
                    @keyup.enter.prevent="item.type === 'add' ? openAddMonitorDialog() : openServerDetail(item.serverIp)"
                    @keyup.space.prevent="item.type === 'add' ? openAddMonitorDialog() : openServerDetail(item.serverIp)"
                    :tabindex="0"
                  >
                    <template v-if="item.type === 'add'">
                      <div class="glass-card flex h-full flex-col items-center justify-center border-dashed p-8 text-center">
                        <div class="flex h-[72px] w-[72px] items-center justify-center rounded-full bg-brand/10 text-brand">
                          <svg class="h-10 w-10" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6v12m6-6H6" />
                          </svg>
                        </div>
                        <h4 class="mt-6 text-2xl font-bold text-ui-text">新增服务器</h4>
                        <p class="mt-3 max-w-[260px] text-sm leading-6 text-ui-subtext">
                          接入后默认开始采集服务器监控数据。
                        </p>
                        <div class="mt-6 inline-flex items-center rounded-full bg-brand px-4 py-2 text-sm font-semibold text-white shadow-sm">
                          添加
                        </div>
                      </div>
                    </template>

                    <template v-else>
                      <div
                        class="server-status-card glass-card flex h-full flex-col justify-between px-4 py-3 ring-1 ring-white/14"
                        :style="getServerCardVisualStyle(item)"
                      >
                        <div class="flex items-start justify-between gap-3">
                          <div class="min-w-0">
                            <p class="truncate text-sm font-semibold text-ui-text">{{ item.serverIp }}</p>
                            <p class="mt-1 text-xs text-ui-subtext">{{ item.subtitle }}</p>
                          </div>
                          <span class="server-health-pill rounded-full border px-3 py-1 text-xs font-semibold">
                            {{ item.health.label }}
                          </span>
                        </div>

                        <div class="server-health-stage my-1.5 flex justify-center">
                          <div class="liquid-sphere-wrapper" :style="getLiquidSphereStyle(item)">
                            <div class="liquid-sphere">
                              <div class="liquid-level">
                                <div class="wave wave-back"></div>
                                <div class="wave wave-front"></div>
                              </div>
                              <div class="sphere-glare"></div>
                              <div class="sphere-shadow"></div>
                              
                              <div class="server-health-value-sphere">
                                <div class="text-[26px] font-bold">{{ item.health.score }}%</div>
                                <div class="mt-1 text-[11px] font-medium ">系统健康度</div>
                              </div>
                            </div>
                          </div>
                        </div>

                        <div class="server-metric-grid grid grid-cols-3 gap-2">
                          <div
                            v-for="metric in item.metrics"
                            :key="metric.key"
                            class="server-metric-card glass-soft px-2.5 py-2"
                          >
                            <div class="text-[10px] uppercase tracking-[0.16em] text-ui-subtext">{{ metric.label }}</div>
                            <div class="server-metric-value mt-1 text-[13px] font-semibold leading-tight" :class="metric.valueClass">
                              {{ metric.value }}
                            </div>
                          </div>
                        </div>

                        <div class="mt-1.5 flex items-center justify-between text-sm">
                          <span class="text-ui-subtext">进入详情</span>
                          <span class="font-semibold text-brand">查看</span>
                        </div>
                      </div>
                    </template>
                  </article>
                </div>

                <div class="pt-3 flex items-center justify-center gap-2">
                  <button
                    v-for="(card, index) in serverCards"
                    :key="`dot-${card.serverIp}`"
                    type="button"
                    class="server-dot"
                    :class="{ 'is-active': activeCardIndex === index }"
                    @click="snapToCard(index)"
                  ></button>
                </div>

                <div v-if="!serverCards.length" class="glass-soft pt-4 px-4 py-5 text-center text-sm text-ui-subtext">
                  暂无服务器监控，可先添加一台。
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="dashboard-page">
        <div class="dashboard-page-shell h-full" :style="getPageMotionStyle(1)">
          <div class="dashboard-detail-shell glass-card h-full min-h-0 flex flex-col overflow-hidden">
            <div class="glass-toolbar flex items-center justify-between gap-4 rounded-none border-x-0 border-t-0 px-5 py-4 lg:px-6">
              <div class="flex items-center gap-3">
                <button
                  type="button"
                  class="glass-chip px-4 py-2 text-sm font-medium text-ui-text transition-colors hover:border-brand/35 hover:text-brand"
                  @click="scrollToPage(0)"
                >
                  <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
                  </svg>
                  返回首页
                </button>
              </div>

              <div class="min-w-0 text-right">
                <div class="text-xs uppercase tracking-[0.18em] text-ui-subtext">详情</div>
                <div class="truncate text-sm font-medium text-ui-text">{{ selectedServer || '当前服务器' }}</div>
                <div class="mt-1 text-xs" :class="selectedMonitorEnabled ? 'text-ui-success' : 'text-ui-warning'">
                  {{ selectedMonitorEnabled ? '状态：检测中' : '状态：已暂停检测' }}
                </div>
              </div>
            </div>

            <div class="flex-1 min-h-0 overflow-hidden p-4 lg:p-5">
              <DashboardOverviewDetail
                class="dashboard-detail-panel h-full min-h-0"
                :health-state="selectedHealthState"
                :info-list="selectedServerInfoList"
                :loading-info="loadingInfo"
                :server-list="serverList"
                :selected-server="selectedServer"
                :loading-monitor="loadingMonitor"
                :current-info="currentInfo"
                :history-data="historyData"
              />
            </div>
          </div>
        </div>
      </section>
    </div>

    <el-dialog
      v-model="addMonitorDialogVisible"
      title="新增服务器"
      width="86vw"
      class="add-server-dialog"
      modal-class="keep-bright-overlay"
      append-to-body
      :close-on-click-modal="true"
      :close-on-press-escape="true"
      destroy-on-close
    >
      <div class="add-server-dialog__body flex h-full min-h-0 flex-col">
        
        

        <el-form class="mt-5 flex-1 overflow-y-auto pr-1 custom-scrollbar" label-position="top">
        <el-form-item label="服务器 IP">
          <el-input v-model="addMonitorForm.serverIp" placeholder="192.168.1.10 或 192.168.1.10:22" clearable />
        </el-form-item>

        <div class="grid gap-4 md:grid-cols-2">
          <el-form-item label="SSH 用户名">
            <el-input v-model="addMonitorForm.username" placeholder="root" clearable />
          </el-form-item>

          <el-form-item label="SSH 密码">
            <el-input v-model="addMonitorForm.password" type="password" placeholder="请输入 SSH 密码" show-password />
          </el-form-item>
        </div>

        <div class="glass-subcard px-4 py-3  ">
        <p class="text-base">注意</p>
        <div style="margin-left:10px " class="mt-3 text-sm text-ui-subtext">
        <p>采集内容包括:系统型号、运行时间、节点、内存、网卡IO、磁盘IO;</p>
        <p>如需检测其他组件，可以在诊断-新增列表中添加</p>
        </div>
        </div>
        
        </el-form>
      </div>

      <template #footer>
        <div class="flex justify-end gap-3">
          <el-button @click="addMonitorDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="addMonitorLoading" @click="submitAddServerMonitor">
            保存并开始
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { addServerMonitor, resumeServerMonitor, stopServerMonitor } from '../api/diagnosis'
import { selectAllInfo } from '../api/info'
import { getSystemDashboard } from '../api/monitor'
import DashboardOverviewDetail from '../components/DashboardOverviewDetail.vue'
import {
  formatDate,
  getDateTimestamp,
  normalizeMonitorSettings,
  normalizeRiskLevel,
  resolveSystemHealth,
} from '../utils/dashboardHealth'

const pageTabs = [
  { label: '首页' },
  { label: '详情页' },
]

const pagerRef = ref(null)
const carouselRef = ref(null)
const slideElements = ref([])
const slideMotionStyles = ref([])

const activePage = ref(0)
const scrollProgress = ref(0)
const activeCardIndex = ref(0)

const infoList = ref([])
const loadingInfo = ref(false)

const currentInfo = ref({})
const historyData = ref([])
const serverList = ref([])
const selectedServer = ref('')
const serverMonitorMap = ref({})
const loadingMonitor = ref(false)
const lastUpdatedAt = ref('')

const addMonitorDialogVisible = ref(false)
const addMonitorLoading = ref(false)
const stoppingMonitor = ref(false)

const defaultMonitorSettings = () => ({
  cpuEnabled: true,
  memEnabled: true,
  netRxEnabled: true,
  netTxEnabled: true,
  diskReadEnabled: true,
  diskWriteEnabled: true,
})

const addMonitorForm = reactive({
  serverIp: '',
  username: '',
  password: '',
  ...defaultMonitorSettings(),
})

let refreshTimer = null
let scrollFrame = null
let carouselFrame = null

const selectedServerInfoList = computed(() => {
  if (!selectedServer.value) return infoList.value
  return infoList.value.filter(item => item.serverIp === selectedServer.value)
})

const lastUpdatedLabel = computed(() => lastUpdatedAt.value ? formatDate(lastUpdatedAt.value) : '等待刷新')

const toneMap = {
  success: {
    text: 'text-ui-success',
    softText: 'text-green-600',
    pill: 'border-green-100 bg-green-50 text-ui-success',
    track: 'rgba(72, 187, 120, 0.16)',
    gradientStart: '#8de3aa',
    gradientEnd: '#48bb78',
  },
  warning: {
    text: 'text-ui-warning',
    softText: 'text-orange-600',
    pill: 'border-orange-100 bg-orange-50 text-ui-warning',
    track: 'rgba(237, 137, 54, 0.16)',
    gradientStart: '#f6be73',
    gradientEnd: '#ed8936',
  },
  error: {
    text: 'text-ui-error',
    softText: 'text-red-600',
    pill: 'border-red-100 bg-red-50 text-ui-error',
    track: 'rgba(245, 101, 101, 0.16)',
    gradientStart: '#f89a9a',
    gradientEnd: '#f56565',
  },
}

const liquidSphereToneMap = {
  success: {
    start: '#5af29f',
    end: '#14c767',
    shellTop: '#effff6',
    shellMid: '#baf7d5',
    shellBottom: '#76e9b5',
    text: '#ffffff',
  },
  warning: {
    start: '#ffd54f',
    end: '#ff8a1f',
    shellTop: '#fff6dc',
    shellMid: '#ffe0a8',
    shellBottom: '#ffbf74',
    text: '#fffdf8',
  },
  error: {
    start: '#ff8fab',
    end: '#ff4b4b',
    shellTop: '#ffe7ed',
    shellMid: '#ffc0cd',
    shellBottom: '#ff92a7',
    text: '#fffafb',
  },
}

const hexToRgba = (hex, alpha) => {
  const normalized = String(hex || '').trim().replace('#', '')
  const fullHex = normalized.length === 3
    ? normalized.split('').map(char => `${char}${char}`).join('')
    : normalized

  if (fullHex.length !== 6) {
    return `rgba(72, 187, 120, ${alpha})`
  }

  const red = Number.parseInt(fullHex.slice(0, 2), 16)
  const green = Number.parseInt(fullHex.slice(2, 4), 16)
  const blue = Number.parseInt(fullHex.slice(4, 6), 16)

  if ([red, green, blue].some(Number.isNaN)) {
    return `rgba(72, 187, 120, ${alpha})`
  }

  return `rgba(${red}, ${green}, ${blue}, ${alpha})`
}

const getToneByLevel = level => toneMap[level] || toneMap.success

const getServerCardVisualStyle = item => ({
  '--server-ring-glow': hexToRgba(item?.tone?.gradientEnd, 0.28),
  '--server-ring-glow-soft': hexToRgba(item?.tone?.gradientStart, 0.18),
  '--server-ring-shadow-soft': hexToRgba(item?.tone?.gradientEnd, 0.18),
})

const getLiquidSphereStyle = item => {
  const palette = liquidSphereToneMap[item?.health?.level] || liquidSphereToneMap.success
  const score = Math.max(0, Math.min(Number(item?.health?.score) || 0, 100))

  return {
    '--color-start': palette.start,
    '--color-end': palette.end,
    '--fill-level': `${score}%`,
    '--sphere-shell-top': hexToRgba(palette.shellTop, 0.56),
    '--sphere-shell-mid': hexToRgba(palette.shellMid, 0.34),
    '--sphere-shell-bottom': hexToRgba(palette.shellBottom, 0.22),
    '--sphere-glow': hexToRgba(palette.end, 0.44),
    '--sphere-glow-soft': hexToRgba(palette.start, 0.32),
    '--sphere-ring': hexToRgba(palette.end, 0.54),
    '--sphere-highlight': hexToRgba(palette.shellTop, 0.88),
    '--sphere-liquid-shadow': hexToRgba(palette.end, 0.3),
    '--sphere-text': palette.text,
  }
}

const buildUsageTone = usage => {
  if (usage >= 85) return { text: 'text-ui-error' }
  if (usage >= 70) return { text: 'text-ui-warning' }
  return { text: 'text-ui-success' }
}

// 通用服务器的经验阈值：绿色表示日常波动，橙色表示明显偏高，红色表示较少见的高占用。
const metricRateThresholds = {
  network: {
    warning: 5 * 1024 ** 2,
    error: 20 * 1024 ** 2,
  },
  disk: {
    warning: 12 * 1024 ** 2,
    error: 48 * 1024 ** 2,
  },
}

const buildRateTone = (value, thresholds) => {
  const parsed = Number(value)
  if (!Number.isFinite(parsed) || parsed < 0) return { text: 'text-ui-subtext' }
  if (parsed >= thresholds.error) return { text: 'text-ui-error' }
  if (parsed >= thresholds.warning) return { text: 'text-ui-warning' }
  return { text: 'text-ui-success' }
}

const formatCompactRate = value => {
  const parsed = Number(value)
  if (!Number.isFinite(parsed) || parsed < 0) return '--'
  if (parsed >= 1024 ** 3) return `${(parsed / 1024 ** 3).toFixed(1)}G/s`
  if (parsed >= 1024 ** 2) return `${(parsed / 1024 ** 2).toFixed(1)}M/s`
  if (parsed >= 1024) return `${(parsed / 1024).toFixed(1)}K/s`
  return `${parsed.toFixed(0)}B/s`
}

const buildServerMetricItems = (snapshot, health) => {
  const current = snapshot?.current || {}
  const settings = normalizeMonitorSettings(current.monitorSettings)

  return [
    {
      key: 'cpu',
      label: 'CPU',
      value: settings.cpuEnabled ? `${health.cpuUsage}%` : 'OFF',
      valueClass: settings.cpuEnabled ? buildUsageTone(health.cpuUsage).text : 'text-ui-subtext',
    },
    {
      key: 'mem',
      label: 'MEM',
      value: settings.memEnabled ? `${health.memUsage}%` : 'OFF',
      valueClass: settings.memEnabled ? buildUsageTone(health.memUsage).text : 'text-ui-subtext',
    },
    {
      key: 'rx',
      label: 'RX',
      value: settings.netRxEnabled ? formatCompactRate(current.netRxBytesPerSec) : 'OFF',
      valueClass: settings.netRxEnabled
        ? buildRateTone(current.netRxBytesPerSec, metricRateThresholds.network).text
        : 'text-ui-subtext',
    },
    {
      key: 'tx',
      label: 'TX',
      value: settings.netTxEnabled ? formatCompactRate(current.netTxBytesPerSec) : 'OFF',
      valueClass: settings.netTxEnabled
        ? buildRateTone(current.netTxBytesPerSec, metricRateThresholds.network).text
        : 'text-ui-subtext',
    },
    {
      key: 'read',
      label: 'READ',
      value: settings.diskReadEnabled ? formatCompactRate(current.diskReadBytesPerSec) : 'OFF',
      valueClass: settings.diskReadEnabled
        ? buildRateTone(current.diskReadBytesPerSec, metricRateThresholds.disk).text
        : 'text-ui-subtext',
    },
    {
      key: 'write',
      label: 'WRITE',
      value: settings.diskWriteEnabled ? formatCompactRate(current.diskWriteBytesPerSec) : 'OFF',
      valueClass: settings.diskWriteEnabled
        ? buildRateTone(current.diskWriteBytesPerSec, metricRateThresholds.disk).text
        : 'text-ui-subtext',
    },
  ]
}

const getServerInfoList = serverIp => {
  if (!serverIp) return []
  return infoList.value.filter(item => item.serverIp === serverIp)
}

const buildPendingHealthState = infoItems => {
  const baseState = resolveSystemHealth({
    currentInfo: { cpuUsage: 0, memUsage: 0 },
    infoList: infoItems,
  })

  if (baseState.level !== 'success' || baseState.activeAlertCount > 0) {
    return {
      ...baseState,
      description: `${baseState.description} 当前监控数据暂未刷新，资源占用按 0% 暂存显示。`,
      reasons: [
        ...baseState.reasons,
        '当前服务器尚未拿到最新系统监控采样数据，将在下一次采样后刷新。',
      ],
    }
  }

  return {
    ...baseState,
    score: 0,
    label: '待采样',
    level: 'warning',
    description: '暂未获取到系统监控数据，请稍候等待首次采样。',
    reasons: ['当前服务器还没有最新监控数据，系统将继续采集已启用的资源指标。'],
  }
}

const hasBackendHealthState = healthState => healthState && typeof healthState === 'object' && Object.keys(healthState).length > 0

const resolveSnapshotHealthState = (snapshot, infoItems) => {
  if (hasBackendHealthState(snapshot?.healthState)) {
    return snapshot.healthState
  }

  const hasData = snapshot?.current && Object.keys(snapshot.current).length > 0
  if (!hasData) {
    return buildPendingHealthState(infoItems)
  }

  return resolveSystemHealth({
    currentInfo: snapshot.current || {},
    infoList: infoItems,
  })
}

const selectedHealthState = computed(() => {
  const snapshot = serverMonitorMap.value[selectedServer.value] || {}
  return resolveSnapshotHealthState(snapshot, selectedServerInfoList.value)
})

const selectedMonitorEnabled = computed(() => {
  const snapshot = serverMonitorMap.value[selectedServer.value] || {}
  return snapshot.monitorEnabled !== false
})

const serverCards = computed(() => serverList.value.map(serverIp => {
  const snapshot = serverMonitorMap.value[serverIp] || {}
  const serverInfoList = getServerInfoList(serverIp)
  const health = resolveSnapshotHealthState(snapshot, serverInfoList)

  return {
    key: serverIp,
    type: 'server',
    serverIp,
    subtitle: snapshot.monitorEnabled === false
      ? '检测已暂停，保留状态盘展示'
      : (snapshot.current?.os || '等待首次系统监控采样'),
    health,
    tone: getToneByLevel(health.level),
    metrics: buildServerMetricItems(snapshot, health),
  }
}))

const carouselItems = computed(() => ([
  ...serverCards.value,
  { key: '__add_server_monitor__', type: 'add' },
]))

const dashboardStats = computed(() => {
  const cards = serverCards.value
  const total = cards.length
  const success = cards.filter(card => card.health.level === 'success').length
  const warning = cards.filter(card => card.health.level === 'warning').length
  const averageScore = total
    ? Math.round(cards.reduce((sum, card) => sum + card.health.score, 0) / total)
    : 0

  return {
    total,
    success,
    warning,
    averageScore,
  }
})

const averageScoreTone = computed(() => {
  if (dashboardStats.value.averageScore >= 80) return toneMap.success
  if (dashboardStats.value.averageScore >= 60) return toneMap.warning
  return toneMap.error
})

const setSlideRef = (el, index) => {
  slideElements.value[index] = el
}

const getSlideStyle = index => slideMotionStyles.value[index] || {
  transform: 'perspective(1400px) translate3d(0, 10px, 0) scale(0.94)',
  opacity: '0.62',
}

const updateCarouselMotion = () => {
  const carousel = carouselRef.value
  if (!carousel) return

  const rect = carousel.getBoundingClientRect()
  const center = rect.left + rect.width / 2
  const styles = []
  let nextActiveIndex = 0
  let minDistance = Number.POSITIVE_INFINITY

  slideElements.value.forEach((element, index) => {
    if (!element) return

    const slideRect = element.getBoundingClientRect()
    const slideCenter = slideRect.left + slideRect.width / 2
    const distance = Math.abs(slideCenter - center)
    const ratio = Math.min(1, distance / Math.max(rect.width * 0.52, 1))
    const signedRatio = Math.max(-1, Math.min(1, (slideCenter - center) / Math.max(rect.width * 0.52, 1)))
    const scale = 1 - ratio * 0.06
    const opacity = 1 - ratio * 0.42
    const rotateY = signedRatio * -8
    const translateY = ratio * 10
    const translateZ = (1 - ratio) * 22

    styles[index] = {
      transform: `perspective(1400px) translate3d(0, ${translateY.toFixed(1)}px, ${translateZ.toFixed(1)}px) rotateY(${rotateY.toFixed(2)}deg) scale(${scale.toFixed(3)})`,
      opacity: opacity.toFixed(3),
    }

    if (distance < minDistance) {
      minDistance = distance
      nextActiveIndex = index
    }
  })

  slideMotionStyles.value = styles
  activeCardIndex.value = nextActiveIndex
}

const handleCarouselScroll = () => {
  if (carouselFrame) cancelAnimationFrame(carouselFrame)
  carouselFrame = requestAnimationFrame(updateCarouselMotion)
}

const handleCarouselWheel = event => {
  const carousel = carouselRef.value
  if (!carousel) return

  const delta = Math.abs(event.deltaY) > Math.abs(event.deltaX) ? event.deltaY : event.deltaX
  carousel.scrollBy({ left: delta, behavior: 'auto' })
}

const snapToCard = index => {
  const element = slideElements.value[index]
  element?.scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' })
}

const switchCarousel = direction => {
  const nextIndex = Math.max(0, Math.min(activeCardIndex.value + direction, carouselItems.value.length - 1))
  snapToCard(nextIndex)
}

const syncPagerState = () => {
  const pager = pagerRef.value
  if (!pager) return

  const pageWidth = pager.clientWidth || 1
  scrollProgress.value = pager.scrollLeft / pageWidth
  activePage.value = Math.round(scrollProgress.value)
}

const handlePagerScroll = () => {
  if (scrollFrame) cancelAnimationFrame(scrollFrame)
  scrollFrame = requestAnimationFrame(syncPagerState)
}

const scrollToPage = (index, smooth = true) => {
  const pager = pagerRef.value
  if (!pager) return

  const targetPage = Math.max(0, Math.min(index, pageTabs.length - 1))
  activePage.value = targetPage
  pager.scrollTo({
    left: targetPage * pager.clientWidth,
    behavior: smooth ? 'smooth' : 'auto',
  })

  if (!smooth) {
    scrollProgress.value = targetPage
  }
}

const switchPage = direction => {
  scrollToPage(activePage.value + direction)
}

const getPageMotionStyle = index => {
  const distance = Math.min(Math.abs(scrollProgress.value - index), 1)
  const scale = 1 - distance * 0.04
  const opacity = 1 - distance * 0.26
  const translateY = distance * 10

  return {
    transform: `scale(${scale.toFixed(3)}) translateY(${translateY.toFixed(1)}px)`,
    opacity: opacity.toFixed(3),
  }
}

const syncSelectedSnapshot = () => {
  const snapshot = serverMonitorMap.value[selectedServer.value]
  currentInfo.value = snapshot?.current || {}
  historyData.value = snapshot?.history || []
}

const applyServerSnapshot = (serverIp, payload) => {
  if (!serverIp) return

  serverMonitorMap.value = {
    ...serverMonitorMap.value,
    [serverIp]: {
      serverIp,
      current: payload?.current || {},
      history: Array.isArray(payload?.history) ? payload.history : [],
      healthState: hasBackendHealthState(payload?.healthState) ? payload.healthState : null,
      monitorEnabled: payload?.monitorEnabled !== false,
      fetchedAt: Date.now(),
    },
  }
}

const fetchInfo = async () => {
  loadingInfo.value = true

  try {
    const res = await selectAllInfo()

    if (Array.isArray(res)) {
      infoList.value = res
        .map(item => ({
          ...item,
          riskLevel: normalizeRiskLevel(item?.riskLevel),
        }))
        .sort((left, right) => getDateTimestamp(right.createdAt) - getDateTimestamp(left.createdAt))
    } else {
      infoList.value = []
    }
  } catch (error) {
    console.error('Failed to fetch info:', error)
  } finally {
    loadingInfo.value = false
  }
}

const fetchServerSnapshot = async serverIp => {
  if (!serverIp) return null

  try {
    const res = await getSystemDashboard(serverIp)
    applyServerSnapshot(serverIp, res)
    if (selectedServer.value === serverIp) {
      syncSelectedSnapshot()
    }
    return res
  } catch (error) {
    console.error(`Failed to fetch monitor data for ${serverIp}:`, error)
    return null
  }
}

const refreshDashboard = async preferredServer => {
  loadingMonitor.value = true

  try {
    const overview = await getSystemDashboard(preferredServer || selectedServer.value)
    const nextServerList = Array.from(new Set((overview?.servers || []).filter(Boolean)))

    serverList.value = nextServerList

    const targetServer = nextServerList.includes(preferredServer)
      ? preferredServer
      : (nextServerList.includes(overview?.selectedIp)
        ? overview.selectedIp
        : (nextServerList.includes(selectedServer.value) ? selectedServer.value : (nextServerList[0] || '')))
    selectedServer.value = targetServer

    if (!nextServerList.length) {
      serverMonitorMap.value = {}
      currentInfo.value = {}
      historyData.value = []
      return
    }

    const nextMonitorMap = {}
    nextServerList.forEach(serverIp => {
      if (serverMonitorMap.value[serverIp]) {
        nextMonitorMap[serverIp] = serverMonitorMap.value[serverIp]
      }
    })
    serverMonitorMap.value = nextMonitorMap

    if (overview?.selectedIp) {
      applyServerSnapshot(overview.selectedIp, overview)
    }

    await Promise.allSettled(
      nextServerList.map(serverIp => {
        if (serverIp === overview?.selectedIp) return Promise.resolve(overview)
        return fetchServerSnapshot(serverIp)
      }),
    )

    syncSelectedSnapshot()
    lastUpdatedAt.value = new Date().toISOString()
  } catch (error) {
    console.error('Failed to refresh dashboard:', error)
  } finally {
    loadingMonitor.value = false
  }
}

const scrollCardIntoViewByIp = serverIp => {
  const index = serverCards.value.findIndex(card => card.serverIp === serverIp)
  if (index >= 0) {
    snapToCard(index)
  }
}

const openServerDetail = async serverIp => {
  selectedServer.value = serverIp
  syncSelectedSnapshot()

  const snapshot = serverMonitorMap.value[serverIp]
  if (!snapshot?.current || !Object.keys(snapshot.current).length) {
    await fetchServerSnapshot(serverIp)
  }

  scrollToPage(1)
}

const resetAddMonitorForm = () => {
  Object.assign(addMonitorForm, {
    serverIp: '',
    username: '',
    password: '',
    ...defaultMonitorSettings(),
  })
}

const openAddMonitorDialog = () => {
  addMonitorDialogVisible.value = true
}

const submitAddServerMonitor = async () => {
  const serverIp = addMonitorForm.serverIp.trim()
  const username = addMonitorForm.username.trim()
  const password = addMonitorForm.password

  if (!serverIp || !username || !password) {
    ElMessage.warning('请完整填写服务器 IP、SSH 用户名和密码')
    return
  }

  addMonitorLoading.value = true

  try {
    await addServerMonitor({
      serverIp,
      username,
      password,
      ...defaultMonitorSettings(),
    })
    ElMessage.success('服务器监控已添加，默认开启全量采集')
    addMonitorDialogVisible.value = false
    resetAddMonitorForm()

    await refreshDashboard(serverIp)
    await nextTick()
    scrollCardIntoViewByIp(serverIp)
  } catch (error) {
    ElMessage.error(error?.message || '新增服务器失败')
  } finally {
    addMonitorLoading.value = false
  }
}

const handleStopServerMonitor = async () => {
  if (!selectedServer.value) {
    ElMessage.warning('当前没有可操作的服务器监控')
    return
  }

  const shouldDisable = selectedMonitorEnabled.value

  try {
    await ElMessageBox.confirm(
      shouldDisable
        ? `暂停后将暂时停止采集 ${selectedServer.value} 的 CPU 和内存数据，但状态盘仍会保留，是否继续？`
        : `恢复后将重新开始采集 ${selectedServer.value} 的 CPU 和内存数据，是否继续？`,
      shouldDisable ? '暂停检测' : '恢复检测',
      {
        confirmButtonText: shouldDisable ? '暂停检测' : '恢复检测',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  stoppingMonitor.value = true

  try {
    const targetServer = selectedServer.value
    if (shouldDisable) {
      await stopServerMonitor(targetServer)
      ElMessage.success(`已暂停 ${targetServer} 的服务器检测`)
    } else {
      await resumeServerMonitor(targetServer)
      ElMessage.success(`已恢复 ${targetServer} 的服务器检测`)
    }

    await refreshDashboard(targetServer)
  } catch (error) {
    ElMessage.error(error?.message || `${shouldDisable ? '暂停' : '恢复'}服务器监控失败`)
  } finally {
    stoppingMonitor.value = false
  }
}

const handleResize = async () => {
  scrollToPage(activePage.value, false)
  await nextTick()
  updateCarouselMotion()
}

watch(carouselItems, async () => {
  await nextTick()
  updateCarouselMotion()
}, { deep: true })

onMounted(async () => {
  await Promise.allSettled([
    fetchInfo(),
    refreshDashboard(),
  ])

  await nextTick()
  syncPagerState()
  scrollToPage(0, false)
  scrollCardIntoViewByIp(selectedServer.value)
  updateCarouselMotion()

  window.addEventListener('resize', handleResize)
  refreshTimer = setInterval(async () => {
    await Promise.allSettled([
      fetchInfo(),
      refreshDashboard(selectedServer.value),
    ])
  }, 60000)
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
  if (scrollFrame) cancelAnimationFrame(scrollFrame)
  if (carouselFrame) cancelAnimationFrame(carouselFrame)
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
.dashboard-pager {
  display: flex;
  overflow-x: auto;
  overflow-y: hidden;
  scroll-snap-type: x mandatory;
  scroll-behavior: smooth;
  scrollbar-width: none;
  -ms-overflow-style: none;
  touch-action: pan-y pinch-zoom;
  overscroll-behavior-x: contain;
}

.dashboard-pager::-webkit-scrollbar {
  display: none;
}

.dashboard-page {
  flex: 0 0 100%;
  width: 100%;
  min-width: 0;
  height: 100%;
  scroll-snap-align: center;
  scroll-snap-stop: always;
}

.dashboard-page-shell {
  transform-origin: center center;
  transition: transform 260ms cubic-bezier(0.22, 1, 0.36, 1), opacity 260ms ease;
  will-change: transform, opacity;
}

.server-carousel {
  display: flex;
  gap: clamp(16px, 2.4vw, 24px);
  overflow-x: auto;
  overflow-y: hidden;
  scroll-snap-type: x mandatory;
  scroll-padding-inline: 8px;
  scrollbar-width: none;
  -ms-overflow-style: none;
  touch-action: pan-x pinch-zoom;
  overscroll-behavior-x: contain;
  padding: 4px 2px 6px;
  perspective: 1800px;
  perspective-origin: center center;
}

.server-carousel::-webkit-scrollbar {
  display: none;
}

.server-slide {
  flex: 0 0 auto;
  width: clamp(280px, 30vw, 340px);
  min-height: 390px;
  scroll-snap-align: center;
  scroll-snap-stop: always;
  transition: transform 260ms ease, opacity 260ms ease, box-shadow 260ms ease;
  will-change: transform, opacity;
  cursor: pointer;
  transform-style: preserve-3d;
}

.server-slide.is-active {
  z-index: 1;
}

.server-slide-add {
  width: clamp(280px, 30vw, 340px);
}

.dashboard-detail-panel :deep(.el-select) {
  display: none !important;
}

.server-status-card {
  position: relative;
  overflow: hidden;
  isolation: isolate;
  transform-style: preserve-3d;
}

.server-status-card > * {
  position: relative;
  z-index: 2;
}

.server-health-pill {
  border-color: rgba(255, 255, 255, 0.22);
  background: rgba(242, 248, 255, 0.18);
  color: #5f6f87;
  box-shadow: 0 12px 22px -24px rgba(88, 110, 148, 0.16);
  backdrop-filter: blur(14px);
}

.server-health-stage {
  position: relative;
  display: flex;
  min-height: 166px;
  align-items: center;
  justify-content: center;
  z-index: 2;
  isolation: isolate;
  transform-style: preserve-3d;
}

/* --- 360度液态球体样式 --- */

.liquid-sphere-wrapper {
  position: relative;
  width: 140px;
  height: 140px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.liquid-sphere-wrapper::before {
  content: '';
  position: absolute;
  inset: -12px;
  border-radius: 50%;
  background: radial-gradient(
    circle at 50% 52%,
    var(--sphere-glow, rgba(20, 199, 103, 0.44)) 0%,
    var(--sphere-glow-soft, rgba(90, 242, 159, 0.32)) 34%,
    transparent 72%
  );
  opacity: 0.78;
  z-index: 0;
  filter: blur(16px);
  animation: liquid-sphere-pulse 3.2s ease-in-out infinite;
}

.liquid-sphere {
  position: relative;
  width: 132px;
  height: 132px;
  border-radius: 50%;
  background: linear-gradient(
    160deg,
    var(--sphere-shell-top, rgba(239, 255, 246, 0.56)) 0%,
    var(--sphere-shell-mid, rgba(186, 247, 213, 0.34)) 48%,
    var(--sphere-shell-bottom, rgba(118, 233, 181, 0.22)) 100%
  );
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid var(--sphere-ring, rgba(20, 199, 103, 0.54));
  box-shadow:
    inset 0 0 24px var(--sphere-highlight, rgba(239, 255, 246, 0.88)),
    inset 0 -18px 32px rgba(15, 23, 42, 0.14),
    0 10px 28px var(--sphere-glow-soft, rgba(90, 242, 159, 0.32)),
    0 0 0 1px rgba(255, 255, 255, 0.18);
  overflow: hidden;
  z-index: 1;
  isolation: isolate;
  transform: translateZ(8px);
  filter: saturate(1.12);
}

.liquid-level {
  position: absolute;
  width: 280px;  
  height: 280px;
  left: 50%;
  top: calc(100% - var(--fill-level) + 12px);
  transform: translateX(-50%);
  transition: top 1s cubic-bezier(0.4, 0, 0.2, 1);
  z-index: 1;
}

.wave {
  position: absolute;
  width: 100%;
  height: 100%;
  border-radius: 41%;
  left: 0;
  top: 0;
  transform-origin: 50% 50%;
}

.wave-back {
  background: var(--color-start);
  opacity: 0.72;
  animation: liquid-spin 6s linear infinite;
  filter: saturate(1.08);
}

.wave-front {
  background: linear-gradient(180deg, var(--color-start) 0%, var(--color-end) 100%);
  opacity: 0.98;
  animation: liquid-spin 4.5s linear infinite;
  border-radius: 43%;
  box-shadow:
    inset 0 10px 20px rgba(255, 255, 255, 0.16),
    0 0 22px var(--sphere-liquid-shadow, rgba(20, 199, 103, 0.3));
}

@keyframes liquid-spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

@keyframes liquid-sphere-pulse {
  0%,
  100% {
    transform: scale(0.98);
    opacity: 0.7;
  }

  50% {
    transform: scale(1.04);
    opacity: 0.9;
  }
}

.sphere-glare {
  position: absolute;
  top: 4px;
  left: 10%;
  width: 80%;
  height: 35%;
  border-radius: 50%;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.86), rgba(255, 255, 255, 0));
  z-index: 2;
  pointer-events: none;
}

.sphere-shadow {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  box-shadow:
    inset 0 -15px 25px rgba(15, 23, 42, 0.15),
    inset 0 1px 0 rgba(219, 234, 254, 0.34);
  z-index: 2;
  pointer-events: none;
}

.server-health-value-sphere {
  position: absolute;
  inset: 0;
  z-index: 3;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  pointer-events: none;
  color: var(--sphere-text, #ffffff);
  text-shadow:
    0 8px 18px rgba(15, 23, 42, 0.32),
    0 0 16px rgba(255, 255, 255, 0.18);
}

/* 清理了硬编码渐变，由 glass-soft 接管 */
.server-metric-card {
  position: relative;
  overflow: hidden;
  isolation: isolate;
  transform: translateZ(8px);
}

.server-metric-grid {
  position: relative;
  z-index: 1;
}

.server-metric-value {
  text-shadow: none;
}

.server-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: rgba(113, 128, 150, 0.3);
  transform: scale(0.92);
  transition: transform 240ms ease, background-color 240ms ease;
}

.server-dot.is-active {
  background: #4299e1;
  transform: scale(1.4);
}

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

:deep(.add-server-dialog) {
  width: min(1380px, calc(100vw - 96px)) !important;
  max-width: calc(100vw - 96px) !important;
  height: 60vh;
  max-height: 60vh;
  margin: 20vh auto 0 !important;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.44) !important;
  -webkit-backdrop-filter: blur(28px) saturate(135%);
  backdrop-filter: blur(28px) saturate(135%);
}

:deep(.add-server-dialog .el-dialog__header) {
  border-bottom: 1px solid rgba(255, 255, 255, 0.34);
  padding: 22px 28px 18px;
}

:deep(.add-server-dialog .el-dialog__title) {
  color: #0f172a;
  font-weight: 700;
  font-size: 1.125rem;
}

:deep(.add-server-dialog .el-dialog__headerbtn) {
  top: 14px;
  right: 16px;
}

:deep(.add-server-dialog .el-dialog__body) {
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
  padding: 24px 28px;
  background: transparent;
}

:deep(.add-server-dialog .el-dialog__footer) {
  border-top: 1px solid rgba(255, 255, 255, 0.34);
  padding: 18px 28px 22px;
  background: transparent;
}

.add-server-dialog__body {
  min-height: 0;
}

@media (max-width: 768px) {
  .server-health-stage {
    min-height: 154px;
  }

  :deep(.add-server-dialog) {
    width: calc(100vw - 24px) !important;
    max-width: calc(100vw - 24px) !important;
    max-height: calc(100vh - 24px);
    margin: 12px auto 0 !important;
  }

  :deep(.add-server-dialog .el-dialog__header) {
    padding: 18px 20px 14px;
  }

  :deep(.add-server-dialog .el-dialog__body) {
    padding: 18px 20px;
  }

  :deep(.add-server-dialog .el-dialog__footer) {
    padding: 14px 20px 18px;
  }
}
</style>
