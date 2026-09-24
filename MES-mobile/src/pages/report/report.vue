<template>
  <view class="page">
    <view class="head" :style="{ paddingTop: statusBarHeight + 16 + 'px' }">
      <text class="title">工序报工</text>
      <text class="subtitle">扫码 / 输入工单号，选择工序后提交</text>
    </view>

    <view class="card">
      <view class="field">
        <text class="label">工单号</text>
        <view class="scan-row">
          <input class="input" v-model="form.moNo" placeholder="输入或扫码工单号" @confirm="loadContext" />
          <button class="mini-btn" @click="scan">扫码</button>
          <button class="mini-btn primary" @click="loadContext">查询</button>
        </view>
      </view>
      <view v-if="context.typeHint" class="hint">{{ context.typeHint }}</view>
      <view v-if="queueCount" class="offline-tip">待同步 {{ queueCount }} 条，联网后自动上传</view>
    </view>

    <view class="card" v-if="plans.length">
      <view class="field">
        <text class="label">工序</text>
        <picker mode="selector" :range="planLabels" :value="planIndex" @change="onPlanChange">
          <view class="picker">{{ currentPlanLabel || '请选择工序' }}</view>
        </picker>
      </view>

      <view class="field">
        <text class="label">报工类型</text>
        <view class="seg">
          <view class="seg-item" :class="{ active: form.reportType === 'NORMAL' }" @click="setType('NORMAL')">正常报工</view>
          <view class="seg-item" :class="{ active: form.reportType === 'REWORK' }" @click="setType('REWORK')">返工报工</view>
        </view>
      </view>

      <view class="field" v-if="form.reportType === 'REWORK'">
        <text class="label">关联不良单</text>
        <picker mode="selector" :range="defectLabels" @change="onDefectChange">
          <view class="picker">{{ form.defectNo || '请选择不良单' }}</view>
        </picker>
      </view>

      <view class="field">
        <text class="label">报工数量</text>
        <input class="input" type="digit" v-model="form.qty" placeholder="请输入数量" />
      </view>

      <view class="field">
        <text class="label">重量(kg)</text>
        <input class="input" type="digit" v-model="form.weightKg" placeholder="选填，手动录入" />
      </view>

      <view class="field">
        <text class="label">设备</text>
        <picker mode="selector" :range="equipmentLabels" :value="equipIndex" @change="onEquipChange">
          <view class="picker">{{ currentEquipLabel || '请选择设备' }}</view>
        </picker>
      </view>

      <view class="field">
        <text class="label">备注</text>
        <input class="input" v-model="form.remark" placeholder="选填" />
      </view>
    </view>

    <button class="submit" :disabled="!canSubmit" @click="handleSubmit">提交报工</button>
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { requireSession } from '@/utils/authStorage.js'
import { getReportContext, submitReport, syncReports } from '@/api/mes.js'
import {
  enqueue,
  flushQueue,
  genClientReportNo,
  getQueueCount,
  isNetworkError,
} from '@/utils/offlineQueue.js'
import { toast } from '@/utils/ui.js'

const statusBarHeight = ref(uni.getSystemInfoSync().statusBarHeight || 20)
const context = ref({})
const plans = ref([])
const equipment = ref([])
const queueCount = ref(getQueueCount())
const form = reactive({
  moNo: '',
  processCode: '',
  reportType: 'NORMAL',
  defectNo: '',
  qty: '',
  weightKg: '',
  equipmentCode: '',
  remark: '',
})

const planLabels = computed(() =>
  plans.value.map((p) => `${p.processName || p.processCode} (已报${p.reportedQty || 0}/${p.planQty || 0})`),
)
const planIndex = computed(() => {
  const i = plans.value.findIndex((p) => p.processCode === form.processCode)
  return i < 0 ? 0 : i
})
const currentPlanLabel = computed(() => (planLabels.value.length ? planLabels.value[planIndex.value] : ''))

const equipmentLabels = computed(() => equipment.value.map((e) => `${e.equipmentName || e.equipmentCode}`))
const equipIndex = computed(() => {
  const i = equipment.value.findIndex((e) => e.equipmentCode === form.equipmentCode)
  return i < 0 ? 0 : i
})
const currentEquipLabel = computed(() => (equipmentLabels.value.length ? equipmentLabels.value[equipIndex.value] : ''))

const defectLabels = computed(() => context.value.openDefectNos || [])

const canSubmit = computed(
  () => !!form.moNo.trim() && !!form.processCode && Number(form.qty) > 0 && !!form.equipmentCode,
)

function onPlanChange(e) {
  const i = Number(e.detail.value)
  form.processCode = plans.value[i]?.processCode || ''
  syncTypeByRules()
}

function onDefectChange(e) {
  form.defectNo = defectLabels.value[Number(e.detail.value)] || ''
}

function onEquipChange(e) {
  form.equipmentCode = equipment.value[Number(e.detail.value)]?.equipmentCode || ''
}

function setType(type) {
  form.reportType = type
  if (type === 'REWORK' && !form.defectNo && defectLabels.value.length) {
    form.defectNo = defectLabels.value[0]
  }
}

function syncTypeByRules() {
  const allowed = context.value.allowedReportTypes || ['NORMAL']
  if (!allowed.includes(form.reportType)) {
    form.reportType = allowed[0] || 'NORMAL'
  }
  if (form.reportType === 'REWORK' && !form.defectNo && defectLabels.value.length) {
    form.defectNo = defectLabels.value[0]
  }
}

async function loadContext() {
  if (!form.moNo.trim()) {
    toast('请输入工单号')
    return
  }
  try {
    const ctx = await getReportContext(form.moNo.trim())
    context.value = ctx || {}
    plans.value = ctx?.plans || []
    equipment.value = ctx?.equipment || []
    if (plans.value.length) {
      form.processCode = plans.value[0].processCode
    }
    syncTypeByRules()
    toast(`已加载 ${plans.value.length} 道工序`)
  } catch {
    /* http.js 已提示 */
  }
}

function scan() {
  if (!uni.scanCode) {
    toast('当前环境不支持扫码，请手动输入')
    return
  }
  uni.scanCode({
    success: (res) => {
      form.moNo = res.result
      loadContext()
    },
    fail: () => {},
  })
}

async function handleSubmit() {
  if (!canSubmit.value) {
    toast('请完整填写工单、工序、数量和设备')
    return
  }
  const payload = {
    clientReportNo: genClientReportNo(),
    clientTime: Date.now(),
    moNo: form.moNo.trim(),
    processCode: form.processCode,
    reportType: form.reportType,
    qty: Number(form.qty),
    weightKg: form.weightKg ? Number(form.weightKg) : null,
    equipmentCode: form.equipmentCode,
    defectNo: form.reportType === 'REWORK' ? form.defectNo : null,
    remark: form.remark || null,
  }
  try {
    const report = await submitReport(payload)
    toast(`报工成功 ${report?.reportNo || ''}`)
    form.qty = ''
    form.weightKg = ''
    form.remark = ''
  } catch (err) {
    if (isNetworkError(err)) {
      enqueue(payload)
      queueCount.value = getQueueCount()
      toast('网络异常，已离线缓存')
    }
  }
}

async function tryFlush() {
  if (!getQueueCount()) return
  try {
    const res = await flushQueue((items, deviceNo) => syncReports(items, deviceNo))
    queueCount.value = getQueueCount()
    if (res?.successCount) toast(`已同步 ${res.successCount} 条离线报工`)
  } catch {
    /* 忽略，下次再传 */
  }
}

onShow(() => {
  if (!requireSession()) return
  queueCount.value = getQueueCount()
  tryFlush()
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
.seg {
  display: flex;
  background: #f0f1f6;
  border-radius: 14rpx;
  padding: 6rpx;
}
.seg-item {
  flex: 1;
  text-align: center;
  padding: 16rpx 0;
  font-size: 28rpx;
  color: #6b7088;
  border-radius: 10rpx;
}
.seg-item.active {
  background: #5c67f2;
  color: #fff;
  font-weight: 600;
}
.hint {
  margin: 16rpx 0;
  padding: 16rpx 20rpx;
  font-size: 24rpx;
  color: #b26a00;
  background: #fff7e6;
  border-radius: 12rpx;
}
.offline-tip {
  margin: 16rpx 0;
  font-size: 24rpx;
  color: #e8853d;
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
