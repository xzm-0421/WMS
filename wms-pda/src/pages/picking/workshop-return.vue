<template>
  <view class="page">
    <ScanInput
      v-model="scanCode"
      placeholder="扫描物料条码 / 发料单号"
      @scan="onScan"
    />
    <view class="form card">
      <view class="field">
        <text class="label">原发料单</text>
        <input v-model="form.issueNo" class="input" placeholder="PI..." />
      </view>
      <view class="field">
        <text class="label">仓库</text>
        <input v-model="form.warehouseCode" class="input" />
      </view>
      <view class="field">
        <text class="label">物料编码</text>
        <input v-model="form.materialCode" class="input" />
      </view>
      <view class="field">
        <text class="label">批次</text>
        <input v-model="form.batchNo" class="input" />
      </view>
      <view class="field">
        <text class="label">退库原因</text>
        <input v-model="form.returnReason" class="input" />
      </view>
    </view>
    <button class="btn primary" @click="submit">确认退库（1件/次）</button>
    <view v-if="lastNo" class="tip">退库单号：{{ lastNo }}</view>
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import ScanInput from '@/components/ScanInput.vue'
import { submitWorkshopReturn } from '@/api/picking.js'

const scanCode = ref('')
const lastNo = ref('')
const form = reactive({
  issueNo: '',
  warehouseCode: 'WH001',
  materialCode: '',
  batchNo: '',
  returnReason: '余料退库',
})

function onScan(code) {
  const c = (code || '').trim()
  if (c.startsWith('PI:') || c.startsWith('PI')) {
    form.issueNo = c.startsWith('PI:') ? c.slice(3) : c
    return
  }
  const parts = c.split('|')
  form.materialCode = parts[0] || c
  if (parts[1]) form.batchNo = parts[1]
}

async function submit() {
  if (!form.materialCode) {
    uni.showToast({ title: '请扫描物料', icon: 'none' })
    return
  }
  const returnNo = await submitWorkshopReturn({
    ...form,
    returnQty: 1,
    autoConfirm: true,
  })
  lastNo.value = returnNo
  uni.showToast({ title: '退库成功', icon: 'success' })
  form.materialCode = ''
  form.batchNo = ''
}
</script>

<style scoped>
.page { padding: 24rpx; }
.card { background: #fff; border-radius: 12rpx; padding: 24rpx; margin-top: 24rpx; }
.field { margin-bottom: 20rpx; }
.label { display: block; font-size: 26rpx; color: #64748b; margin-bottom: 8rpx; }
.input { border: 1px solid #e2e8f0; border-radius: 8rpx; padding: 16rpx; font-size: 28rpx; }
.btn { margin-top: 32rpx; }
.primary { background: #1d4ed8; color: #fff; }
.tip { margin-top: 24rpx; color: #16a34a; font-size: 26rpx; }
</style>
