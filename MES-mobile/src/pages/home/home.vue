<template>
  <view class="page">
    <scroll-view scroll-y class="scroll" :show-scrollbar="false">
      <view class="hero">
        <RippleBg />
        <view class="head" :style="{ paddingTop: statusBarHeight + 16 + 'px' }">
          <text class="page-title">首页</text>
        </view>

        <view class="stats">
          <view v-for="item in stats" :key="item.label" class="stat" @click="comingSoon(item.label)">
            <text class="stat-num">{{ item.value }}</text>
            <text class="stat-label">{{ item.label }}</text>
          </view>
        </view>
      </view>

      <view class="quick-row">
        <view v-for="item in quickMenus" :key="item.label" class="quick" @click="onQuick(item)">
          <view class="quick-circle" :style="{ background: item.color }">
            <text class="quick-glyph">{{ item.glyph }}</text>
          </view>
          <text class="quick-label">{{ item.label }}</text>
        </view>
      </view>

      <view class="todo-head">
        <view class="todo-title-wrap" @click="comingSoon('待办筛选')">
          <text class="todo-title">我的待办</text>
          <view class="caret"></view>
        </view>
      </view>

      <view v-if="!todos.length" class="todo-empty">
        <text>暂无待办</text>
      </view>
      <view v-for="item in todos" :key="item.id" class="todo-card" @click="comingSoon('工单详情')">
        <view class="todo-top">
          <text class="tag" :class="item.tagType">{{ item.tag }}</text>
          <text class="status" :class="item.statusType">{{ item.status }}</text>
        </view>
        <text class="todo-name">{{ item.title }}</text>
        <view v-if="item.location" class="meta-row">
          <text class="pin">📍</text>
          <text class="meta">{{ item.location }}</text>
        </view>
        <view v-if="item.ip" class="meta-row">
          <text class="pin">🖥</text>
          <text class="meta">{{ item.ip }}</text>
        </view>
        <view v-if="item.starter" class="todo-foot">
          <text>发起人：{{ item.starter }}</text>
          <text>发起时间：{{ item.time }}</text>
        </view>
      </view>
      <view class="bottom-space"></view>
    </scroll-view>
    <AppTabBar current="home" />
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import AppTabBar from '@/components/AppTabBar.vue'
import RippleBg from '@/components/RippleBg.vue'
import { requireSession } from '@/utils/authStorage.js'
import { comingSoon } from '@/utils/ui.js'
import { flushQueue, getQueueCount } from '@/utils/offlineQueue.js'
import { syncReports } from '@/api/mes.js'

const statusBarHeight = ref(uni.getSystemInfoSync().statusBarHeight || 20)

const stats = [
  { value: 0, label: '全部工单' },
  { value: 0, label: '全部待办' },
  { value: 0, label: '全部办结' },
  { value: 0, label: '我的工单' },
  { value: 0, label: '我的待办' },
  { value: 0, label: '我的已办' },
]

const quickMenus = [
  { label: '工单报工', color: '#ff8a3d', glyph: '🛠', url: '/pages/report/report' },
  { label: '工单统计', color: '#3ec6e0', glyph: '📊' },
  { label: '服务热线', color: '#4d7cff', glyph: '☎' },
  { label: '意见反馈', color: '#f5c542', glyph: '✎' },
]

const todos = []

function onQuick(item) {
  if (item.url) {
    uni.navigateTo({ url: item.url })
    return
  }
  comingSoon(item.label)
}

async function tryFlush() {
  if (!getQueueCount()) return
  try {
    await flushQueue((items, deviceNo) => syncReports(items, deviceNo))
  } catch {
    /* ignore */
  }
}

onShow(() => {
  if (!requireSession()) return
  tryFlush()
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: linear-gradient(180deg, #f3dff0 0%, #e4ecfb 36%, #ffffff 62%);
}
.scroll {
  height: 100vh;
  box-sizing: border-box;
  background: linear-gradient(180deg, #f3dff0 0%, #e4ecfb 32%, #ffffff 58%);
}
.hero {
  position: relative;
  overflow: hidden;
  background: linear-gradient(180deg, #f3dff0 0%, #e4ecfb 55%, #ffffff 100%);
}
.head {
  position: relative;
  z-index: 1;
  padding-left: 36rpx;
  padding-right: 36rpx;
}
.page-title {
  font-size: 48rpx;
  font-weight: 800;
  color: #1a1a1a;
}
.stats {
  position: relative;
  z-index: 1;
  margin: 28rpx 24rpx 8rpx;
  display: flex;
  flex-wrap: wrap;
}
.stat {
  width: 33.33%;
  text-align: center;
  padding: 18rpx 0 28rpx;
}
.stat-num {
  display: block;
  font-size: 44rpx;
  font-weight: 800;
  color: #222;
}
.stat-label {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #666;
}
.quick-row {
  display: flex;
  justify-content: space-between;
  padding: 36rpx 40rpx 16rpx;
}
.quick {
  width: 25%;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.quick-circle {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8rpx 16rpx rgba(0, 0, 0, 0.08);
}
.quick-glyph {
  font-size: 36rpx;
  color: #fff;
}
.quick-label {
  margin-top: 12rpx;
  font-size: 22rpx;
  color: #444;
}
.todo-head {
  display: flex;
  align-items: center;
  padding: 12rpx 36rpx 8rpx;
}
.todo-title-wrap {
  display: flex;
  align-items: center;
  gap: 8rpx;
}
.todo-title {
  font-size: 32rpx;
  font-weight: 700;
}
.caret {
  width: 14rpx;
  height: 14rpx;
  border-right: 4rpx solid #333;
  border-bottom: 4rpx solid #333;
  transform: rotate(45deg);
  margin-top: -8rpx;
}
.todo-card {
  margin: 16rpx 28rpx;
  padding: 24rpx;
  background: #fff;
  border-radius: 16rpx;
  box-shadow: 0 8rpx 24rpx rgba(31, 35, 90, 0.06);
}
.todo-empty {
  margin: 16rpx 28rpx;
  padding: 72rpx 24rpx;
  background: #fff;
  border-radius: 16rpx;
  text-align: center;
  color: #9aa0b4;
  font-size: 26rpx;
}
.todo-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.tag {
  font-size: 24rpx;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
}
.tag.blue {
  color: #3b6cff;
  background: #eef3ff;
}
.tag.red {
  color: #e85d5d;
  background: #ffecec;
}
.status {
  font-size: 24rpx;
}
.status.wait {
  color: #f08a2a;
}
.status.overdue {
  color: #fff;
  background: #ee5a5a;
  padding: 4rpx 16rpx;
  border-radius: 20rpx;
}
.todo-name {
  display: block;
  margin: 16rpx 0 12rpx;
  font-size: 30rpx;
  font-weight: 700;
  color: #222;
}
.meta-row {
  display: flex;
  align-items: center;
  gap: 8rpx;
  margin-bottom: 8rpx;
}
.pin { font-size: 22rpx; }
.meta {
  font-size: 24rpx;
  color: #888;
}
.todo-foot {
  margin-top: 12rpx;
  display: flex;
  justify-content: space-between;
  font-size: 22rpx;
  color: #999;
}
.bottom-space {
  height: calc(140rpx + env(safe-area-inset-bottom));
}
</style>
