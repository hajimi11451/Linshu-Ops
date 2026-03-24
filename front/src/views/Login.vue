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
              欢迎回来
            </span>
            <div>
              <h1 class="text-3xl font-semibold tracking-[-0.04em] text-ui-text sm:text-5xl">在同一个工作台里，继续你的智能运维节奏。</h1>
              <p class="mt-4 max-w-lg text-sm leading-7 text-ui-subtext sm:text-base">
                登录后即可返回灵枢智维控制台，查看服务器态势、告警事件与自动化处置进展。
              </p>
            </div>
          </div>

          <div class="grid gap-4 sm:grid-cols-3">
            <div class="glass-soft p-5">
              <div class="text-xs uppercase tracking-[0.22em] text-ui-subtext">监控</div>
              <div class="mt-3 text-xl font-semibold text-ui-text">全局总览</div>
              <p class="mt-2 text-sm text-ui-subtext">实时掌握主机状态、性能趋势和异常波动。</p>
            </div>
            <div class="glass-soft p-5">
              <div class="text-xs uppercase tracking-[0.22em] text-ui-subtext">告警</div>
              <div class="mt-3 text-xl font-semibold text-ui-text">快速响应</div>
              <p class="mt-2 text-sm text-ui-subtext">重点事件集中展示，缩短定位与处理路径。</p>
            </div>
            <div class="glass-soft p-5">
              <div class="text-xs uppercase tracking-[0.22em] text-ui-subtext">自动化</div>
              <div class="mt-3 text-xl font-semibold text-ui-text">持续处置</div>
              <p class="mt-2 text-sm text-ui-subtext">保留诊断与执行链路，让操作过程清晰可追踪。</p>
            </div>
          </div>
        </div>
      </section>

      <section class="glass-card p-6 sm:p-8 lg:min-h-[calc(100vh-140px)]">
        <div class="mx-auto flex h-full w-full max-w-md flex-col justify-center">
          <div class="space-y-2">
            <span class="inline-flex rounded-full bg-brand/10 px-3 py-1 text-xs font-semibold tracking-[0.18em] text-brand">登录</span>
            <h2 class="text-2xl font-semibold tracking-[-0.03em] text-ui-text">登录账号</h2>
            <p class="text-sm text-ui-subtext">请输入账号信息以继续使用。</p>
          </div>

          <el-form :model="form" class="mt-8 space-y-5" label-position="top">
            <el-alert
              v-if="errorMessage"
              :title="errorMessage"
              type="error"
              show-icon
              class="p-0"
              :closable="false"
            />

            <el-form-item label="账号/邮箱" prop="username" class="space-y-1">
              <el-input
                v-model="form.username"
                id="email"
                type="text"
                autocomplete="username"
                placeholder="请输入用户名或邮箱"
                clearable
                class="input-focus block h-12 w-full appearance-none"
              />
            </el-form-item>

            <el-form-item label="密码" prop="password" class="space-y-1">
              <el-input
                v-model="form.password"
                id="password"
                type="password"
                autocomplete="current-password"
                placeholder="请输入密码"
                show-password
                class="input-focus block h-12 w-full appearance-none"
                @keyup.enter="handleLogin"
              />
            </el-form-item>

            <el-button
              type="primary"
              class="w-full !h-auto px-4 py-3 text-sm font-semibold text-white transition-all duration-150"
              :loading="loading"
              :disabled="loading"
              @click="handleLogin"
            >
              {{ loading ? '登录中...' : '登录' }}
            </el-button>

            <div class="pt-2 text-center text-sm text-ui-subtext">
              还没有账号？
              <router-link to="/register" class="font-medium text-brand hover:text-brand-hover">
                立即注册
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
import AppShellHeader from '../components/AppShellHeader.vue'
import { login } from '../api/user'

const router = useRouter()
const loading = ref(false)
const errorMessage = ref('')

const form = reactive({
  username: '',
  password: '',
})

const handleLogin = async () => {
  if (!form.username || !form.password) {
    errorMessage.value = '请输入用户名和密码'
    return
  }

  loading.value = true
  errorMessage.value = ''

  try {
    await login({
      username: form.username,
      password: form.password,
    })

    localStorage.setItem('user', JSON.stringify({ username: form.username }))
    router.push('/dashboard')
  } catch (error) {
    errorMessage.value = error.message || '登录失败，请检查用户名或密码'
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
