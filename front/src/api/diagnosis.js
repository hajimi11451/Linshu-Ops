import request from '../utils/request'

function getCurrentUsername() {
  try {
    const raw = localStorage.getItem('user')
    if (!raw) return ''
    const user = JSON.parse(raw)
    return user?.username || ''
  } catch (e) {
    return ''
  }
}

export function getLogPath(serverIp, component, username, password) {
  return request({
    url: '/diagnosis/logPath',
    method: 'get',
    params: { serverIp, component, username, password }
  })
}

export function executeDiagnosis(data) {
  return request({
    url: '/diagnosis/execute',
    method: 'post',
    data
  })
}

export function chatWithAi(query) {
  return request({
    url: '/diagnosis/ai/chat',
    method: 'post',
    data: { query }
  })
}

export function connectServer(serverInfo) {
  return request({
    url: '/diagnosis/server/connect',
    method: 'post',
    data: serverInfo
  })
}

export function addConfig(data) {
  return request({
    url: '/diagnosis/config/add',
    method: 'post',
    data: {
      ...(data || {}),
      appUsername: (data && data.appUsername) || getCurrentUsername()
    }
  })
}

export function addServerMonitor(data) {
  return request({
    url: '/diagnosis/server-monitor/add',
    method: 'post',
    data: {
      ...(data || {}),
      appUsername: (data && data.appUsername) || getCurrentUsername()
    }
  })
}

export function listConfigs(username) {
  return request({
    url: '/diagnosis/config/list',
    method: 'get',
    params: {
      username: username || getCurrentUsername()
    }
  })
}

export function deleteConfig(id, username) {
  return request({
    url: '/diagnosis/config/delete',
    method: 'post',
    data: {
      id,
      username: username || getCurrentUsername()
    }
  })
}
