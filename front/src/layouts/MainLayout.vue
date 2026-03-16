<template>
  <div class="h-screen flex font-sans antialiased overflow-hidden bg-ui-bg">
    <aside class="w-64 bg-sidebar flex-shrink-0 flex flex-col transition-all duration-300">
      <div class="h-16 flex items-center px-6 bg-dark border-b border-gray-700">
        <router-link to="/dashboard" class="flex items-center hover:opacity-80 transition-opacity">
          <div class="w-8 h-8 bg-brand rounded flex items-center justify-center mr-3">
            <svg class="w-5 h-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z" />
            </svg>
          </div>
          <span class="text-white text-lg font-bold tracking-wide">灵枢智维</span>
        </router-link>
      </div>

      <nav class="flex-1 py-6 space-y-1">
        <router-link
          to="/dashboard"
          class="group flex items-center px-6 py-3 transition-colors border-l-4"
          :class="$route.path === '/dashboard' ? 'bg-dark border-brand text-white' : 'text-gray-400 hover:bg-gray-700 hover:text-white border-transparent'"
        >
          <svg class="mr-3 h-5 w-5" :class="$route.path === '/dashboard' ? 'text-brand' : 'group-hover:text-gray-300'" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z" />
          </svg>
          <span class="text-sm" :class="{ 'font-semibold': $route.path === '/dashboard' }">总览</span>
        </router-link>

        <router-link
          to="/diagnosis"
          class="group flex items-center px-6 py-3 transition-colors border-l-4"
          :class="$route.path === '/diagnosis' ? 'bg-dark border-brand text-white' : 'text-gray-400 hover:bg-gray-700 hover:text-white border-transparent'"
        >
          <svg class="mr-3 h-5 w-5" :class="$route.path === '/diagnosis' ? 'text-brand' : 'group-hover:text-gray-300'" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z" />
          </svg>
          <span class="text-sm" :class="{ 'font-semibold': $route.path === '/diagnosis' }">智能诊断配置</span>
        </router-link>

        <router-link
          to="/ops-assistant"
          class="group flex items-center px-6 py-3 transition-colors border-l-4"
          :class="$route.path === '/ops-assistant' ? 'bg-dark border-brand text-white' : 'text-gray-400 hover:bg-gray-700 hover:text-white border-transparent'"
        >
          <svg class="mr-3 h-5 w-5" :class="$route.path === '/ops-assistant' ? 'text-brand' : 'group-hover:text-gray-300'" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-4 4v-4z" />
          </svg>
          <span class="text-sm" :class="{ 'font-semibold': $route.path === '/ops-assistant' }">灵枢助手</span>
        </router-link>

        <router-link
          to="/auto-execution"
          class="group flex items-center px-6 py-3 transition-colors border-l-4"
          :class="$route.path === '/auto-execution' ? 'bg-dark border-brand text-white' : 'text-gray-400 hover:bg-gray-700 hover:text-white border-transparent'"
        >
          <svg class="mr-3 h-5 w-5" :class="$route.path === '/auto-execution' ? 'text-brand' : 'group-hover:text-gray-300'" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6M7 4h10a2 2 0 012 2v12a2 2 0 01-2 2H7a2 2 0 01-2-2V6a2 2 0 012-2z" />
          </svg>
          <span class="text-sm" :class="{ 'font-semibold': $route.path === '/auto-execution' }">智能处置记录</span>
        </router-link>

        <router-link
          to="/info-list"
          class="group flex items-center px-6 py-3 transition-colors border-l-4"
          :class="$route.path.startsWith('/info-list') ? 'bg-dark border-brand text-white' : 'text-gray-400 hover:bg-gray-700 hover:text-white border-transparent'"
        >
          <svg class="mr-3 h-5 w-5" :class="$route.path.startsWith('/info-list') ? 'text-brand' : 'group-hover:text-gray-300'" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h8a2 2 0 012 2v14a2 2 0 01-2 2z" />
          </svg>
          <span class="text-sm" :class="{ 'font-semibold': $route.path.startsWith('/info-list') }">全部信息与告警</span>
        </router-link>

        <router-link
          to="/alert-settings"
          class="group flex items-center px-6 py-3 transition-colors border-l-4"
          :class="$route.path === '/alert-settings' ? 'bg-dark border-brand text-white' : 'text-gray-400 hover:bg-gray-700 hover:text-white border-transparent'"
        >
          <svg class="mr-3 h-5 w-5" :class="$route.path === '/alert-settings' ? 'text-brand' : 'group-hover:text-gray-300'" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 8l7.89 4.26a2 2 0 002.22 0L21 8m-18 8h18a2 2 0 002-2V8a2 2 0 00-2-2H3a2 2 0 00-2 2v6a2 2 0 002 2z" />
          </svg>
          <span class="text-sm" :class="{ 'font-semibold': $route.path === '/alert-settings' }">邮箱通知设置</span>
        </router-link>
      </nav>
    </aside>

    <div class="flex-1 flex flex-col h-screen overflow-hidden">
      <header class="h-16 bg-white border-b border-ui-border flex justify-between items-center px-8 shadow-sm z-10">
        <div class="flex items-center text-sm text-ui-subtext">
          <router-link to="/dashboard" class="hover:text-brand transition-colors">首页</router-link>
          <span class="mx-2">/</span>
          <span class="text-ui-text font-medium">{{ breadcrumbTitle }}</span>
        </div>

        <div class="flex items-center space-x-6">
          <div class="flex items-center space-x-2">
            <div class="w-8 h-8 rounded-full bg-brand flex items-center justify-center text-white font-bold shadow-sm">
              {{ username.charAt(0).toUpperCase() }}
            </div>
            <div class="flex flex-col">
              <span class="text-sm font-bold text-ui-text leading-tight">{{ username }}</span>
              <button @click="handleLogout" class="text-[10px] text-ui-subtext hover:text-ui-error text-left transition-colors">
                退出登录
              </button>
            </div>
          </div>
        </div>
      </header>

      <main :class="mainContentClass">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter, RouterView } from 'vue-router'

const route = useRoute()
const router = useRouter()

const username = ref('Admin')

const breadcrumbTitle = computed(() => route.meta?.title || '总览')

const mainContentClass = computed(() => {
  if (route.path === '/dashboard') {
    return 'flex-1 overflow-hidden bg-ui-bg p-0'
  }

  return 'flex-1 overflow-y-auto bg-ui-bg p-8 custom-scrollbar'
})

const handleLogout = () => {
  localStorage.removeItem('user')
  router.push('/login')
}

onMounted(() => {
  const userStr = localStorage.getItem('user')
  if (!userStr) return
  try {
    const user = JSON.parse(userStr)
    if (user.username) {
      username.value = user.username
    }
  } catch (e) {
    console.error('Failed to parse user info', e)
  }
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
