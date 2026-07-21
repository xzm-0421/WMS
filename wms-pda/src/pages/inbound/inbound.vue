<template>
  <view class="page">
    <ScanWorkbench
      v-if="orderNo"
      ref="workbenchRef"
      mode="inbound"
      :order-no="orderNo"
      :warehouse-code="order?.warehouseCode || ''"
      @success="onScanSuccess"
    />

    <view v-if="orderOptions.length > 1" class="order-picker">
      <text class="picker-label">当前单据</text>
      <picker :range="orderOptions" range-key="label" @change="onOrderPick">
        <view class="picker-value">
          {{ currentOrderLabel }} ▾
        </view>
      </picker>
    </view>

    <view v-if="order" class="header">
      <text v-if="moduleLabel" class="type-tag">{{ moduleLabel }}</text>
      <text>入库单: {{ order.orderNo }}</text>
      <text>仓库: {{ order.warehouseCode }} · {{ statusLabel }}</text>
    </view>

    <view v-else-if="!loading" class="empty-hint">
      <text>暂无可作业入库单</text>
    </view>

    <text v-if="order" class="section-title">明细进度</text>
    <view v-for="line in order?.details || []" :key="line.lineNo" class="line-card">
      <text class="name">{{ line.materialName || line.materialCode }}</text>
      <text>计划 {{ line.orderQty }} / 已收 {{ line.receivedQty || 0 }}</text>
      <view class="progress-bar">
        <view class="progress-fill" :style="{ width: lineProgress(line) + '%' }" />
      </view>
    </view>

    <button v-if="order" class="complete-btn" type="warn" @click="doComplete">确认入库完成</button>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { completeInbound, getInboundOrder } from '@/api/mobile.js'
import ScanWorkbench from '@/components/ScanWorkbench.vue'
import { getModuleConfig } from '@/constants/orderModules.js'
import { getStatusLabel } from '@/constants/orderStatus.js'
import { fetchScannableOrders } from '@/utils/scanEntry.js'

const orderNo = ref('')
const moduleId = ref('')
const moduleLabel = ref('')
const order = ref(null)
const loading = ref(false)
const orderOptions = ref([])
const workbenchRef = ref(null)

const statusLabel = computed(() =>
  order.value ? getStatusLabel(order.value.status, 'inbound') : '',
)

const currentOrderLabel = computed(() => {
  const found = orderOptions.value.find((o) => o.orderNo === orderNo.value)
  return found?.label || orderNo.value
})

onLoad(async (options) => {
  orderNo.value = options?.orderNo || ''
  moduleId.value = options?.module || 'purchase'
  moduleLabel.value = getModuleConfig(moduleId.value, 'inbound')?.label || ''
  if (moduleLabel.value) uni.setNavigationBarTitle({ title: moduleLabel.value })

  if (!orderNo.value) {
    await resolveOrder()
  } else {
    await loadOrderOptions()
    await loadOrder()
  }
})

async function resolveOrder() {
  loading.value = true
  try {
    const orders = await fetchScannableOrders(moduleId.value, 'inbound')
    orderOptions.value = orders.map((o) => ({
      orderNo: o.orderNo,
      label: `${o.orderNo} · ${getStatusLabel(o.status, 'inbound')}`,
    }))
    if (orders.length) {
      orderNo.value = orders[0].orderNo
      await loadOrder()
    }
  } finally {
    loading.value = false
  }
}

async function loadOrderOptions() {
  const orders = await fetchScannableOrders(moduleId.value, 'inbound')
  orderOptions.value = orders.map((o) => ({
    orderNo: o.orderNo,
    label: `${o.orderNo} · ${getStatusLabel(o.status, 'inbound')}`,
  }))
}

async function loadOrder() {
  if (!orderNo.value) return
  order.value = await getInboundOrder(orderNo.value)
}

function onOrderPick(e) {
  const idx = Number(e.detail.value)
  const picked = orderOptions.value[idx]
  if (picked && picked.orderNo !== orderNo.value) {
    orderNo.value = picked.orderNo
    loadOrder()
  }
}

function lineProgress(line) {
  const total = Number(line.orderQty) || 1
  const done = Number(line.receivedQty) || 0
  return Math.min(100, Math.round((done / total) * 100))
}

function onScanSuccess() {
  uni.showToast({ title: '入库成功', icon: 'success', duration: 800 })
  loadOrder()
}

async function doComplete() {
  await completeInbound(orderNo.value)
  uni.showToast({ title: '入库完成', icon: 'success' })
  setTimeout(() => uni.navigateBack(), 800)
}

onShow(() => {
  // 仅页面重新显示时恢复一次焦点，不反复 toggle
  setTimeout(() => workbenchRef.value?.focusInput?.(), 500)
})
</script>

<style scoped>
.page { padding: 24rpx; padding-bottom: 120rpx; padding-top: 0; }
.order-picker {
  display: flex; align-items: center; gap: 16rpx;
  background: #fff; padding: 20rpx 24rpx; border-radius: 12rpx; margin-bottom: 16rpx;
}
.picker-label { font-size: 24rpx; color: #64748b; flex-shrink: 0; }
.picker-value { flex: 1; font-size: 26rpx; color: #1d4ed8; font-weight: 500; }
.header { background: #fff; padding: 24rpx; border-radius: 12rpx; margin-bottom: 24rpx; }
.type-tag {
  display: inline-block; background: #eff6ff; color: #1d4ed8;
  padding: 4rpx 12rpx; border-radius: 6rpx; font-size: 22rpx; margin-bottom: 8rpx;
}
.empty-hint { text-align: center; color: #94a3b8; padding: 80rpx 0; }
.section-title { font-weight: bold; margin-bottom: 16rpx; display: block; }
.line-card { background: #fff; padding: 20rpx 24rpx; border-radius: 12rpx; margin-bottom: 12rpx; }
.name { font-weight: bold; display: block; margin-bottom: 4rpx; font-size: 26rpx; }
.progress-bar { height: 8rpx; background: #e2e8f0; border-radius: 4rpx; margin-top: 8rpx; overflow: hidden; }
.progress-fill { height: 100%; background: #22c55e; transition: width 0.3s; }
.complete-btn { position: fixed; left: 24rpx; right: 24rpx; bottom: 32rpx; }
</style>
