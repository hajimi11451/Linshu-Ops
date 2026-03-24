<template>
  <div class="relative flex min-h-screen flex-col text-ui-text">
    <div class="light-effects">
      <div class="glow-1"></div>
      <div class="glow-2"></div>
      <div class="glow-3"></div>
    </div>

    <AppShellHeader class="relative z-10" show-auth-links />

    <main class="relative z-10 mx-auto grid w-full max-w-[1280px] flex-1 gap-5 px-4 py-5 sm:px-6 sm:py-6 lg:min-h-[calc(100vh-92px)] lg:grid-cols-2 lg:items-stretch lg:px-8 lg:py-6">
      <section class="glass-card relative overflow-hidden p-6 sm:p-8 lg:min-h-[calc(100vh-140px)]">
        <div class="relative z-10 flex h-full flex-col justify-center gap-8 lg:gap-10">
          <div class="max-w-xl space-y-6">
            <span class="inline-flex rounded-full border border-brand/20 bg-brand/10 px-4 py-1 text-xs font-semibold uppercase tracking-[0.24em] text-brand">
              创建账号
            </span>
            <div>
              <h1 class="text-3xl font-semibold tracking-[-0.04em] text-ui-text sm:text-5xl">把监控、诊断和处置放进同一个工作台。</h1>
              <p class="mt-4 max-w-lg text-sm leading-7 text-ui-subtext sm:text-base">
                注册后即可使用灵枢智维控制台，统一查看服务器态势、告警记录与自动化处置流程。
              </p>
            </div>
          </div>

          <div class="grid gap-4 sm:grid-cols-3">
            <div class="glass-soft p-5">
              <div class="text-xs uppercase tracking-[0.22em] text-ui-subtext">总览</div>
              <div class="mt-3 text-xl font-semibold text-ui-text">状态清晰</div>
              <p class="mt-2 text-sm text-ui-subtext">统一查看服务器健康和最新告警。</p>
            </div>
            <div class="glass-soft p-5">
              <div class="text-xs uppercase tracking-[0.22em] text-ui-subtext">助手</div>
              <div class="mt-3 text-xl font-semibold text-ui-text">智能协同</div>
              <p class="mt-2 text-sm text-ui-subtext">对话式生成命令并保留操作记录。</p>
            </div>
            <div class="glass-soft p-5">
              <div class="text-xs uppercase tracking-[0.22em] text-ui-subtext">通知</div>
              <div class="mt-3 text-xl font-semibold text-ui-text">闭环提醒</div>
              <p class="mt-2 text-sm text-ui-subtext">关键告警通过邮件触达接收人。</p>
            </div>
          </div>
        </div>
      </section>

      <section class="glass-card p-6 sm:p-8 lg:min-h-[calc(100vh-140px)]">
        <div class="mx-auto flex h-full w-full max-w-md flex-col justify-center">
          <div class="space-y-2">
            <span class="inline-flex rounded-full bg-brand/10 px-3 py-1 text-xs font-semibold tracking-[0.18em] text-brand">注册</span>
            <h2 class="text-2xl font-semibold tracking-[-0.03em] text-ui-text">创建账号</h2>
            <p class="text-sm text-ui-subtext">填写信息后立即开始使用。</p>
          </div>

          <el-form :model="form" class="mt-8 space-y-5" label-position="top">
            <el-alert
              v-if="message"
              :title="message"
              :type="messageType === 'success' ? 'success' : 'error'"
              show-icon
              class="p-0"
              :closable="false"
            />

            <el-form-item label="用户名" prop="username" class="space-y-1">
              <el-input
                v-model="form.username"
                type="text"
                autocomplete="username"
                placeholder="请设置用户名"
                clearable
                class="appearance-none block h-12 w-full input-focus"
              />
            </el-form-item>

            <el-form-item label="密码" prop="password" class="space-y-1">
              <el-input
                v-model="form.password"
                type="password"
                autocomplete="new-password"
                placeholder="请输入密码"
                show-password
                class="appearance-none block h-12 w-full input-focus"
              />
            </el-form-item>

            <el-form-item label="确认密码" prop="confirmPassword" class="space-y-1">
              <el-input
                v-model="form.confirmPassword"
                type="password"
                autocomplete="new-password"
                placeholder="再次输入密码"
                show-password
                class="appearance-none block h-12 w-full input-focus"
                @keyup.enter="handleRegister"
              />
            </el-form-item>

            <el-button
              type="primary"
              class="w-full !h-auto px-4 py-3 text-sm font-semibold text-white transition-all duration-150"
              :loading="loading"
              :disabled="loading"
              @click="handleRegister"
            >
              {{ loading ? '注册中...' : '注册' }}
            </el-button>

            <div class="pt-2 text-center text-sm text-ui-subtext">
              已有账号？
              <router-link to="/login" class="font-medium text-brand hover:text-brand-hover">
                去登录
              </router-link>
            </div>
          </el-form>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { register } from '../api/user'
import AppShellHeader from '../components/AppShellHeader.vue'

const router = useRouter()
const loading = ref(false)
const message = ref('')
const messageType = ref('error')

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
})

const handleRegister = async () => {
  if (!form.username || !form.password || !form.confirmPassword) {
    message.value = '请完整填写注册信息'
    messageType.value = 'error'
    return
  }

  if (form.password !== form.confirmPassword) {
    message.value = '两次输入的密码不一致'
    messageType.value = 'error'
    return
  }

  loading.value = true
  message.value = ''

  try {
    await register({
      username: form.username,
      password: form.password,
    })

    message.value = '注册成功，正在跳转登录页...'
    messageType.value = 'success'
    setTimeout(() => {
      router.push('/login')
    }, 1200)
  } catch (error) {
    messageType.value = 'error'
    message.value = error.message || '注册失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.light-effects {
  position: absolute;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  background-color: #f0f4f9;
}

.glow-1 {
  position: absolute;
  top: -10%;
  right: 5%;
  width: 45vw;
  height: 45vw;
  background: radial-gradient(circle, rgba(37, 99, 235, 0.25) 0%, rgba(37, 99, 235, 0.08) 40%, transparent 70%);
  border-radius: 50%;
  animation: float 20s ease-in-out infinite alternate;
}

.glow-2 {
  position: absolute;
  bottom: -15%;
  left: 5%;
  width: 55vw;
  height: 55vw;
  background: radial-gradient(circle, rgba(79, 70, 229, 0.15) 0%, rgba(79, 70, 229, 0.05) 40%, transparent 70%);
  border-radius: 50%;
  animation: float 25s ease-in-out infinite alternate-reverse;
}

.glow-3 {
  position: absolute;
  top: 30%;
  left: 35%;
  width: 30vw;
  height: 30vw;
  background: radial-gradient(circle, rgba(6, 182, 212, 0.2) 0%, rgba(6, 182, 212, 0.06) 40%, transparent 70%);
  border-radius: 50%;
}

@keyframes float {
  0% {
    transform: translate(0, 0);
  }

  100% {
    transform: translate(3%, 5%);
  }
}
</style>
