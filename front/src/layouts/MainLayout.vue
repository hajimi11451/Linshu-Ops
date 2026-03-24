<template>
  <div class="app-shell-lighting flex h-screen flex-col overflow-hidden text-ui-text relative">
    
    <div class="light-effects">
      <div class="glow-1"></div>
      <div class="glow-2"></div>
      <div class="glow-3"></div>
    </div>

    <AppShellHeader class="relative z-10" :username="username" @logout="handleLogout" />

    <div class="mx-auto flex min-h-0 w-full max-w-[1600px] flex-1 flex-col gap-4 px-3 py-4 sm:px-5 lg:flex-row lg:px-6 relative z-10">
      <aside class="shrink-0 lg:w-[108px]">
        <div class="shell-surface flex rounded-[28px] border border-white/20 bg-white/10 shadow-sm backdrop-blur-[24px] lg:h-full lg:flex-col p-2">
          <nav class="grid flex-1 grid-cols-3 gap-2 sm:grid-cols-6 lg:grid-cols-1">
            <router-link
              v-for="item in navItems"
              :key="item.path"
              :to="item.path"
              class="group flex min-w-0 flex-col items-center gap-2.5 rounded-[20px] px-2 py-3 text-center transition-all duration-200"
              :class="isActive(item)
                ? 'bg-white/20 text-brand shadow-sm backdrop-blur-xl'
                : 'text-ui-subtext hover:bg-white/10 hover:text-ui-text'"
            >
              <span
                class="flex h-11 w-11 items-center justify-center rounded-2xl transition-colors"
                :class="isActive(item) ? 'bg-brand/10 text-brand' : 'bg-transparent text-ui-subtext group-hover:bg-white/10'"
              >
                <svg class="h-8 w-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" :d="item.icon" />
                </svg>
              </span>
              <span class="max-w-full truncate text-[15px] font-semibold tracking-[0.05em]">{{ item.label }}</span>
            </router-link>
          </nav>

          <div class="mt-2 hidden rounded-[22px] border border-white/10 bg-white/5 px-3 py-3 text-center text-xs font-medium text-ui-subtext backdrop-blur-xl lg:block">
            {{ username || '游客' }}
          </div>
        </div>
      </aside>

      <section class="flex min-h-0 flex-1 flex-col overflow-hidden">
        <div class="mb-3 flex items-center justify-between gap-3 px-1">
          <div>
            <div class="text-[11px] font-semibold uppercase tracking-[0.28em] text-ui-subtext">{{ pageEyebrow }}</div>
            <h1 class="mt-2 text-[26px] font-semibold tracking-[-0.04em] text-ui-text">{{ pageTitle }}</h1>
          </div>

          <div class="hidden items-center gap-2 rounded-full border border-white/20 bg-white/10 px-4 py-2 text-xs text-ui-subtext backdrop-blur-xl md:flex">
            <span class="inline-flex h-2 w-2 rounded-full" :class="username ? 'bg-ui-success' : 'bg-ui-warning'"></span>
            {{ username ? '账号在线' : '未登录' }}
          </div>
        </div>

        <main :class="mainContentClass">
          <RouterView />
        </main>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import AppShellHeader from '../components/AppShellHeader.vue'

const route = useRoute()
const router = useRouter()

const resolveStoredUsername = () => {
  try {
    const raw = localStorage.getItem('user')
    if (!raw) return ''
    return JSON.parse(raw)?.username || ''
  } catch {
    return ''
  }
}

const username = ref(resolveStoredUsername())

const navItems = [
  {
    label: '总览',
    path: '/dashboard',
    match: path => path === '/dashboard',
    icon: 'M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z',
  },
  {
    label: '诊断',
    path: '/diagnosis',
    match: path => path === '/diagnosis',
    icon: 'M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z',
  },
  {
    label: '助手',
    path: '/ops-assistant',
    match: path => path === '/ops-assistant',
    icon: 'M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-4 4v-4z',
  },
  {
    label: '处置',
    path: '/auto-execution',
    match: path => path === '/auto-execution',
    icon: 'M9 12h6m-6 4h6M7 4h10a2 2 0 012 2v12a2 2 0 01-2 2H7a2 2 0 01-2-2V6a2 2 0 012-2z',
  },
  {
    label: '告警',
    path: '/info-list',
    match: path => path.startsWith('/info-list'),
    icon: 'M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h8a2 2 0 012 2v14a2 2 0 01-2 2z',
  },
  {
    label: '通知',
    path: '/alert-settings',
    match: path => path === '/alert-settings',
    icon: 'M3 8l7.89 4.26a2 2 0 002.22 0L21 8m-18 8h18a2 2 0 002-2V8a2 2 0 00-2-2H3a2 2 0 00-2 2v6a2 2 0 002 2z',
  },
]

const pageTitle = computed(() => route.meta?.title || '总览')

const pageEyebrow = computed(() => route.path === '/dashboard' ? 'Overview' : 'Workspace')

const mainContentClass = computed(() => {
  /* 移除了极其复杂的双层阴影，使用全局清透变量 */
  const baseClass = 'shell-surface flex-1 min-h-0 rounded-[30px] border border-white/20 bg-white/10 shadow-sm backdrop-blur-[24px]'

  if (route.path === '/dashboard' || route.path === '/alert-settings') {
    return `${baseClass} overflow-hidden`
  }

  return `${baseClass} overflow-y-auto p-4 sm:p-5 lg:p-6 custom-scrollbar`
})

const isActive = item => item.match(route.path)

const syncUser = () => {
  username.value = resolveStoredUsername()
}

const handleLogout = () => {
  localStorage.removeItem('user')
  username.value = ''
  router.push('/login')
}

onMounted(() => {
  syncUser()
  window.addEventListener('storage', syncUser)
})

onUnmounted(() => {
  window.removeEventListener('storage', syncUser)
})
</script>

<style scoped>
/* ==============================================
   底部几何光晕特效 (无色带、无性能问题的完美版)
============================================== */
.light-effects {
  position: absolute;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  background-color: #f0f4f9; 
}

.glow-1 {
  position: absolute; top: -10%; right: 5%; width: 45vw; height: 45vw;
  background: radial-gradient(circle, rgba(37, 99, 235, 0.25) 0%, rgba(37, 99, 235, 0.08) 40%, transparent 70%);
  border-radius: 50%; animation: float 20s ease-in-out infinite alternate;
}

.glow-2 {
  position: absolute; bottom: -15%; left: 5%; width: 55vw; height: 55vw;
  background: radial-gradient(circle, rgba(79, 70, 229, 0.15) 0%, rgba(79, 70, 229, 0.05) 40%, transparent 70%);
  border-radius: 50%; animation: float 25s ease-in-out infinite alternate-reverse;
}

.glow-3 {
  position: absolute; top: 30%; left: 35%; width: 30vw; height: 30vw;
  background: radial-gradient(circle, rgba(6, 182, 212, 0.2) 0%, rgba(6, 182, 212, 0.06) 40%, transparent 70%);
  border-radius: 50%;
}

@keyframes float {
  0% { transform: translate(0, 0); }
  100% { transform: translate(3%, 5%); }
}

/* ==============================================
   滚动条样式优化
============================================== */
.custom-scrollbar::-webkit-scrollbar {
  width: 6px;
}

.custom-scrollbar::-webkit-scrollbar-track {
  background: transparent;
}

.custom-scrollbar::-webkit-scrollbar-thumb {
  background-color: rgba(148, 163, 184, 0.5);
  border-radius: 999px;
}
</style>
