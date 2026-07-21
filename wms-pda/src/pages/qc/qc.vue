<template>
  <view class="page">
    <view v-if="order" class="header">
      <text>质检单: {{ order.qcNo }}</text>
      <text>物料: {{ order.materialCode }} · 抽样: {{ order.sampleQty }}</text>
    </view>
    <view class="form-card">
      <text class="label">质检结果</text>
      <radio-group @change="onResultChange">
        <label class="radio-item"><radio value="PASS" :checked="result === 'PASS'" />合格</label>
        <label class="radio-item"><radio value="FAIL" :checked="result === 'FAIL'" />不合格</label>
      </radio-group>
      <text class="label">备注</text>
      <textarea v-model="remark" class="textarea" placeholder="备注信息" />
      <button class="btn" type="primary" @click="submit">提交结果</button>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getQcOrder, submitQcResult } from '@/api/mobile.js'

const qcNo = ref('')
const order = ref(null)
const result = ref('PASS')
const remark = ref('')

onLoad((options) => {
  qcNo.value = options?.qcNo || ''
  loadOrder()
})

async function loadOrder() {
  if (!qcNo.value) return
  order.value = await getQcOrder(qcNo.value)
}

function onResultChange(e) {
  result.value = e.detail.value
}

async function submit() {
  await submitQcResult(qcNo.value, { result: result.value, remark: remark.value })
  uni.showToast({ title: '质检完成', icon: 'success' })
  setTimeout(() => uni.navigateBack(), 800)
}
</script>

<style scoped>
.page { padding: 24rpx; }
.header {
  background: #fff;
  padding: 24rpx;
  border-radius: 12rpx;
  margin-bottom: 24rpx;
}
.form-card {
  background: #fff;
  padding: 24rpx;
  border-radius: 12rpx;
}
.label { display: block; font-weight: bold; margin: 16rpx 0 8rpx; }
.radio-item { display: inline-flex; align-items: center; margin-right: 32rpx; }
.textarea {
  width: 100%;
  border: 1px solid #e2e8f0;
  border-radius: 8rpx;
  padding: 16rpx;
  min-height: 120rpx;
  box-sizing: border-box;
}
.btn { margin-top: 32rpx; background: #1d4ed8; color: #fff; }
</style>
