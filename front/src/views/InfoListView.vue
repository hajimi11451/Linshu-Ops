<template>
  <div class="workspace-cool-glass mx-auto max-w-7xl space-y-6">
    <div class="flex items-center justify-between mb-6">
      <div class="flex items-center gap-3">
        <h2 class="text-xl font-bold text-ui-text">告警记录</h2>
        <span class="text-sm text-ui-subtext">{{ recordSummaryText }}</span>
      </div>
      <div class="flex items-center gap-2">
        <el-button @click="goDashboard">回到总览</el-button>
        <el-button
          type="danger"
          :loading="clearing"
          :disabled="loading || overallTotal === 0 || clearing"
          @click="handleClearAll"
        >
          清空记录
        </el-button>
      </div>
    </div>

    <el-card
      class="alert-shell-card glass-card rounded-[34px]"
      :body-style="{ padding: '20px' }"
    >
      <div class="grid grid-cols-1 gap-4 md:grid-cols-[minmax(0,320px)_auto] md:items-center">
        <div>
          <!-- <div class="mb-2 text-sm font-medium text-ui-text">服务器 IP 筛选</div> -->
          <el-select
            v-model="filters.serverIp"
            class="w-full"
            clearable
            filterable
            :loading="configLoading"
            placeholder="选择服务器 IP"
            no-data-text="当前用户暂无可选服务器 IP"
          >
            <el-option
              v-for="serverIp in serverIpOptions"
              :key="serverIp"
              :label="serverIp"
              :value="serverIp"
            />
          </el-select>
          <!-- <div class="mt-2 text-xs text-ui-subtext">
            可选项来自当前用户在 componentconfig 中配置的服务器。
          </div> -->
        </div>

      <div class="flex items-center gap-2 md:justify-end">
        <el-button :disabled="!filters.serverIp" @click="resetFilters">
          重置筛选
        </el-button>
        <el-button
          type="warning"
          :loading="deletingServerIp"
          :disabled="!filters.serverIp || deletingServerIp || loading || clearing"
          @click="handleDeleteServerIp"
        >
          删除该 IP 告警
        </el-button>
        <el-button type="primary" :loading="loading || configLoading" @click="refreshPage">
          刷新
        </el-button>
      </div>
      </div>
    </el-card>

    <el-card
      class="alert-shell-card glass-card rounded-[34px]"
      :body-style="{ padding: '0' }"
    >
      <div class="alert-table-wrap overflow-hidden rounded-[30px]">
        <el-table
          :data="paginatedList"
          style="width: 100%"
          border
          stripe
          v-loading="loading"
        >
          <el-table-column prop="createdAt" label="时间" min-width="180" >
            <template #default="{ row }">
              {{ formatDate(row.createdAt) }}
            </template>
          </el-table-column>

          <el-table-column prop="serverIp" label="服务器 IP" min-width="140" />
          <el-table-column prop="component" label="组件" min-width="120" />

          <el-table-column prop="riskLevel" label="风险等级" min-width="120">
            <template #default="{ row }">
              <el-tag :type="getTagType(row.riskLevel)" effect="light" size="small">
                {{ formatRiskLevel(row.riskLevel) }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column
            prop="errorSummary"
            label="问题摘要"
            min-width="160"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              <div class="max-w-full overflow-hidden text-ellipsis whitespace-nowrap text-ui-text">
                {{ compactAlertText(row.errorSummary, 24) }}
              </div>
            </template>
          </el-table-column>

          <el-table-column
            prop="analysisResult"
            label="问题详情"
            min-width="200"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              <div class="max-w-full overflow-hidden text-ellipsis whitespace-nowrap text-ui-text">
                {{ compactAlertText(row.analysisResult, 36) }}
              </div>
            </template>
          </el-table-column>

          <el-table-column
            prop="suggestedActions"
            label="处理建议"
            min-width="200"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              <div class="max-w-full overflow-hidden text-ellipsis whitespace-nowrap text-ui-text">
                {{ compactAlertText(formatSuggestedActions(row.suggestedActions), 32) }}
              </div>
            </template>
          </el-table-column>

          <el-table-column label="操作" min-width="140" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link class="glass-link-button text-sm font-medium" @click="goDetail(row)">
                查看详情
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      

      <div v-if="total > 0" class="glass-table-footer flex justify-end rounded-none border-x-0 border-b-0 px-4 py-4">
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="total"
          layout="total, prev, pager, next"
          background
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { selectAllInfo, deleteAllInfo, deleteInfoByServerIp } from '../api/info'
import { listConfigs } from '../api/diagnosis'

const router = useRouter()
const infoList = ref([])
const loading = ref(false)
const clearing = ref(false)
const deletingServerIp = ref(false)
const configLoading = ref(false)
const pageSize = 10
const currentPage = ref(1)
const componentConfigs = ref([])
const filters = ref({
  serverIp: '',
})

const sortedList = computed(() => {
  const list = [...infoList.value]
  return list.sort((a, b) => parseDateToTime(b.createdAt) - parseDateToTime(a.createdAt))
})

const serverIpOptions = computed(() => {
  const uniqueServerIps = new Set()

  componentConfigs.value.forEach(item => {
    const serverIp = String(item?.serverIp || '').trim()
    if (serverIp) {
      uniqueServerIps.add(serverIp)
    }
  })

  return Array.from(uniqueServerIps).sort((a, b) => a.localeCompare(b))
})

const filteredList = computed(() => {
  const selectedServerIp = String(filters.value.serverIp || '').trim()
  if (!selectedServerIp) {
    return sortedList.value
  }

  return sortedList.value.filter(item => String(item?.serverIp || '').trim() === selectedServerIp)
})

const total = computed(() => filteredList.value.length)

const overallTotal = computed(() => sortedList.value.length)

const recordSummaryText = computed(() => (
  filters.value.serverIp
    ? `筛选后 ${total.value} / ${overallTotal.value} 条记录`
    : `共 ${overallTotal.value} 条记录`
))

const paginatedList = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return filteredList.value.slice(start, start + pageSize)
})

function parseDateToTime(value) {
  if (value == null) return 0
  if (Array.isArray(value)) {
    const [y, m, d, h, min, s] = value
    return new Date(y, (m || 1) - 1, d || 1, h || 0, min || 0, s || 0).getTime()
  }
  return new Date(value).getTime()
}

function formatDate(value) {
  if (value == null) return ''
  if (Array.isArray(value)) {
    const [y, m, d, h, min, s] = value
    return new Date(y, (m || 1) - 1, d || 1, h || 0, min || 0, s || 0).toLocaleString()
  }
  return new Date(value).toLocaleString()
}

function getTagType(level) {
  const normalizedLevel = formatRiskLevel(level)
  if (normalizedLevel === '高') return 'danger'
  if (normalizedLevel === '中') return 'warning'
  if (normalizedLevel === '低') return 'info'
  return ''
}

function formatRiskLevel(level) {
  const value = String(level || '').trim()
  if (['高', '中', '低', '无'].includes(value)) return value
  const lowered = value.toLowerCase()
  if (lowered.includes('high') || lowered.includes('critical') || lowered.includes('error')) return '高'
  if (lowered.includes('medium') || lowered.includes('warning') || lowered.includes('warn')) return '中'
  if (lowered.includes('low') || lowered.includes('info')) return '低'
  return '无'
}

function formatSuggestedActions(value) {
  const text = String(value || '').trim()
  if (!text || text === '[]') return '-'
  try {
    const parsed = JSON.parse(text)
    if (Array.isArray(parsed) && parsed.length > 0) {
      return parsed.join('；')
    }
  } catch {
  }
  return text.replace(/[\r\n]+/g, '；')
}

function compactAlertText(value, maxLength = 32) {
  const text = String(value || '').replace(/\s+/g, ' ').trim()
  if (!text) return '-'
  return text.length > maxLength ? `${text.slice(0, maxLength)}...` : text
}

async function fetchAllInfo() {
  loading.value = true
  try {
    const res = await selectAllInfo()
    if (Array.isArray(res)) {
      infoList.value = res
    } else if (res && Array.isArray(res.data)) {
      infoList.value = res.data
    } else {
      infoList.value = []
    }
    currentPage.value = 1
  } catch (e) {
    console.error('Failed to fetch all info', e)
  } finally {
    loading.value = false
  }
}

async function fetchServerIpOptions() {
  configLoading.value = true
  try {
    const res = await listConfigs()
    if (Array.isArray(res)) {
      componentConfigs.value = res
    } else if (res && Array.isArray(res.data)) {
      componentConfigs.value = res.data
    } else {
      componentConfigs.value = []
    }
  } catch (error) {
    componentConfigs.value = []
    ElMessage.error(error?.message || '获取服务器 IP 筛选项失败')
  } finally {
    configLoading.value = false
  }
}

async function refreshPage() {
  await Promise.allSettled([
    fetchAllInfo(),
    fetchServerIpOptions(),
  ])
}

function resetFilters() {
  filters.value.serverIp = ''
  currentPage.value = 1
}

function goDashboard() {
  router.push('/dashboard')
}

function goDetail(row) {
  if (!row?.id) {
    ElMessage.warning('当前记录缺少详情 ID')
    return
  }
  router.push({ name: 'info-detail', params: { id: row.id } })
}

async function handleClearAll() {
  try {
    await ElMessageBox.confirm(
      '该操作将删除当前登录用户的全部历史告警与信息记录，是否继续？',
      '确认删除',
      {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        type: 'warning',
        modalClass: 'keep-bright-overlay',
      }
    )
  } catch {
    return
  }

  clearing.value = true
  try {
    const deleted = await deleteAllInfo()
    ElMessage.success(`删除完成，共删除 ${deleted || 0} 条记录`)
    await fetchAllInfo()
  } catch (e) {
    console.error('Failed to delete all info', e)
    ElMessage.error(e?.message || '删除失败')
  } finally {
    clearing.value = false
  }
}

async function handleDeleteServerIp() {
  const selectedServerIp = String(filters.value.serverIp || '').trim()
  if (!selectedServerIp) {
    return
  }

  try {
    await ElMessageBox.confirm(
      `该操作将删除服务器 ${selectedServerIp} 的全部告警记录，是否继续？`,
      '确认删除',
      {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        type: 'warning',
        modalClass: 'keep-bright-overlay',
      }
    )
  } catch {
    return
  }

  deletingServerIp.value = true
  try {
    const deleted = await deleteInfoByServerIp(selectedServerIp)
    ElMessage.success(`已删除 ${selectedServerIp} 的 ${deleted || 0} 条告警记录`)
    await fetchAllInfo()
  } catch (error) {
    console.error('Failed to delete info by server ip', error)
    ElMessage.error(error?.message || '按服务器 IP 删除告警失败')
  } finally {
    deletingServerIp.value = false
  }
}

watch(
  () => filters.value.serverIp,
  () => {
    currentPage.value = 1
  }
)

watch(serverIpOptions, options => {
  if (filters.value.serverIp && !options.includes(filters.value.serverIp)) {
    filters.value.serverIp = ''
  }
})

onMounted(() => {
  refreshPage()
})
</script>

<style scoped>
.alert-shell-card {
  border-radius: 34px !important;
}

.alert-shell-card :deep(.el-card__body) {
  border-radius: inherit;
}

.alert-table-wrap {
  border-radius: 30px;
}

.alert-table-wrap :deep(.el-table) {
  border-radius: 30px;
}

.alert-table-wrap :deep(.el-table__inner-wrapper) {
  border-radius: inherit;
  overflow: hidden;
}
</style>
