<template>
  <view class="page">
    <ScanInput
      v-if="!issue"
      v-model="issueScanCode"
      placeholder="扫描拣配发料单二维码"
      @scan="onIssueScan"
    />

    <view v-if="issue" class="header card">
      <text class="title">发料单 {{ issue.issueNo }}</text>
      <text>仓库：{{ issue.warehouseCode }} · {{ statusText }}</text>
      <text>交接区：{{ issue.handoverArea || '-' }}</text>
    </view>

    <view v-if="issue" class="step card">
      <text class="step-label">当前步骤：{{ stepLabel }}</text>
      <ScanInput
        ref="scanInputRef"
        :placeholder="stepPlaceholder"
        @scan="onStepScan"
      />
      <text v-if="currentLocation" class="loc-hint">已选库位：{{ currentLocation }}</text>
    </view>

    <text v-if="lines.length" class="section-title">拣货进度</text>
    <view v-for="line in lines" :key="line.lineNo" class="line-card">
      <text class="name">{{ line.materialCode }}</text>
      <text>应拣 {{ line.pickQty }} / 已拣 {{ line.pickedQty || 0 }}</text>
      <text class="loc">推荐库位 {{ line.sourceLocation || '-' }}</text>
      <view class="progress-bar">
        <view class="progress-fill" :style="{ width: lineProgress(line) + '%' }" />
      </view>
    </view>

    <view v-if="scanLog.length" class="log card">
      <text class="log-title">扫码记录</text>
      <view v-for="(item, i) in scanLog" :key="i" :class="['log-item', item.ok ? 'ok' : 'fail']">
        <text>{{ item.time }} · {{ item.msg }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import ScanInput from '@/components/ScanInput.vue'
import { getPickIssueDetail, scanPickIssue } from '@/api/picking.js'
import { recognizeBarcode } from '@/api/scan.js'

const issueScanCode = ref('')
const issue = ref(null)
const lines = ref([])
const currentLocation = ref('')
const scanStep = ref('location')
const scanLog = ref([])
const scanInputRef = ref(null)

const stepLabel = computed(() => (scanStep.value === 'location' ? '扫描库位' : '扫描物料条码'))
const stepPlaceholder = computed(() =>
  scanStep.value === 'location' ? '对准库位条码扫描' : '对准物料条码扫描',
)

const statusText = computed(() => {
  const s = issue.value?.status
  if (s === 'PICKING') return '拣货中'
  if (s === 'PICKED_UP') return '已领料'
  return s || ''
})

function parseIssueNo(code) {
  const c = (code || '').trim()
  if (c.startsWith('PI:')) return c.slice(3)
  return c
}

function isLocationCode(code) {
  return /^WH\d/i.test((code || '').trim())
}

function lineProgress(line) {
  const total = Number(line.pickQty) || 1
  const done = Number(line.pickedQty) || 0
  return Math.min(100, Math.round((done / total) * 100))
}

function addLog(ok, msg) {
  const now = new Date()
  const time = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}:${String(now.getSeconds()).padStart(2, '0')}`
  scanLog.value.unshift({ ok, msg, time })
  if (scanLog.value.length > 30) scanLog.value.pop()
}

async function onIssueScan(code) {
  try {
    const issueNo = parseIssueNo(code)
    const data = await getPickIssueDetail(issueNo)
    issue.value = data.issue
    lines.value = data.lines || []
    currentLocation.value = ''
    scanStep.value = 'location'
    if (issue.value.status !== 'PICKING') {
      uni.showToast({ title: '该单不可拣货', icon: 'none' })
    }
  } catch (e) {
    uni.showToast({ title: e.message || '加载失败', icon: 'none' })
  }
}

async function onStepScan(code) {
  if (!issue.value) return
  const raw = (code || '').trim()
  if (!raw) return

  if (scanStep.value === 'location') {
    if (!isLocationCode(raw)) {
      addLog(false, '请扫描库位条码（WH开头）')
      uni.showToast({ title: '请扫描库位', icon: 'none' })
      return
    }
    currentLocation.value = raw
    scanStep.value = 'material'
    addLog(true, `库位 ${raw}`)
    uni.showToast({ title: '请扫描物料', icon: 'success', duration: 800 })
    return
  }

  await doMaterialPick(raw)
}

async function doMaterialPick(barcode) {
  if (!currentLocation.value) {
    scanStep.value = 'location'
    uni.showToast({ title: '请先扫描库位', icon: 'none' })
    return
  }
  try {
    const recognized = await recognizeBarcode(barcode, issue.value.warehouseCode)
    const materialCode = recognized.materialCode
    if (!materialCode) {
      addLog(false, '无法识别物料')
      uni.showToast({ title: '无法识别物料', icon: 'none' })
      return
    }
    const result = await scanPickIssue(issue.value.issueNo, {
      materialCode,
      locationCode: currentLocation.value,
      batchNo: recognized.batchNo,
      quantity: 1,
      barcodeContent: barcode,
    })
    addLog(true, `${result.materialName || materialCode} +1 @${currentLocation.value}`)
    uni.showToast({ title: '拣货成功', icon: 'success', duration: 800 })
    const data = await getPickIssueDetail(issue.value.issueNo)
    lines.value = data.lines || []
    if (result.allComplete) {
      uni.showModal({
        title: '拣货完成',
        content: '全部明细已拣完，可前往领料确认',
        showCancel: false,
      })
    }
    scanStep.value = 'material'
  } catch (e) {
    addLog(false, e.message || '拣货失败')
    uni.showToast({ title: e.message || '拣货失败', icon: 'none' })
  }
}

onShow(() => {
  setTimeout(() => scanInputRef.value?.focusInput?.(), 500)
})
</script>

<style scoped>
.page { padding: 24rpx; padding-bottom: 48rpx; }
.card { background: #fff; border-radius: 12rpx; padding: 24rpx; margin-bottom: 24rpx; }
.header .title { font-size: 32rpx; font-weight: 600; display: block; margin-bottom: 8rpx; }
.step-label { font-weight: 600; display: block; margin-bottom: 12rpx; color: #1d4ed8; }
.loc-hint { display: block; margin-top: 12rpx; color: #16a34a; font-size: 26rpx; }
.section-title { font-weight: bold; margin-bottom: 16rpx; display: block; }
.line-card { background: #fff; padding: 20rpx 24rpx; border-radius: 12rpx; margin-bottom: 12rpx; }
.name { font-weight: bold; display: block; margin-bottom: 4rpx; font-size: 26rpx; }
.loc { color: #64748b; font-size: 24rpx; display: block; margin-top: 4rpx; }
.progress-bar { height: 8rpx; background: #e2e8f0; border-radius: 4rpx; margin-top: 8rpx; overflow: hidden; }
.progress-fill { height: 100%; background: #22c55e; transition: width 0.3s; }
.log-title { font-weight: 600; display: block; margin-bottom: 8rpx; }
.log-item { padding: 8rpx 0; font-size: 24rpx; border-bottom: 1px solid #f1f5f9; }
.log-item.ok { color: #16a34a; }
.log-item.fail { color: #dc2626; }
</style>
