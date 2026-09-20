import defaultConfig from './config.js'
import { clearSession, getRefreshToken, getToken, saveAccessToken } from './authStorage.js'
import { getBaseUrl } from './server.js'

let refreshing = null
let loggingOut = false

function forceLogout(message = '登录已过期') {
  if (loggingOut) return
  loggingOut = true
  clearSession()
  uni.showToast({ title: message, icon: 'none' })
  uni.reLaunch({ url: '/pages/login/login' })
  setTimeout(() => {
    loggingOut = false
  }, 2000)
}

async function tryRefreshToken() {
  const refreshToken = getRefreshToken()
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
            saveAccessToken(body.data.accessToken, body.data.refreshToken)
            resolve(true)
          } else {
            resolve(false)
          }
        },
        fail: () => resolve(false),
        complete: () => {
          refreshing = null
        },
      })
    })
  }
  return refreshing
}

export function request(options) {
  return new Promise((resolve, reject) => {
    const doRequest = (retried) => {
      const token = getToken()
      uni.request({
        url: getBaseUrl() + options.url,
        method: options.method || 'GET',
        data: options.data,
        timeout: options.timeout || 60000,
        header: {
          'Content-Type': 'application/json',
          Authorization: token ? `Bearer ${token}` : '',
          'X-Device-ID': defaultConfig.deviceNo,
          ...options.header,
        },
        success(res) {
          const body = res.data
          const unauthorized = body?.code === 401 || res.statusCode === 401
          if (body && body.code === 200) {
            resolve(body.data)
          } else if (unauthorized && !retried) {
            tryRefreshToken().then((ok) => {
              if (ok) {
                doRequest(true)
              } else {
                forceLogout('登录已过期，请重新登录')
                reject(body || { code: 401, message: '登录已过期' })
              }
            })
          } else if (unauthorized) {
            forceLogout('登录已过期，请重新登录')
            reject(body || { code: 401, message: '登录已过期' })
          } else {
            if (!options.silent) {
              uni.showToast({ title: body?.message || '请求失败', icon: 'none' })
            }
            reject(body)
          }
        },
        fail(err) {
          if (!options.silent) {
            uni.showToast({ title: '网络错误', icon: 'none' })
          }
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
