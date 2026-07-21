<template>
  <view class="page">
    <view class="tabs">
      <text :class="['tab', mode === 'code' && 'active']" @click="mode = 'code'">编码查询</text>
      <text :class="['tab', mode === 'barcode' && 'active']" @click="mode = 'barcode'">扫码查询</text>
    </view>
    <view class="search-bar">
      <input v-if="mode === 'code'" v-model="materialCode" class="input" placeholder="物料编码" />
      <input v-model="warehouseCode" class="input" placeholder="仓库编码(可选)" />
      <input v-if="mode === 'barcode'" v-model="barcode" class="input" placeholder="条码内容" />
      <button class="btn" @click="search">查询</button>
      <button v-if="mode === 'barcode'" class="btn scan" @click="scanSearch">扫码查询</button>
    </view>
    <view v-if="summary.materialCode" class="summary">
      <text class="title">{{ summary.materialName || summary.materialCode }}</text>
      <text>总库存: {{ summary.totalStockQty }} / 可用: {{ summary.totalAvailableQty }}</text>
    </view>
    <view v-for="(item, idx) in list" :key="idx" class="card">
      <text class="title">{{ item.materialCode || summary.materialCode }}</text>
      <text>仓库: {{ item.warehouseCode }} · 库位: {{ item.locationCode }}</text>
      <text>批次: {{ item.batchNo }} · 库存: {{ item.stockQty || item.totalStockQty }}</text>
    </view>
    <view v-if="searched && !list.length && !summary.materialCode" class="empty">无库存数据</view>
  </view>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { queryInventoryGet, queryInventoryPost } from '@/api/mobile.js'
import { scanCode } from '@/utils/scan.js'

const mode = ref('code')
const materialCode = ref('')
const warehouseCode = ref('')
const barcode = ref('')
const list = ref([])
const summary = reactive({})
const searched = ref(false)

async function search() {
  searched.value = true
  Object.keys(summary).forEach((k) => delete summary[k])
  if (mode.value === 'barcode') {
    const res = await queryInventoryPost({
      barcode: barcode.value,
      queryType: 'MATERIAL',
      warehouseCode: warehouseCode.value || undefined,
    })
    Object.assign(summary, res)
    list.value = res.stocks || []
  } else {
    const res = await queryInventoryGet({
      materialCode: materialCode.value || undefined,
      warehouseCode: warehouseCode.value || undefined,
    })
    list.value = Array.isArray(res) ? res : (res?.records || [])
  }
}

async function scanSearch() {
  try {
    barcode.value = await scanCode('扫描库存条码')
    mode.value = 'barcode'
    await search()
  } catch {
    // cancel
  }
}
</script>

<style scoped>
.page { padding: 24rpx; }
.tabs { display: flex; gap: 24rpx; margin-bottom: 16rpx; }
.tab { padding: 12rpx 24rpx; background: #e2e8f0; border-radius: 8rpx; font-size: 26rpx; }
.tab.active { background: #1d4ed8; color: #fff; }
.search-bar { background: #fff; padding: 24rpx; border-radius: 12rpx; margin-bottom: 24rpx; }
.input { border: 1px solid #e2e8f0; border-radius: 8rpx; padding: 16rpx; margin-bottom: 16rpx; }
.btn { background: #1d4ed8; color: #fff; margin-bottom: 12rpx; }
.btn.scan { background: #0ea5e9; }
.summary { background: #eff6ff; padding: 24rpx; border-radius: 12rpx; margin-bottom: 16rpx; }
.card { background: #fff; padding: 24rpx; border-radius: 12rpx; margin-bottom: 16rpx; }
.title { font-weight: bold; display: block; margin-bottom: 8rpx; }
.empty { text-align: center; color: #94a3b8; padding: 40rpx; }
</style>
