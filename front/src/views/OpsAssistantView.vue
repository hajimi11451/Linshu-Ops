<template>
  <div class="space-y-6">
    <el-card class="bg-white rounded-[8px] shadow-sm border border-ui-border" :body-style="{ padding: '20px' }">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 class="text-lg font-bold text-ui-text">灵枢助手</h2>
          <p class="text-xs text-ui-subtext mt-1">AI 聊天 + 命令规划 + 可选远程执行</p>
        </div>
        <div class="flex items-center gap-2">
          <span class="inline-flex items-center px-2 py-1 rounded text-xs font-medium" :class="connected ? 'bg-green-50 text-ui-success' : 'bg-gray-100 text-ui-subtext'">
            {{ connected ? 'WebSocket 已连接' : 'WebSocket 未连接' }}
          </span>
          <el-button size="small" @click="connectWs" :disabled="connected">连接</el-button>
          <el-button size="small" @click="disconnectWs" :disabled="!connected">断开</el-button>
        </div>
      </div>
    </el-card>

    <el-card class="bg-white rounded-[8px] shadow-sm border border-ui-border" :body-style="{ padding: '20px' }">
      <div class="grid grid-cols-1 md:grid-cols-2 gap-4 mb-4">
        <el-select
          v-model="selectedSavedConnection"
          filterable
          clearable
          placeholder="选择已保存连接（自动回填 IP/账号/密码）"
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
        <el-input v-model="serverIp" placeholder="serverIp: 192.168.1.10:22" />
        <el-input v-model="username" placeholder="username: root" />
        <el-input v-model="password" type="password" show-password placeholder="password" />
        <el-checkbox v-model="execute">允许执行命令（execute=true）</el-checkbox>
      </div>
      <p class="text-xs text-ui-subtext mt-3">默认先规划命令，不执行。勾选后会调用 SSH 在目标服务器执行。</p>
    </el-card>

    <el-card class="bg-white rounded-[8px] shadow-sm border border-ui-border" :body-style="{ padding: '20px' }">
      <div ref="chatBox" class="h-[460px] overflow-y-auto bg-ui-bg border border-ui-border rounded-lg p-4 space-y-3">
        <div v-for="(msg, idx) in messages" :key="idx" class="flex" :class="msg.role === 'user' ? 'justify-end' : 'justify-start'">
          <div class="max-w-[85%] rounded-lg px-3 py-2 text-sm whitespace-pre-wrap" :class="msg.role === 'user' ? 'bg-brand text-white' : 'bg-white border border-ui-border text-ui-text'">
            <div class="font-semibold text-xs mb-1 opacity-80">{{ msg.role === 'user' ? '你' : '灵枢助手' }}</div>
            <template v-if="msg.type === 'confirm'">
              <div class="space-y-2">
                <div>命令：{{ msg.command }}</div>
                <div>风险：{{ msg.riskLevel }}</div>
                <div class="flex gap-2 pt-1">
                  <el-button size="small" type="primary" :disabled="msg.handled" @click="confirmExecute(msg)">确定执行</el-button>
                  <el-button size="small" :disabled="msg.handled" @click="cancelExecute(msg)">取消</el-button>
                </div>
              </div>
            </template>
            <template v-else>
              <div>{{ msg.content }}</div>
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
          placeholder="例如：帮我安装 nginx 并设置开机自启"
          @keydown.enter.exact.prevent="sendMessage"
        />
        <el-button type="primary" class="h-auto px-6" :disabled="!input.trim()" @click="sendMessage">发送</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import { listConfigs } from '../api/diagnosis'

const ws = ref(null)
const connected = ref(false)

const serverIp = ref('')
const username = ref('')
const password = ref('')
const execute = ref(false)
const selectedSavedConnection = ref('')
const savedConnections = ref([])

const input = ref('')
const messages = ref([])
const chatBox = ref(null)

const appendMessage = async (role, content) => {
  messages.value.push({ role, content, type: 'text' })
  await nextTick()
  if (chatBox.value) {
    chatBox.value.scrollTop = chatBox.value.scrollHeight
  }
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
  await nextTick()
  if (chatBox.value) {
    chatBox.value.scrollTop = chatBox.value.scrollHeight
  }
}

const getWsUrl = () => {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}/ws/server/connect`
}

const connectWs = () => {
  if (connected.value) return
  const socket = new WebSocket(getWsUrl())

  socket.onopen = async () => {
    ws.value = socket
    connected.value = true
    await appendMessage('assistant', '连接已建立，可以开始聊天。')
  }

  socket.onmessage = async event => {
    try {
      const data = JSON.parse(event.data)
      if (data.type === 'welcome') {
        await appendMessage('assistant', data.message || '欢迎使用灵枢助手。')
        return
      }
      if (data.type === 'ops_progress') {
        const progressText = `[进度] ${data.message || data.stage || '处理中'}${data.elapsedMs ? `（${data.elapsedMs}ms）` : ''}`
        await appendMessage('assistant', progressText)
        return
      }
      if (data.type === 'ops_chat_result') {
        const summary = formatOpsResult(data)
        await appendMessage('assistant', summary)

        if (!execute.value && !data.executed && data.hasCommand && data.command) {
          await appendConfirmMessage(data.query, data.command, data.riskLevel)
        }
        return
      }
      await appendMessage('assistant', typeof event.data === 'string' ? event.data : JSON.stringify(data))
    } catch (e) {
      await appendMessage('assistant', String(event.data || '收到未知响应'))
    }
  }

  socket.onclose = async () => {
    connected.value = false
    ws.value = null
    await appendMessage('assistant', '连接已断开。')
  }

  socket.onerror = async () => {
    await appendMessage('assistant', '连接异常，请检查后端服务。')
  }
}

const disconnectWs = () => {
  if (ws.value) {
    ws.value.close()
  }
}

const sendMessage = async () => {
  const text = input.value.trim()
  if (!text) return

  if (!connected.value || !ws.value) {
    await appendMessage('assistant', 'WebSocket 未连接，正在自动连接...')
    connectWs()
    return
  }

  await appendMessage('user', text)
  await appendMessage('assistant', execute.value ? '收到，正在执行中...' : '收到，正在处理中...')

  const payload = {
    type: 'ops_chat',
    query: text,
    execute: execute.value,
    serverIp: serverIp.value,
    username: username.value,
    password: password.value,
  }

  ws.value.send(JSON.stringify(payload))
  input.value = ''
}

const executeByConfirmation = async query => {
  if (!connected.value || !ws.value) {
    await appendMessage('assistant', 'WebSocket 未连接，无法执行命令。')
    return
  }
  if (!serverIp.value || !username.value || !password.value) {
    await appendMessage('assistant', '请先填写 serverIp/username/password 后再执行。')
    return
  }
  if (!query) {
    await appendMessage('assistant', '缺少原始指令，无法执行。')
    return
  }

  await appendMessage('assistant', '收到，正在执行中...')
  ws.value.send(JSON.stringify({
    type: 'ops_chat',
    query,
    execute: true,
    serverIp: serverIp.value,
    username: username.value,
    password: password.value,
  }))
}

const confirmExecute = async msg => {
  if (msg.handled) return
  msg.handled = true
  await executeByConfirmation(msg.query)
}

const cancelExecute = async msg => {
  if (msg.handled) return
  msg.handled = true
  await appendMessage('assistant', '已取消执行。')
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
  }
}

const handleSavedConnectionChange = value => {
  const target = savedConnections.value.find(item => item.id === value)
  if (!target) return
  serverIp.value = target.serverIp
  username.value = target.username
  password.value = target.password
}

const formatOpsResult = data => {
  const lines = []
  lines.push(data.executed ? '执行完成。' : '计划已生成。')
  if (data.executed) {
    lines.push(`结果：${shortText(data.execResult || '无返回')}`)
  } else {
    lines.push(`风险：${data.riskLevel || 'medium'}`)
    if (data.hasCommand && data.command) {
      lines.push(`命令：${shortText(data.command)}`)
    } else {
      lines.push(shortText(data.reply || 'AI 未提供可执行命令。'))
    }
  }
  return lines.join('\n')
}

const shortText = (text, max = 160) => {
  const val = String(text || '').replace(/\s+/g, ' ').trim()
  if (!val) return ''
  return val.length <= max ? val : `${val.slice(0, max)}...`
}

onMounted(() => {
  connectWs()
  loadSavedConnections()
})

onUnmounted(() => {
  disconnectWs()
})
</script>
