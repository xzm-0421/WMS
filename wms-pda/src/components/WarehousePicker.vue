<template>
  <view class="warehouse-picker" :class="theme">
    <view class="mode-tabs">
      <text :class="['tab', mode === 'auto' && 'active']" @click="setMode('auto')">自动分配</text>
      <text :class="['tab', mode === 'manual' && 'active']" @click="setMode('manual')">手动选仓</text>
    </view>

    <view v-if="mode === 'auto'" class="auto-panel">
      <view class="wh-display">
        <text class="wh-label">物料仓库</text>
        <text class="wh-code">{{ recommended.label || '-' }}</text>
        <text class="wh-hint">按物料仓库或生产订单仓库自动分配，跳过未分配仓</text>
      </view>
    </view>

    <view v-else class="manual-panel">
      <picker
        v-if="warehouseOptions.length"
        :value="manualIndex < 0 ? 0 : manualIndex"
        :range="warehouseOptions"
        range-key="label"
        @change="onPickerChange"
      >
        <view class="picker-value">{{ recommended.label || '选择仓库' }} ▾</view>
      </picker>
      <text v-else class="wh-empty">{{ loading ? '加载仓库...' : '暂无可用仓库' }}</text>
      <button size="mini" class="refresh-btn" :loading="loading" @click="loadWarehouseList">刷新</button>
    </view>
  </view>
</template>

<script setup>
import { useWarehousePicker } from '@/composables/useWarehousePicker.js'

const props = defineProps({
  suggestCode: { type: String, default: '' },
  suggestName: { type: String, default: '' },
  theme: { type: String, default: 'light' },
})

const emit = defineEmits(['change'])

const {
  mode,
  loading,
  warehouseOptions,
  manualIndex,
  recommended,
  setMode,
  loadWarehouseList,
  onPickerChange,
  getPayload,
} = useWarehousePicker(props, emit)

defineExpose({ getPayload, setMode })
</script>

<style scoped>
.warehouse-picker {
  background: #fff;
  border-radius: 12rpx;
  border: 1rpx solid #e2e8f0;
  padding: 16rpx;
  margin-bottom: 12rpx;
}
.mode-tabs { display: flex; gap: 12rpx; margin-bottom: 12rpx; }
.tab {
  flex: 1; text-align: center; padding: 10rpx 0;
  font-size: 22rpx; color: #64748b; background: #f1f5f9; border-radius: 8rpx;
}
.tab.active { background: #1d4ed8; color: #fff; }
.auto-panel, .manual-panel { display: flex; align-items: center; gap: 12rpx; }
.wh-display { flex: 1; }
.wh-label { font-size: 20rpx; color: #94a3b8; display: block; }
.wh-code { font-size: 28rpx; color: #1d4ed8; font-weight: 700; display: block; }
.wh-hint { font-size: 20rpx; color: #94a3b8; display: block; margin-top: 4rpx; }
.picker-value {
  flex: 1; padding: 14rpx 16rpx; background: #f8fafc;
  border: 1rpx solid #e2e8f0; border-radius: 10rpx; font-size: 24rpx; color: #0f172a;
}
.wh-empty { flex: 1; font-size: 22rpx; color: #94a3b8; }
.refresh-btn { background: #eff6ff; color: #1d4ed8; }
</style>
