import axios from 'axios'

const request = axios.create({
  baseURL: '/', // 去除 /api 前缀，直接使用根路径配合 Vite 代理
  timeout: 10000
})

// 响应拦截器
request.interceptors.response.use(
  response => {
    const res = response.data
    // 如果返回的是原始字符串或非对象结构（虽然现在后端已统一）
    if (res && typeof res === 'object' && 'code' in res) {
      if (res.code !== 200) {
        console.error('API Error:', res.msg || 'Unknown Error')
        // 对于400等错误，返回包含完整错误信息的Error对象
        const error = new Error(res.msg || 'Unknown Error')
        error.code = res.code
        return Promise.reject(error)
      }
      // 统一返回 data 部分，如果没用 data 则返回整个 res (包含 msg 等)
      return res.data !== undefined ? res.data : res
    }
    return res
  },
  error => {
    // 处理HTTP错误（如400, 500等）
    if (error.response && error.response.data) {
      const res = error.response.data
      if (res && typeof res === 'object' && 'msg' in res) {
        const err = new Error(res.msg || '请求失败')
        err.code = res.code || error.response.status
        console.error('Request Error:', err.message)
        return Promise.reject(err)
      }
    }
    if (error.request && !error.response) {
      const err = new Error('无法连接后端服务，请确认接口已启动')
      err.code = 'NETWORK_ERROR'
      console.error('Request Error:', err.message)
      return Promise.reject(err)
    }
    console.error('Request Error:', error)
    return Promise.reject(error)
  }
)

export default request
