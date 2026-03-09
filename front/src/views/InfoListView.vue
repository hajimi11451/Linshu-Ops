<template>
  <div>
    <div class="flex items-center justify-between mb-6">
      <div class="flex items-center gap-3">
        <h2 class="text-xl font-bold text-ui-text">全部信息与告警</h2>
        <span class="text-sm text-ui-subtext">共 {{ total }} 条记录</span>
      </div>
      <div class="flex items-center gap-2">
        <el-button @click="goDashboard">退出并返回总览</el-button>
        <el-button
          type="danger"
          :loading="clearing"
          :disabled="loading || total === 0 || clearing"
          @click="handleClearAll"
        >
          删除我的历史告警
        </el-button>
      </div>
    </div>

    <el-card
      class="bg-white rounded-[8px] shadow-sm border border-ui-border"
      :body-style="{ padding: '0' }"
    >
      <el-table
        :data="paginatedList"
        style="width: 100%"
        border
        stripe
        v-loading="loading"
      >
        <el-table-column prop="createdAt" label="时间" min-width="180">
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
            {{ row.errorSummary || '-' }}
          </template>
        </el-table-column>

        <el-table-column
          prop="analysisResult"
          label="遇到的问题"
          min-width="240"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.analysisResult || '-' }}
          </template>
        </el-table-column>

        <el-table-column
          prop="suggestedActions"
          label="建议处理方式"
          min-width="240"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ formatSuggestedActions(row.suggestedActions) }}
          </template>
        </el-table-column>

        <el-table-column label="操作" min-width="140" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="goDetail(row)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div
        v-if="!loading && infoList.length === 0"
        class="py-10 text-center text-sm text-ui-subtext"
      >
        暂无告警或信息记录
      </div>

      <div v-if="total > 0" class="flex justify-end py-4 px-4 border-t border-ui-border">
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
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { selectAllInfo, deleteAllInfo } from '../api/info'

const router = useRouter()
const infoList = ref([])
const loading = ref(false)
const clearing = ref(false)
const pageSize = 10
const currentPage = ref(1)

const sortedList = computed(() => {
  const list = [...infoList.value]
  return list.sort((a, b) => parseDateToTime(b.createdAt) - parseDateToTime(a.createdAt))
})

const total = computed(() => sortedList.value.length)

const paginatedList = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return sortedList.value.slice(start, start + pageSize)
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
        type: 'warning'
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

onMounted(() => {
  fetchAllInfo()
})
</script>
