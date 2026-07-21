<template>
  <view class="page">
    <!-- 顶部紧凑扫码区 -->
    <view class="scan-top">
      <CompactScanBox
        ref="scanInputRef"
        :disabled="processing"
        @scan="onScan"
      />
    </view>

    <!-- 下方可滚动物料列表 -->
    <scroll-view class="list-scroll" scroll-y :show-scrollbar="false">
      <view v-if="inboundOrder" class="order-bar">
        <text class="order-no">{{ inboundOrder.orderNo }}</text>
        <text class="order-reset" @click="onResetOrder">换单</text>
      </view>

      <view v-if="lines.length" class="list-head">
        <text class="list-count">共 {{ totalCount }} 项</text>
        <text class="list-progress">{{ completedCount }}/{{ totalCount }} 已收</text>
      </view>

      <view
        v-for="line in lines"
        :key="line.lineNo"
        :class="['mat-row', rowClass(line)]"
      >
        <view class="row-main">
          <text class="mat-code">{{ line.materialCode }}</text>
          <text class="mat-name">{{ line.materialName || '-' }}</text>
          <text class="mat-spec">规格 {{ line.specification || '-' }}</text>
        </view>
        <view class="row-qty">
          <text class="qty-num">{{ line.receivedQty }}/{{ line.orderQty }}</text>
          <text class="qty-unit">{{ line.unitCode || '' }}</text>
        </view>
      </view>

      <view v-if="!lines.length && !processing" class="list-empty">
        <text class="empty-icon">📋</text>
      </view>

      <view v-if="inboundOrder" class="order-wh">
        <text>入库仓库 {{ inboundOrder.warehouseCode || warehouseCode }}</text>
      </view>

      <view class="scroll-bottom-pad" />
    </scroll-view>

    <view v-if="inboundOrder && phase === 'receive_items' && canFinish" class="footer">
      <button
        class="finish-btn"
        type="primary"
        :loading="processing"
        @click="onFinish"
      >
        完成入库
      </button>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import CompactScanBox from '@/components/CompactScanBox.vue'
import useInboundOrderScan from '@/composables/useInboundOrderScan.js'
import usePageAlive from '@/composables/usePageAlive.js'

const scanInputRef = ref(null)
const warehouseCode = ref('WH01')
const { alive, refocusScanInput } = usePageAlive()

const {
  phase,
  processing,
  inboundOrder,
  lines,
  lastHighlightLineNo,
  totalCount,
  completedCount,
  handleScan,
  finishInbound,
  reset,
} = useInboundOrderScan()

const canFinish = computed(() => /^IN\d/i.test(inboundOrder.value?.orderNo || ''))

function rowClass(line) {
  if (line.lineNo === lastHighlightLineNo.value) return 'flash'
  if (line.pendingQty <= 0) return 'done'
  return 'pending'
}

async function onScan(barcode) {
  if (!alive.value) return
  await handleScan(barcode)
  if (!alive.value) return
  refocusScanInput(scanInputRef, 300)
}

function onResetOrder() {
  uni.showModal({
    title: '重新扫单',
    content: '清空当前入库明细？',
    success: (res) => {
      if (res.confirm) reset()
    },
  })
}

async function onFinish() {
  await finishInbound()
}

onLoad(() => uni.setNavigationBarTitle({ title: '快速入库' }))

let shownOnce = false
onShow(() => {
  if (!shownOnce) {
    shownOnce = true
    refocusScanInput(scanInputRef, 500)
  }
})
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f1f5f9;
  box-sizing: border-box;
}

.scan-top {
  flex-shrink: 0;
  padding: 20rpx 24rpx 16rpx;
  background: #fff;
  border-bottom: 1rpx solid #e2e8f0;
  z-index: 10;
}

.list-scroll {
  flex: 1;
  height: 0;
  padding: 16rpx 24rpx 0;
  box-sizing: border-box;
}

.order-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16rpx 20rpx;
  background: #fff;
  border-radius: 12rpx;
  border: 1rpx solid #e2e8f0;
  margin-bottom: 12rpx;
}
.order-no {
  font-size: 28rpx;
  font-weight: 700;
  color: #1d4ed8;
}
.order-reset {
  font-size: 24rpx;
  color: #64748b;
  padding: 8rpx 16rpx;
}

.list-head {
  display: flex;
  justify-content: space-between;
  padding: 8rpx 4rpx 12rpx;
  font-size: 22rpx;
  color: #64748b;
}
.list-progress {
  color: #3b82f6;
  font-weight: 600;
}

.mat-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  background: #fff;
  border: 1rpx solid #e2e8f0;
  border-radius: 12rpx;
  padding: 20rpx;
  margin-bottom: 12rpx;
}
.mat-row.pending {
  border-left: 6rpx solid #f59e0b;
}
.mat-row.done {
  border-left: 6rpx solid #22c55e;
  opacity: 0.85;
}
.mat-row.flash {
  animation: row-flash 0.6s ease;
  border-color: #3b82f6;
}
@keyframes row-flash {
  50% { background: #eff6ff; }
}

.row-main {
  flex: 1;
  min-width: 0;
}
.mat-code {
  display: block;
  font-size: 26rpx;
  font-weight: 700;
  color: #0f172a;
  font-family: monospace;
}
.mat-name {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: #334155;
  margin-top: 4rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mat-spec {
  display: block;
  font-size: 22rpx;
  color: #64748b;
  margin-top: 4rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-qty {
  flex-shrink: 0;
  text-align: right;
}
.qty-num {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
  color: #1d4ed8;
}
.qty-unit {
  display: block;
  font-size: 20rpx;
  color: #94a3b8;
  margin-top: 2rpx;
}

.list-empty {
  padding: 120rpx 0;
  text-align: center;
}
.empty-icon {
  font-size: 64rpx;
  opacity: 0.25;
}

.order-wh {
  margin-top: 8rpx;
  background: #fff;
  border-radius: 12rpx;
  border: 1rpx solid #e2e8f0;
  padding: 16rpx 20rpx;
  margin-bottom: 12rpx;
  font-size: 24rpx;
  color: #1d4ed8;
  font-weight: 600;
}

.scroll-bottom-pad {
  height: 140rpx;
}

.footer {
  flex-shrink: 0;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background: #fff;
  border-top: 1rpx solid #e2e8f0;
  box-shadow: 0 -2rpx 12rpx rgba(0, 0, 0, 0.04);
}
.finish-btn {
  background: #1d4ed8;
  color: #fff;
  border-radius: 12rpx;
  font-weight: 600;
}
</style>
