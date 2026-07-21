import defaultConfig from './config.js'

const STORAGE_KEY = 'wms_server_base_url'
const API_SUFFIX = '/api/v1'

/** 读取当前 API 基址（登录前后均可用） */
export function getBaseUrl() {
  const saved = uni.getStorageSync(STORAGE_KEY)
  if (saved) return normalizeBaseUrl(saved)
  return defaultConfig.baseUrl
}

/** 保存服务器地址，返回规范化后的值 */
export function setBaseUrl(input) {
  const normalized = normalizeBaseUrl(input)
  uni.setStorageSync(STORAGE_KEY, normalized)
  return normalized
}

/** 清除自定义配置，恢复默认 */
export function resetBaseUrl() {
  uni.removeStorageSync(STORAGE_KEY)
  return defaultConfig.baseUrl
}

/** 是否使用用户自定义服务器 */
export function hasCustomServer() {
  return !!uni.getStorageSync(STORAGE_KEY)
}

/** 规范化：补全协议与 /api/v1 后缀 */
export function normalizeBaseUrl(input) {
  if (!input || !String(input).trim()) {
    return defaultConfig.baseUrl
  }
  let url = String(input).trim().replace(/\/+$/, '')
  // H5 开发代理路径
  if (url.startsWith('/')) {
    return url.endsWith(API_SUFFIX) ? url : `${url}${API_SUFFIX}`.replace('//', '/')
  }
  if (!/^https?:\/\//i.test(url)) {
    url = `http://${url}`
  }
  if (!url.endsWith(API_SUFFIX)) {
    url = `${url}${API_SUFFIX}`
  }
  return url
}

/** 用于展示的简短地址 */
export function getServerDisplay() {
  const base = getBaseUrl()
  if (base.startsWith('/')) return '本地开发代理'
  try {
    const root = base.replace(API_SUFFIX, '')
    const u = new URL(root)
    return u.host
  } catch {
    return base
  }
}

/** 供输入框使用的服务器根地址（不含 /api/v1） */
export function getServerInputValue() {
  const base = getBaseUrl()
  if (base.startsWith('/')) return ''
  return base.replace(API_SUFFIX, '').replace(/\/+$/, '')
}

export default {
  getBaseUrl,
  setBaseUrl,
  resetBaseUrl,
  hasCustomServer,
  normalizeBaseUrl,
  getServerDisplay,
  getServerInputValue,
}
