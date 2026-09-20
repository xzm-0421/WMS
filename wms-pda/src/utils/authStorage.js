/** 清除本地登录态（换服务器 / Token 失效时调用） */
export function clearSession() {
  uni.removeStorageSync('wms_token')
  uni.removeStorageSync('wms_refresh_token')
  uni.removeStorageSync('wms_user')
}

export function hasSession() {
  return !!uni.getStorageSync('wms_token')
}
