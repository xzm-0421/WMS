<template>
  <view class="profile-panel">
    <!-- 顶部个人卡片 -->
    <view class="hero-card">
      <view class="settings-btn" @click="goSettings">
        <text class="settings-icon">⚙️</text>
      </view>
      <view class="avatar">
        <text class="avatar-text">{{ avatarLetter }}</text>
      </view>
      <text class="nickname">{{ user.realName || user.username || '未登录' }}</text>
      <text class="username">工号: {{ user.username || '-' }}</text>
      <view v-if="roleText" class="role-tag">{{ roleText }}</view>
    </view>

    <!-- 账号信息 -->
    <view class="info-section">
      <text class="section-title">账号信息</text>
      <view class="info-grid">
        <view class="info-item">
          <text class="info-label">用户ID</text>
          <text class="info-value">{{ user.userId || '-' }}</text>
        </view>
        <view class="info-item">
          <text class="info-label">绑定仓库</text>
          <text class="info-value">{{ user.warehouseCode || '全部' }}</text>
        </view>
        <view class="info-item">
          <text class="info-label">设备编号</text>
          <text class="info-value">{{ deviceNo }}</text>
        </view>
        <view class="info-item">
          <text class="info-label">离线队列</text>
          <text class="info-value">{{ queueCount }} 条</text>
        </view>
        <view class="info-item wide">
          <text class="info-label">当前服务器</text>
          <text class="info-value server">{{ serverDisplay }}</text>
        </view>
      </view>
    </view>

    <!-- 快捷操作 -->
    <view class="action-section">
      <view class="action-item" @click="handleSync">
        <text class="action-icon">🔄</text>
        <text>同步离线数据</text>
      </view>
      <view class="action-item" @click="goSettings">
        <text class="action-icon">⚙️</text>
        <text>系统设置</text>
      </view>
    </view>

    <button class="logout-btn" type="warn" @click="handleLogout">退出登录</button>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import defaultConfig from '@/utils/config.js'
import { getServerDisplay } from '@/utils/server.js'
import { getUserInfo, syncOfflineData } from '@/api/mobile.js'
import { getOfflineQueue } from '@/utils/offline.js'

const user = ref({})
const deviceNo = defaultConfig.deviceNo
const queueCount = ref(0)
const serverDisplay = ref(getServerDisplay())

const avatarLetter = computed(() => {
  const name = user.value.realName || user.value.username || '?'
  return name.charAt(0).toUpperCase()
})

const roleText = computed(() => {
  const roles = user.value.roles
  if (!roles?.length) return ''
  return Array.isArray(roles) ? roles.join(' · ') : roles
})

async function loadProfile() {
  queueCount.value = getOfflineQueue().length
  serverDisplay.value = getServerDisplay()
  try {
    const info = await getUserInfo()
    user.value = info
    uni.setStorageSync('wms_user', info)
  } catch {
    user.value = uni.getStorageSync('wms_user') || {}
  }
}

function goSettings() {
  uni.navigateTo({ url: '/pages/settings/settings' })
}

async function handleSync() {
  const queue = getOfflineQueue()
  if (!queue.length) {
    uni.showToast({ title: '无离线数据', icon: 'none' })
    return
  }
  const res = await syncOfflineData(queue, uni.getStorageSync('last_sync_time') || '')
  uni.setStorageSync('last_sync_time', new Date().toISOString())
  uni.removeStorageSync('offline_queue')
  queueCount.value = 0
  const ok = res.successCount ?? res.totalSynced ?? 0
  const fail = res.failCount ?? 0
  uni.showToast({ title: `成功${ok}条${fail ? `，失败${fail}条` : ''}`, icon: 'none' })
}

function handleLogout() {
  uni.showModal({
    title: '确认退出',
    content: '确定要退出登录吗？',
    success(res) {
      if (res.confirm) {
        uni.removeStorageSync('wms_token')
        uni.removeStorageSync('wms_refresh_token')
        uni.removeStorageSync('wms_user')
        uni.reLaunch({ url: '/pages/login/login' })
      }
    },
  })
}

defineExpose({ loadProfile })
</script>

<style scoped>
.profile-panel { padding-bottom: 24rpx; }

.hero-card {
  background: linear-gradient(135deg, #1d4ed8, #3b82f6);
  border-radius: 20rpx;
  padding: 48rpx 32rpx 40rpx;
  text-align: center;
  color: #fff;
  margin-bottom: 24rpx;
  position: relative;
}
.settings-btn {
  position: absolute;
  top: 24rpx;
  right: 24rpx;
  width: 64rpx;
  height: 64rpx;
  background: rgba(255,255,255,0.2);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}
.settings-icon { font-size: 36rpx; }
.avatar {
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  background: rgba(255,255,255,0.25);
  margin: 0 auto 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 4rpx solid rgba(255,255,255,0.5);
}
.avatar-text { font-size: 52rpx; font-weight: bold; }
.nickname { font-size: 40rpx; font-weight: bold; display: block; }
.username { font-size: 26rpx; opacity: 0.85; margin-top: 8rpx; display: block; }
.role-tag {
  display: inline-block;
  margin-top: 16rpx;
  padding: 6rpx 20rpx;
  background: rgba(255,255,255,0.2);
  border-radius: 20rpx;
  font-size: 22rpx;
}

.info-section {
  background: #fff;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 24rpx;
}
.section-title { font-weight: bold; font-size: 28rpx; margin-bottom: 16rpx; display: block; }
.info-grid { display: flex; flex-wrap: wrap; gap: 16rpx; }
.info-item {
  width: calc(50% - 8rpx);
  background: #f8fafc;
  padding: 20rpx;
  border-radius: 12rpx;
  box-sizing: border-box;
}
.info-item.wide { width: 100%; }
.info-label { color: #94a3b8; font-size: 22rpx; display: block; }
.info-value { font-size: 28rpx; font-weight: 600; margin-top: 6rpx; display: block; color: #1e293b; }
.info-value.server { font-size: 24rpx; font-weight: normal; word-break: break-all; }

.action-section {
  display: flex;
  gap: 16rpx;
  margin-bottom: 24rpx;
}
.action-item {
  flex: 1;
  background: #fff;
  border-radius: 12rpx;
  padding: 28rpx;
  text-align: center;
  font-size: 26rpx;
  color: #475569;
}
.action-icon { display: block; font-size: 40rpx; margin-bottom: 8rpx; }

.logout-btn { background: #ef4444; color: #fff; }
</style>
