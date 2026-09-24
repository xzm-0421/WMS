<template>
  <view class="page">
    <view class="head" :style="{ paddingTop: statusBarHeight + 16 + 'px' }">
      <text class="title">发起返工</text>
      <text class="subtitle">登记不良并自动生成返工工序序列</text>
    </view>

    <view class="card">
      <view class="field">
        <text class="label">工单号</text>
        <view class="scan-row">
          <input class="input" v-model="form.moNo" placeholder="输入工单号" @confirm="loadContext" />
          <button class="mini-btn primary" @click="loadContext">查询</button>
        </view>
      </view>

      <view class="field" v-if="plans.length">
        <text class="label">源工序</text>
        <picker mode="selector" :range="planLabels" :value="sourceIndex" @change="onSourceChange">
          <view class="picker">{{ planLabels[sourceIndex] || '请选择源工序' }}</view>
        </picker>
      </view>

      <view class="field">
        <text class="label">不良数量</text>
        <input class="input" type="digit" v-model="form.defectQty" placeholder="请输入不良数量" />
      </view>

      <view class="field">
        <text class="label">不良类型</text>
        <picker mode="selector" :range="defectTypeLabels" :value="defectTypeIndex" @change="onDefectTypeChange">
          <view class="picker">{{ defectTypeLabels[defectTypeIndex] }}</view>
        </picker>
      </view>

      <view class="field">
        <text class="label">不良描述</text>
        <input class="input" v-model="form.defectDesc" placeholder="选填" />
      </view>

      <view class="field">
        <text class="label">责任人</text>
        <input class="input" v-model="form.ownerName" placeholder="选填" />
      </view>
    </view>

    <button class="submit" :disabled="!canSubmit" @click="handleCreate">发起返工</button>

    <view class="card" v-if="sequence.operations && sequence.operations.length">
      <text class="card-title">返工序列 · {{ sequence.defect?.defectNo }}</text>
      <view v-for="op in sequence.operations" :key="op.seqNo" class="seq-row">
        <text class="seq-no">{{ op.seqNo }}</text>
        <text class="seq-name">{{ op.processName || op.processCode }}</text>
        <text class="seq-qty">计划 {{ op.planQty }}</text>
      </view>
    </view>

    <view class="card" v-if="defects.length">
      <text class="card-title">最近不良单</text>
      <view v-for="d in defects" :key="d.defectNo" class="seq-row" @click="showSequence(d.defectNo)">
        <text class="seq-name">{{ d.defectNo }}</text>
        <text class="seq-qty">{{ d.sourceProcessName || d.sourceProcessCode }} · {{ d.defectQty }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { requireSession } from '@/utils/authStorage.js'
import { getReportContext, createDefect, getDefects, getDefectSequence } from '@/api/mes.js'
import { toast } from '@/utils/ui.js'

const statusBarHeight = ref(uni.getSystemInfoSync().statusBarHeight || 20)
const plans = ref([])
const defects = ref([])
const sequence = ref({})
const defectTypeValues = ['APPEARANCE', 'SIZE', 'FUNCTION', 'OTHER']
const defectTypeLabels = ['外观', '尺寸', '功能', '其他']
const defectTypeIndex = ref(0)

const form = reactive({
  moNo: '',
  sourceProcessCode: '',
  defectQty: '',
  defectType: 'APPEARANCE',
  defectDesc: '',
  ownerName: '',
})

const planLabels = computed(() => plans.value.map((p) => `${p.processName || p.processCode}`))
const sourceIndex = computed(() => {
  const i = plans.value.findIndex((p) => p.processCode === form.sourceProcessCode)
  return i < 0 ? 0 : i
})
const canSubmit = computed(
  () => !!form.moNo.trim() && !!form.sourceProcessCode && Number(form.defectQty) > 0,
)

function onSourceChange(e) {
  form.sourceProcessCode = plans.value[Number(e.detail.value)]?.processCode || ''
}
function onDefectTypeChange(e) {
  defectTypeIndex.value = Number(e.detail.value)
  form.defectType = defectTypeValues[defectTypeIndex.value]
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
      form.sourceProcessCode = plans.value[0].processCode
    }
    await loadDefects()
  } catch {
    /* http.js 已提示 */
  }
}

async function loadDefects() {
  if (!form.moNo.trim()) return
  try {
    const res = await getDefects({ moNo: form.moNo.trim(), current: 1, size: 10 })
    defects.value = res?.records || []
  } catch {
    defects.value = []
  }
}

async function handleCreate() {
  if (!canSubmit.value) {
    toast('请填写工单、源工序和不良数量')
    return
  }
  try {
    sequence.value = await createDefect({
      moNo: form.moNo.trim(),
      sourceProcessCode: form.sourceProcessCode,
      defectQty: Number(form.defectQty),
      defectType: form.defectType,
      defectDesc: form.defectDesc || null,
      ownerName: form.ownerName || null,
    })
    toast('返工序列已创建')
    form.defectQty = ''
    form.defectDesc = ''
    await loadDefects()
  } catch {
    /* http.js 已提示 */
  }
}

async function showSequence(defectNo) {
  try {
    sequence.value = await getDefectSequence(defectNo)
  } catch {
    /* ignore */
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
.card-title {
  display: block;
  padding: 20rpx 0 8rpx;
  font-size: 28rpx;
  font-weight: 700;
  color: #1f2340;
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
.seq-row {
  display: flex;
  align-items: center;
  padding: 20rpx 0;
  border-bottom: 1rpx solid #f0f1f6;
}
.seq-row:last-child {
  border-bottom: none;
}
.seq-no {
  width: 48rpx;
  height: 48rpx;
  line-height: 48rpx;
  text-align: center;
  font-size: 24rpx;
  color: #fff;
  background: #8a8dfe;
  border-radius: 50%;
  margin-right: 20rpx;
}
.seq-name {
  flex: 1;
  font-size: 28rpx;
  color: #1f2340;
}
.seq-qty {
  font-size: 24rpx;
  color: #8a8fa3;
}
</style>
