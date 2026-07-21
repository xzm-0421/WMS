<template>
  <view class="page">
    <view class="scan-bar-fixed">
      <view class="scan-bar-inner">
        <text class="scan-title">扫码出库 · 扫即出</text>
        <ScanInput
          ref="scanInputRef"
          :disabled="processing"
          placeholder="扫描物料条码"
          @scan="onScan"
        />
        <view class="info-row">
          <text>数量</text>
          <text class="info-val">{{ OUTBOUND_SCAN_QTY }}/次</text>
          <text class="info-divider">|</text>
          <text>已出</text>
          <text class="info-val">{{ scanCount }}</text>
        </view>
      </view>
    </view>
    <view class="scan-bar-spacer" />

    <!-- 出库确认面板 -->
    <view v-if="pendingConfirm" class="confirm-panel">
      <text class="confirm-title">确认出库</text>
      <text class="confirm-name">{{ pendingConfirm.preview.materialName }}</text>
      <text class="confirm-row">物料 {{ pendingConfirm.preview.materialCode }}</text>
      <text class="confirm-row">规格 {{ pendingConfirm.preview.specification || '-' }}</text>
      <text class="confirm-row">库位 {{ pendingConfirm.preview.recommendedLocation }}</text>
      <text class="confirm-row">批次 {{ pendingConfirm.preview.recommendedBatchNo || '-' }}</text>
      <text class="confirm-stock">
        可用 {{ pendingConfirm.preview.recommendedAvailableQty }}
        / 总 {{ pendingConfirm.preview.totalAvailableQty }}
      </text>
      <view class="confirm-btns">
        <button class="btn-confirm" type="warn" :loading="processing" @click="onConfirm">确认出库</button>
        <button class="btn-cancel" @click="onCancel">重新扫描</button>
      </view>
    </view>

    <view v-else class="hint-card">
      <text class="hint-icon">📤</text>
      <text class="hint-title">扫描物料条码</text>
      <text class="hint-desc">系统自动匹配库存，点击确认即可完成出库</text>
    </view>

    <view v-if="scanLog.length" class="log-section">
      <view class="log-header">
        <text class="log-title">出库记录</text>
        <text class="log-clear" @click="clearLog">清空</text>
      </view>
      <view v-for="(item, i) in scanLog" :key="i" :class="['log-item', item.ok ? 'ok' : 'fail']">
        <text>{{ item.time }} {{ item.barcode || '' }}</text>
        <text>{{ item.msg }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import ScanInput from '@/components/ScanInput.vue'
import useScanOutbound, { OUTBOUND_SCAN_QTY } from '@/composables/useScanOutbound.js'
import usePageAlive from '@/composables/usePageAlive.js'

const scanInputRef = ref(null)
const warehouseCode = ref('WH01')
const { alive, refocusScanInput } = usePageAlive()

const {
  processing,
  pendingConfirm,
  scanLog,
  scanCount,
  handleScan,
  confirmOutbound,
  cancelConfirm,
  clearLog,
} = useScanOutbound()

async function onScan(barcode) {
  if (!alive.value) return
  await handleScan(barcode, warehouseCode.value)
  if (!alive.value) return
  if (!pendingConfirm.value) {
    refocusScanInput(scanInputRef, 300)
  }
}

async function onConfirm() {
  if (!alive.value) return
  await confirmOutbound()
  if (!alive.value) return
  refocusScanInput(scanInputRef, 300)
}

function onCancel() {
  cancelConfirm()
  refocusScanInput(scanInputRef, 300)
}

onLoad(() => uni.setNavigationBarTitle({ title: '扫码出库' }))

let shownOnce = false
onShow(() => {
  if (!shownOnce) {
    shownOnce = true
    refocusScanInput(scanInputRef, 500)
  }
})
</script>

<style scoped>
.page { padding: 0 24rpx 48rpx; }
.scan-bar-fixed {
  position: fixed; left: 0; right: 0; top: var(--window-top, 44px); z-index: 200;
  background: linear-gradient(180deg, #1e293b, #0f172a);
  padding: 16rpx 24rpx 20rpx; box-shadow: 0 4rpx 24rpx rgba(0,0,0,0.15);
}
.scan-bar-inner { max-width: 750rpx; margin: 0 auto; }
.scan-title { display: block; text-align: center; color: #94a3b8; font-size: 22rpx; margin-bottom: 12rpx; }
.scan-bar-spacer { height: 200rpx; }
.info-row {
  display: flex; align-items: center; justify-content: center; gap: 12rpx;
  margin-top: 16rpx; font-size: 24rpx; color: #94a3b8;
}
.info-val { color: #f97316; font-weight: 600; font-size: 26rpx; }
.info-divider { color: #475569; }

.hint-card {
  background: #fff; border-radius: 16rpx; padding: 48rpx 32rpx;
  text-align: center; margin-bottom: 20rpx;
}
.hint-icon { font-size: 56rpx; display: block; margin-bottom: 12rpx; }
.hint-title { font-size: 30rpx; font-weight: bold; display: block; }
.hint-desc { font-size: 24rpx; color: #64748b; display: block; margin-top: 8rpx; }

.confirm-panel {
  background: #fffbeb; border: 2rpx solid #f59e0b; border-radius: 16rpx;
  padding: 28rpx; margin-bottom: 20rpx;
}
.confirm-title { font-weight: bold; font-size: 28rpx; color: #d97706; display: block; }
.confirm-name { font-size: 32rpx; font-weight: bold; margin: 12rpx 0; display: block; }
.confirm-row { font-size: 26rpx; color: #475569; display: block; margin-top: 6rpx; }
.confirm-stock { font-size: 26rpx; color: #1d4ed8; font-weight: bold; margin-top: 12rpx; display: block; }
.confirm-btns { display: flex; gap: 16rpx; margin-top: 24rpx; }
.btn-confirm { flex: 1; background: #f97316; color: #fff; }
.btn-cancel { flex: 1; background: #e2e8f0; color: #334155; }

.log-section { background: #fff; border-radius: 16rpx; padding: 24rpx; }
.log-header { display: flex; justify-content: space-between; margin-bottom: 12rpx; }
.log-title { font-weight: bold; }
.log-clear { color: #94a3b8; font-size: 24rpx; }
.log-item { padding: 12rpx 16rpx; border-radius: 8rpx; margin-bottom: 8rpx; font-size: 24rpx; }
.log-item.ok { background: #fff7ed; color: #c2410c; }
.log-item.fail { background: #fef2f2; color: #dc2626; }
.log-item text { display: block; }
</style>
