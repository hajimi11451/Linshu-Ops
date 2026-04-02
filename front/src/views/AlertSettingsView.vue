<template>
  <div v-loading="loadingContact" class="workspace-cool-glass notification-page h-full min-h-0 w-full overflow-y-auto custom-scrollbar p-4 sm:p-5 lg:p-6">
    <div class="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
      <div>
        <h2 class="text-xl font-bold text-ui-text">通知邮箱</h2>
        <p class="mt-1 text-sm text-ui-subtext">
          配置告警接收邮箱并验证链路。
        </p>
      </div>

      <div class="flex flex-wrap gap-2">
        <el-tag :type="contactStatusType" effect="light">
          {{ contactStatusText }}
        </el-tag>
      </div>
    </div>

    

    <div class="notification-body-grid mt-6 min-h-0 flex-1">
      <section class="notification-form-panel glass-subcard h-full min-h-0 rounded-[30px] p-6">
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          @submit.prevent
        >
          <el-form-item label="当前用户">
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
              留空保存可清空当前邮箱。
            </div>
          </el-form-item>

          <el-form-item>
            <div class="flex flex-wrap gap-3">
              <el-button type="primary" :loading="saving" @click="handleSave">
                <el-icon class="mr-1"><Check /></el-icon>
                保存设置
              </el-button>
              <el-button class="mail-action-button" :loading="testing" @click="handleTestMail">
                <el-icon class="mr-1"><Promotion /></el-icon>
                测试邮件
              </el-button>
              <el-button class="mail-action-button" :disabled="!form.email" @click="handleClear">
                <el-icon class="mr-1"><Delete /></el-icon>
                清空输入
              </el-button>
              <el-button class="mail-action-button" :disabled="!savedEmail" @click="handleRestoreSaved">
                <el-icon class="mr-1"><RefreshLeft /></el-icon>
                恢复
              </el-button>
            </div>
          </el-form-item>
        </el-form>
        
        <div style="margin: 20px 0 0 10px">
        <p class="text-xl">注意：</p>
        <div style="margin-left:10px " class="mt-3 text-base text-ui-subtext">
        <p>同一问题连续两次高风险才会发送邮件，冷却 30 分钟。</p>
        <p>用户收到信息后请尽快检查服务器，本项目不能完美解决高风险问题</p>
        <p>如遇到难解决的问题，联系管理员解决</p>
        <p></p>
        </div>
        </div>
      </section>

      <section class="status-panel glass-subcard h-full min-h-0 rounded-[30px] p-5">
        <div class="mb-4 font-semibold text-ui-text">当前状态</div>

        <el-descriptions class="status-plain-descriptions" :column="1">
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
            {{ lastTestMessage || '尚未测试邮件' }}
          </el-descriptions-item>
        </el-descriptions>

        <el-alert
          class="mt-4"
          :type="savedEmail ? 'success' : 'warning'"
          :closable="false"
          show-icon
          :title="savedEmail ? '已配置邮箱，可发送通知。' : '未配置邮箱，当前不会自动通知。'"
        />
      </section>
    </div>
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
    ElMessage.error('未识别当前用户')
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
    ElMessage.error('未识别当前用户')
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
      ElMessage.error(error?.message || '测试邮件失败')
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

<style scoped>
.notification-page {
  display: flex;
  flex-direction: column;
}

.notification-body-grid {
  display: grid;
  flex: 1;
  min-height: 0;
  gap: 20px;
  align-items: stretch;
  grid-template-columns: minmax(0, 1.55fr) minmax(300px, 0.95fr);
}

.notification-form-panel {
  display: flex;
  min-height: 0;
  flex-direction: column;
}

.status-panel {
  display: flex;
  min-height: 0;
  flex-direction: column;
  min-width: 0;
  border: 1px solid rgba(255, 255, 255, 0.26);
}

.status-plain-descriptions :deep(.el-descriptions__body) {
  background: transparent;
  padding: 10px 10px 10px 20px;
}

.status-plain-descriptions :deep(.el-descriptions__table) {
  background: transparent;
}

.status-plain-descriptions :deep(.el-descriptions__cell) {
  background: transparent !important;
  border: 0 !important;
  box-shadow: none !important;
  padding: 8px 0;
}

.status-plain-descriptions :deep(.el-descriptions__label) {
  width: 88px;
  color: #5f6f87;
  font-weight: 500;
}

.status-plain-descriptions :deep(.el-descriptions__content) {
  color: #0f172a;
}

.status-plain-descriptions {
  flex: 1;
}

.mail-action-button {
  box-shadow:
    inset 0 0 0 1px rgba(255, 255, 255, 0.18),
    0 14px 26px -18px rgba(88, 110, 148, 0.22),
    0 0 0 1px rgba(176, 197, 228, 0.16);
}

.custom-scrollbar::-webkit-scrollbar {
  width: 6px;
}

.custom-scrollbar::-webkit-scrollbar-track {
  background: transparent;
}

.custom-scrollbar::-webkit-scrollbar-thumb {
  background-color: #c7d6ea;
  border-radius: 999px;
}

@media (max-width: 1023px) {
  .notification-body-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>

