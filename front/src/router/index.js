import { createRouter, createWebHashHistory } from 'vue-router'
import Login from '../views/Login.vue'
import Register from '../views/Register.vue'
import MainLayout from '../layouts/MainLayout.vue'
import Dashboard from '../views/Dashboard.vue'
import DiagnosisView from '../views/DiagnosisView.vue'
import InfoListView from '../views/InfoListView.vue'
import InfoDetailView from '../views/InfoDetailView.vue'
import OpsAssistantView from '../views/OpsAssistantView.vue'
import AutoExecutionView from '../views/AutoExecutionView.vue'
import AlertSettingsView from '../views/AlertSettingsView.vue'

const router = createRouter({
  history: createWebHashHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: Login,
    },
    {
      path: '/',
      redirect: '/login',
    },
    {
      path: '/register',
      name: 'register',
      component: Register,
    },
    {
      path: '/',
      component: MainLayout,
      children: [
        {
          path: '/dashboard',
          name: 'dashboard',
          component: Dashboard,
          meta: {
            title: '总览',
          },
        },
        {
          path: '/diagnosis',
          name: 'diagnosis',
          component: DiagnosisView,
          meta: {
            title: '智能诊断配置',
          },
        },
        {
          path: '/ops-assistant',
          name: 'ops-assistant',
          component: OpsAssistantView,
          meta: {
            title: '灵枢助手',
          },
        },
        {
          path: '/auto-execution',
          name: 'auto-execution',
          component: AutoExecutionView,
          meta: {
            title: '智能处置记录',
          },
        },
        {
          path: '/info-list',
          name: 'info-list',
          component: InfoListView,
          meta: {
            title: '全部信息与告警',
          },
        },
        {
          path: '/info-list/:id',
          name: 'info-detail',
          component: InfoDetailView,
          meta: {
            title: '告警详情',
          },
        },
        {
          path: '/alert-settings',
          name: 'alert-settings',
          component: AlertSettingsView,
          meta: {
            title: '邮箱通知设置',
          },
        },
      ],
    },
  ],
})

export default router
