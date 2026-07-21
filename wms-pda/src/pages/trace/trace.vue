<template>
  <view class="page">
    <view class="search-bar">
      <input v-model="materialCode" class="input" placeholder="物料编码" />
      <input v-model="batchNo" class="input" placeholder="批次号" />
      <input v-model="barcode" class="input" placeholder="条码(可选)" />
      <button class="btn" @click="search">追溯查询</button>
      <button class="btn scan" @click="scanTrace">扫码追溯</button>
    </view>
    <view v-if="result.materialCode" class="summary">
      <text class="title">{{ result.materialName || result.materialCode }}</text>
      <text>批次: {{ result.batchNo }} · 当前库存: {{ result.currentStock ?? '-' }}</text>
      <text>当前库位: {{ result.currentLocation || '-' }}</text>
    </view>
    <view v-for="row in result.traceRecords || []" :key="row.seq" class="card">
      <text class="type">{{ row.transactionType }} · {{ row.operationTime }}</text>
      <text>库位: {{ row.locationCode }} · 数量: {{ row.qty }}</text>
      <text>单据: {{ row.sourceOrderNo || '-' }} · 操作人: {{ row.operatorName || '-' }}</text>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { traceBatch } from '@/api/mobile.js'
import { scanAndParse } from '@/utils/scan.js'

const materialCode = ref('')
const batchNo = ref('')
const barcode = ref('')
const result = reactive({ traceRecords: [] })

async function search() {
  const data = await traceBatch({
    materialCode: materialCode.value || undefined,
    batchNo: batchNo.value || undefined,
    barcode: barcode.value || undefined,
  })
  Object.assign(result, data)
  result.traceRecords = data.traceRecords || []
}

async function scanTrace() {
  try {
    const parsed = await scanAndParse('扫描追溯条码')
    materialCode.value = parsed.materialCode
    batchNo.value = parsed.batchNo
    barcode.value = parsed.barcodeContent
    await search()
  } catch {
    // cancel
  }
}
</script>

<style scoped>
.page { padding: 24rpx; }
.search-bar { background: #fff; padding: 24rpx; border-radius: 12rpx; margin-bottom: 24rpx; }
.input { border: 1px solid #e2e8f0; border-radius: 8rpx; padding: 16rpx; margin-bottom: 16rpx; }
.btn { background: #1d4ed8; color: #fff; margin-bottom: 12rpx; }
.btn.scan { background: #0ea5e9; }
.summary { background: #eff6ff; padding: 24rpx; border-radius: 12rpx; margin-bottom: 16rpx; }
.title { font-weight: bold; display: block; margin-bottom: 8rpx; }
.card { background: #fff; padding: 24rpx; border-radius: 12rpx; margin-bottom: 16rpx; }
.type { font-weight: bold; display: block; margin-bottom: 8rpx; }
</style>
