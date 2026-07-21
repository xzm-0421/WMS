<template>
  <view class="page">
    <view class="header">
      <text class="title">{{ moduleConfig.title }}</text>
      <text class="desc">共 {{ taskList.length }} 条待办</text>
    </view>

    <view
      v-for="item in taskList"
      :key="item._key"
      class="task-card"
      @click="handleClick(item)"
    >
      <text class="order-no">{{ item._title }}</text>
      <text class="meta">{{ item._meta }}</text>
    </view>

    <view v-if="loaded && !taskList.length" class="empty">
      <text class="empty-icon">{{ moduleConfig.icon }}</text>
      <text>暂无{{ moduleConfig.label }}任务</text>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad, onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { getTasks } from '@/api/mobile.js'

const moduleType = ref('stockcheck')
const tasks = ref({})
const loaded = ref(false)

const moduleConfig = computed(() => {
  const map = {
    stockcheck: { label: '盘点', title: '盘点任务', icon: '📋' },
    qc: { label: '质检', title: '质检任务', icon: '✅' },
  }
  return map[moduleType.value] || map.stockcheck
})

const taskList = computed(() => {
  const raw = tasks.value[moduleType.value]?.tasks || []
  return raw.map((item) => {
    if (moduleType.value === 'stockcheck') {
      return {
        ...item,
        _key: item.taskNo,
        _title: item.taskNo,
        _meta: `${item.warehouseCode || '-'} · ${item.status || '-'}`,
      }
    }
    return {
      ...item,
      _key: item.qcNo,
      _title: item.qcNo,
      _meta: `${item.materialCode || '-'} · ${item.status || '-'}`,
    }
  })
})

onLoad((options) => {
  moduleType.value = options?.type || 'stockcheck'
  uni.setNavigationBarTitle({ title: moduleConfig.value.title })
  loadData()
})

onShow(loadData)

onPullDownRefresh(async () => {
  await loadData()
  uni.stopPullDownRefresh()
})

async function loadData() {
  try {
    tasks.value = await getTasks()
  } catch {
    tasks.value = {}
  } finally {
    loaded.value = true
  }
}

function handleClick(item) {
  if (moduleType.value === 'stockcheck') {
    uni.navigateTo({ url: `/pages/stockcheck/stockcheck?taskNo=${item.taskNo}` })
  } else {
    uni.navigateTo({ url: `/pages/qc/qc?qcNo=${item.qcNo}` })
  }
}
</script>

<style scoped>
.page { padding: 24rpx; }
.header {
  background: #fff;
  padding: 24rpx;
  border-radius: 12rpx;
  margin-bottom: 24rpx;
}
.title { font-weight: bold; font-size: 32rpx; display: block; }
.desc { color: #94a3b8; font-size: 24rpx; }
.task-card {
  background: #fff;
  padding: 24rpx;
  border-radius: 12rpx;
  margin-bottom: 16rpx;
  border-left: 6rpx solid #22c55e;
}
.order-no { font-weight: bold; display: block; }
.meta { color: #64748b; font-size: 24rpx; margin-top: 4rpx; display: block; }
.empty {
  text-align: center;
  color: #94a3b8;
  padding: 80rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
}
.empty-icon { font-size: 64rpx; opacity: 0.5; }
</style>
