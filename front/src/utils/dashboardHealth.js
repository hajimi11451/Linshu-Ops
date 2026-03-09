const clamp = (value, min, max) => Math.min(max, Math.max(min, value))

const parseUsage = value => {
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) return 0
  return clamp(Number(parsed.toFixed(1)), 0, 100)
}

const toDate = value => {
  if (!value) return null

  if (Array.isArray(value)) {
    const [year, month, day, hour, minute, second] = value
    const date = new Date(
      year || 1970,
      (month || 1) - 1,
      day || 1,
      hour || 0,
      minute || 0,
      second || 0,
    )

    return Number.isNaN(date.getTime()) ? null : date
  }

  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

export const normalizeRiskLevel = level => {
  const value = String(level || '').trim()

  if (['高', '中', '低', '无'].includes(value)) return value

  const lowered = value.toLowerCase()

  if (/(high|critical|fatal|danger|error)/.test(lowered)) return '高'
  if (/(medium|warning|warn|moderate)/.test(lowered)) return '中'
  if (/(low|info|notice)/.test(lowered)) return '低'
  if (/(normal|none|ok|safe|healthy)/.test(lowered)) return '无'

  return '中'
}

export const getAlertClass = level => {
  const map = {
    高: 'bg-red-50 border-ui-error',
    中: 'bg-orange-50 border-ui-warning',
    低: 'bg-blue-50 border-brand',
    无: 'bg-gray-50 border-gray-300',
  }

  return map[normalizeRiskLevel(level)] || map.无
}

export const getDateTimestamp = value => {
  const date = toDate(value)
  return date ? date.getTime() : 0
}

export const formatDate = value => {
  const date = toDate(value)
  return date ? date.toLocaleString('zh-CN') : ''
}

const summarizeRiskLevels = (infoList = []) => infoList.reduce((summary, item) => {
  const normalizedLevel = normalizeRiskLevel(item?.riskLevel)
  summary[normalizedLevel] += 1
  return summary
}, {
  高: 0,
  中: 0,
  低: 0,
  无: 0,
})

export const resolveSystemHealth = ({ currentInfo = {}, infoList = [] } = {}) => {
  const cpuUsage = parseUsage(currentInfo?.cpuUsage)
  const memUsage = parseUsage(currentInfo?.memUsage)
  const riskSummary = summarizeRiskLevels(infoList)

  const highRiskCount = riskSummary.高
  const mediumRiskCount = riskSummary.中
  const lowRiskCount = riskSummary.低
  const normalCount = riskSummary.无
  const activeAlertCount = highRiskCount + mediumRiskCount + lowRiskCount

  const highCpu = cpuUsage >= 70
  const highMem = memUsage >= 80
  const veryHighCpu = cpuUsage >= 85
  const veryHighMem = memUsage >= 85
  const hasHighRisk = highRiskCount > 0
  const hasNonCriticalAlerts = mediumRiskCount + lowRiskCount > 0

  let score = 100
  score -= highRiskCount * 18
  score -= mediumRiskCount * 8
  score -= lowRiskCount * 4
  score -= Math.max(cpuUsage - 30, 0) * 0.6
  score -= Math.max(memUsage - 35, 0) * 0.5

  if (veryHighCpu) score -= 8
  if (veryHighMem) score -= 8

  score = clamp(Math.round(score), 0, 100)

  let level = 'success'
  let label = '稳定'
  let description = '暂无告警，CPU 与内存占用保持在健康区间。'

  if (hasHighRisk || (veryHighCpu && veryHighMem)) {
    level = 'error'
    label = '高风险'
    description = hasHighRisk
      ? '检测到高风险告警，建议立即排查关键服务与异常事件。'
      : 'CPU 与内存占用都处于高位，系统正承受较大压力。'
    score = clamp(score, 12, 49)
  } else if (highCpu || highMem || hasNonCriticalAlerts) {
    level = 'warning'
    label = '需关注'
    description = hasNonCriticalAlerts
      ? '存在非高危告警或资源波动，建议持续观察系统状态。'
      : '资源占用偏高，建议关注热点进程、容量和服务响应。'
    score = clamp(score, 50, 79)
  } else {
    score = clamp(score, 80, 100)
  }

  const reasons = []

  if (hasHighRisk) reasons.push(`高风险告警 ${highRiskCount} 条，健康度直接进入红色区间`)
  if (hasNonCriticalAlerts) reasons.push(`需关注告警 ${mediumRiskCount + lowRiskCount} 条，健康度进入黄色区间`)
  if (cpuUsage > 0) reasons.push(`CPU 当前占用 ${cpuUsage}%`)
  if (memUsage > 0) reasons.push(`内存当前占用 ${memUsage}%`)
  if (!reasons.length) reasons.push('当前未发现异常信号，系统整体运行平稳')

  return {
    score,
    level,
    label,
    description,
    reasons,
    cpuUsage,
    memUsage,
    riskSummary,
    activeAlertCount,
    highRiskCount,
    mediumRiskCount,
    lowRiskCount,
    normalCount,
    totalLogsCount: infoList.length,
  }
}
