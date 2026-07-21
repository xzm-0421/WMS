<template>
  <view class="page">
    <view class="toolbar">
      <button class="create-btn" type="primary" size="mini" @click="goCreate">+ 新建单据</button>
    </view>

    <scroll-view scroll-x class="tabs-scroll">
      <view class="tabs">
        <text
          v-for="tab in STATUS_TABS"
          :key="tab.key"
          :class="['tab', activeTab === tab.key && 'active']"
          @click="switchTab(tab.key)"
        >{{ tab.label }}</text>
      </view>
    </scroll-view>

    <view
      v-for="item in orders"
      :key="item.orderNo"
      class="order-card"
      @click="goDetail(item.orderNo)"
    >
      <view class="row-top">
        <text class="order-no">{{ item.orderNo }}</text>
        <text class="status" :style="{ color: getStatusColor(item.status, direction) }">
          {{ getStatusLabel(item.status, direction) }}
        </text>
      </view>
      <text class="meta">仓库: {{ item.warehouseCode }} · {{ item.createTime || '-' }}</text>
      <text v-if="item.remark" class="remark">{{ item.remark }}</text>
    </view>

    <view v-if="loaded && !orders.length" class="empty">暂无单据</view>
    <view v-if="hasMore" class="load-more" @click="loadMore">加载更多</view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad, onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import { listOrders } from '@/api/order.js'
import { getModule, getModuleConfig } from '@/constants/orderModules.js'
import {
  STATUS_TABS,
  getStatusLabel,
  getStatusColor,
  statusesForTab,
} from '@/constants/orderStatus.js'

const moduleId = ref('')
const direction = ref('inbound')
const moduleConfig = ref(null)
const activeTab = ref('all')
const orders = ref([])
const loaded = ref(false)
const current = ref(1)
const total = ref(0)
const pageSize = 20

onLoad((options) => {
  moduleId.value = options?.module || 'purchase'
  direction.value = options?.direction || 'inbound'
  moduleConfig.value = getModuleConfig(moduleId.value, direction.value)
  const mod = getModule(moduleId.value)
  const title = moduleConfig.value?.label || '单据列表'
  uni.setNavigationBarTitle({ title })
  loadList(true)
})

onPullDownRefresh(async () => {
  await loadList(true)
  uni.stopPullDownRefresh()
})

onReachBottom(() => {
  if (hasMore.value) loadMore()
})

const hasMore = ref(false)

async function loadList(reset = false) {
  if (reset) {
    current.value = 1
    orders.value = []
  }
  const statusList = statusesForTab(activeTab.value, direction.value)
  const params = {
    orderType: moduleConfig.value?.orderType,
    current: current.value,
    size: pageSize,
  }
  if (statusList?.length === 1) {
    params.status = statusList[0]
  }
  const res = await listOrders(direction.value, params)
  let records = res.records || []
  if (statusList?.length > 1) {
    records = records.filter((r) => statusList.includes(r.status))
  }
  orders.value = reset ? records : [...orders.value, ...records]
  total.value = res.total || 0
  hasMore.value = orders.value.length < total.value
  loaded.value = true
}

function loadMore() {
  current.value += 1
  loadList(false)
}

function switchTab(key) {
  activeTab.value = key
  loadList(true)
}

function goCreate() {
  uni.navigateTo({
    url: `/pages/order/create?module=${moduleId.value}&direction=${direction.value}`,
  })
}

function goDetail(orderNo) {
  uni.navigateTo({
    url: `/pages/order/detail?module=${moduleId.value}&direction=${direction.value}&orderNo=${orderNo}`,
  })
}
</script>

<style scoped>
.page { padding: 24rpx; padding-bottom: 48rpx; }
.toolbar { margin-bottom: 16rpx; }
.create-btn { background: #1d4ed8; color: #fff; }
.tabs-scroll { white-space: nowrap; margin-bottom: 16rpx; }
.tabs { display: inline-flex; gap: 12rpx; }
.tab {
  padding: 12rpx 24rpx;
  background: #e2e8f0;
  border-radius: 8rpx;
  font-size: 24rpx;
  display: inline-block;
}
.tab.active { background: #1d4ed8; color: #fff; }
.order-card {
  background: #fff;
  padding: 24rpx;
  border-radius: 12rpx;
  margin-bottom: 16rpx;
}
.row-top { display: flex; justify-content: space-between; margin-bottom: 8rpx; }
.order-no { font-weight: bold; font-size: 28rpx; }
.status { font-size: 24rpx; font-weight: 500; }
.meta { color: #64748b; font-size: 24rpx; display: block; }
.remark { color: #94a3b8; font-size: 22rpx; margin-top: 8rpx; display: block; }
.empty { text-align: center; color: #94a3b8; padding: 60rpx; }
.load-more { text-align: center; color: #1d4ed8; padding: 24rpx; font-size: 26rpx; }
</style>
