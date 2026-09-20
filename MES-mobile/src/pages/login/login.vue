<template>
  <view class="page">
    <RippleBg />
    <view class="server-bar" :style="{ paddingTop: statusBarHeight + 8 + 'px' }" @click="showServer = !showServer">
      <text class="server-label">服务器</text>
      <text class="server-value">{{ serverDisplay }}</text>
      <text class="server-gear">⚙️</text>
    </view>
    <view v-if="showServer" class="server-panel">
      <ServerConfigForm ref="serverFormRef" @saved="onServerSaved" />
    </view>
    <view class="inner">
      <view class="hero">
        <text class="title">欢迎登录进行使用!</text>
      </view>

      <view class="form">
        <CapsuleField v-model="account" placeholder="请输入您的账号" />
        <CapsuleField
          v-model="password"
          placeholder="请输入您的密码"
          is-password
          show-eye
        />
        <view class="btn" :class="{ disabled: loading }" @click="handleLogin">
          <text>{{ loading ? '登录中…' : '登录' }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getUserInfo, mobileLogin } from '@/api/auth.js'
import CapsuleField from '@/components/CapsuleField.vue'
import RippleBg from '@/components/RippleBg.vue'
import ServerConfigForm from '@/components/ServerConfigForm.vue'
import { clearSession, hasSession, saveSession, saveUserInfo } from '@/utils/authStorage.js'
import { getServerDisplay } from '@/utils/server.js'
import { toast } from '@/utils/ui.js'

const account = ref('')
const password = ref('')
const loading = ref(false)
const showServer = ref(false)
const serverFormRef = ref(null)
const serverDisplay = ref(getServerDisplay())
const statusBarHeight = ref(uni.getSystemInfoSync().statusBarHeight || 20)

onShow(async () => {
  serverDisplay.value = getServerDisplay()
  serverFormRef.value?.refresh?.()
  if (!hasSession()) {
    return
  }
  try {
    const info = await getUserInfo({ silent: true, skipExpireHandler: true })
    saveUserInfo(info)
    uni.reLaunch({ url: '/pages/home/home' })
  } catch {
    clearSession()
  }
})

function onServerSaved() {
  serverDisplay.value = getServerDisplay()
}

async function handleLogin() {
  if (loading.value) return
  if (!account.value.trim()) {
    toast('请输入账号')
    return
  }
  if (!password.value) {
    toast('请输入密码')
    return
  }
  loading.value = true
  try {
    clearSession()
    const res = await mobileLogin(account.value.trim(), password.value)
    saveSession(res)
    uni.reLaunch({ url: '/pages/home/home' })
  } catch {
    // http.js 已 toast
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: linear-gradient(180deg, #f3dff0 0%, #e4ecfb 28%, #ffffff 52%);
  position: relative;
  overflow: hidden;
}
.server-bar {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 12rpx 40rpx 8rpx;
  color: #4a4578;
  font-size: 24rpx;
}
.server-label { opacity: 0.7; }
.server-value { flex: 1; font-weight: 600; }
.server-gear { font-size: 28rpx; }
.server-panel {
  position: relative;
  z-index: 2;
  margin: 0 40rpx 12rpx;
  padding: 20rpx;
  background: rgba(255, 255, 255, 0.88);
  border-radius: 20rpx;
}
.inner {
  padding: 0 56rpx 80rpx;
  position: relative;
  z-index: 1;
}
.hero {
  margin-top: 48rpx;
  margin-bottom: 72rpx;
}
.title {
  display: block;
  font-size: 48rpx;
  font-weight: 800;
  color: #2a2870;
  letter-spacing: 1rpx;
  line-height: 1.4;
}
.form {
  margin-top: 20rpx;
}
.btn {
  margin-top: 18rpx;
  height: 96rpx;
  border-radius: 48rpx;
  background: #5c67f2;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 32rpx;
  font-weight: 700;
  box-shadow: 0 16rpx 32rpx rgba(92, 103, 242, 0.28);
}
.btn.disabled {
  opacity: 0.7;
}
</style>
