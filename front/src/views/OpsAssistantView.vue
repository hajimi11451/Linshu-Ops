<template>
  <div class="workspace-cool-glass mx-auto max-w-7xl space-y-5">
    <!-- <el-card class="glass-card rounded-[2.125rem]" :body-style="{ padding: '1.25rem' }">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 class="text-lg font-bold text-ui-text">灵枢运维助手</h2>
          <p class="text-xs text-ui-subtext mt-1">对话、规划、执行</p>
        </div>
        <div class="flex items-center gap-2">
          <span class="glass-chip px-2.5 py-1 text-xs font-medium" :class="connected ? 'text-ui-success' : 'text-ui-subtext'">
            {{ connected ? '在线' : '离线' }}
          </span>
          <el-button size="small" @click="connectWs" :disabled="connected">连接</el-button>
          <el-button size="small" @click="disconnectWs" :disabled="!connected">断开</el-button>
        </div>
      </div>
    </el-card> -->

    <el-card class="assistant-shell-card glass-card rounded-[1.875rem]" :body-style="{ padding: '1.25rem' }">
      <div class="grid grid-cols-1 md:grid-cols-2 gap-4 mb-4">
        <el-select
          v-model="selectedSavedConnection"
          filterable
          clearable
          placeholder="选择已保存连接"
          @change="handleSavedConnectionChange"
        >
          <el-option
            v-for="item in savedConnections"
            :key="item.id"
            :label="item.label"
            :value="item.id"
          />
        </el-select>
      </div>
      <div class="grid grid-cols-1 md:grid-cols-4 gap-4">
        <el-input v-model="serverIp" placeholder="服务器 IP" />
        <el-input v-model="username" placeholder="账号" />
        <el-input v-model="password" type="password" show-password placeholder="密码" />
        <el-input-number v-model="maxRounds" :min="1" :max="50" controls-position="right" placeholder="最大轮数" />
      </div>
      <p class="text-xs text-ui-subtext mt-3">已启用自动模式：即使填写了连接，也会先判断是咨询还是执行；只有明确是执行请求时才操作服务器。</p>
    </el-card>

    <el-card class="assistant-shell-card glass-card rounded-[1.875rem]" :body-style="{ padding: '1.25rem' }">
      <div ref="chatBox" class="glass-subcard custom-scrollbar h-[28.75rem] min-h-0 overflow-y-auto overflow-x-hidden rounded-[1.875rem] p-4 space-y-3 lg:h-[30rem]">
        <div v-for="(msg, idx) in messages" :key="idx" class="flex" :class="msg.role === 'user' ? 'justify-end' : 'justify-start'">
          <div
            class="max-w-[85%] rounded-[1.5rem] px-4 py-3 text-sm"
            :class="[
              msg.role === 'user'
                ? 'assistant-user-bubble text-white'
                : 'glass-soft text-ui-text',
              isMarkdownMessage(msg) ? 'whitespace-normal' : 'whitespace-pre-wrap',
            ]"
          >
            <div class="font-semibold text-xs mb-1 opacity-80">{{ msg.role === 'user' ? '你' : '运维助手' }}</div>
            <template v-if="msg.type === 'confirm'">
              <div class="space-y-2">
                <div>命令：{{ msg.command }}</div>
                <div>风险：{{ msg.riskLevel }}</div>
                <div v-if="!msg.handled" class="flex gap-2 pt-1">
                  <el-button size="small" type="primary" :disabled="msg.handled" @click="confirmExecute(msg)">确定执行</el-button>
                  <el-button size="small" :disabled="msg.handled" @click="cancelExecute(msg)">取消</el-button>
                </div>
                <div v-else :class="interactiveStatusClass(msg.statusType)">
                  {{ msg.statusText || '已处理' }}
                </div>
              </div>
            </template>
            <template v-else-if="msg.type === 'timeout'">
              <div class="space-y-2">
                <div class="text-ui-warning font-semibold">任务达到最大轮数，已暂停</div>
                <div>是否继续执行？</div>
                <div v-if="!msg.handled" class="flex gap-2 pt-1">
                  <el-button size="small" type="primary" :disabled="msg.handled" @click="confirmContinue(msg)">继续执行</el-button>
                  <el-button size="small" :disabled="msg.handled" @click="cancelContinue(msg)">停止</el-button>
                </div>
                <div v-else :class="interactiveStatusClass(msg.statusType)">
                  {{ msg.statusText || '已处理' }}
                </div>
              </div>
            </template>
            <template v-else-if="msg.type === 'risk_confirm'">
              <div class="space-y-2">
                <div class="text-ui-warning font-semibold">检测到高风险命令，请确认是否执行</div>
                <div>命令：{{ msg.command }}</div>
                <div>风险等级：{{ msg.riskLevel }}</div>
                <div v-if="!msg.handled" class="flex gap-2 pt-1">
                  <el-button size="small" type="danger" :disabled="msg.handled" @click="confirmRiskExecute(msg)">仍要执行</el-button>
                  <el-button size="small" :disabled="msg.handled" @click="cancelRiskExecute(msg)">取消</el-button>
                </div>
                <div v-else :class="interactiveStatusClass(msg.statusType)">
                  {{ msg.statusText || '已处理' }}
                </div>
              </div>
            </template>
            <template v-else-if="msg.type === 'chart_action'">
              <div class="space-y-2">
                <div class="text-ui-success font-semibold">AI 判断当前结果适合图表展示</div>
                <div>图表标题：{{ msg.chartTitle }}</div>
                <div>原因：{{ msg.reason }}</div>
                <div>建议范围：{{ msg.timeRange }}</div>
                <div>图表类型：{{ chartTemplateLabel(msg.chartTemplate) }}</div>
                <div v-if="!msg.handled" class="flex gap-2 pt-1">
                  <el-button size="small" type="primary" :disabled="msg.handled" @click="generateChart(msg)">生成图表</el-button>
                  <el-button size="small" :disabled="msg.handled" @click="cancelChart(msg)">取消</el-button>
                </div>
                <div v-else :class="interactiveStatusClass(msg.statusType)">
                  {{ msg.statusText || '已处理' }}
                </div>
              </div>
            </template>
            <template v-else-if="msg.type === 'chart_render'">
              <InlineMetricsTemplate :title="msg.title" :chart-data="msg.chartData" />
            </template>
            <template v-else-if="msg.type === 'rich_markdown'">
              <div class="space-y-3">
                <div class="result-card-head">
                  <div class="min-w-0">
                    <div v-if="msg.eyebrow" class="text-[0.6875rem] font-semibold tracking-[0.16em] uppercase text-ui-subtext/80">
                      {{ msg.eyebrow }}
                    </div>
                    <div class="text-[0.9375rem] font-semibold text-ui-text mt-1">{{ msg.title }}</div>
                    <div v-if="msg.subtitle" class="text-xs text-ui-subtext mt-1">{{ msg.subtitle }}</div>
                  </div>
                  <span :class="resultToneClass(msg.tone)">{{ resultToneLabel(msg.tone) }}</span>
                </div>
                <div v-if="msg.content" v-html="renderMarkdown(msg.content)" class="markdown-body markdown-result"></div>
              </div>
            </template>
            <template v-else>
              <div v-if="msg.role === 'assistant'" v-html="renderMarkdown(msg.content)" class="markdown-body"></div>
              <div v-else>{{ msg.content }}</div>
            </template>
          </div>
        </div>
      </div>

      <div class="mt-4 flex gap-3">
        <el-input
          v-model="input"
          type="textarea"
          :rows="3"
          resize="none"
          :disabled="isAgentRunning"
          placeholder="例如：检查 nginx 状态并修复"
          @keydown.enter.exact.prevent="sendMessage()"
        />
        <el-button
          :type="isAgentRunning ? 'danger' : 'primary'"
          class="h-auto px-6"
          :disabled="isAgentRunning ? !connected : !input.trim()"
          @click="isAgentRunning ? forceStop() : sendMessage()"
        >
          {{ isAgentRunning ? '中断执行' : '发送' }}
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listConfigs } from '../api/diagnosis'
import InlineMetricsTemplate from '../components/InlineMetricsTemplate.vue'
import MarkdownIt from 'markdown-it'

const PENDING_TASK_KEY = 'opsAssistantPendingTask'

const md = new MarkdownIt({
  html: false,
  linkify: true,
  typographer: true,
})

const route = useRoute()
const router = useRouter()

const ws = ref(null)
const connected = ref(false)

const serverIp = ref('')
const username = ref('')
const password = ref('')
const execute = ref(true)
const maxRounds = ref(15)
const isAgentRunning = ref(false)
const selectedSavedConnection = ref('')
const savedConnections = ref([])
const savedConnectionsLoaded = ref(false)
const pendingTaskHandled = ref(false)

const input = ref('')
const messages = ref([])
const chatBox = ref(null)
const lastTaskDoneDigest = ref('')
const lastTaskFailedDigest = ref('')
const stepTitleMap = ref({})

const scrollChatToBottom = async () => {
  await nextTick()
  if (chatBox.value) {
    chatBox.value.scrollTop = chatBox.value.scrollHeight
  }
}

const appendMessage = async (role, content) => {
  const normalizedContent = role === 'assistant' ? toUserFriendlyText(content) : content
  if (role === 'assistant') {
    await humanLikeDelay()
  }
  messages.value.push({ role, content: normalizedContent, type: 'text' })
  await scrollChatToBottom()
}

const appendRichMarkdownMessage = async ({
  title,
  content = '',
  subtitle = '',
  tone = 'info',
  eyebrow = '',
}) => {
  messages.value.push({
    role: 'assistant',
    type: 'rich_markdown',
    title: title || '处理结果',
    content,
    subtitle,
    tone,
    eyebrow,
  })
  await scrollChatToBottom()
}

const appendChartActionMessage = async (reason, timeRange, chartTemplate, chartTitle) => {
  messages.value.push({
    role: 'assistant',
    type: 'chart_action',
    reason: reason || '结果包含可视化分析价值',
    timeRange: timeRange || '1h',
    chartTemplate: chartTemplate || 'health_overview',
    chartTitle: chartTitle || '服务器健康总览',
    handled: false,
  })
  await scrollChatToBottom()
}

const appendChartRenderMessage = async chartData => {
  messages.value.push({
    role: 'assistant',
    type: 'chart_render',
    title: chartData?.title || `服务器监控图（${chartData?.timeRange || '1h'}）`,
    chartData,
  })
  await scrollChatToBottom()
}

const appendConfirmMessage = async (query, command, riskLevel) => {
  messages.value.push({
    role: 'assistant',
    type: 'confirm',
    query,
    command,
    riskLevel: riskLevel || 'medium',
    handled: false,
  })
  await scrollChatToBottom()
}

const appendTimeoutMessage = async () => {
  messages.value.push({
    role: 'assistant',
    type: 'timeout',
    handled: false,
  })
  await scrollChatToBottom()
}

const appendRiskConfirmMessage = async (command, riskLevel, reason) => {
  messages.value.push({
    role: 'assistant',
    type: 'risk_confirm',
    command,
    riskLevel: riskLevel || 'high',
    reason: reason || '',
    handled: false,
  })
  await scrollChatToBottom()
}

const markInteractiveHandled = (msg, statusText, statusType = 'info') => {
  msg.handled = true
  msg.statusText = statusText
  msg.statusType = statusType
}

const interactiveStatusClass = type => {
  if (type === 'success') {
    return 'rounded-xl border border-emerald-200/40 bg-emerald-400/10 px-3 py-2 text-xs font-medium text-ui-success'
  }
  if (type === 'danger') {
    return 'rounded-xl border border-red-200/40 bg-red-400/10 px-3 py-2 text-xs font-medium text-red-600'
  }
  return 'rounded-xl border border-sky-200/40 bg-sky-400/10 px-3 py-2 text-xs font-medium text-sky-700'
}

const getWsUrl = () => {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}/ws/server/connect`
}

const parseServerEndpoint = value => {
  const text = String(value || '').trim()
  if (!text) {
    return { raw: '', host: '', port: '' }
  }

  if (text.startsWith('[')) {
    const endBracket = text.indexOf(']')
    if (endBracket > -1) {
      const host = text.slice(1, endBracket).trim().toLowerCase()
      const suffix = text.slice(endBracket + 1)
      const port = suffix.startsWith(':') ? suffix.slice(1).trim() : ''
      return { raw: text, host, port }
    }
  }

  const lastColonIndex = text.lastIndexOf(':')
  if (lastColonIndex > -1 && text.indexOf(':') === lastColonIndex) {
    const host = text.slice(0, lastColonIndex).trim().toLowerCase()
    const port = text.slice(lastColonIndex + 1).trim()
    if (host && /^\d+$/.test(port)) {
      return { raw: text, host, port }
    }
  }

  return {
    raw: text,
    host: text.toLowerCase(),
    port: '',
  }
}

const isSameServerEndpoint = (left, right) => {
  const a = parseServerEndpoint(left)
  const b = parseServerEndpoint(right)

  if (!a.host || !b.host || a.host !== b.host) {
    return false
  }
  if (a.port === b.port) {
    return true
  }

  // Bare IP and IP:22 should be treated as the same target.
  return (!a.port && b.port === '22') || (!b.port && a.port === '22')
}

const readPendingTask = () => {
  if (pendingTaskHandled.value || route.query.autostart !== '1') return null
  try {
    const raw = sessionStorage.getItem(PENDING_TASK_KEY)
    if (!raw) return null
    return JSON.parse(raw)
  } catch (error) {
    console.error('Failed to parse pending ops task', error)
    return null
  }
}

const clearPendingTask = async () => {
  pendingTaskHandled.value = true
  sessionStorage.removeItem(PENDING_TASK_KEY)
  if (route.query.autostart === '1') {
    await router.replace({ name: 'ops-assistant' })
  }
}

const hasCompleteConnection = () => {
  return Boolean(serverIp.value && username.value && password.value)
}

const tryApplyPendingConnection = pendingTask => {
  const targetIp = String(pendingTask?.serverIp || '').trim()
  if (!targetIp) {
    return hasCompleteConnection()
  }

  const matched = savedConnections.value.find(item => isSameServerEndpoint(item.serverIp, targetIp))
  if (matched) {
    selectedSavedConnection.value = matched.id
    handleSavedConnectionChange(matched.id)
    return true
  }

  serverIp.value = targetIp
  return isSameServerEndpoint(serverIp.value, targetIp) && Boolean(username.value && password.value)
}

const maybeRunPendingTask = async () => {
  const pendingTask = readPendingTask()
  if (!connected.value || !ws.value || !savedConnectionsLoaded.value) {
    return
  }

  if (!pendingTask) {
    if (route.query.autostart === '1') {
      await clearPendingTask()
    }
    return
  }

  const canExecute = tryApplyPendingConnection(pendingTask)
  input.value = String(pendingTask.query || '').trim()

  if (!input.value) {
    await clearPendingTask()
    return
  }

  if (!canExecute) {
    await appendMessage('assistant', '未找到与该告警匹配的服务器连接，请先选择或补全连接信息后再执行。')
    return
  }

  const started = await sendMessage(input.value)
  if (started === false) {
    return
  }
  await clearPendingTask()
}

const connectWs = () => {
  if (connected.value) {
    maybeRunPendingTask()
    return
  }
  const socket = new WebSocket(getWsUrl())

  socket.onopen = async () => {
    ws.value = socket
    connected.value = true
    await appendMessage('assistant', '连接已建立，可以开始对话。')
    await maybeRunPendingTask()
  }

  socket.onmessage = async event => {
    try {
      const data = JSON.parse(event.data)
      if (data.type === 'welcome') {
        await appendMessage('assistant', data.message || '欢迎使用运维助手。')
        return
      }
      if (data.type === 'status') {
        return
      }
      if (data.type === 'plan') {
        await appendRichMarkdownMessage({
          eyebrow: '执行计划',
          title: '我先整理了一份处理步骤',
          subtitle: serverIp.value ? `目标服务器：${serverIp.value}` : '',
          tone: 'info',
          content: formatPlanMessage(data.plan),
        })
        return
      }
      if (data.type === 'step_start') {
        const stepIndex = Number(data.index || 0)
        if (stepIndex > 0 && data.description) {
          stepTitleMap.value = {
            ...stepTitleMap.value,
            [stepIndex]: String(data.description),
          }
        }
        const stepMessage = formatStepStartMessage(data)
        if (stepMessage) {
          await appendMessage('assistant', stepMessage)
        }
        return
      }
      if (data.type === 'step_done') {
        const stepMessage = formatStepDoneMessage(data)
        if (stepMessage) {
          await appendMessage('assistant', stepMessage)
        }
        return
      }
      if (data.type === 'step_failed') {
        await appendRichMarkdownMessage({
          eyebrow: '执行异常',
          title: '某个步骤没有顺利完成',
          tone: 'danger',
          content: formatStepFailedMessage(data),
        })
        return
      }
      if (data.type === 'task_done') {
        isAgentRunning.value = false
        lastTaskDoneDigest.value = normalizeComparableText(data.summary)
        lastTaskFailedDigest.value = ''
        await appendRichMarkdownMessage({
          eyebrow: '执行完成',
          title: '处理结果已经整理好了',
          subtitle: serverIp.value ? `目标服务器：${serverIp.value}` : '',
          tone: 'success',
          content: formatTaskDoneMessage(data),
        })
        return
      }
      if (data.type === 'task_failed') {
        isAgentRunning.value = false
        lastTaskFailedDigest.value = normalizeComparableText(data.error)
        lastTaskDoneDigest.value = ''
        await appendRichMarkdownMessage({
          eyebrow: '任务失败',
          title: '这次处理没有顺利完成',
          subtitle: serverIp.value ? `目标服务器：${serverIp.value}` : '',
          tone: 'danger',
          content: formatTaskFailedMessage(data),
        })
        return
      }
      if (data.type === 'tool_call') {
        return
      }
      if (data.type === 'ops_progress') {
        if (['agent_finish', 'agent_timeout', 'agent_stopped', 'agent_stop', 'finished'].includes(String(data.stage || ''))) {
          isAgentRunning.value = false
        }
        const progressText = formatProgressForUser(data)
        if (progressText) {
          await appendMessage('assistant', progressText)
        }
        return
      }
      if (data.type === 'ops_chat_result') {
        isAgentRunning.value = false
        const summary = formatOpsResult(data)
        if (shouldAppendOpsResultSummary(data, summary)) {
          await appendMessage('assistant', summary)
        }

        if (data.needRiskConfirm && data.riskCommand) {
          await appendRiskConfirmMessage(data.riskCommand, data.riskLevel, data.reply)
        } else if (data.timeout) {
          await appendTimeoutMessage()
        }

        if (!execute.value && !data.executed && data.hasCommand && data.command) {
          await appendConfirmMessage(data.query, data.command, data.riskLevel)
        }
        if (data.chartSuggest) {
          await appendChartActionMessage(data.chartReason, data.chartTimeRange, data.chartTemplate, data.chartTitle)
        }
        return
      }
      if (data.type === 'ops_force_stop_result' || data.type === 'ops_stop_ack') {
        isAgentRunning.value = false
        await appendMessage('assistant', data.message || '已发送强制停止请求。')
        return
      }
      if (data.type === 'chart_data_result') {
        if (!data.success) {
          await appendMessage('assistant', data.message || '生成图表失败。')
          return
        }
        await appendMessage('assistant', '图表数据已生成，正在渲染...')
        await appendChartRenderMessage(data.chartData || {})
        return
      }
      await appendMessage('assistant', typeof event.data === 'string' ? event.data : JSON.stringify(data))
    } catch (error) {
      await appendMessage('assistant', String(event.data || '收到未知响应'))
    }
  }

  socket.onclose = async () => {
    connected.value = false
    isAgentRunning.value = false
    ws.value = null
    await appendMessage('assistant', '连接已断开。')
  }

  socket.onerror = async () => {
    isAgentRunning.value = false
    await appendMessage('assistant', '连接异常，请检查后端服务。')
  }
}

const disconnectWs = () => {
  if (ws.value) {
    ws.value.close()
  }
}

const HIGH_RISK_KEYWORDS = [
  'rm -rf /', 'rm -rf /*', 'rm -rf ~', 'rm -rf /root', 'rm -rf /home',
  'mkfs', 'mkfs.ext', 'mkfs.xfs', 'mkfs.ntfs',
  'shutdown', 'shutdown -h', 'shutdown -r', 'poweroff', 'halt', 'reboot',
  'dd if=/dev/zero', 'dd if=/dev/random', 'dd if=/dev/urandom',
  'chmod -R 777 /', 'chmod 777 /',
  'userdel -r root', 'userdel root',
  '> /etc/passwd', '> /etc/shadow', '> /etc/hosts',
  ':(){ :|:& };:', 'fork bomb'
]

const containsHighRiskCommand = (text) => {
  const lowerText = text.toLowerCase()
  return HIGH_RISK_KEYWORDS.some(keyword => lowerText.includes(keyword.toLowerCase()))
}

const sendMessage = async presetText => {
  if (isAgentRunning.value) return false
  const text = typeof presetText === 'string' ? presetText.trim() : input.value.trim()
  if (!text) return false

  if (containsHighRiskCommand(text)) {
    await appendMessage('assistant', '**安全拦截**：检测到输入包含高风险系统命令（如 rm -rf、shutdown、mkfs 等），该请求已被前端安全层拦截。如需执行服务器维护操作，请使用自然语言描述需求，由 AI 生成安全的处置方案。')
    input.value = ''
    return false
  }

  if (!connected.value || !ws.value) {
    await appendMessage('assistant', '当前未连接，正在自动连接...')
    connectWs()
    return false
  }

  isAgentRunning.value = true
  lastTaskDoneDigest.value = ''
  lastTaskFailedDigest.value = ''
  stepTitleMap.value = {}
  const shouldExecute = hasCompleteConnection()
  execute.value = shouldExecute

  const payload = {
    type: 'ops_chat',
    query: text,
    execute: shouldExecute,
    serverIp: serverIp.value,
    username: username.value,
    password: password.value,
    maxRounds: Math.min(50, Math.max(1, Number(maxRounds.value) || 15)),
  }

  try {
    await appendMessage('user', text)
    await appendMessage('assistant', '收到，我先分析一下你的请求。')
    ws.value.send(JSON.stringify(payload))
    input.value = ''
    return true
  } catch (error) {
    isAgentRunning.value = false
    await appendMessage('assistant', error?.message || '发送失败，请稍后重试。')
    return false
  }
}

const executeByConfirmation = async query => {
  if (!connected.value || !ws.value) {
    await appendMessage('assistant', '当前未连接，无法执行命令。')
    return false
  }
  if (!serverIp.value || !username.value || !password.value) {
    await appendMessage('assistant', '请先填写服务器连接信息后再执行。')
    return false
  }
  if (!query) {
    await appendMessage('assistant', '缺少原始指令，无法执行。')
    return false
  }

  await appendMessage('assistant', '收到，正在执行中...')
  lastTaskDoneDigest.value = ''
  lastTaskFailedDigest.value = ''
  stepTitleMap.value = {}
  ws.value.send(JSON.stringify({
    type: 'ops_chat',
    query,
    execute: true,
    serverIp: serverIp.value,
    username: username.value,
    password: password.value,
    maxRounds: Math.min(50, Math.max(1, Number(maxRounds.value) || 15)),
  }))
  isAgentRunning.value = true
  return true
}

const confirmExecute = async msg => {
  if (msg.handled) return
  const started = await executeByConfirmation(msg.query)
  if (started) {
    markInteractiveHandled(msg, '已确认，正在执行。', 'success')
  }
}

const cancelExecute = async msg => {
  if (msg.handled) return
  markInteractiveHandled(msg, '已取消执行。', 'info')
  await appendMessage('assistant', '已取消执行。')
}

const confirmRiskExecute = async msg => {
  if (msg.handled) return

  if (!connected.value || !ws.value) {
    await appendMessage('assistant', '当前未连接，无法执行高风险命令。')
    return
  }
  if (!serverIp.value || !username.value || !password.value) {
    await appendMessage('assistant', '请先填写服务器连接信息后再执行。')
    return
  }

  await appendMessage('assistant', '收到高风险执行确认，正在执行中...')
  isAgentRunning.value = true
  lastTaskDoneDigest.value = ''
  lastTaskFailedDigest.value = ''
  stepTitleMap.value = {}
  markInteractiveHandled(msg, '已确认高风险执行，正在处理。', 'danger')
  ws.value.send(JSON.stringify({
    type: 'risk_execute',
    command: msg.command,
    serverIp: serverIp.value,
    username: username.value,
    password: password.value,
  }))
}

const cancelRiskExecute = async msg => {
  if (msg.handled) return
  markInteractiveHandled(msg, '已取消高风险命令执行。', 'info')
  await appendMessage('assistant', '已取消高风险命令执行。')
}

const confirmContinue = async msg => {
  if (msg.handled) return

  if (!connected.value || !ws.value) {
    await appendMessage('assistant', '当前未连接，无法继续执行。')
    return
  }

  await appendMessage('user', '继续')
  await appendMessage('assistant', '收到，正在继续执行中...')
  markInteractiveHandled(msg, '已确认继续执行。', 'success')
  lastTaskDoneDigest.value = ''
  lastTaskFailedDigest.value = ''

  const payload = {
    type: 'ops_chat',
    query: '继续',
    execute: true,
    serverIp: serverIp.value,
    username: username.value,
    password: password.value,
    maxRounds: Math.min(50, Math.max(1, Number(maxRounds.value) || 15)),
  }

  ws.value.send(JSON.stringify(payload))
  isAgentRunning.value = true
}

const cancelContinue = async msg => {
  if (msg.handled) return
  markInteractiveHandled(msg, '已停止任务。', 'info')
  await appendMessage('assistant', '已停止任务。')
}

const generateChart = async msg => {
  if (msg.handled) return

  if (!connected.value || !ws.value) {
    await appendMessage('assistant', '当前未连接，无法生成图表。')
    return
  }
  if (!serverIp.value) {
    await appendMessage('assistant', '请先填写服务器 IP。')
    return
  }

  await appendMessage('assistant', '正在生成图表数据...')
  markInteractiveHandled(msg, '图表生成请求已提交。', 'success')
  ws.value.send(JSON.stringify({
    type: 'chart_data_request',
    serverIp: serverIp.value,
    username: username.value,
    password: password.value,
    timeRange: msg.timeRange || '1h',
    chartTemplate: msg.chartTemplate || 'health_overview',
    chartTitle: msg.chartTitle || '服务器健康总览',
  }))
}

const cancelChart = async msg => {
  if (msg.handled) return
  markInteractiveHandled(msg, '已取消图表生成。', 'info')
  await appendMessage('assistant', '已取消图表生成。')
}

const forceStop = async () => {
  if (!connected.value || !ws.value) {
    isAgentRunning.value = false
    await appendMessage('assistant', '连接未建立，无法强制停止。')
    return
  }
  ws.value.send(JSON.stringify({ type: 'ops_force_stop' }))
  await appendMessage('assistant', '已发送强制停止请求...')
}

const loadSavedConnections = async () => {
  try {
    const configs = await listConfigs()
    if (!Array.isArray(configs)) return

    const unique = new Map()
    configs.forEach(item => {
      const ip = item?.serverIp || ''
      const user = item?.username || ''
      const pass = item?.password || ''
      if (!ip || !user || !pass) return
      const key = `${ip}__${user}__${pass}`
      if (!unique.has(key)) {
        unique.set(key, {
          id: key,
          serverIp: ip,
          username: user,
          password: pass,
          label: `${ip} | ${user}`,
        })
      }
    })
    savedConnections.value = Array.from(unique.values())
  } catch (error) {
    console.error('Failed to load saved connections', error)
  } finally {
    savedConnectionsLoaded.value = true
    await maybeRunPendingTask()
  }
}

const handleSavedConnectionChange = value => {
  const target = savedConnections.value.find(item => item.id === value)
  if (!target) return
  serverIp.value = target.serverIp
  username.value = target.username
  password.value = target.password
}

const renderMarkdown = content => {
  if (!content) return ''
  return md.render(content)
}

const isMarkdownMessage = msg => {
  return msg?.type === 'rich_markdown' || (msg?.role === 'assistant' && msg?.type === 'text')
}

const formatOpsResult = data => {
  const lines = []
  const replySummary = toUserFriendlyText(data.reply)

  if (replySummary) {
    lines.push(replySummary)
  } else {
    lines.push(data.executed ? '处理完成。' : '我已整理好处理建议。')
  }

  if (data.chatOnly) {
    return lines.join('\n\n')
  }

  if (data.executed) {
    const execResult = toUserFriendlyText(data.execResult)
    if (execResult && !isGenericExecResult(execResult)) {
      lines.push(`> ${execResult}`)
    }
  } else {
    if (data.needRiskConfirm) {
      lines.push('涉及高风险操作，需要你确认后我再继续。')
      lines.push(`待确认命令：\`${data.riskCommand || ''}\``)
      return lines.join('\n\n')
    }
    if (data.hasCommand && data.command) {
      lines.push(`建议命令：\`${data.command}\``)
    }
    lines.push(`风险等级：${data.riskLevel || 'medium'}`)
  }
  return lines.join('\n\n')
}

const formatPlanMessage = plan => {
  if (!Array.isArray(plan) || plan.length === 0) {
    return '我会先检查现状，再根据结果决定是否需要执行变更。'
  }

  const lines = ['我会按下面的顺序推进：', '']
  plan.forEach((item, index) => {
    const description = String(item?.description || `步骤 ${index + 1}`).trim()
    const tags = []
    if (item?.isRisky) {
      tags.push('高风险')
    }
    if (item?.hasRollback) {
      tags.push('可回滚')
    }
    const tagSuffix = tags.length ? `  _(${tags.join(' / ')})_` : ''
    lines.push(`${index + 1}. ${description}${tagSuffix}`)
  })
  return lines.join('\n')
}

const formatTaskDoneMessage = data => {
  const summary = String(data?.summary || '').trim()
  if (!summary) {
    return '本次处理已经完成，当前没有更多补充信息。'
  }
  return summary
}

const formatTaskFailedMessage = data => {
  const error = toUserFriendlyText(data?.error || '')
  const lines = ['这次执行在处理中断了。']
  if (error) {
    lines.push(`**原因**：${error}`)
  }
  lines.push('你可以调整指令后重新执行，或者让我继续针对失败点做进一步排查。')
  return lines.join('\n\n')
}

const formatStepStartMessage = data => {
  const description = String(data?.description || '').trim()
  const index = Number(data?.index || 0)
  const total = Number(data?.total || 0)

  if (description && index > 0 && total > 0) {
    return `正在处理第 ${index}/${total} 步：**${description}**`
  }
  if (description) {
    return `正在处理：**${description}**`
  }
  return ''
}

const formatStepDoneMessage = data => {
  const index = Number(data?.index || 0)
  const description = stepTitleMap.value[index]
  const result = toUserFriendlyText(data?.result || '')

  if (description && result) {
    return `已完成：**${description}**\n\n> ${truncateText(result, 140)}`
  }
  if (description) {
    return `已完成：**${description}**`
  }
  if (result) {
    return `已完成一个步骤。\n\n> ${truncateText(result, 140)}`
  }
  return '已完成一个步骤。'
}

const formatStepFailedMessage = data => {
  const lines = []
  const description = String(data?.description || '').trim()
  const error = toUserFriendlyText(data?.error || '')
  const action = actionLabel(data?.action)
  const reason = toUserFriendlyText(data?.reason || '')

  if (description) {
    lines.push(`**出问题的步骤**：${description}`)
  }
  if (error) {
    lines.push(`**错误信息**：${error}`)
  }
  if (action) {
    lines.push(`**系统处理**：${action}`)
  }
  if (reason) {
    lines.push(`**原因说明**：${reason}`)
  }

  return lines.join('\n\n')
}

const chartTemplateLabel = template => {
  switch (template) {
    case 'cpu_mem_trend':
      return 'CPU / 内存趋势图'
    case 'anomaly_timeline':
      return '异常时序图'
    case 'health_score_radar':
      return '健康评分雷达图'
    default:
      return '服务器健康总览'
  }
}

const toUserFriendlyText = text => {
  return String(text || '')
    .replace(/第\s*\d+\s*轮[:：]?\s*/g, '')
    .replace(/（\s*\d+\s*ms\s*）/gi, '')
    .replace(/\(\s*\d+\s*ms\s*\)/gi, '')
    .replace(/\[进度\]\s*/g, '')
    .replace(/^收到，正在执行中\.\.\.$/g, '收到，我开始处理了。')
    .replace(/^收到，正在处理中\.\.\.$/g, '收到，我先帮你分析一下。')
    .replace(/^Agent 循环已完成[。.]?/g, '处理已经完成。')
    .trim()
}

const formatProgressForUser = data => {
  const stage = String(data?.stage || '')
  const message = toUserFriendlyText(data?.message || '')

  if (['plan_ready', 'step_start', 'step_done', 'task_done', 'task_failed', 'tool_call', 'agent_think', 'finished'].includes(stage)) {
    return ''
  }
  if (stage === 'start') {
    return '已经收到你的请求，开始处理。'
  }
  if (stage === 'planning') {
    return '我先分析目标，整理执行计划。'
  }
  if (stage === 'intent_detecting' || stage === 'intent_result') {
    return ''
  }
  if (stage === 'cmd_exec_start') {
    if (!message) {
      return '正在执行命令...'
    }
    return `正在执行命令：\`${truncateText(message.replace(/^执行命令[:：]\s*/g, ''), 120)}\``
  }
  if (stage === 'cmd_exec_done') {
    return ''
  }
  if (stage === 'cmd_exec_fail') {
    return `命令执行失败：\`${truncateText(data.command || '未知命令', 120)}\``
  }
  if (stage === 'metrics_fetch_start') {
    return '正在读取服务器监控数据...'
  }
  if (stage === 'metrics_fetch_done') {
    return ''
  }
  if (stage === 'log_read_start') {
    return '正在检查相关日志...'
  }
  if (stage === 'log_read_done') {
    return ''
  }
  if (stage === 'risk_exec_start') {
    return '正在执行你确认的高风险命令...'
  }
  if (stage === 'risk_exec_done') {
    return '高风险命令执行完成。'
  }
  if (stage === 'step_retry') {
    return '刚才的步骤没有成功，我正在自动重试。'
  }
  if (stage === 'step_skip') {
    return '有一个步骤已跳过，我会继续处理后续内容。'
  }
  if (stage === 'agent_timeout') {
    return '这次处理超出了当前轮数限制，已经先暂停。'
  }
  if (stage === 'agent_stopped' || stage === 'agent_stop') {
    return '任务已强制停止。'
  }

  return ''
}

const isGenericExecResult = text => {
  const normalized = String(text || '').trim()
  return [
    'Agent 循环已完成。',
    'Agent 循环已完成，请参考上方实时进度日志。',
    '高风险命令执行并后续流程已完成。',
    '任务已被强制停止。',
    '达到最大轮数，任务暂停。你可以发送“继续”来恢复执行。',
  ].includes(normalized)
}

const normalizeComparableText = text => {
  return String(text || '')
    .replace(/\s+/g, ' ')
    .trim()
}

const shouldAppendOpsResultSummary = (data, summary) => {
  const currentDigest = normalizeComparableText(data?.reply || summary)
  if (!currentDigest) {
    return false
  }

  if (data?.executed && lastTaskDoneDigest.value && currentDigest === lastTaskDoneDigest.value) {
    return false
  }
  if (!data?.executed && lastTaskFailedDigest.value && currentDigest === lastTaskFailedDigest.value) {
    return false
  }
  return true
}

const truncateText = (text, maxLength = 120) => {
  const value = String(text || '').trim()
  if (!value || value.length <= maxLength) {
    return value
  }
  return `${value.slice(0, maxLength)}...`
}

const actionLabel = action => {
  switch (String(action || '').toLowerCase()) {
    case 'retry':
      return '系统决定重试当前步骤'
    case 'skip':
      return '系统决定跳过当前步骤'
    case 'abort':
      return '系统决定终止本次任务'
    default:
      return ''
  }
}

const resultToneClass = tone => {
  if (tone === 'success') {
    return 'result-tone result-tone-success'
  }
  if (tone === 'danger') {
    return 'result-tone result-tone-danger'
  }
  return 'result-tone result-tone-info'
}

const resultToneLabel = tone => {
  if (tone === 'success') {
    return '已完成'
  }
  if (tone === 'danger') {
    return '需处理'
  }
  return '进行中'
}

const humanLikeDelay = () => {
  const delay = Math.floor(Math.random() * 401) + 100
  return new Promise(resolve => setTimeout(resolve, delay))
}

onMounted(() => {
  connectWs()
  loadSavedConnections()
})

onUnmounted(() => {
  disconnectWs()
})
</script>

<style scoped>
.assistant-shell-card {
  border-radius: 2.125rem !important;
}

.assistant-shell-card :deep(.el-card__body) {
  border-radius: inherit;
}

/* 用户气泡使用品牌蓝纯色底色加亮，机器人回复交给 global glass-soft */
.assistant-user-bubble {
  background: linear-gradient(135deg, rgba(37, 99, 235, 0.96), rgba(96, 165, 250, 0.94));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.2), 0 0.5rem 1rem -0.375rem rgba(37, 99, 235, 0.4);
}

.result-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.75rem;
  padding-bottom: 0.75rem;
  border-bottom: 1px solid rgba(148, 163, 184, 0.2);
}

.result-tone {
  flex-shrink: 0;
  border-radius: 62.4375rem;
  padding: 0.25rem 0.625rem;
  font-size: 0.6875rem;
  font-weight: 600;
  line-height: 1;
}

.result-tone-success {
  background: rgba(16, 185, 129, 0.12);
  color: #047857;
  border: 1px solid rgba(16, 185, 129, 0.2);
}

.result-tone-danger {
  background: rgba(239, 68, 68, 0.12);
  color: #b91c1c;
  border: 1px solid rgba(239, 68, 68, 0.2);
}

.result-tone-info {
  background: rgba(59, 130, 246, 0.12);
  color: #1d4ed8;
  border: 1px solid rgba(59, 130, 246, 0.2);
}

:deep(.markdown-body ul) {
  list-style-type: disc;
  padding-left: 1.5em;
  margin-bottom: 0.75em;
}
:deep(.markdown-body ol) {
  list-style-type: decimal;
  padding-left: 1.5em;
  margin-bottom: 0.75em;
}
:deep(.markdown-body pre) {
  background: rgba(255, 255, 255, 0.2);
  padding: 1em;
  border-radius: 0.75rem;
  overflow-x: auto;
  margin-bottom: 0.75em;
  border: 1px solid rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(1.125rem);
}
:deep(.markdown-body code) {
  background: rgba(255, 255, 255, 0.2);
  padding: 0.2em 0.4em;
  border-radius: 0.35rem;
  font-family: monospace;
}
:deep(.markdown-body p) {
  margin-bottom: 0.75em;
}
:deep(.markdown-body h1),
:deep(.markdown-body h2),
:deep(.markdown-body h3) {
  font-weight: bold;
  margin-bottom: 0.5em;
  margin-top: 1em;
}
:deep(.markdown-body blockquote) {
  margin: 0 0 0.85em;
  padding: 0.8em 1em;
  border-left: 0.1875rem solid rgba(59, 130, 246, 0.35);
  background: rgba(255, 255, 255, 0.16);
  border-radius: 0.85rem;
}
:deep(.markdown-body li + li) {
  margin-top: 0.3em;
}
:deep(.markdown-result > :first-child) {
  margin-top: 0;
}
:deep(.markdown-result strong) {
  color: #0f172a;
}

.custom-scrollbar::-webkit-scrollbar {
  width: 0.375rem;
}

.custom-scrollbar::-webkit-scrollbar-track {
  background: transparent;
}

.custom-scrollbar::-webkit-scrollbar-thumb {
  background-color: rgba(148, 163, 184, 0.5);
  border-radius: 62.4375rem;
}
</style>
