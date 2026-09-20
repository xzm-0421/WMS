import defaultConfig from './config.js'

const STORAGE_KEY = 'mes_mobile_server_base_url'
const API_SUFFIX = '/api/v1'

export function getBaseUrl() {
  const saved = uni.getStorageSync(STORAGE_KEY)
  if (saved) return normalizeBaseUrl(saved)
  return defaultConfig.baseUrl
}

export function setBaseUrl(input) {
  const normalized = normalizeBaseUrl(input)
  uni.setStorageSync(STORAGE_KEY, normalized)
  return normalized
}

export function resetBaseUrl() {
  uni.removeStorageSync(STORAGE_KEY)
  return defaultConfig.baseUrl
}

export function hasCustomServer() {
  return !!uni.getStorageSync(STORAGE_KEY)
}

export function normalizeBaseUrl(input) {
  if (!input || !String(input).trim()) {
    return defaultConfig.baseUrl
  }
  let url = String(input).trim().replace(/\/+$/, '')
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
