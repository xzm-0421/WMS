import defaultConfig from './config.js'
import { getBaseUrl } from './server.js'

let refreshing = null

async function tryRefreshToken() {
  const refreshToken = uni.getStorageSync('wms_refresh_token')
  if (!refreshToken) return false
  if (!refreshing) {
    refreshing = new Promise((resolve) => {
      uni.request({
        url: getBaseUrl() + '/auth/refresh',
        method: 'POST',
        data: { refreshToken },
        header: { 'Content-Type': 'application/json' },
        success(res) {
          const body = res.data
          if (body?.code === 200 && body.data?.accessToken) {
            uni.setStorageSync('wms_token', body.data.accessToken)
            if (body.data.refreshToken) {
              uni.setStorageSync('wms_refresh_token', body.data.refreshToken)
            }
            resolve(true)
          } else resolve(false)
        },
        fail: () => resolve(false),
        complete: () => { refreshing = null },
      })
    })
  }
  return refreshing
}

export function request(options) {
  return new Promise((resolve, reject) => {
    const doRequest = (retried) => {
      const token = uni.getStorageSync('wms_token')
      uni.request({
        url: getBaseUrl() + options.url,
        method: options.method || 'GET',
        data: options.data,
        header: {
          'Content-Type': 'application/json',
          Authorization: token ? `Bearer ${token}` : '',
          'X-Device-ID': defaultConfig.deviceNo,
          ...options.header,
        },
        success(res) {
          const body = res.data
          if (body && body.code === 200) {
            resolve(body.data)
          } else if (body?.code === 401 && !retried) {
            tryRefreshToken().then((ok) => {
              if (ok) doRequest(true)
              else {
                uni.showToast({ title: '登录已过期', icon: 'none' })
                uni.reLaunch({ url: '/pages/login/login' })
                reject(body)
              }
            })
          } else {
            if (!options.silent) {
              uni.showToast({ title: body?.message || '请求失败', icon: 'none' })
            }
            reject(body)
          }
        },
        fail(err) {
          uni.showToast({ title: '网络错误', icon: 'none' })
          reject(err)
        },
      })
    }
    doRequest(false)
  })
}

export function withDevice(data = {}) {
  return { ...data, deviceNo: defaultConfig.deviceNo }
}

export default request
