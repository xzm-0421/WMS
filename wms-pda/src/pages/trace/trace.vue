<template>
  <view class="page">
    <view class="tab-bar">
      <view
        :class="['tab-item', mode === 'code' && 'active']"
        @click="switchMode('code')"
      >
        <text class="tab-text">条件查询</text>
      </view>
      <view
        :class="['tab-item', mode === 'barcode' && 'active']"
        @click="switchMode('barcode')"
      >
        <text class="tab-text">扫码追溯</text>
      </view>
      <view class="tab-indicator" :class="mode === 'barcode' ? 'right' : 'left'" />
    </view>

    <!-- 条件查询 -->
    <view v-if="mode === 'code'" class="panel">
      <view class="query-row">
        <input
          v-model="materialCode"
          class="query-input"
          type="text"
          confirm-type="search"
          placeholder="物料编码"
          :disabled="loading"
          @confirm="onCodeSearch"
        />
        <button
          class="query-btn"
          type="primary"
          :loading="loading"
          :disabled="loading"
          @click="onCodeSearch"
        >
          查询
        </button>
      </view>
      <view class="query-row secondary">
        <input
          v-model="batchNo"
          class="query-input alone"
          type="text"
          confirm-type="search"
          placeholder="批次号（建议填写，结果更准）"
          :disabled="loading"
          @confirm="onCodeSearch"
        />
      </view>
      <text class="hint">须填写物料编码或批次号至少一项</text>
    </view>

    <!-- 扫码追溯：侧键扫码，无摄像头 -->
    <view v-else class="panel">
      <ScanSearchBar
        ref="scanInputRef"
        v-model="barcode"
        placeholder="侧键扫码或输入条码"
        action-text="追溯"
        :disabled="loading"
        @scan="onBarcodeScan"
        @search="onBarcodeScan"
      />
      <text class="hint">保持输入框聚焦，按设备侧键扫码后自动追溯</text>
    </view>

    <!-- 汇总 -->
    <view v-if="hasResult" class="summary">
      <view class="summary-head">
        <text class="summary-name">{{ result.materialName || result.materialCode || '批次追溯' }}</text>
        <text v-if="result.materialCode" class="summary-code">{{ result.materialCode }}</text>
      </view>
      <view class="summary-grid">
        <view class="sg-item">
          <text class="sg-label">批次</text>
          <text class="sg-value">{{ result.batchNo || '-' }}</text>
        </view>
        <view class="sg-item">
          <text class="sg-label">当前库存</text>
          <text class="sg-value accent">{{ formatQty(result.currentStock) }}</text>
        </view>
        <view class="sg-item wide">
          <text class="sg-label">当前库位</text>
          <text class="sg-value">{{ result.currentLocation || '-' }}</text>
        </view>
      </view>
    </view>

    <!-- 流水时间线 -->
    <scroll-view v-if="records.length" class="list-scroll" scroll-y :show-scrollbar="false">
      <view class="timeline-title">
        <text>流转记录</text>
        <text class="count">{{ records.length }} 条</text>
      </view>
      <view
        v-for="(row, idx) in records"
        :key="row.seq || idx"
        class="tl-item"
      >
        <view class="tl-rail">
          <view :class="['tl-dot', txnTone(row.transactionType)]" />
          <view v-if="idx < records.length - 1" class="tl-line" />
        </view>
        <view class="tl-card">
          <view class="tl-top">
            <text :class="['type-tag', txnTone(row.transactionType)]">
              {{ txnLabel(row.transactionType) }}
            </text>
            <text class="qty">{{ formatQty(row.qty) }}</text>
          </view>
          <text class="tl-time">{{ formatTime(row.operationTime) }}</text>
          <view class="tl-meta">
            <text>仓 {{ row.warehouseCode || '-' }}</text>
            <text>位 {{ row.locationCode || '-' }}</text>
          </view>
          <view class="tl-meta">
            <text>单 {{ row.sourceOrderNo || '-' }}</text>
            <text>人 {{ row.operatorName || '-' }}</text>
          </view>
        </view>
      </view>
      <view class="list-end">最多显示近期 50 条</view>
    </scroll-view>

    <view v-else-if="searched && !loading" class="empty">
      <text class="empty-icon">🔎</text>
      <text class="empty-text">暂无追溯记录</text>
      <text class="empty-hint">请确认物料/批次是否正确</text>
    </view>

    <view v-else-if="!searched && !loading" class="empty idle">
      <text class="empty-icon">🔍</text>
      <text class="empty-text">请输入条件或扫码追溯</text>
      <text class="empty-hint">不支持无条件全量查询</text>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import ScanSearchBar from '@/components/ScanSearchBar.vue'
import { traceBatch } from '@/api/mobile.js'
import usePageAlive from '@/composables/usePageAlive.js'

const TXN_LABELS = {
  PURCHASE_IN: '采购入库',
  PRODUCTION_IN: '生产汇报入库',
  OTHER_IN: '其他入库',
  SALES_OUT: '销售出库',
  PRODUCTION_OUT: '生产领料',
  PRODUCTION_FEED: '生产补料',
  OUTSOURCE_FEED: '委外补料',
  OTHER_OUT: '其他出库',
  TRANSFER_OUT: '移库出',
  TRANSFER_IN: '移库入',
  STOCKTAKE_GAIN: '盘盈',
  STOCKTAKE_LOSS: '盘亏',
  WORKSHOP_RETURN: '车间退库',
  PRODUCTION_RETURN: '生产退料',
  PRODUCTION_RET_STOCK: '生产退库',
}

const mode = ref('code')
const materialCode = ref('')
const batchNo = ref('')
const barcode = ref('')
const loading = ref(false)
const searched = ref(false)
const result = reactive({})
const records = ref([])
const scanInputRef = ref(null)
const { alive, refocusScanInput } = usePageAlive()

const hasResult = computed(() =>
  !!(result.materialCode || result.batchNo || result.materialName || records.value.length),
)

function formatQty(v) {
  if (v == null || v === '') return '-'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  return Number.isInteger(n) ? String(n) : String(Math.round(n * 1000) / 1000)
}

function formatTime(v) {
  if (!v) return '-'
  const s = String(v).replace('T', ' ')
  return s.length > 19 ? s.slice(0, 19) : s
}

function txnLabel(type) {
  if (!type) return '未知'
  return TXN_LABELS[type] || type
}

function txnTone(type) {
  const t = String(type || '')
  if (t.includes('IN') || t.includes('GAIN') || t.includes('RETURN')) return 'in'
  if (t.includes('OUT') || t.includes('LOSS') || t.includes('FEED')) return 'out'
  return 'neutral'
}

function toast(title) {
  uni.showToast({ title, icon: 'none' })
}

function clearResult() {
  searched.value = false
  records.value = []
  Object.keys(result).forEach((k) => delete result[k])
}

function switchMode(next) {
  if (mode.value === next) return
  mode.value = next
  clearResult()
  if (next === 'barcode') {
    barcode.value = ''
    refocusScanInput(scanInputRef, 200)
  }
}

function applyResult(data) {
  Object.keys(result).forEach((k) => delete result[k])
  Object.assign(result, data || {})
  records.value = data?.traceRecords || []
}

async function doTrace(payload) {
  if (!alive.value || loading.value) return
  loading.value = true
  searched.value = true
  records.value = []
  Object.keys(result).forEach((k) => delete result[k])
  try {
    const data = await traceBatch(payload)
    applyResult(data)
    if (!records.value.length) toast('暂无追溯记录')
  } catch (e) {
    toast(e?.message || '追溯失败')
  } finally {
    loading.value = false
  }
}

async function onCodeSearch() {
  const mat = (materialCode.value || '').trim()
  const batch = (batchNo.value || '').trim()
  if (!mat && !batch) {
    toast('请输入物料编码或批次号')
    return
  }
  await doTrace({
    materialCode: mat || undefined,
    batchNo: batch || undefined,
  })
}

async function onBarcodeScan(raw) {
  const code = (raw || barcode.value || '').trim()
  if (!code) {
    toast('请先扫码')
    return
  }
  barcode.value = code
  await doTrace({ barcode: code })
  refocusScanInput(scanInputRef, 300)
}

onLoad(() => uni.setNavigationBarTitle({ title: '批次追溯' }))
onShow(() => {
  if (mode.value === 'barcode') refocusScanInput(scanInputRef, 300)
})
onMounted(() => {
  if (mode.value === 'barcode') refocusScanInput(scanInputRef, 400)
})
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f1f5f9;
  padding: 0 24rpx 24rpx;
  box-sizing: border-box;
}

.tab-bar {
  position: relative;
  display: flex;
  flex-shrink: 0;
  margin: 20rpx 0 16rpx;
  padding: 6rpx;
  background: #e2e8f0;
  border-radius: 16rpx;
}
.tab-item {
  flex: 1;
  z-index: 1;
  text-align: center;
  padding: 18rpx 0;
  border-radius: 12rpx;
}
.tab-text {
  font-size: 28rpx;
  font-weight: 600;
  color: #64748b;
}
.tab-item.active .tab-text { color: #1e3a8a; }
.tab-indicator {
  position: absolute;
  top: 6rpx;
  bottom: 6rpx;
  width: calc(50% - 6rpx);
  background: #fff;
  border-radius: 12rpx;
  box-shadow: 0 2rpx 8rpx rgba(15, 23, 42, 0.08);
  transition: transform 0.2s ease;
}
.tab-indicator.left { left: 6rpx; transform: translateX(0); }
.tab-indicator.right { left: 6rpx; transform: translateX(100%); }

.panel {
  flex-shrink: 0;
  background: #fff;
  border-radius: 16rpx;
  padding: 24rpx;
  border: 1rpx solid #e2e8f0;
  margin-bottom: 16rpx;
}
.query-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
}
.query-row.secondary { margin-top: 16rpx; }
.query-input {
  flex: 1;
  min-width: 0;
  height: 72rpx;
  padding: 0 20rpx;
  font-size: 28rpx;
  color: #0f172a;
  background: #f8fafc;
  border: 2rpx solid #e2e8f0;
  border-radius: 12rpx;
  box-sizing: border-box;
}
.query-input.alone { width: 100%; }
.query-btn {
  flex-shrink: 0;
  width: 140rpx;
  height: 72rpx;
  line-height: 72rpx;
  margin: 0;
  padding: 0;
  font-size: 28rpx;
  font-weight: 600;
  color: #fff;
  background: #1d4ed8;
  border-radius: 12rpx;
}
.query-btn[disabled] { background: #94a3b8; }
.hint {
  display: block;
  margin-top: 14rpx;
  font-size: 22rpx;
  color: #94a3b8;
  line-height: 1.4;
}

.summary {
  flex-shrink: 0;
  background: linear-gradient(135deg, #0f766e, #14b8a6);
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 16rpx;
  color: #fff;
}
.summary-head { margin-bottom: 16rpx; }
.summary-name {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
}
.summary-code {
  display: block;
  margin-top: 4rpx;
  font-size: 22rpx;
  opacity: 0.85;
  font-family: monospace;
}
.summary-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}
.sg-item {
  width: calc(50% - 6rpx);
  background: rgba(255, 255, 255, 0.12);
  border-radius: 12rpx;
  padding: 14rpx 16rpx;
  box-sizing: border-box;
}
.sg-item.wide { width: 100%; }
.sg-label {
  display: block;
  font-size: 20rpx;
  opacity: 0.8;
}
.sg-value {
  display: block;
  margin-top: 4rpx;
  font-size: 28rpx;
  font-weight: 700;
  word-break: break-all;
}
.sg-value.accent { color: #bbf7d0; }

.list-scroll {
  flex: 1;
  height: 0;
}
.timeline-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4rpx 4rpx 16rpx;
  font-size: 26rpx;
  font-weight: 700;
  color: #334155;
}
.timeline-title .count {
  font-size: 22rpx;
  font-weight: 500;
  color: #94a3b8;
}

.tl-item {
  display: flex;
  gap: 16rpx;
  margin-bottom: 4rpx;
}
.tl-rail {
  width: 28rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  flex-shrink: 0;
  padding-top: 22rpx;
}
.tl-dot {
  width: 18rpx;
  height: 18rpx;
  border-radius: 50%;
  background: #94a3b8;
  border: 4rpx solid #e2e8f0;
  box-sizing: border-box;
}
.tl-dot.in { background: #16a34a; border-color: #bbf7d0; }
.tl-dot.out { background: #ea580c; border-color: #fed7aa; }
.tl-line {
  flex: 1;
  width: 4rpx;
  min-height: 24rpx;
  background: #e2e8f0;
  margin-top: 6rpx;
}
.tl-card {
  flex: 1;
  min-width: 0;
  background: #fff;
  border: 1rpx solid #e2e8f0;
  border-radius: 14rpx;
  padding: 18rpx 20rpx;
  margin-bottom: 12rpx;
}
.tl-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12rpx;
}
.type-tag {
  font-size: 22rpx;
  font-weight: 700;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
  background: #f1f5f9;
  color: #475569;
}
.type-tag.in { background: #f0fdf4; color: #15803d; }
.type-tag.out { background: #fff7ed; color: #c2410c; }
.qty {
  font-size: 30rpx;
  font-weight: 700;
  color: #0f172a;
}
.tl-time {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #94a3b8;
}
.tl-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx 20rpx;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #64748b;
}
.list-end {
  text-align: center;
  color: #cbd5e1;
  font-size: 22rpx;
  padding: 8rpx 0 32rpx;
}

.empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80rpx 24rpx;
}
.empty-icon { font-size: 64rpx; opacity: 0.35; }
.empty-text {
  margin-top: 16rpx;
  font-size: 28rpx;
  color: #94a3b8;
}
.empty-hint {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #cbd5e1;
}
</style>
