<template>
  <view class="page">
    <view class="card">
      <text class="title">应用更新</text>
      <text class="sub">进入页面后自动检测最新版本</text>

      <view class="version-box">
        <view class="row">
          <text class="label">当前版本</text>
          <text class="value">{{ local.versionName || '-' }} ({{ local.versionCode || '-' }})</text>
        </view>
        <view class="row">
          <text class="label">最新版本</text>
          <text class="value">{{ remote.latestVersionName || '-' }} ({{ remote.latestVersionCode || '-' }})</text>
        </view>
        <view class="row">
          <text class="label">检测状态</text>
          <text :class="['value', statusClass]">{{ statusText }}</text>
        </view>
        <view v-if="downloadUrl" class="row">
          <text class="label">下载地址</text>
          <text class="value url" @longpress="copyUrl">{{ downloadUrl }}</text>
        </view>
      </view>

      <view v-if="remote.changelog" class="changelog">
        <text class="changelog-title">更新说明</text>
        <text class="changelog-body">{{ remote.changelog }}</text>
      </view>

      <view v-if="downloading" class="progress-wrap">
        <view class="progress-bar">
          <view class="progress-inner" :style="{ width: progress + '%' }" />
        </view>
        <text class="progress-text">下载中 {{ progress }}%</text>
      </view>

      <button
        class="btn-primary"
        type="primary"
        :loading="checking || downloading"
        :disabled="checking || downloading"
        @click="runCheck(true)"
      >
        {{ checking ? '检测中...' : '重新检测' }}
      </button>

      <button
        v-if="hasUpdate"
        class="btn-update"
        type="warn"
        :loading="downloading"
        :disabled="checking || downloading"
        @click="onInstall"
      >
        {{ downloading ? '更新中...' : (remote.force ? '立即更新（强制）' : '立即更新') }}
      </button>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { detectAppUpdate, downloadAndInstallUpdate } from '@/utils/appUpdate.js'

const checking = ref(false)
const downloading = ref(false)
const progress = ref(0)
const local = ref({})
const remote = ref({})
const hasUpdate = ref(false)
const downloadUrl = ref('')
const errorTip = ref('')
let autoChecked = false

const statusText = computed(() => {
  if (checking.value) return '正在检测...'
  if (errorTip.value) return errorTip.value
  if (hasUpdate.value) return remote.value.message || '发现新版本'
  return remote.value.message || '已是最新版本'
})

const statusClass = computed(() => {
  if (errorTip.value) return 'err'
  if (hasUpdate.value) return 'new'
  return 'ok'
})

async function runCheck(force = false) {
  if (checking.value || downloading.value) return
  checking.value = true
  errorTip.value = ''
  try {
    const result = await detectAppUpdate()
    local.value = result.local || {}
    remote.value = result.remote || {}
    hasUpdate.value = !!result.hasUpdate
    downloadUrl.value = result.downloadUrl || ''
    if (force && !hasUpdate.value) {
      uni.showToast({ title: remote.value.message || '已是最新版本', icon: 'none' })
    }
    // 强制更新：检测后直接进入安装
    if (hasUpdate.value && remote.value.force && force !== false) {
      // 首次自动检测时若强制更新，提示后安装
    }
  } catch (e) {
    errorTip.value = e?.message || '检测失败，请检查网络'
    hasUpdate.value = false
    uni.showToast({ title: errorTip.value, icon: 'none' })
  } finally {
    checking.value = false
  }
}

function copyUrl() {
  if (!downloadUrl.value) return
  uni.setClipboardData({
    data: downloadUrl.value,
    success: () => uni.showToast({ title: '已复制下载地址', icon: 'none' }),
  })
}

async function onInstall() {
  if (!hasUpdate.value || downloading.value) return
  if (!downloadUrl.value || !/^https?:\/\//i.test(downloadUrl.value)) {
    uni.showToast({
      title: '下载地址无效，请先在登录页配置完整服务器地址',
      icon: 'none',
      duration: 3000,
    })
    return
  }
  downloading.value = true
  progress.value = 0
  try {
    const res = await downloadAndInstallUpdate({
      downloadUrl: downloadUrl.value,
      packageType: remote.value.packageType || 'apk',
      onProgress: (p) => {
        progress.value = Math.max(0, Math.min(100, Number(p) || 0))
      },
    })
    if (!res?.ok) {
      uni.showModal({
        title: '更新失败',
        content: res?.message || '更新失败',
        showCancel: false,
      })
    } else if (res.message) {
      uni.showToast({ title: res.message, icon: 'none', duration: 2500 })
    }
  } finally {
    downloading.value = false
  }
}

onLoad(() => {
  uni.setNavigationBarTitle({ title: '检查更新' })
})

onShow(async () => {
  if (autoChecked) return
  autoChecked = true
  await runCheck(false)
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #f1f5f9;
  padding: 24rpx;
}
.card {
  background: #fff;
  border-radius: 16rpx;
  padding: 32rpx 28rpx;
}
.title {
  display: block;
  font-size: 34rpx;
  font-weight: 700;
  color: #0f172a;
}
.sub {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #94a3b8;
}
.version-box {
  margin-top: 28rpx;
  background: #f8fafc;
  border-radius: 12rpx;
  padding: 8rpx 20rpx;
}
.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 18rpx 0;
  border-bottom: 1rpx solid #e2e8f0;
}
.row:last-child { border-bottom: none; }
.label { font-size: 26rpx; color: #64748b; }
.value { font-size: 26rpx; color: #0f172a; font-weight: 600; max-width: 60%; text-align: right; }
.value.ok { color: #16a34a; }
.value.new { color: #ea580c; }
.value.err { color: #dc2626; }
.value.url {
  font-size: 22rpx;
  font-weight: 500;
  color: #2563eb;
  word-break: break-all;
}
.changelog {
  margin-top: 24rpx;
  padding: 20rpx;
  background: #fff7ed;
  border-radius: 12rpx;
}
.changelog-title {
  display: block;
  font-size: 26rpx;
  font-weight: 600;
  color: #c2410c;
}
.changelog-body {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #9a3412;
  line-height: 1.5;
  white-space: pre-wrap;
}
.progress-wrap { margin-top: 24rpx; }
.progress-bar {
  height: 16rpx;
  background: #e2e8f0;
  border-radius: 999rpx;
  overflow: hidden;
}
.progress-inner {
  height: 100%;
  background: #2563eb;
  border-radius: 999rpx;
  transition: width 0.2s;
}
.progress-text {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #64748b;
  text-align: center;
}
.btn-primary {
  margin-top: 32rpx;
  background: #1d4ed8;
  color: #fff;
}
.btn-update {
  margin-top: 16rpx;
}
</style>
