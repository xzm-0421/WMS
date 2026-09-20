const TOKEN_KEY = 'mes_mobile_token'
const REFRESH_TOKEN_KEY = 'mes_mobile_refresh_token'
const USER_KEY = 'mes_mobile_user'

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

export function getUser() {
  return uni.getStorageSync(USER_KEY) || null
}

export function saveUserInfo(userInfo) {
  uni.setStorageSync(USER_KEY, userInfo || {})
}

export function saveSession(loginResult) {
  uni.setStorageSync(TOKEN_KEY, loginResult.accessToken)
  if (loginResult.refreshToken) {
    uni.setStorageSync(REFRESH_TOKEN_KEY, loginResult.refreshToken)
  }
  if (loginResult.userInfo) {
    saveUserInfo(loginResult.userInfo)
  }
}

export function saveAccessToken(accessToken, refreshToken) {
  uni.setStorageSync(TOKEN_KEY, accessToken)
  if (refreshToken) {
    uni.setStorageSync(REFRESH_TOKEN_KEY, refreshToken)
  }
}

export function requireSession() {
  if (hasSession()) return true
  uni.reLaunch({ url: '/pages/login/login' })
  return false
}

/** 账号旁展示的角色名称：取 Web 角色管理中的角色名称，未分配则空 */
export function formatRoleNames(userInfo) {
  const names = userInfo?.roleNames
  if (!Array.isArray(names) || names.length === 0) {
    return ''
  }
  return names.map((name) => String(name || '').trim()).filter(Boolean).join('、')
}
