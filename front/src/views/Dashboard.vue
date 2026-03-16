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
          <div class="flex h-full min-h-0 flex-col overflow-y-auto bg-ui-bg p-6 lg:p-8 custom-scrollbar">
            <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
              <div class="rounded-[24px] border border-ui-border bg-gradient-to-br from-white to-slate-50 p-5 shadow-sm">
                <div class="text-xs uppercase tracking-[0.22em] text-ui-subtext">监控服务器</div>
                <div class="mt-3 text-3xl font-bold text-ui-text">{{ dashboardStats.total }}</div>
                <p class="mt-2 text-sm text-ui-subtext">当前已接入 {{ dashboardStats.total }} 台服务器监控</p>
              </div>

              <div class="rounded-[24px] border border-ui-border bg-gradient-to-br from-white to-green-50 p-5 shadow-sm">
                <div class="text-xs uppercase tracking-[0.22em] text-ui-subtext">健康服务器</div>
                <div class="mt-3 text-3xl font-bold text-ui-success">{{ dashboardStats.success }}</div>
                <p class="mt-2 text-sm text-ui-subtext">CPU 与内存均处于稳定区间</p>
              </div>

              <div class="rounded-[24px] border border-ui-border bg-gradient-to-br from-white to-orange-50 p-5 shadow-sm">
                <div class="text-xs uppercase tracking-[0.22em] text-ui-subtext">关注中</div>
                <div class="mt-3 text-3xl font-bold text-ui-warning">{{ dashboardStats.warning }}</div>
                <p class="mt-2 text-sm text-ui-subtext">单项资源偏高，建议持续观察</p>
              </div>

              <div class="rounded-[24px] border border-ui-border bg-gradient-to-br from-white to-red-50 p-5 shadow-sm">
                <div class="text-xs uppercase tracking-[0.22em] text-ui-subtext">平均健康度</div>
                <div class="mt-3 text-3xl font-bold" :class="averageScoreTone.text">{{ dashboardStats.averageScore }}%</div>
                <p class="mt-2 text-sm text-ui-subtext">最近刷新：{{ lastUpdatedLabel }}</p>
              </div>
            </div>

            <div class="mt-6 flex flex-1 min-h-0 flex-col rounded-[28px] border border-ui-border bg-gradient-to-br from-slate-50 via-white to-slate-50 p-5 shadow-sm lg:p-6">
              <div class="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
                <div>
                  <h3 class="text-lg font-bold text-ui-text">服务器状态盘</h3>
                </div>

                <div class="flex flex-wrap items-center gap-3">
                  <!-- <div class="hidden xl:flex items-center gap-2 rounded-full border border-ui-border bg-white px-3 py-2 text-xs text-ui-subtext shadow-sm">
                    <svg class="h-4 w-4 text-brand" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 16l-4-4m0 0l4-4m-4 4h18m-4 4l4-4m0 0l-4-4" />
                    </svg>
                    左右滑动 / 触控板 / 多点触控 
                  </div> -->

                  <div class="inline-flex items-center rounded-full border border-ui-border bg-white p-1 shadow-sm">
                    <button
                      type="button"
                      class="flex h-9 w-9 items-center justify-center rounded-full text-ui-subtext transition-colors hover:bg-ui-bg hover:text-ui-text disabled:cursor-not-allowed disabled:opacity-40"
                      :disabled="activeCardIndex <= 0"
                      @click="switchCarousel(-1)"
                    >
                      <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
                      </svg>
                    </button>
                    <button
                      type="button"
                      class="flex h-9 w-9 items-center justify-center rounded-full text-ui-subtext transition-colors hover:bg-ui-bg hover:text-ui-text disabled:cursor-not-allowed disabled:opacity-40"
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

              <div class="mt-6 flex flex-1 min-h-0 flex-col">
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
                      <div class="flex h-full flex-col items-center justify-center rounded-[24px] border border-dashed border-brand/30 bg-gradient-to-br from-blue-50 to-white p-8 text-center">
                        <div class="flex h-[72px] w-[72px] items-center justify-center rounded-full bg-brand/10 text-brand">
                          <svg class="h-10 w-10" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6v12m6-6H6" />
                          </svg>
                        </div>
                        <h4 class="mt-6 text-2xl font-bold text-ui-text">添加服务器监控</h4>
                        <p class="mt-3 max-w-[260px] text-sm leading-6 text-ui-subtext">
                          只添加服务器级监控，不监控组件日志；只采集 CPU 与内存使用率。
                        </p>
                        <div class="mt-6 inline-flex items-center rounded-full bg-brand px-4 py-2 text-sm font-semibold text-white shadow-sm">
                          立即添加
                        </div>
                      </div>
                    </template>

                    <template v-else>
                      <div
                        class="server-status-card flex h-full flex-col justify-between rounded-[24px] border border-white/80 bg-gradient-to-br from-white via-white to-slate-50 p-6 shadow-[0_18px_50px_-34px_rgba(26,37,48,0.45)] ring-1 ring-white/80"
                        :style="getServerCardVisualStyle(item)"
                      >
                        <div class="flex items-start justify-between gap-3">
                          <div class="min-w-0">
                            <p class="truncate text-sm font-semibold text-ui-text">{{ item.serverIp }}</p>
                            <p class="mt-1 text-xs text-ui-subtext">{{ item.subtitle }}</p>
                          </div>
                          <span class="rounded-full border px-3 py-1 text-xs font-semibold" :class="item.tone.pill">
                            {{ item.health.label }}
                          </span>
                        </div>

                        <div class="server-health-stage my-6">
                          <span class="server-health-shadow" aria-hidden="true"></span>
                          <span class="server-health-aura" aria-hidden="true"></span>
                          <span class="server-health-plate" aria-hidden="true"></span>
                          <span class="server-health-reflection" aria-hidden="true"></span>

                          <svg class="server-health-ring h-[190px] w-[190px] -rotate-90" viewBox="0 0 160 160" aria-hidden="true">
                            <defs>
                              <linearGradient :id="`server-card-gradient-${index}`" x1="0%" y1="0%" x2="100%" y2="0%">
                                <stop offset="0%" :stop-color="item.tone.gradientStart" />
                                <stop offset="100%" :stop-color="item.tone.gradientEnd" />
                              </linearGradient>
                              <radialGradient :id="`server-card-center-${index}`" cx="32%" cy="28%" r="76%">
                                <stop offset="0%" stop-color="#ffffff" />
                                <stop offset="54%" stop-color="#f8fafc" />
                                <stop offset="100%" stop-color="#e2e8f0" />
                              </radialGradient>
                            </defs>

                            <circle cx="80" cy="80" r="58" :fill="`url(#server-card-center-${index})`" />
                            <circle cx="80" cy="80" r="58" fill="none" stroke="rgba(255, 255, 255, 0.8)" stroke-width="1.5" />
                            <circle cx="80" cy="80" r="52" fill="none" :stroke="item.tone.track" stroke-width="12" />
                            <circle
                              cx="80"
                              cy="80"
                              r="52"
                              fill="none"
                              :stroke="`url(#server-card-gradient-${index})`"
                              stroke-width="12"
                              stroke-linecap="round"
                              :stroke-dasharray="cardRingCircumference"
                              :stroke-dashoffset="getCardRingOffset(item.health.score)"
                            />
                          </svg>

                          <div class="server-health-value">
                            <div class="server-health-score text-[36px] font-bold" :class="item.tone.text">{{ item.health.score }}%</div>
                            <div class="server-health-caption mt-2 text-sm font-medium" :class="item.tone.softText">系统健康度</div>
                          </div>
                        </div>

                        <div class="grid grid-cols-2 gap-3">
                          <div class="server-metric-card rounded-2xl border border-ui-border bg-white px-4 py-3">
                            <div class="text-xs uppercase tracking-[0.18em] text-ui-subtext">CPU</div>
                            <div class="mt-2 text-xl font-bold" :class="item.cpuTone.text">{{ item.health.cpuUsage }}%</div>
                          </div>
                          <div class="server-metric-card rounded-2xl border border-ui-border bg-white px-4 py-3">
                            <div class="text-xs uppercase tracking-[0.18em] text-ui-subtext">MEM</div>
                            <div class="mt-2 text-xl font-bold" :class="item.memTone.text">{{ item.health.memUsage }}%</div>
                          </div>
                        </div>

                        <div class="mt-5 flex items-center justify-between text-sm">
                          <span class="text-ui-subtext">点击进入详情页</span>
                          <span class="font-semibold text-brand">查看总览</span>
                        </div>
                      </div>
                    </template>
                  </article>
                </div>

                <div class="pt-6 flex items-center justify-center gap-2">
                  <button
                    v-for="(card, index) in serverCards"
                    :key="`dot-${card.serverIp}`"
                    type="button"
                    class="server-dot"
                    :class="{ 'is-active': activeCardIndex === index }"
                    @click="snapToCard(index)"
                  ></button>
                </div>

                <div v-if="!serverCards.length" class="pt-6 rounded-2xl border border-dashed border-ui-border bg-white/80 px-4 py-5 text-center text-sm text-ui-subtext">
                  还没有服务器监控，先点击上方或右侧卡片添加一台服务器，只采集 CPU 与内存。
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="dashboard-page">
        <div class="dashboard-page-shell h-full" :style="getPageMotionStyle(1)">
          <div class="h-full p-4 lg:p-6">
            <div class="flex h-full min-h-0 flex-col overflow-hidden rounded-[26px] border border-ui-border bg-white shadow-sm">
              <div class="flex items-center justify-between gap-4 border-b border-ui-border bg-white/90 px-5 py-4 backdrop-blur-sm lg:px-6">
                <div class="flex items-center gap-3">
                  <button
                    type="button"
                    class="inline-flex items-center gap-2 rounded-full border border-ui-border bg-ui-bg px-4 py-2 text-sm font-medium text-ui-text transition-colors hover:border-brand hover:text-brand"
                    @click="scrollToPage(0)"
                  >
                    <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
                    </svg>
                    返回首页
                  </button>

                
                </div>

                <div class="min-w-0 text-right">
                  <div class="text-xs uppercase tracking-[0.18em] text-ui-subtext">总览详情</div>
                  <div class="truncate text-sm font-medium text-ui-text">{{ selectedServer || '当前服务器' }}</div>
                  <div class="mt-1 text-xs" :class="selectedMonitorEnabled ? 'text-ui-success' : 'text-ui-warning'">
                    {{ selectedMonitorEnabled ? '状态：检测中' : '状态：已暂停检测' }}
                  </div>
                </div>
              </div>

              <div class="flex-1 min-h-0 bg-ui-bg p-4 lg:p-6">
                <DashboardOverviewDetail
                  class="dashboard-detail-panel h-full"
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
        </div>
      </section>
    </div>

    <el-dialog
      v-model="addMonitorDialogVisible"
      title="添加服务器监控"
      width="520px"
      destroy-on-close
    >
      <div class="rounded-2xl border border-blue-100 bg-blue-50 px-4 py-3 text-sm text-brand">
        这里只添加服务器级监控，不创建组件日志监控任务；保存后只采集 CPU 和内存指标。
      </div>

      <el-form class="mt-5" label-position="top">
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
      </el-form>

      <template #footer>
        <div class="flex justify-end gap-3">
          <el-button @click="addMonitorDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="addMonitorLoading" @click="submitAddServerMonitor">
            保存并开始监控
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
const addMonitorForm = reactive({
  serverIp: '',
  username: '',
  password: '',
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
  '--server-metric-highlight': hexToRgba(item?.tone?.gradientStart, 0.1),
})

const buildUsageTone = usage => {
  if (usage >= 85) return { text: 'text-ui-error' }
  if (usage >= 70) return { text: 'text-ui-warning' }
  return { text: 'text-ui-success' }
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
        '当前服务器尚未拿到最新 CPU / 内存采样数据，将在下一次采样后刷新。',
      ],
    }
  }

  return {
    ...baseState,
    score: 0,
    label: '待采样',
    level: 'warning',
    description: '暂未获取到 CPU 与内存监控数据，请稍候等待首次采样。',
    reasons: ['当前服务器还没有最新监控数据，系统将继续采集 CPU 与内存状态。'],
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
      : (snapshot.current?.os || '等待首次 CPU / 内存采样'),
    health,
    tone: getToneByLevel(health.level),
    cpuTone: buildUsageTone(health.cpuUsage),
    memTone: buildUsageTone(health.memUsage),
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

const cardRingRadius = 52
const cardRingCircumference = Number((2 * Math.PI * cardRingRadius).toFixed(2))

const getCardRingOffset = score => Number(
  (cardRingCircumference * (1 - Math.max(0, Math.min(score, 100)) / 100)).toFixed(2),
)

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
  addMonitorForm.serverIp = ''
  addMonitorForm.username = ''
  addMonitorForm.password = ''
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
    await addServerMonitor({ serverIp, username, password })
    ElMessage.success('服务器监控已添加，开始采集 CPU 与内存数据')
    addMonitorDialogVisible.value = false
    resetAddMonitorForm()

    await refreshDashboard(serverIp)
    await nextTick()
    scrollCardIntoViewByIp(serverIp)
  } catch (error) {
    ElMessage.error(error?.message || '添加服务器监控失败')
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
  padding: 6px 2px 10px;
  perspective: 1800px;
  perspective-origin: center center;
}

.server-carousel::-webkit-scrollbar {
  display: none;
}

.server-slide {
  flex: 0 0 auto;
  width: clamp(280px, 30vw, 340px);
  min-height: 480px;
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

.server-status-card::before {
  content: '';
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 24% 18%, rgba(255, 255, 255, 0.88), transparent 34%),
    radial-gradient(circle at 76% 84%, var(--server-ring-glow-soft, rgba(72, 187, 120, 0.18)), transparent 34%);
  opacity: 0.88;
  pointer-events: none;
}

.server-status-card > * {
  position: relative;
  z-index: 1;
}

.server-health-stage {
  position: relative;
  display: flex;
  min-height: 236px;
  align-items: center;
  justify-content: center;
  isolation: isolate;
  transform-style: preserve-3d;
}

.server-health-shadow {
  position: absolute;
  bottom: 18px;
  width: 146px;
  height: 34px;
  border-radius: 999px;
  background: radial-gradient(
    circle at 50% 50%,
    var(--server-ring-glow, rgba(72, 187, 120, 0.28)) 0%,
    rgba(15, 23, 42, 0.18) 36%,
    transparent 76%
  );
  filter: blur(14px);
  opacity: 0.72;
  transform: translateZ(-22px) scaleX(0.92);
}

.server-health-aura {
  position: absolute;
  inset: 22px;
  border-radius: 999px;
  background: radial-gradient(circle, var(--server-ring-glow-soft, rgba(72, 187, 120, 0.18)) 0%, transparent 70%);
  filter: blur(4px);
  opacity: 0.88;
  transform: translateZ(2px) scale(1.04);
}

.server-health-plate {
  position: absolute;
  inset: 28px;
  border-radius: 999px;
  background: radial-gradient(
    circle at 32% 30%,
    rgba(255, 255, 255, 0.98) 0%,
    rgba(255, 255, 255, 0.88) 28%,
    rgba(248, 250, 252, 0.95) 58%,
    rgba(226, 232, 240, 0.96) 100%
  );
  box-shadow:
    inset 0 2px 0 rgba(255, 255, 255, 0.95),
    inset 0 -14px 26px rgba(148, 163, 184, 0.18),
    0 18px 30px -22px rgba(15, 23, 42, 0.4),
    0 0 0 1px rgba(255, 255, 255, 0.92);
  transform: translateZ(4px);
}

.server-health-reflection {
  position: absolute;
  top: 34px;
  left: 52px;
  width: 94px;
  height: 38px;
  border-radius: 999px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.84), rgba(255, 255, 255, 0));
  opacity: 0.85;
  transform: translateZ(12px) rotate(-14deg);
}

.server-health-ring {
  position: relative;
  z-index: 2;
  filter:
    drop-shadow(0 16px 18px var(--server-ring-shadow-soft, rgba(72, 187, 120, 0.18)))
    drop-shadow(0 4px 8px rgba(255, 255, 255, 0.78));
  transform: rotateX(18deg) translateZ(10px);
}

.server-health-value {
  position: absolute;
  z-index: 3;
  display: flex;
  min-width: 128px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 18px 24px;
  border-radius: 999px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.78), rgba(255, 255, 255, 0.58));
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.82),
    0 18px 24px -22px rgba(15, 23, 42, 0.35);
  backdrop-filter: blur(10px);
  transform: translateZ(20px);
}

.server-health-score {
  line-height: 1;
  letter-spacing: -0.04em;
  text-shadow: 0 10px 20px rgba(255, 255, 255, 0.34);
}

.server-health-caption {
  text-shadow: 0 1px 0 rgba(255, 255, 255, 0.42);
}

.server-metric-card {
  position: relative;
  border-color: transparent;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(248, 250, 252, 0.92)),
    radial-gradient(circle at 18% 18%, var(--server-metric-highlight, rgba(255, 255, 255, 0.1)), transparent 44%);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.88),
    0 16px 24px -24px rgba(15, 23, 42, 0.3);
  transform: translateZ(8px);
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

@media (max-width: 768px) {
  .server-health-stage {
    min-height: 216px;
  }

  .server-health-value {
    min-width: 118px;
    padding: 16px 22px;
  }

  .server-health-reflection {
    left: 46px;
    width: 84px;
  }
}
</style>
