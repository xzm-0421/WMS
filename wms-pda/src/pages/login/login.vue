<template>
  <view class="login-page">
    <view class="server-bar" @click="goServerSettings">
      <text class="server-label">服务器</text>
      <text class="server-value">{{ serverDisplay }}</text>
      <text class="server-gear">⚙️</text>
    </view>

    <view class="card">
      <text class="title">WMS PDA</text>
      <text class="subtitle">仓储作业终端</text>
      <input v-model="username" class="input" placeholder="工号" />
      <input v-model="password" class="input" password placeholder="密码" />
      <button class="btn" :loading="loading" @click="handleLogin">登录</button>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { mobileLogin } from '@/api/mobile.js'
import { getServerDisplay } from '@/utils/server.js'

const username = ref('admin')
const password = ref('123456')
const loading = ref(false)
const serverDisplay = ref(getServerDisplay())

function refreshServerDisplay() {
  serverDisplay.value = getServerDisplay()
}

function goServerSettings() {
  uni.navigateTo({ url: '/pages/settings/settings?from=login' })
}

async function handleLogin() {
  if (!username.value || !password.value) {
    uni.showToast({ title: '请输入账号密码', icon: 'none' })
    return
  }
  loading.value = true
  try {
    const res = await mobileLogin(username.value, password.value)
    uni.setStorageSync('wms_token', res.accessToken)
    if (res.refreshToken) uni.setStorageSync('wms_refresh_token', res.refreshToken)
    uni.setStorageSync('wms_user', res.userInfo)
    uni.reLaunch({ url: '/pages/index/index' })
  } catch {
    // http.js 已 toast
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  refreshServerDisplay()
  if (uni.getStorageSync('wms_token')) {
    uni.reLaunch({ url: '/pages/index/index' })
  }
})

onShow(refreshServerDisplay)
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #1d4ed8;
  padding: 24rpx;
  box-sizing: border-box;
}
.server-bar {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 24rpx 32rpx;
  padding-top: calc(24rpx + env(safe-area-inset-top));
  background: rgba(0,0,0,0.15);
  color: #fff;
  font-size: 24rpx;
}
.server-label { opacity: 0.8; }
.server-value { flex: 1; font-weight: 500; }
.server-gear { font-size: 32rpx; }
.card {
  width: 85%;
  max-width: 640rpx;
  background: #fff;
  border-radius: 16rpx;
  padding: 48rpx;
  margin-top: 80rpx;
}
.title {
  display: block;
  text-align: center;
  font-size: 40rpx;
  font-weight: bold;
}
.subtitle {
  display: block;
  text-align: center;
  color: #94a3b8;
  font-size: 24rpx;
  margin: 8rpx 0 40rpx;
}
.input {
  border: 1px solid #e2e8f0;
  border-radius: 8rpx;
  padding: 20rpx;
  margin-bottom: 24rpx;
}
.btn {
  background: #1d4ed8;
  color: #fff;
  border-radius: 8rpx;
}
</style>
