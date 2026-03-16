<template>
  <div class="mx-auto max-w-5xl space-y-6">
    <el-card v-loading="loadingContact" shadow="never" class="border border-ui-border">
      <template #header>
        <div class="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
          <div>
            <h2 class="text-xl font-bold text-ui-text">邮箱通知设置</h2>
            <p class="mt-1 text-sm text-ui-subtext">
              配置高风险告警邮件接收人，并快速验证通知链路是否可用。
            </p>
          </div>

          <div class="flex flex-wrap gap-2">
            <el-tag :type="contactStatusType" effect="light">
              {{ contactStatusText }}
            </el-tag>
            <el-tag type="danger" effect="plain">红色告警邮件</el-tag>
          </div>
        </div>
      </template>

      <el-alert
        title="同一问题连续两次高风险确认后才发送邮件，并带 30 分钟冷却时间。"
        type="info"
        :closable="false"
        show-icon
      />

      <el-row :gutter="20" class="mt-6">
        <el-col :xs="24" :lg="15">
          <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            label-position="top"
            @submit.prevent
          >
            <el-form-item label="当前登录用户">
              <el-input :model-value="username || '未登录'" disabled>
                <template #prefix>
                  <el-icon><User /></el-icon>
                </template>
              </el-input>
            </el-form-item>

            <el-form-item label="通知邮箱" prop="email">
              <el-input
                v-model="form.email"
                clearable
                placeholder="例如：ops-team@qq.com"
                @keyup.enter="handleSave"
              >
                <template #prefix>
                  <el-icon><Message /></el-icon>
                </template>
              </el-input>
              <div class="mt-2 text-xs text-ui-subtext">
                输入新邮箱后可直接保存；留空后点击“保存设置”会清空当前已保存的收件邮箱。
              </div>
            </el-form-item>

            <el-form-item>
              <div class="flex flex-wrap gap-3">
                <el-button type="primary" :loading="saving" @click="handleSave">
                  <el-icon class="mr-1"><Check /></el-icon>
                  保存设置
                </el-button>
                <el-button :loading="testing" @click="handleTestMail">
                  <el-icon class="mr-1"><Promotion /></el-icon>
                  发送测试邮件
                </el-button>
                <el-button :disabled="!form.email" @click="handleClear">
                  <el-icon class="mr-1"><Delete /></el-icon>
                  清空输入
                </el-button>
                <el-button :disabled="!savedEmail" @click="handleRestoreSaved">
                  <el-icon class="mr-1"><RefreshLeft /></el-icon>
                  恢复已保存
                </el-button>
              </div>
            </el-form-item>
          </el-form>
        </el-col>

        <el-col :xs="24" :lg="9">
          <el-card shadow="never" class="border border-ui-border bg-ui-bg">
            <template #header>
              <span class="font-semibold text-ui-text">当前配置</span>
            </template>

            <el-descriptions :column="1" border>
              <el-descriptions-item label="当前用户">
                {{ username || '未登录' }}
              </el-descriptions-item>
              <el-descriptions-item label="已保存邮箱">
                <span class="break-all">{{ savedEmail || '暂未保存' }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="本次发送对象">
                <span class="break-all">{{ effectiveEmail || '未指定' }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="最近测试状态">
                {{ lastTestMessage || '尚未发送测试邮件' }}
              </el-descriptions-item>
            </el-descriptions>

            <el-alert
              class="mt-4"
              :type="savedEmail ? 'success' : 'warning'"
              :closable="false"
              show-icon
              :title="savedEmail ? '已配置收件邮箱，系统可在触发条件满足时发出通知。' : '尚未配置收件邮箱，当前不会发送自动通知。'"
            />
          </el-card>
        </el-col>
      </el-row>
    </el-card>

   
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getAlertContact, saveAlertContact, sendAlertTestMail } from '../api/alert'

const formRef = ref(null)
const loadingContact = ref(false)
const saving = ref(false)
const testing = ref(false)
const savedEmail = ref('')
const lastTestMessage = ref('')

const form = reactive({
  email: '',
})

const rules = {
  email: [
    {
      validator: (_rule, value, callback) => {
        const text = String(value || '').trim()
        if (!text) {
          callback()
          return
        }

        const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
        if (!emailPattern.test(text)) {
          callback(new Error('请输入有效的邮箱地址'))
          return
        }

        callback()
      },
      trigger: ['blur', 'change'],
    },
  ],
}

const username = computed(() => {
  try {
    const raw = localStorage.getItem('user')
    if (!raw) return ''
    return JSON.parse(raw)?.username || ''
  } catch (error) {
    return ''
  }
})

const effectiveEmail = computed(() => {
  return String(form.email || '').trim() || savedEmail.value || ''
})

const contactStatusText = computed(() => (
  savedEmail.value ? '已配置收件邮箱' : '未配置收件邮箱'
))

const contactStatusType = computed(() => (
  savedEmail.value ? 'success' : 'info'
))

async function validateEmailFieldIfNeeded() {
  const text = String(form.email || '').trim()
  if (!text || !formRef.value) {
    return true
  }

  try {
    await formRef.value.validateField('email')
    return true
  } catch {
    return false
  }
}

async function loadContact() {
  if (!username.value) return

  loadingContact.value = true
  try {
    const data = await getAlertContact(username.value)
    savedEmail.value = String(data?.email || '').trim()
    form.email = savedEmail.value
  } catch (error) {
    ElMessage.error(error?.message || '读取紧急联系人邮箱失败')
  } finally {
    loadingContact.value = false
  }
}

async function handleSave() {
  if (!username.value) {
    ElMessage.error('未识别当前登录用户')
    return
  }

  const valid = await validateEmailFieldIfNeeded()
  if (!valid) {
    return
  }

  saving.value = true
  try {
    const data = await saveAlertContact({
      username: username.value,
      email: String(form.email || '').trim(),
    })
    savedEmail.value = String(data?.email || '').trim()
    form.email = savedEmail.value
    lastTestMessage.value = ''
    ElMessage.success(savedEmail.value ? '通知邮箱已保存' : '通知邮箱已清空')
  } catch (error) {
    ElMessage.error(error?.message || '保存通知邮箱失败')
  } finally {
    saving.value = false
  }
}

async function handleTestMail() {
  if (!username.value) {
    ElMessage.error('未识别当前登录用户')
    return
  }

  const targetEmail = effectiveEmail.value
  if (!targetEmail) {
    ElMessage.warning('请先输入邮箱，或先保存一个通知邮箱')
    return
  }

  const valid = await validateEmailFieldIfNeeded()
  if (!valid) {
    return
  }

  testing.value = true
  try {
    await sendAlertTestMail({
      username: username.value,
      email: String(form.email || '').trim(),
    })
    lastTestMessage.value = `测试邮件已发送到 ${targetEmail}`
    ElMessage.success('测试邮件已发送，请检查收件箱')
  } catch (error) {
    const message = String(error?.message || '')
    if (message.includes('timeout')) {
      lastTestMessage.value = `请求等待超时，邮件可能已发送到 ${targetEmail}`
      ElMessage.warning('请求等待超时，邮件可能已经发出，请检查收件箱')
    } else {
      ElMessage.error(error?.message || '发送测试邮件失败')
    }
  } finally {
    testing.value = false
  }
}

function handleClear() {
  form.email = ''
  formRef.value?.clearValidate('email')
}

function handleRestoreSaved() {
  form.email = savedEmail.value
  formRef.value?.clearValidate('email')
}

onMounted(() => {
  loadContact()
})
</script>
