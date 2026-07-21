<template>
  <view class="page">
    <view class="form-card">
      <text class="label">板码</text>
      <input v-model="form.panelCode" class="input" placeholder="扫描或输入板码 (PLT/BM/P开头)" />
      <button size="mini" class="scan-btn" @click="scanPanel">扫码输入</button>

      <text class="label">物料编码（可选，交叉校验）</text>
      <input v-model="form.materialCode" class="input" placeholder="物料编码" />

      <text class="label">批次号（可选）</text>
      <input v-model="form.batchNo" class="input" placeholder="批次号" />

      <text class="label">仓库（可选）</text>
      <input v-model="form.warehouseCode" class="input" placeholder="WH01" />

      <button class="verify-btn" type="primary" :loading="loading" @click="doVerify">开始校验</button>
    </view>

    <view v-if="result" :class="['result-card', result.valid ? 'pass' : 'fail']">
      <text class="result-icon">{{ result.valid ? '✅' : '❌' }}</text>
      <text class="result-title">{{ result.valid ? '校验通过' : '校验失败' }}</text>
      <text class="result-msg">{{ result.message }}</text>
      <view class="result-detail">
        <text>板码: {{ result.panelCode }}</text>
        <text v-if="result.materialName">物料: {{ result.materialName }} ({{ result.parsedMaterialCode }})</text>
        <text v-else-if="result.parsedMaterialCode">解析物料: {{ result.parsedMaterialCode }}</text>
        <text v-if="result.parsedBatchNo">解析批次: {{ result.parsedBatchNo }}</text>
        <text v-if="result.totalStockQty != null">库存合计: {{ result.totalStockQty }}</text>
        <text class="time">校验时间: {{ result.verifyTime }}</text>
      </view>
    </view>

    <view v-if="result?.stocks?.length" class="stock-section">
      <text class="section-title">匹配库存</text>
      <view v-for="(s, i) in result.stocks" :key="i" class="stock-card">
        <text>{{ s.materialCode }} · {{ s.batchNo || '-' }}</text>
        <text>仓库 {{ s.warehouseCode }} · 库位 {{ s.locationCode }}</text>
        <text>库存 {{ s.stockQty }} / 可用 {{ s.availableQty }}</text>
      </view>
    </view>

    <view v-if="history.length" class="history-section">
      <text class="section-title">最近校验</text>
      <view v-for="(h, i) in history" :key="i" class="history-item">
        <text :class="h.valid ? 'ok' : 'ng'">{{ h.valid ? 'PASS' : 'FAIL' }}</text>
        <text>{{ h.panelCode }}</text>
        <text class="time">{{ h.verifyTime }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { verifyPanelCode } from '@/api/mobile.js'
import { scanCode } from '@/utils/scan.js'

const form = reactive({
  panelCode: '',
  materialCode: '',
  batchNo: '',
  warehouseCode: '',
})
const loading = ref(false)
const result = ref(null)
const history = ref(uni.getStorageSync('panel_verify_history') || [])

async function scanPanel() {
  try {
    form.panelCode = await scanCode('扫描板码')
  } catch {
    // cancel
  }
}

async function doVerify() {
  if (!form.panelCode?.trim()) {
    uni.showToast({ title: '请输入板码', icon: 'none' })
    return
  }
  loading.value = true
  try {
    const data = await verifyPanelCode({
      panelCode: form.panelCode.trim(),
      materialCode: form.materialCode || undefined,
      batchNo: form.batchNo || undefined,
      warehouseCode: form.warehouseCode || undefined,
    })
    result.value = data
    const record = {
      panelCode: data.panelCode,
      valid: data.valid,
      verifyTime: data.verifyTime,
    }
    history.value = [record, ...history.value.filter((h) => h.panelCode !== record.panelCode)].slice(0, 10)
    uni.setStorageSync('panel_verify_history', history.value)
    uni.showToast({
      title: data.valid ? '校验通过' : '校验失败',
      icon: data.valid ? 'success' : 'none',
    })
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.page { padding: 24rpx; }
.form-card {
  background: #fff;
  padding: 24rpx;
  border-radius: 12rpx;
  margin-bottom: 24rpx;
}
.label { display: block; font-weight: bold; font-size: 26rpx; margin: 16rpx 0 8rpx; color: #475569; }
.input {
  border: 1px solid #e2e8f0;
  border-radius: 8rpx;
  padding: 16rpx;
  margin-bottom: 8rpx;
}
.scan-btn { margin-bottom: 16rpx; background: #eff6ff; color: #1d4ed8; }
.verify-btn { background: #1d4ed8; color: #fff; margin-top: 24rpx; }

.result-card {
  border-radius: 16rpx;
  padding: 32rpx;
  margin-bottom: 24rpx;
  text-align: center;
}
.result-card.pass { background: #f0fdf4; border: 2rpx solid #22c55e; }
.result-card.fail { background: #fef2f2; border: 2rpx solid #ef4444; }
.result-icon { font-size: 64rpx; display: block; margin-bottom: 12rpx; }
.result-title { font-size: 36rpx; font-weight: bold; display: block; margin-bottom: 8rpx; }
.result-msg { color: #475569; font-size: 26rpx; display: block; margin-bottom: 16rpx; }
.result-detail { text-align: left; font-size: 24rpx; color: #64748b; }
.result-detail text { display: block; margin-top: 6rpx; }
.time { color: #94a3b8; font-size: 22rpx; margin-top: 12rpx; }

.section-title { font-weight: bold; margin-bottom: 16rpx; display: block; }
.stock-card {
  background: #fff;
  padding: 20rpx;
  border-radius: 12rpx;
  margin-bottom: 12rpx;
  font-size: 24rpx;
  color: #475569;
}
.stock-card text { display: block; margin-top: 4rpx; }

.history-item {
  display: flex;
  align-items: center;
  gap: 16rpx;
  background: #fff;
  padding: 16rpx 20rpx;
  border-radius: 8rpx;
  margin-bottom: 8rpx;
  font-size: 24rpx;
}
.ok { color: #22c55e; font-weight: bold; }
.ng { color: #ef4444; font-weight: bold; }
.history-item .time { margin-left: auto; color: #94a3b8; font-size: 22rpx; }
</style>
