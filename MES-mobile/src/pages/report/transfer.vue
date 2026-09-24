<template>
  <view class="page">
    <view class="head" :style="{ paddingTop: statusBarHeight + 16 + 'px' }">
      <text class="title">工序转移</text>
      <text class="subtitle">同工作中心报工后自动转移，跨车间请在此手工提交</text>
    </view>

    <view class="card">
      <view class="field">
        <text class="label">工单号</text>
        <view class="scan-row">
          <input class="input" v-model="form.moNo" placeholder="输入或扫码工单号" @confirm="loadContext" />
          <button class="mini-btn primary" @click="loadContext">查询</button>
        </view>
      </view>

      <template v-if="plans.length">
        <view class="field">
          <text class="label">源工序</text>
          <picker mode="selector" :range="planLabels" :value="fromIndex" @change="onFromChange">
            <view class="picker">{{ planLabels[fromIndex] || '请选择源工序' }}</view>
          </picker>
        </view>
        <view class="field">
          <text class="label">目标工序</text>
          <picker mode="selector" :range="planLabels" :value="toIndex" @change="onToChange">
            <view class="picker">{{ planLabels[toIndex] || '请选择目标工序' }}</view>
          </picker>
        </view>
        <view class="field">
          <text class="label">转移数量</text>
          <input class="input" type="digit" v-model="form.qty" placeholder="请输入数量" />
        </view>
        <view class="field">
          <text class="label">备注</text>
          <input class="input" v-model="form.remark" placeholder="选填" />
        </view>
      </template>
    </view>

    <button class="submit" :disabled="!canSubmit" @click="handleSubmit">提交转移</button>
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { requireSession } from '@/utils/authStorage.js'
import { getReportContext, submitTransfer } from '@/api/mes.js'
import { toast } from '@/utils/ui.js'

const statusBarHeight = ref(uni.getSystemInfoSync().statusBarHeight || 20)
const plans = ref([])
const form = reactive({ moNo: '', fromProcessCode: '', toProcessCode: '', qty: '', remark: '' })

const planLabels = computed(() => plans.value.map((p) => `${p.processName || p.processCode}`))
const fromIndex = computed(() => {
  const i = plans.value.findIndex((p) => p.processCode === form.fromProcessCode)
  return i < 0 ? 0 : i
})
const toIndex = computed(() => {
  const i = plans.value.findIndex((p) => p.processCode === form.toProcessCode)
  return i < 0 ? 0 : i
})

const canSubmit = computed(
  () => !!form.moNo.trim() && !!form.fromProcessCode && !!form.toProcessCode && Number(form.qty) > 0,
)

function onFromChange(e) {
  form.fromProcessCode = plans.value[Number(e.detail.value)]?.processCode || ''
}
function onToChange(e) {
  form.toProcessCode = plans.value[Number(e.detail.value)]?.processCode || ''
}

async function loadContext() {
  if (!form.moNo.trim()) {
    toast('请输入工单号')
    return
  }
  try {
    const ctx = await getReportContext(form.moNo.trim())
    plans.value = ctx?.plans || []
    if (plans.value.length) {
      form.fromProcessCode = plans.value[0].processCode
      form.toProcessCode = plans.value[1]?.processCode || plans.value[0].processCode
    }
    toast(`已加载 ${plans.value.length} 道工序`)
  } catch {
    /* http.js 已提示 */
  }
}

async function handleSubmit() {
  if (!canSubmit.value) {
    toast('请完整填写工单、源/目标工序和数量')
    return
  }
  try {
    await submitTransfer({
      moNo: form.moNo.trim(),
      fromProcessCode: form.fromProcessCode,
      toProcessCode: form.toProcessCode,
      qty: Number(form.qty),
      remark: form.remark || null,
    })
    toast('转移成功')
    form.qty = ''
    form.remark = ''
  } catch {
    /* http.js 已提示 */
  }
}

onShow(() => {
  requireSession()
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #f5f6fb;
  padding-bottom: 48rpx;
}
.head {
  padding: 0 36rpx 28rpx;
  background: linear-gradient(120deg, #efe7fb 0%, #e4ecfb 100%);
}
.title {
  font-size: 44rpx;
  font-weight: 800;
  color: #1f2340;
}
.subtitle {
  display: block;
  margin-top: 10rpx;
  font-size: 24rpx;
  color: #7b8194;
}
.card {
  margin: 20rpx 24rpx;
  padding: 8rpx 28rpx;
  background: #fff;
  border-radius: 24rpx;
  box-shadow: 0 8rpx 24rpx rgba(31, 35, 90, 0.05);
}
.field {
  padding: 22rpx 0;
  border-bottom: 1rpx solid #f0f1f6;
}
.field:last-child {
  border-bottom: none;
}
.label {
  display: block;
  font-size: 24rpx;
  color: #8a8fa3;
  margin-bottom: 12rpx;
}
.input {
  height: 72rpx;
  font-size: 30rpx;
  color: #1f2340;
}
.scan-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}
.scan-row .input {
  flex: 1;
  background: #f5f6fb;
  border-radius: 12rpx;
  padding: 0 20rpx;
}
.mini-btn {
  font-size: 24rpx;
  line-height: 1.6;
  padding: 0 20rpx;
  background: #eef0f8;
  color: #4a4f66;
  border-radius: 12rpx;
}
.mini-btn.primary {
  background: #5c67f2;
  color: #fff;
}
.picker {
  height: 72rpx;
  line-height: 72rpx;
  font-size: 30rpx;
  color: #1f2340;
}
.submit {
  margin: 32rpx 24rpx 0;
  height: 92rpx;
  line-height: 92rpx;
  font-size: 32rpx;
  color: #fff;
  background: linear-gradient(120deg, #6d74f0, #5b8cff);
  border-radius: 46rpx;
}
.submit[disabled] {
  opacity: 0.5;
}
</style>
