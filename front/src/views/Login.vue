<template>
  <div class="h-screen flex font-sans antialiased overflow-hidden">
    <!-- 左侧品牌区域 (45%) -->
    <div class="hidden md:flex md:w-5/12 bg-brand-dark relative flex-col justify-center px-12 lg:px-20 text-white overflow-hidden">
      <!-- 背景装饰 -->
      <div class="absolute inset-0 opacity-10 pointer-events-none">
        <svg class="w-full h-full" viewBox="0 0 100 100" preserveAspectRatio="none">
          <path d="M0 100 L100 0 L100 100 Z" fill="#4299e1" />
        </svg>
      </div>

      <div class="relative z-10 space-y-8">
        <div class="flex items-center space-x-3">
          <div class="w-10 h-10 bg-brand rounded flex items-center justify-center">
            <svg class="w-6 h-6 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"></path>
            </svg>
          </div>
          <span class="text-2xl font-bold tracking-wide">Mimir-Ops</span>
        </div>

        <div>
          <h1 class="text-3xl font-bold mb-2">智能驱动，运维无忧</h1>
          <p class="text-gray-400 text-lg">企业级智能运维解决方案，为您的业务保驾护航。</p>
        </div>

        <ul class="space-y-4 text-gray-300">
          <li class="flex items-center space-x-3">
            <svg class="w-5 h-5 text-ui-success flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path>
            </svg>
            <span>全链路智能监控与数据分析</span>
          </li>
          <li class="flex items-center space-x-3">
            <svg class="w-5 h-5 text-ui-success flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path>
            </svg>
            <span>毫秒级异常预警机制</span>
          </li>
          <li class="flex items-center space-x-3">
            <svg class="w-5 h-5 text-ui-success flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path>
            </svg>
            <span>自动化根因分析 (RCA)</span>
          </li>
        </ul>
      </div>

      
    </div>

    <!-- 右侧登录表单 (55%) -->
    <div class="w-full md:w-7/12 bg-white flex flex-col justify-center items-center px-8 md:px-16">
      <div class="w-full max-w-md space-y-8">
        <div class="text-center md:text-left">
          <h2 class="text-2xl font-bold text-ui-text">欢迎回来</h2>
          <p class="mt-2 text-ui-subtext">请登录您的管理账号以继续</p>
        </div>

        <el-form
          :model="form"
          class="space-y-6"
          label-position="top"
        >
          <!-- 错误提示 -->
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
              class="appearance-none block w-full h-12 placeholder-gray-400 input-focus sm:text-sm"
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
              class="appearance-none block w-full h-12 placeholder-gray-400 input-focus sm:text-sm"
            />
          </el-form-item>

          <!-- <div class="flex items-center justify-between">
            <el-checkbox v-model="remember" class="text-sm text-ui-text">
              记住我
            </el-checkbox>
          </div> -->

          <div>
            <el-button
              type="primary"
              class="w-full !h-auto py-3 px-4 border border-transparent rounded-lg shadow-sm text-sm font-bold text-white bg-gradient-to-r from-brand to-brand-hover hover:to-[#2c5282] focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-brand btn-hover transition-all duration-150 disabled:opacity-50 disabled:cursor-not-allowed"
              :loading="loading"
              :disabled="loading"
              @click="handleLogin"
            >
              <span v-if="loading">登录中...</span>
              <span v-else>登 录</span>
            </el-button>
          </div>

          <div class="mt-6 text-center">
            <p class="text-sm text-ui-subtext">
              还没有账号？
              <router-link to="/register" class="font-medium text-brand hover:text-brand-hover">
                立即注册
              </router-link>
            </p>
          </div>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { login } from '../api/user'

const router = useRouter()
const loading = ref(false)
const errorMessage = ref('')
const remember = ref(false)

const form = reactive({
  username: '', 
  password: ''
})

const handleLogin = async () => {
  if (!form.username || !form.password) {
    errorMessage.value = '请输入用户名和密码'
    return
  }

  loading.value = true
  errorMessage.value = ''
  
  try {
    console.log('Attempting login with:', form.username)
    const res = await login({
      username: form.username,
      password: form.password
    })

    console.log('Login success:', res)
    // request.js 已经处理了 code !== 200 的情况并 reject 了
    localStorage.setItem('user', JSON.stringify({ username: form.username, remember: remember.value }))
    router.push('/dashboard')
  } catch (error) {
    console.error('Login error:', error)
    errorMessage.value = error.message || '登录失败，请检查用户名或密码'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
/* 可以在这里补充一些特定的样式，但大部分已由 Tailwind 处理 */
</style>
