<template>
  <view class="page">
    <view class="head" :style="{ paddingTop: statusBarHeight + 16 + 'px' }">
      <text class="title">我的报工</text>
      <view class="panel" v-if="panel">
        <view class="panel-item">
          <text class="panel-num">{{ panel.pendingCount || 0 }}</text>
          <text class="panel-label">待同步</text>
        </view>
        <view class="panel-item">
          <text class="panel-num">{{ panel.failedCount || 0 }}</text>
          <text class="panel-label">同步失败</text>
        </view>
        <view class="panel-item">
          <text class="panel-num">{{ panel.todaySyncedCount || 0 }}</text>
          <text class="panel-label">今日已同步</text>
        </view>
        <view class="panel-item">
          <text class="panel-num">{{ panel.networkStatus === 'ONLINE' ? '在线' : '离线' }}</text>
          <text class="panel-label">网络</text>
        </view>
      </view>
    </view>

    <view class="seg">
      <view class="seg-item" :class="{ active: tab === 'queue' }" @click="tab = 'queue'">
        本地待同步({{ queue.length }})
      </view>
      <view class="seg-item" :class="{ active: tab === 'server' }" @click="switchServer">服务端记录</view>
    </view>

    <view v-if="tab === 'queue'" class="card">
      <view v-if="!queue.length" class="empty">暂无本地待同步数据</view>
      <view v-for="item in queue" :key="item.clientReportNo" class="row">
        <view class="row-main">
          <text class="row-title">{{ item.moNo }} · {{ item.processCode }}</text>
          <text class="row-sub">{{ item.reportType }} · {{ item.qty }} · {{ formatTime(item.clientTime) }}</text>
        </view>
      </view>
      <button v-if="queue.length" class="ghost-btn" @click="syncNow" :loading="syncing">立即同步</button>
    </view>

    <view v-else class="card">
      <view v-if="!reports.length" class="empty">暂无报工记录</view>
      <view v-for="r in reports" :key="r.reportNo" class="row">
        <view class="row-main">
          <text class="row-title">{{ r.reportNo }} · {{ r.processName || r.processCode }}</text>
          <text class="row-sub">{{ r.moNo }} · {{ r.qty }} · {{ syncLabel(r.syncStatus) }}</text>
        </view>
        <button
          v-if="r.syncStatus === 'FAILED' || r.syncStatus === 'MANUAL_REQUIRED'"
          class="retry-btn"
          @click="handleRetry(r)"
        >
          重试
        </button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { requireSession } from '@/utils/authStorage.js'
import { getMyReports, retryReport, getSyncPanel, syncReports } from '@/api/mes.js'
import { flushQueue, getQueue, getQueueCount } from '@/utils/offlineQueue.js'
import { toast } from '@/utils/ui.js'

const statusBarHeight = ref(uni.getSystemInfoSync().statusBarHeight || 20)
const tab = ref('queue')
const queue = ref([])
const reports = ref([])
const panel = ref(null)
const syncing = ref(false)

function syncLabel(status) {
  const map = {
    PENDING: '待同步',
    SYNCING: '同步中',
    SUCCESS: '已同步',
    FAILED: '同步失败',
    MANUAL_REQUIRED: '需人工处理',
    STALE: '已过期',
    EXPIRED: '超期',
  }
  return map[status] || status || '-'
}

function formatTime(ms) {
  if (!ms) return ''
  const d = new Date(ms)
  const p = (n) => String(n).padStart(2, '0')
  return `${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

async function loadQueue() {
  queue.value = getQueue()
  await tryFlush()
  queue.value = getQueue()
}

async function tryFlush() {
  if (!getQueueCount()) return
  try {
    await flushQueue((items, deviceNo) => syncReports(items, deviceNo))
  } catch {
    /* ignore */
  }
}

async function switchServer() {
  tab.value = 'server'
  await loadReports()
  await loadPanel()
}

async function loadReports() {
  try {
    const res = await getMyReports({ current: 1, size: 20 })
    reports.value = res?.records || []
  } catch {
    reports.value = []
  }
}

async function loadPanel() {
  try {
    panel.value = await getSyncPanel()
  } catch {
    panel.value = null
  }
}

async function syncNow() {
  syncing.value = true
  try {
    const res = await flushQueue((items, deviceNo) => syncReports(items, deviceNo))
    toast(`成功 ${res?.successCount || 0} 条，失败 ${res?.failCount || 0} 条`)
    queue.value = getQueue()
    await loadPanel()
  } catch {
    /* http.js 已提示 */
  } finally {
    syncing.value = false
  }
}

async function handleRetry(report) {
  try {
    await retryReport(report.reportNo)
    toast('已加入重试队列')
    await loadReports()
  } catch {
    /* ignore */
  }
}

onShow(() => {
  if (!requireSession()) return
  loadQueue()
  loadPanel()
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #f5f6fb;
  padding-bottom: 48rpx;
}
.head {
  padding: 0 36rpx 24rpx;
  background: linear-gradient(120deg, #efe7fb 0%, #e4ecfb 100%);
}
.title {
  font-size: 44rpx;
  font-weight: 800;
  color: #1f2340;
}
.panel {
  display: flex;
  margin-top: 24rpx;
  background: #fff;
  border-radius: 20rpx;
  padding: 20rpx 0;
  box-shadow: 0 8rpx 24rpx rgba(31, 35, 90, 0.05);
}
.panel-item {
  flex: 1;
  text-align: center;
}
.panel-num {
  display: block;
  font-size: 34rpx;
  font-weight: 800;
  color: #1f2340;
}
.panel-label {
  display: block;
  margin-top: 6rpx;
  font-size: 22rpx;
  color: #8a8fa3;
}
.seg {
  display: flex;
  margin: 20rpx 24rpx 0;
  background: #eceef6;
  border-radius: 16rpx;
  padding: 6rpx;
}
.seg-item {
  flex: 1;
  text-align: center;
  padding: 16rpx 0;
  font-size: 26rpx;
  color: #6b7088;
  border-radius: 12rpx;
}
.seg-item.active {
  background: #fff;
  color: #5c67f2;
  font-weight: 600;
}
.card {
  margin: 20rpx 24rpx;
  padding: 8rpx 28rpx;
  background: #fff;
  border-radius: 24rpx;
  box-shadow: 0 8rpx 24rpx rgba(31, 35, 90, 0.05);
}
.row {
  display: flex;
  align-items: center;
  padding: 22rpx 0;
  border-bottom: 1rpx solid #f0f1f6;
}
.row:last-child {
  border-bottom: none;
}
.row-main {
  flex: 1;
}
.row-title {
  display: block;
  font-size: 28rpx;
  color: #1f2340;
}
.row-sub {
  display: block;
  margin-top: 6rpx;
  font-size: 24rpx;
  color: #8a8fa3;
}
.empty {
  padding: 60rpx 0;
  text-align: center;
  font-size: 26rpx;
  color: #9aa0b4;
}
.ghost-btn {
  margin: 24rpx 0;
  height: 80rpx;
  line-height: 80rpx;
  font-size: 28rpx;
  color: #5c67f2;
  background: #eef0ff;
  border-radius: 40rpx;
}
.retry-btn {
  margin: 0;
  font-size: 24rpx;
  line-height: 1.8;
  padding: 0 24rpx;
  color: #fff;
  background: #f0a23d;
  border-radius: 12rpx;
}
</style>
