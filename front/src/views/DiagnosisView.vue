<template>
  <div class="workspace-cool-glass mx-auto max-w-7xl space-y-6">
    <el-card
      class="diagnosis-shell-card glass-card rounded-[34px]"
      :body-style="{ padding: '24px' }"
    >
      <h2 class="text-lg font-bold mb-4 text-ui-text flex items-center">
        <svg
          class="w-5 h-5 mr-2 text-brand"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path
            stroke-linecap="round"
            stroke-linejoin="round"
            stroke-width="2"
            d="M12 6v6m0 0v6m0-6h6m-6 0H6"
          />
        </svg>
        新增监控
      </h2>

      <el-alert
        v-if="errorMessage"
        :title="errorMessage"
        type="error"
        show-icon
        :closable="true"
        @close="errorMessage = ''"
        class="mb-4"
      >
        <template #default>
          <div class="text-sm">
            {{ errorMessage }}
            <div class="mt-2 text-xs text-ui-subtext">
              请检查 IP、账号密码、日志路径和 sudo 权限后重试。
            </div>
          </div>
        </template>
      </el-alert>

      <el-form :model="config" label-position="top">
        <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
          <el-form-item
            class="mb-0"
            label="服务器 IP"
            prop="serverIp"
          >
            <template #label>
              <span class="block text-sm font-medium text-ui-subtext mb-1">
                服务器 IP
                <span class="text-xs text-ui-subtext font-normal ml-2">
                  (支持 IP:Port，默认 22)
                </span>
              </span>
            </template>
            <el-input
              v-model="config.serverIp"
              placeholder="输入服务器ip"
              clearable
            />
          </el-form-item>

          <el-form-item
            class="mb-0"
            label="SSH 用户名"
          >
            <el-input
              v-model="config.username"
              placeholder="请输入用户名"
              clearable
            />
          </el-form-item>

          <el-form-item
            class="mb-0"
            label="SSH 密码"
          >
            <el-input
              v-model="config.password"
              type="password"
              placeholder="请输入密码"
              show-password
            />
          </el-form-item>

          <el-form-item class="mb-0" label="日志提权">
            <div class="glass-subcard flex min-h-[40px] items-center justify-between px-4 py-3 w-full">
              <div>
                <div class="text-sm font-medium text-ui-text">使用 sudo 读取日志</div>
                <div class="text-xs text-ui-subtext">当前 SSH 用户需要具备 sudo 权限</div>
              </div>
              <el-switch
                v-model="config.useSudo"
                inline-prompt
                active-text="开"
                inactive-text="关"
                @change="handlePrivilegeToggle"
              />
            </div>
          </el-form-item>

          <el-form-item
            class="mb-0"
            label="组件名称"
            prop="component"
          >
            <el-input
              v-model="config.component"
              @blur="handleComponentChange"
              placeholder="如 MySQL"
              clearable
            />
          </el-form-item>

          <el-form-item class="mb-0" label="日志路径">
            <template #label>
              <label class="block text-sm font-medium text-ui-subtext mb-1 flex justify-between items-center">
                <span>日志路径 <span class="text-xs text-ui-subtext font-normal ml-2">(若为空则由ai自动识别)</span></span>
                <span
                  v-if="config.logPath"
                  :class="isVerified ? 'text-ui-success' : 'text-ui-warning'"
                  class="text-xs font-bold"
                >
                  {{ isVerified ? '已验证' : '识别中' }}
                </span>
                
              </label>
            </template>
            <div class="relative w-full">
              <el-input
                v-model="config.logPath"
                placeholder="留空自动探测"
                clearable
              />
              <div v-if="pathLoading" class="absolute right-3 top-3">
                <div class="animate-spin h-4 w-4 border-2 border-brand border-t-transparent rounded-full"></div>
              </div>
            </div>
          </el-form-item>
        </div>
      </el-form>

      <div class="mt-6 flex justify-end">
        <el-button
          type="primary"
          class="px-6"
          :loading="loading"
          :disabled="loading || !config.serverIp || !config.component"
          @click="handleAddConfig"
        >
          <span v-if="loading">保存中...</span>
          <span v-else>保存</span>
        </el-button>
      </div>
    </el-card>

    <el-card
      class="diagnosis-shell-card glass-card rounded-[34px]"
      :body-style="{ padding: '24px' }"
    >
      <h2 class="text-lg font-bold mb-4 text-ui-text flex items-center">
        <svg
          class="w-5 h-5 mr-2 text-brand"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path
            stroke-linecap="round"
            stroke-linejoin="round"
            stroke-width="2"
            d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2"
          />
        </svg>
        监控列表
      </h2>

      <div class="diagnosis-table-wrap overflow-x-auto">
        <el-table
          :data="monitorList"
          style="width: 100%"
          border
          stripe
          :header-cell-class-name="() => 'text-xs font-medium uppercase tracking-wider text-ui-subtext'"
          :cell-class-name="() => 'text-sm text-ui-text'"
        >
          <el-table-column
            prop="serverIp"
            label="服务器 IP"
            min-width="160"
          >
            <template #default="{ row }">
              <span class="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">
                {{ row.serverIp }}
              </span>
            </template>
          </el-table-column>

          <el-table-column
            prop="component"
            label="组件"
            min-width="120"
          >
            <template #default="{ row }">
              <span class="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                {{ row.component }}
              </span>
            </template>
          </el-table-column>

          <el-table-column
            prop="configValue"
            label="日志路径"
            min-width="220"
          >
            <template #default="{ row }">
              <span class="px-6 py-4 whitespace-nowrap text-sm text-gray-500 font-mono">
                {{ row.configValue }}
              </span>
            </template>
          </el-table-column>

          <el-table-column
            label="读取方式"
            min-width="120"
          >
            <template #default="{ row }">
              <span
                class="glass-chip px-2.5 py-1 text-xs leading-5 font-semibold"
                :class="row.useSudo ? 'border-amber-200/30 bg-amber-400/10 text-ui-warning' : 'border-sky-200/30 bg-sky-400/10 text-sky-700'"
              >
                {{ row.useSudo ? 'sudo' : '普通读取' }}
              </span>
            </template>
          </el-table-column>

          <el-table-column
            label="状态"
            min-width="120"
          >
            <template #default="{ row }">
              <span
                class="glass-chip px-2.5 py-1 text-xs leading-5 font-semibold"
                :class="getMonitorStatusClass(row.isEnabled)"
              >
                {{ Number(row.isEnabled) === 0 ? '已暂停' : '监控中' }}
              </span>
            </template>
          </el-table-column>

          <el-table-column
            label="操作"
            align="left"
            width="220"
          >
            <template #default="{ row }">
              <div class="flex items-center justify-end gap-3">
                <el-popconfirm
                  :title="Number(row.isEnabled) === 0 ? '确定要恢复该监控任务吗？' : '确定要暂停该监控任务吗？'"
                  confirm-button-text="确定"
                  cancel-button-text="取消"
                  @confirm="handleToggleStatus(row)"
                >
                  <template #reference>
                    <el-button
                      link
                      :type="Number(row.isEnabled) === 0 ? 'primary' : 'warning'"
                      class="glass-link-button text-sm font-medium"
                    >
                      {{ Number(row.isEnabled) === 0 ? '恢复检测' : '暂停检测' }}
                    </el-button>
                  </template>
                </el-popconfirm>

                <el-popconfirm
                  title="确定要删除该监控任务吗？"
                  confirm-button-text="确定"
                  cancel-button-text="取消"
                  @confirm="handleDelete(row.id)"
                >
                  <template #reference>
                    <el-button
                      link
                      type="danger"
                      class="glass-link-button text-sm font-medium"
                    >
                      删除
                    </el-button>
                  </template>
                </el-popconfirm>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div
          v-if="monitorList.length === 0"
          class="px-6 py-12 text-center text-ui-subtext text-sm"
        >
          暂无监控配置
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getLogPath, addConfig, listConfigs, deleteConfig, updateConfigStatus } from '../api/diagnosis'

const config = reactive({
  serverIp: '',
  component: '',
  logPath: '',
  username: '',
  password: '',
  useSudo: false,
})

const isVerified = ref(false)
const pathLoading = ref(false)
const loading = ref(false)
const monitorList = ref([])
const errorMessage = ref('')

const getMonitorStatusClass = isEnabled => (
  Number(isEnabled) === 0
    ? 'border-amber-200/30 bg-amber-400/10 text-ui-warning'
    : 'border-emerald-200/30 bg-emerald-400/10 text-ui-success'
)

const handleComponentChange = async () => {
  if (!config.serverIp || !config.component) return

  pathLoading.value = true
  errorMessage.value = ''
  try {
    const res = await getLogPath(
      config.serverIp,
      config.component,
      config.username,
      config.password,
      config.useSudo,
    )
    // request.js 已经把后端 { code, data } 解包成 data
    // 这里期望 res 为 { path: string, ... }
    if (res && res.path) {
      config.logPath = res.path
      isVerified.value = true
    }
  } catch (error) {
    console.error('Auto detect path failed', error)
    isVerified.value = false
    errorMessage.value = error?.message || '自动探测日志路径失败'
  } finally {
    pathLoading.value = false
  }
}

const handlePrivilegeToggle = () => {
  isVerified.value = false
  if (config.serverIp && config.component) {
    handleComponentChange()
  }
}

const handleAddConfig = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const payload = {
      serverIp: config.serverIp,
      component: config.component,
      configKey: 'error_log_path',
      configValue: config.logPath,
      isEnabled: 1,
      username: config.username,
      password: config.password,
      useSudo: config.useSudo,
    }
    await addConfig(payload)

    // 成功：清空表单并刷新列表
    config.serverIp = ''
    config.component = ''
    config.logPath = ''
    config.username = ''
    config.password = ''
    config.useSudo = false
    isVerified.value = false
    errorMessage.value = ''

    fetchConfigs()
  } catch (error) {
    console.error('Failed to add config', error)
    // 显示错误信息（如SSH认证失败），用户可重新输入账号密码
    errorMessage.value = error.message || '添加配置失败，请检查后重试'
  } finally {
    loading.value = false
  }
}

const fetchConfigs = async () => {
  try {
    const res = await listConfigs()
    // 经过 request.js 拦截后，如果后端为 { code, data: [...] }
    // 这里的 res 应该已经是数组
    if (Array.isArray(res)) {
      monitorList.value = res
    } else if (res && Array.isArray(res.data)) {
      // 兼容旧结构
      monitorList.value = res.data
    } else {
      monitorList.value = []
    }
  } catch (error) {
    console.error('Failed to list configs', error)
  }
}

const handleDelete = async id => {
  try {
    await deleteConfig(id)
    ElMessage.success('监控任务已删除')
    fetchConfigs()
  } catch (error) {
    console.error('Failed to delete config', error)
    ElMessage.error(error?.message || '删除监控任务失败')
  }
}

const handleToggleStatus = async row => {
  try {
    const nextEnabled = Number(row?.isEnabled) === 0 ? 1 : 0
    await updateConfigStatus(row.id, nextEnabled)
    ElMessage.success(nextEnabled === 1 ? '监控已恢复' : '监控已暂停')
    fetchConfigs()
  } catch (error) {
    console.error('Failed to update config status', error)
    ElMessage.error(error?.message || '更新监控状态失败')
  }
}

onMounted(() => {
  fetchConfigs()
})
</script>

<style scoped>
/* 已经移除了所有会导致画面发脏的覆盖背景，一切由全局 glass-card/glass-subcard 接管 */
.diagnosis-shell-card {
  border-radius: 34px !important;
}

.diagnosis-shell-card :deep(.el-card__body) {
  border-radius: inherit;
}

.diagnosis-table-wrap {
  overflow: hidden;
  border-radius: 28px;
}

.diagnosis-table-wrap :deep(.el-table) {
  border-radius: 28px;
}

.diagnosis-table-wrap :deep(.el-table__inner-wrapper) {
  border-radius: inherit;
  overflow: hidden;
}
</style>


