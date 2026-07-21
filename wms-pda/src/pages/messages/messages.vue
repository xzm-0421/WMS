<template>
  <view class="page">
    <view class="header">
      <text>未读消息: {{ unreadCount }}</text>
      <button size="mini" @click="readAll">全部已读</button>
    </view>
    <view v-for="msg in messages" :key="msg.messageId" class="card" @click="readOne(msg)">
      <view class="row">
        <text class="type">{{ msg.messageType }}</text>
        <text v-if="!msg.isRead" class="dot">未读</text>
      </view>
      <text class="title">{{ msg.title }}</text>
      <text class="content">{{ msg.content }}</text>
      <text class="time">{{ msg.createTime }}</text>
    </view>
    <view v-if="!messages.length" class="empty">暂无消息</view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import {
  getMessages,
  getUnreadCount,
  markAllMessagesRead,
  markMessageRead,
} from '@/api/mobile.js'

const messages = ref([])
const unreadCount = ref(0)

async function loadData() {
  const [listRes, countRes] = await Promise.all([
    getMessages({ current: 1, size: 50 }),
    getUnreadCount(),
  ])
  messages.value = listRes.records || []
  unreadCount.value = countRes.count || 0
}

async function readOne(msg) {
  if (!msg.isRead) {
    await markMessageRead(msg.messageId)
    msg.isRead = true
    unreadCount.value = Math.max(0, unreadCount.value - 1)
  }
}

async function readAll() {
  await markAllMessagesRead()
  messages.value.forEach((m) => { m.isRead = true })
  unreadCount.value = 0
  uni.showToast({ title: '已全部标记', icon: 'success' })
}

onMounted(loadData)
</script>

<style scoped>
.page { padding: 24rpx; }
.header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16rpx; }
.card { background: #fff; padding: 24rpx; border-radius: 12rpx; margin-bottom: 16rpx; }
.row { display: flex; justify-content: space-between; margin-bottom: 8rpx; }
.type { font-size: 24rpx; color: #64748b; }
.dot { color: #ef4444; font-size: 24rpx; }
.title { font-weight: bold; display: block; }
.content { color: #475569; font-size: 26rpx; display: block; margin: 8rpx 0; }
.time { font-size: 22rpx; color: #94a3b8; }
.empty { text-align: center; color: #94a3b8; padding: 40rpx; }
</style>
