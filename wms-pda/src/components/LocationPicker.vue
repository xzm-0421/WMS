<template>
  <view class="location-picker" :class="theme">
    <view class="mode-tabs">
      <text :class="['tab', mode === 'auto' && 'active']" @click="setMode('auto')">自动分配</text>
      <text :class="['tab', mode === 'manual' && 'active']" @click="setMode('manual')">自选库位</text>
    </view>

    <view v-if="mode === 'auto'" class="auto-panel">
      <view class="loc-display">
        <text class="loc-label">分配库位</text>
        <text class="loc-code">{{ recommended.locationCode || (loading ? '分配中...' : '-') }}</text>
        <text v-if="recommended.message" class="loc-hint">{{ recommended.message }}</text>
      </view>
      <button size="mini" class="refresh-btn" :loading="loading" @click="fetchAllocate">刷新</button>
    </view>

    <view v-else class="manual-panel">
      <picker
        v-if="locationOptions.length"
        :range="locationOptions"
        range-key="label"
        @change="onPickerChange"
      >
        <view class="picker-value">{{ manualLabel || '选择库位' }} ▾</view>
      </picker>
      <input
        v-model="manualCode"
        class="manual-input"
        placeholder="或输入/扫描库位码"
        @blur="onManualInput"
      />
      <button size="mini" class="scan-loc-btn" @click="scanLocation">扫库位</button>
      <text v-if="recommended.locationCode" class="loc-confirm">已选: {{ recommended.locationCode }}</text>
    </view>
  </view>
</template>

<script setup>
import { useLocationPicker } from '@/composables/useLocationPicker.js'

const props = defineProps({
  warehouseCode: { type: String, default: 'WH01' },
  materialCode: { type: String, default: '' },
  batchNo: { type: String, default: '' },
  theme: { type: String, default: 'dark' },
})

const emit = defineEmits(['change'])

const {
  mode,
  loading,
  manualCode,
  manualLabel,
  locationOptions,
  recommended,
  setMode,
  fetchAllocate,
  onPickerChange,
  onManualInput,
  scanLocation,
  getPayload,
} = useLocationPicker(props, emit)

defineExpose({ getPayload, refresh: fetchAllocate, setMode })
</script>

<style scoped>
.location-picker {
  margin-top: 12rpx;
  padding-top: 12rpx;
  border-top: 1px solid #334155;
}
.mode-tabs { display: flex; gap: 12rpx; margin-bottom: 12rpx; }
.tab {
  flex: 1; text-align: center; padding: 10rpx 0;
  font-size: 22rpx; color: #94a3b8; background: #1e293b; border-radius: 8rpx;
}
.tab.active { background: #1d4ed8; color: #fff; }
.auto-panel { display: flex; align-items: center; gap: 12rpx; }
.loc-display { flex: 1; }
.loc-label { font-size: 20rpx; color: #64748b; display: block; }
.loc-code { font-size: 26rpx; color: #60a5fa; font-weight: 600; display: block; }
.loc-hint { font-size: 20rpx; color: #64748b; display: block; margin-top: 4rpx; }
.refresh-btn { background: #334155; color: #e2e8f0; }
.manual-panel { display: flex; flex-direction: column; gap: 10rpx; }
.picker-value {
  background: #1e293b; color: #f8fafc;
  padding: 14rpx 16rpx; border-radius: 8rpx; font-size: 24rpx;
}
.manual-input {
  background: #1e293b; color: #f8fafc;
  padding: 14rpx 16rpx; border-radius: 8rpx; font-size: 24rpx; border: 1px solid #475569;
}
.scan-loc-btn { align-self: flex-start; background: #334155; color: #e2e8f0; }
.loc-confirm { font-size: 22rpx; color: #60a5fa; }

.location-picker.light .tab { background: #f1f5f9; color: #64748b; }
.location-picker.light .tab.active { background: #1d4ed8; color: #fff; }
.location-picker.light .loc-code, .location-picker.light .loc-confirm { color: #1d4ed8; }
.location-picker.light .picker-value,
.location-picker.light .manual-input { background: #fff; color: #0f172a; border: 1px solid #e2e8f0; }
.location-picker.light { border-top: none; padding-top: 0; }
.location-picker.light .refresh-btn,
.location-picker.light .scan-loc-btn { background: #eff6ff; color: #1d4ed8; }
</style>
