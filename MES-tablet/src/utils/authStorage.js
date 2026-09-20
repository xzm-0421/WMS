const TOKEN_KEY = 'mes_tablet_token'
const REFRESH_TOKEN_KEY = 'mes_tablet_refresh_token'
const USER_KEY = 'mes_tablet_user'

/** 清除本地登录态（换服务器 / Token 失效时调用） */
export function clearSession() {
  uni.removeStorageSync(TOKEN_KEY)
  uni.removeStorageSync(REFRESH_TOKEN_KEY)
  uni.removeStorageSync(USER_KEY)
}

export function hasSession() {
  return !!uni.getStorageSync(TOKEN_KEY)
}

export function getToken() {
  return uni.getStorageSync(TOKEN_KEY) || ''
}

export function getRefreshToken() {
  return uni.getStorageSync(REFRESH_TOKEN_KEY) || ''
}

export function saveSession(loginResult) {
  uni.setStorageSync(TOKEN_KEY, loginResult.accessToken)
  if (loginResult.refreshToken) {
    uni.setStorageSync(REFRESH_TOKEN_KEY, loginResult.refreshToken)
  }
  if (loginResult.userInfo) {
    uni.setStorageSync(USER_KEY, loginResult.userInfo)
  }
}

export function saveAccessToken(accessToken, refreshToken) {
  uni.setStorageSync(TOKEN_KEY, accessToken)
  if (refreshToken) {
    uni.setStorageSync(REFRESH_TOKEN_KEY, refreshToken)
  }
}
