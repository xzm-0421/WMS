<template>
  <view class="login-page">
    <view class="server-bar" @click="goServerSettings">
      <text class="server-label">服务器</text>
      <text class="server-value">{{ serverDisplay }}</text>
      <text class="server-gear">⚙️</text>
    </view>

    <view class="card">
      <text class="title">MES-tablet</text>
      <text class="subtitle">现场作业端（独立于 PDA / 管理后台）</text>
      <input v-model="username" class="input" placeholder="工号" />
      <input v-model="password" class="input" password placeholder="密码" />
      <button class="btn" :loading="loading" @click="handleLogin">登录</button>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { mobileLogin } from '@/api/auth.js'
import { hasSession, saveSession } from '@/utils/authStorage.js'
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
    saveSession(res)
    uni.reLaunch({ url: '/pages/station/station' })
  } catch {
    // http.js 已 toast
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  refreshServerDisplay()
  if (hasSession()) {
    uni.reLaunch({ url: '/pages/station/station' })
  }
})

onShow(refreshServerDisplay)
</script>

<style scoped>
.login-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #0f766e;
  padding: 24px;
  box-sizing: border-box;
}
.server-bar {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 24px;
  padding-top: calc(16px + env(safe-area-inset-top));
  background: rgba(0, 0, 0, 0.15);
  color: #fff;
  font-size: 14px;
}
.server-label { opacity: 0.8; }
.server-value { flex: 1; font-weight: 500; }
.server-gear { font-size: 18px; }
.card {
  width: 520px;
  max-width: 86%;
  background: #fff;
  border-radius: 12px;
  padding: 32px;
}
.title {
  display: block;
  text-align: center;
  font-size: 26px;
  font-weight: bold;
}
.subtitle {
  display: block;
  text-align: center;
  color: #94a3b8;
  font-size: 14px;
  margin: 8px 0 28px;
}
.input {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 12px 14px;
  margin-bottom: 16px;
  font-size: 16px;
}
.btn {
  background: #0f766e;
  color: #fff;
  border-radius: 8px;
}
</style>
