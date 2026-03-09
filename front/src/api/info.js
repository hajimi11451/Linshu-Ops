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

export function selectAllInfo(username) {
  return request({
    url: '/info/selectAllInfo',
    method: 'post',
    data: { username: username || getCurrentUsername() }
  })
}

export function selectInfo(params) {
  return request({
    url: '/info/selectInfo',
    method: 'post',
    data: {
      ...(params || {}),
      username: (params && params.username) || getCurrentUsername()
    }
  })
}

export function selectInfoById(id, username) {
  return request({
    url: '/info/selectInfo',
    method: 'post',
    data: {
      id: String(id || ''),
      username: username || getCurrentUsername()
    }
  })
}

export function insertProcess(data) {
  return request({
    url: '/info/insertProcess',
    method: 'post',
    data: {
      ...(data || {}),
      username: (data && data.username) || getCurrentUsername()
    }
  })
}

export function selectAllProcess(username) {
  return request({
    url: '/info/selectAllProcess',
    method: 'post',
    data: { username: username || getCurrentUsername() }
  })
}

export function selectProcess(params) {
  return request({
    url: '/info/selectProcess',
    method: 'post',
    data: {
      ...(params || {}),
      username: (params && params.username) || getCurrentUsername()
    }
  })
}

export function deleteAllInfo(username) {
  return request({
    url: '/info/deleteAllInfo',
    method: 'post',
    data: { username: username || getCurrentUsername() }
  })
}
