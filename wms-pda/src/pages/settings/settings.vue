<template>
  <view class="page">
    <view class="section-card">
      <ServerConfigForm ref="serverFormRef" @saved="onServerSaved" />
    </view>

    <view v-if="isLoggedIn" class="section-card">
      <text class="section-title">账号安全</text>
      <text class="label">原密码</text>
      <input v-model="pwd.old" class="input" password placeholder="原密码" />
      <text class="label">新密码</text>
      <input v-model="pwd.new1" class="input" password placeholder="新密码" />
      <text class="label">确认新密码</text>
      <input v-model="pwd.new2" class="input" password placeholder="确认新密码" />
      <button class="btn-primary" @click="handleChangePwd">保存密码</button>
    </view>

    <view class="section-card">
      <text class="section-title">设备信息</text>
      <text class="info-row">设备编号: {{ deviceNo }}</text>
      <text class="info-row">应用版本: WMS PDA 1.0</text>
    </view>

    <button v-if="fromLogin" class="btn-back" @click="goBack">返回登录</button>
  </view>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import ServerConfigForm from '@/components/ServerConfigForm.vue'
import defaultConfig from '@/utils/config.js'
import { changePassword } from '@/api/mobile.js'

const serverFormRef = ref(null)
const fromLogin = ref(false)
const pwd = reactive({ old: '', new1: '', new2: '' })
const deviceNo = defaultConfig.deviceNo

const isLoggedIn = computed(() => !!uni.getStorageSync('wms_token'))

onLoad((options) => {
  fromLogin.value = options?.from === 'login'
})

onShow(() => {
  serverFormRef.value?.refresh?.()
})

function onServerSaved() {
  // 服务器配置已写入 localStorage，登录页与个人主页读取同一 key
}

async function handleChangePwd() {
  if (!pwd.old || !pwd.new1) {
    uni.showToast({ title: '请填写密码', icon: 'none' })
    return
  }
  if (pwd.new1 !== pwd.new2) {
    uni.showToast({ title: '两次密码不一致', icon: 'none' })
    return
  }
  await changePassword(pwd.old, pwd.new1)
  pwd.old = ''
  pwd.new1 = ''
  pwd.new2 = ''
  uni.showToast({ title: '密码已修改', icon: 'success' })
}

function goBack() {
  uni.navigateBack()
}
</script>

<style scoped>
.page { padding: 24rpx; }
.section-card {
  background: #fff;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 24rpx;
}
.section-title { font-weight: bold; font-size: 28rpx; margin-bottom: 16rpx; display: block; }
.label { display: block; color: #64748b; font-size: 24rpx; margin: 12rpx 0 8rpx; }
.input {
  border: 1px solid #e2e8f0;
  border-radius: 8rpx;
  padding: 16rpx;
  margin-bottom: 8rpx;
}
.btn-primary { background: #1d4ed8; color: #fff; margin-top: 16rpx; }
.info-row { display: block; color: #64748b; font-size: 26rpx; margin-top: 8rpx; }
.btn-back {
  background: #f1f5f9;
  color: #475569;
  margin-top: 16rpx;
}
</style>
