<template>
  <view class="location-picker" :class="theme">
    <view class="mode-tabs">
      <text :class="['tab', mode === 'none' && 'active']" @click="setMode('none')">不选库位</text>
      <text :class="['tab', mode === 'auto' && 'active']" @click="setMode('auto')">自动分配</text>
      <text :class="['tab', mode === 'manual' && 'active']" @click="setMode('manual')">自选库位</text>
    </view>

    <view v-if="mode === 'none'" class="none-panel">
      <view class="loc-display">
        <text class="loc-label">库位</text>
        <text class="loc-code">不指定</text>
        <text class="loc-hint">仅按仓库入库，库位可留空</text>
      </view>
    </view>

    <view v-else-if="mode === 'auto'" class="auto-panel">
      <view class="loc-display">
        <text class="loc-label">分配库位</text>
        <text class="loc-code">{{ recommended.locationCode || (loading ? '分配中...' : '无（仅仓库）') }}</text>
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
        <view class="picker-value">{{ manualLabel || '选择库位（可选）' }} ▾</view>
      </picker>
      <text v-else class="loc-empty">{{ warehouseCode ? '该仓库暂无库位，可不选' : '请先选择仓库' }}</text>
      <input
        v-model="manualCode"
        class="manual-input"
        placeholder="或输入/扫描库位码（可留空）"
        @blur="onManualInput"
      />
      <view class="manual-actions">
        <button size="mini" class="scan-loc-btn" @click="scanLocation">扫库位</button>
        <button v-if="manualCode" size="mini" class="clear-btn" @click="clearManual">清空</button>
      </view>
      <text v-if="recommended.locationCode" class="loc-confirm">已选: {{ recommended.locationCode }}</text>
      <text v-else class="loc-hint">未选库位时仅按仓库入库</text>
    </view>
  </view>
</template>

<script setup>
import { useLocationPicker } from '@/composables/useLocationPicker.js'

const props = defineProps({
  warehouseCode: { type: String, default: '' },
  materialCode: { type: String, default: '' },
  batchNo: { type: String, default: '' },
  theme: { type: String, default: 'light' },
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
  clearManual,
  scanLocation,
  getPayload,
} = useLocationPicker(props, emit)

defineExpose({ getPayload, refresh: fetchAllocate, setMode })
</script>

<style scoped>
.location-picker {
  background: #fff;
  border-radius: 12rpx;
  border: 1rpx solid #e2e8f0;
  padding: 16rpx;
  margin-bottom: 12rpx;
}
.mode-tabs { display: flex; gap: 8rpx; margin-bottom: 12rpx; }
.tab {
  flex: 1; text-align: center; padding: 10rpx 0;
  font-size: 20rpx; color: #64748b; background: #f1f5f9; border-radius: 8rpx;
}
.tab.active { background: #1d4ed8; color: #fff; }
.none-panel, .auto-panel { display: flex; align-items: center; gap: 12rpx; }
.loc-display { flex: 1; }
.loc-label { font-size: 20rpx; color: #94a3b8; display: block; }
.loc-code { font-size: 26rpx; color: #1d4ed8; font-weight: 600; display: block; }
.loc-hint { font-size: 20rpx; color: #94a3b8; display: block; margin-top: 4rpx; }
.loc-empty { font-size: 22rpx; color: #94a3b8; }
.refresh-btn { background: #eff6ff; color: #1d4ed8; }
.manual-panel { display: flex; flex-direction: column; gap: 10rpx; }
.picker-value {
  background: #f8fafc; color: #0f172a;
  padding: 14rpx 16rpx; border-radius: 8rpx; font-size: 24rpx;
  border: 1rpx solid #e2e8f0;
}
.manual-input {
  background: #fff; color: #0f172a;
  padding: 14rpx 16rpx; border-radius: 8rpx; font-size: 24rpx; border: 1px solid #e2e8f0;
}
.manual-actions { display: flex; gap: 12rpx; }
.scan-loc-btn { background: #eff6ff; color: #1d4ed8; }
.clear-btn { background: #f1f5f9; color: #64748b; }
.loc-confirm { font-size: 22rpx; color: #1d4ed8; }

.location-picker.dark {
  background: transparent;
  border: none;
  border-radius: 0;
  padding: 12rpx 0 0;
  border-top: 1px solid #334155;
  margin-bottom: 0;
}
.location-picker.dark .tab { background: #1e293b; color: #94a3b8; }
.location-picker.dark .tab.active { background: #1d4ed8; color: #fff; }
.location-picker.dark .loc-code,
.location-picker.dark .loc-confirm { color: #60a5fa; }
.location-picker.dark .picker-value,
.location-picker.dark .manual-input {
  background: #1e293b; color: #f8fafc; border: 1px solid #475569;
}
.location-picker.dark .refresh-btn,
.location-picker.dark .scan-loc-btn { background: #334155; color: #e2e8f0; }
.location-picker.dark .clear-btn { background: #334155; color: #94a3b8; }
</style>
