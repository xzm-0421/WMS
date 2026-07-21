<template>
  <view class="workbench">
    <!-- 固定置顶扫码区 -->
    <view class="scan-bar-fixed">
      <view class="scan-bar-inner">
        <text class="scan-title">
          {{ mode === 'inbound' ? '扫码入库' : '扫码出库' }}
        </text>
        <ScanInput
          ref="scanInputRef"
          :disabled="processing"
          :placeholder="mode === 'inbound' ? '对准条码扫描' : '对准条码扫描'"
          @scan="onScan"
        />
        <view v-if="mode === 'inbound'" class="info-row">
          <text>入库数量</text>
          <text class="info-val">{{ INBOUND_SCAN_QTY }} / 次</text>
          <text class="info-divider">|</text>
          <text>已扫</text>
          <text class="info-val">{{ scanCount }} 件</text>
        </view>
        <view v-else class="qty-row">
          <text>出库数量</text>
          <text class="info-val">1 / 次</text>
        </view>
        <text v-if="mode === 'inbound'" class="wh-hint">入库仓库 {{ warehouseCode || 'WH01' }}</text>
      </view>
    </view>

    <!-- 占位，避免内容被固定栏遮挡 -->
    <view class="scan-bar-spacer" />

    <view class="workbench-body">
      <!-- 出库确认面板 -->
      <view v-if="pendingConfirm" class="confirm-panel">
        <text class="confirm-title">出库确认</text>
        <text class="confirm-name">{{ pendingConfirm.preview.materialName }}</text>
        <text class="confirm-spec">规格: {{ pendingConfirm.preview.specification || '-' }}</text>
        <text class="confirm-row">物料: {{ pendingConfirm.preview.materialCode }}</text>
        <text class="confirm-row">批次: {{ pendingConfirm.preview.batchNo || '-' }}</text>
        <text class="confirm-row">库位: {{ pendingConfirm.preview.sourceLocation }}</text>
        <text class="confirm-row">出库数量: {{ pendingConfirm.preview.quantity }}</text>
        <text class="confirm-stock">
          当前可用库存: {{ pendingConfirm.preview.recommendedAvailableQty }}
          / 总可用: {{ pendingConfirm.preview.totalAvailableQty }}
        </text>
        <view class="confirm-btns">
          <button size="mini" type="warn" :loading="processing" @click="onConfirm">确认出库</button>
          <button size="mini" @click="onCancelConfirm">取消</button>
        </view>
      </view>

      <!-- 扫码记录 -->
      <view class="log-section">
        <view class="log-header">
          <text class="log-title">扫码记录 ({{ scanLog.length }})</text>
          <text class="log-clear" @click="clearLog">清空</text>
        </view>
        <view v-for="(item, i) in scanLog" :key="i" :class="['log-item', item.ok ? 'ok' : 'fail']">
          <text>{{ item.time }} · {{ item.barcode }}</text>
          <text>{{ item.msg }}</text>
        </view>
        <view v-if="!scanLog.length" class="log-empty">扫描条码后将自动录入</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import ScanInput from '@/components/ScanInput.vue'
import { useScanFlow, INBOUND_SCAN_QTY } from '@/composables/useScanFlow.js'

const props = defineProps({
  mode: { type: String, required: true },
  orderNo: { type: String, required: true },
  warehouseCode: { type: String, default: '' },
})

const emit = defineEmits(['success', 'error'])

const scanInputRef = ref(null)

const {
  processing,
  scanLog,
  pendingConfirm,
  scanCount,
  lastMaterialCode,
  handleScan,
  confirmOutbound,
  cancelConfirm,
  clearLog,
} = useScanFlow(props.mode, props.orderNo)

let refocusTimer = null

function refocusScan() {
  if (refocusTimer) clearTimeout(refocusTimer)
  refocusTimer = setTimeout(() => {
    scanInputRef.value?.focusInput?.()
    refocusTimer = null
  }, 400)
}

async function onScan(barcode) {
  try {
    const result = await handleScan(barcode, props.warehouseCode)
    if (result && !result.preview) {
      emit('success', result)
      if (props.mode === 'inbound' && result.materialCode) {
        locationPickerRef.value?.refresh?.()
      }
    }
  } catch (e) {
    emit('error', e)
  }
}

async function onConfirm() {
  try {
    const result = await confirmOutbound()
    emit('success', result)
  } catch (e) {
    emit('error', e)
  } finally {
    refocusScan()
  }
}

function onCancelConfirm() {
  cancelConfirm()
  refocusScan()
}

let shownOnce = false
onShow(() => {
  if (!shownOnce) {
    shownOnce = true
    setTimeout(() => scanInputRef.value?.focusInput?.(), 500)
  }
})

defineExpose({ focusInput: () => scanInputRef.value?.focusInput?.() })
</script>

<style scoped>
.workbench { position: relative; margin-bottom: 24rpx; }

.scan-bar-fixed {
  position: fixed;
  left: 0;
  right: 0;
  top: var(--window-top, 44px);
  z-index: 200;
  background: linear-gradient(180deg, #1e293b 0%, #0f172a 100%);
  padding: 16rpx 24rpx 20rpx;
  box-shadow: 0 4rpx 24rpx rgba(0, 0, 0, 0.15);
}
.scan-bar-inner { max-width: 750rpx; margin: 0 auto; }
.scan-title {
  display: block;
  color: #94a3b8;
  font-size: 22rpx;
  margin-bottom: 12rpx;
  text-align: center;
}
.info-row, .qty-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  margin-top: 16rpx;
  font-size: 24rpx;
  color: #94a3b8;
}
.info-val { color: #60a5fa; font-weight: 600; font-size: 26rpx; }
.info-divider { color: #475569; }
.wh-hint {
  display: block;
  margin-top: 12rpx;
  text-align: center;
  font-size: 24rpx;
  color: #1d4ed8;
  font-weight: 600;
}

.scan-bar-spacer { height: 220rpx; }

.workbench-body { padding: 0 0 16rpx; }

.confirm-panel {
  background: #fffbeb;
  border: 2rpx solid #f59e0b;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 16rpx;
}
.confirm-title { font-weight: bold; font-size: 28rpx; color: #d97706; display: block; }
.confirm-name { font-size: 32rpx; font-weight: bold; margin: 12rpx 0; display: block; }
.confirm-spec, .confirm-row { font-size: 26rpx; color: #475569; display: block; margin-top: 6rpx; }
.confirm-stock { font-size: 26rpx; color: #1d4ed8; font-weight: bold; margin-top: 12rpx; display: block; }
.confirm-btns { display: flex; gap: 16rpx; margin-top: 20rpx; }

.log-section {
  background: #fff;
  border-radius: 16rpx;
  padding: 24rpx;
}
.log-header { display: flex; justify-content: space-between; margin-bottom: 12rpx; }
.log-title { font-weight: bold; font-size: 26rpx; }
.log-clear { color: #94a3b8; font-size: 24rpx; }
.log-item {
  padding: 12rpx 16rpx;
  border-radius: 8rpx;
  margin-bottom: 8rpx;
  font-size: 24rpx;
}
.log-item.ok { background: #f0fdf4; color: #15803d; }
.log-item.fail { background: #fef2f2; color: #dc2626; }
.log-item text { display: block; }
.log-empty { text-align: center; color: #94a3b8; padding: 24rpx; font-size: 24rpx; }
</style>
