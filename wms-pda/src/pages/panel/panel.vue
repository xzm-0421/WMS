<template>
  <view class="page">
    <!-- 步骤1：扫单据 -->
    <view v-if="!billNo" class="step-card">
      <text class="step-title">第 1 步：扫描单据条码</text>
      <text class="step-hint">扫描收料通知单/标签源单二维码，加载待校验物料明细</text>
      <ScanSearchBar
        ref="billScanRef"
        v-model="billInput"
        placeholder="扫码或输入单据号"
        action-text="加载"
        :disabled="loading"
        @scan="onBillScan"
        @search="onBillScan"
      />
      <view v-if="loading" class="loading-tip">加载单据明细...</view>
    </view>

    <!-- 步骤2：扫物料 -->
    <template v-else>
      <view class="bill-bar">
        <view class="bill-info">
          <text class="bill-no">{{ billNo }}</text>
          <text class="bill-sub">待校验 {{ lines.length }} 条 · 已通过 {{ verifiedCount }}</text>
        </view>
        <text class="link" @click="resetBill">换单</text>
      </view>

      <view class="scan-top">
        <ScanSearchBar
          ref="matScanRef"
          v-model="matInput"
          placeholder="扫描物料标签二维码"
          action-text="校验"
          :disabled="busy"
          @scan="onMaterialScan"
          @search="onMaterialScan"
        />
      </view>

      <view v-if="lastResult" :class="['result-card', lastResult.valid ? 'pass' : 'fail']">
        <text class="result-title">{{ lastResult.valid ? '校验通过' : '校验失败' }}</text>
        <text class="result-msg">{{ lastResult.message }}</text>
        <text v-if="lastResult.matchedLine" class="result-detail">
          {{ lastResult.matchedLine.materialCode }} · {{ lastResult.matchedLine.materialName || '-' }}
        </text>
      </view>

      <scroll-view class="list-scroll" scroll-y :show-scrollbar="false">
        <view
          v-for="line in lines"
          :key="line.jobId"
          :class="['line-row', line.verified && 'done', flashJobId === line.jobId && 'flash']"
        >
          <view class="line-main">
            <view class="name-row">
              <text class="mat-code">{{ line.materialCode }}</text>
              <text :class="['tag', line.verified ? 'ok' : 'pending']">
                {{ line.verified ? '已通过' : '待校验' }}
              </text>
            </view>
            <text class="mat-name">{{ line.materialName || '-' }}</text>
            <text class="mat-spec">规格 {{ line.specification || '-' }}</text>
            <text class="mat-meta">
              批次 {{ line.batchNo || '-' }} · 数量 {{ formatQty(line.quantity) }} {{ line.unitCode || '' }}
            </text>
          </view>
        </view>
        <view class="scroll-pad" />
      </scroll-view>
    </template>

    <view v-if="history.length" class="history-section">
      <text class="section-title">最近校验</text>
      <view v-for="(h, i) in history" :key="i" class="history-item">
        <text :class="h.valid ? 'ok' : 'ng'">{{ h.valid ? 'PASS' : 'FAIL' }}</text>
        <text>{{ h.billNo }} · {{ h.materialCode || '-' }}</text>
        <text class="time">{{ h.verifyTime }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import ScanSearchBar from '@/components/ScanSearchBar.vue'
import usePageAlive from '@/composables/usePageAlive.js'
import {
  resolveBarcodeBill,
  getBarcodeBillDetail,
  verifyBarcodeMaterial,
} from '@/api/mobile.js'

const billNo = ref('')
const billInput = ref('')
const matInput = ref('')
const lines = ref([])
const loading = ref(false)
const busy = ref(false)
const lastResult = ref(null)
const flashJobId = ref('')
const billScanRef = ref(null)
const matScanRef = ref(null)
const history = ref(uni.getStorageSync('barcode_verify_history') || [])
const { alive, refocusScanInput } = usePageAlive()

const verifiedCount = computed(() => lines.value.filter((l) => l.verified).length)

function formatQty(val) {
  if (val == null || val === '') return '-'
  const n = Number(val)
  return Number.isNaN(n) ? String(val) : String(n)
}

function toast(title, icon = 'none') {
  uni.showToast({ title, icon, duration: 2200 })
}

async function loadBill(no) {
  const trimmed = String(no || '').trim()
  if (!trimmed) return
  loading.value = true
  try {
    const data = await getBarcodeBillDetail(trimmed)
    billNo.value = data.billNo || trimmed
    lines.value = (data.lines || []).map((l) => ({ ...l, verified: false }))
    lastResult.value = null
    billInput.value = ''
    nextTick(() => refocusScanInput(matScanRef, 300))
  } catch (e) {
    toast(e?.message || '加载单据失败')
  } finally {
    loading.value = false
  }
}

async function onBillScan(barcode) {
  if (!alive.value || loading.value) return
  const raw = String(barcode || billInput.value || '').trim()
  if (!raw) return
  billInput.value = ''
  try {
    const res = await resolveBarcodeBill(raw)
    const no = (res?.billNo || raw).trim()
    if (!no) {
      toast('未识别到单据号')
      return
    }
    await loadBill(no)
  } catch {
    await loadBill(raw)
  } finally {
    refocusScanInput(billScanRef, 300)
  }
}

async function onMaterialScan(barcode) {
  if (!alive.value || busy.value || !billNo.value) return
  const raw = String(barcode || matInput.value || '').trim()
  if (!raw) return
  matInput.value = ''
  busy.value = true
  lastResult.value = null
  try {
    const data = await verifyBarcodeMaterial(billNo.value, raw)
    lastResult.value = data
    if (data.valid && data.matchedLine) {
      const jobId = data.matchedLine.jobId
      const idx = lines.value.findIndex((l) => l.jobId === jobId)
      if (idx >= 0) {
        lines.value[idx] = { ...lines.value[idx], verified: true }
      } else {
        lines.value.push({ ...data.matchedLine, verified: true })
      }
      flashJobId.value = jobId
      pushHistory(data)
      toast('校验通过', 'success')
    } else {
      toast(data.message || '校验失败')
    }
  } catch (e) {
    toast(e?.message || '校验失败')
  } finally {
    busy.value = false
    refocusScanInput(matScanRef, 300)
  }
}

function pushHistory(data) {
  const line = data.matchedLine || {}
  const record = {
    billNo: billNo.value,
    materialCode: line.materialCode,
    valid: !!data.valid,
    verifyTime: data.verifyTime,
  }
  history.value = [record, ...history.value.filter((h) =>
    !(h.billNo === record.billNo && h.materialCode === record.materialCode),
  )].slice(0, 10)
  uni.setStorageSync('barcode_verify_history', history.value)
}

function resetBill() {
  billNo.value = ''
  lines.value = []
  lastResult.value = null
  matInput.value = ''
  billInput.value = ''
  nextTick(() => refocusScanInput(billScanRef, 300))
}

onLoad(() => uni.setNavigationBarTitle({ title: '条码校验' }))
onShow(() => {
  if (!billNo.value) refocusScanInput(billScanRef, 300)
  else refocusScanInput(matScanRef, 300)
})
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: #f1f5f9;
  padding: 20rpx;
  box-sizing: border-box;
}
.step-card {
  background: #fff;
  border-radius: 16rpx;
  padding: 28rpx;
}
.step-title {
  display: block;
  font-size: 32rpx;
  font-weight: 700;
  color: #0f172a;
}
.step-hint {
  display: block;
  margin: 10rpx 0 20rpx;
  font-size: 24rpx;
  color: #64748b;
}
.loading-tip {
  text-align: center;
  padding: 24rpx;
  color: #94a3b8;
  font-size: 26rpx;
}
.bill-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #eff6ff;
  border-radius: 14rpx;
  padding: 20rpx 24rpx;
  margin-bottom: 12rpx;
}
.bill-no {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
  color: #1e3a8a;
}
.bill-sub {
  display: block;
  margin-top: 4rpx;
  font-size: 22rpx;
  color: #64748b;
}
.link {
  font-size: 26rpx;
  color: #1d4ed8;
}
.scan-top {
  margin-bottom: 12rpx;
}
.result-card {
  border-radius: 14rpx;
  padding: 20rpx 24rpx;
  margin-bottom: 12rpx;
}
.result-card.pass {
  background: #f0fdf4;
  border: 2rpx solid #22c55e;
}
.result-card.fail {
  background: #fef2f2;
  border: 2rpx solid #ef4444;
}
.result-title {
  display: block;
  font-size: 28rpx;
  font-weight: 700;
}
.result-card.pass .result-title { color: #15803d; }
.result-card.fail .result-title { color: #dc2626; }
.result-msg {
  display: block;
  margin-top: 6rpx;
  font-size: 24rpx;
  color: #475569;
}
.result-detail {
  display: block;
  margin-top: 6rpx;
  font-size: 22rpx;
  color: #64748b;
}
.list-scroll {
  flex: 1;
  max-height: 52vh;
}
.line-row {
  background: #fff;
  border-radius: 14rpx;
  padding: 20rpx;
  margin-bottom: 12rpx;
  border: 2rpx solid transparent;
}
.line-row.done {
  background: #f8fafc;
}
.line-row.flash {
  border-color: #22c55e;
}
.name-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}
.mat-code {
  font-size: 30rpx;
  font-weight: 700;
  color: #0f172a;
}
.tag {
  font-size: 20rpx;
  padding: 2rpx 10rpx;
  border-radius: 8rpx;
}
.tag.ok {
  background: #dcfce7;
  color: #15803d;
}
.tag.pending {
  background: #e2e8f0;
  color: #64748b;
}
.mat-name,
.mat-spec,
.mat-meta {
  display: block;
  margin-top: 6rpx;
  font-size: 24rpx;
  color: #64748b;
}
.scroll-pad { height: 24rpx; }
.history-section {
  margin-top: 20rpx;
  background: #fff;
  border-radius: 14rpx;
  padding: 20rpx;
}
.section-title {
  display: block;
  font-weight: 700;
  margin-bottom: 12rpx;
  font-size: 26rpx;
}
.history-item {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 12rpx 0;
  border-bottom: 1rpx solid #f1f5f9;
  font-size: 24rpx;
}
.ok { color: #22c55e; font-weight: 700; }
.ng { color: #ef4444; font-weight: 700; }
.history-item .time {
  margin-left: auto;
  color: #94a3b8;
  font-size: 22rpx;
}
</style>
