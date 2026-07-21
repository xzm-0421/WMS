import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const AUTH_PATHS = ['/auth/login', '/auth/captcha', '/auth/mobile/login', '/auth/refresh']

const request = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
})

function isAuthRequest(url?: string) {
  if (!url) return false
  return AUTH_PATHS.some((path) => url.includes(path))
}

function extractMessage(error: unknown): string {
  const err = error as {
    code?: string
    response?: { data?: { message?: string }; status?: number }
    message?: string
  }
  if (err.code === 'ECONNABORTED' || err.message?.includes('timeout')) {
    return '请求超时，请确认后端服务与数据库正常后重试'
  }
  if (!err.response && err.message === 'Network Error') {
    return '无法连接后端服务，请确认 wms-backend 已启动（默认端口 9980）'
  }
  return err.response?.data?.message || err.message || '网络错误'
}

request.interceptors.request.use((config) => {
  if (!isAuthRequest(config.url)) {
    const token = localStorage.getItem('wms_token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code !== 200) {
      const msg = res.message || '请求失败'
      if (!isAuthRequest(response.config?.url)) {
        ElMessage.error(msg)
      }
      return Promise.reject(new Error(msg))
    }
    return res.data
  },
  (error) => {
    const status = error.response?.status
    const message = extractMessage(error)

    if (status === 401 && !isAuthRequest(error.config?.url) && router.currentRoute.value.path !== '/login') {
      localStorage.removeItem('wms_token')
      localStorage.removeItem('wms_refresh_token')
      router.push('/login')
    }

    if (!isAuthRequest(error.config?.url)) {
      ElMessage.error(message)
    }
    return Promise.reject(error)
  }
)

export default request
