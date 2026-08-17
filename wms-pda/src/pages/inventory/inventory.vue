<template>
  <view class="page">
    <!-- 顶部标签 -->
    <view class="tab-bar">
      <view
        :class="['tab-item', mode === 'code' && 'active']"
        @click="switchMode('code')"
      >
        <text class="tab-text">编码查询</text>
      </view>
      <view
        :class="['tab-item', mode === 'barcode' && 'active']"
        @click="switchMode('barcode')"
      >
        <text class="tab-text">扫码查询</text>
      </view>
      <view class="tab-indicator" :class="mode === 'barcode' ? 'right' : 'left'" />
    </view>

    <!-- 编码查询 -->
    <view v-if="mode === 'code'" class="panel">
      <view class="query-row">
        <input
          v-model="materialCode"
          class="query-input"
          type="text"
          confirm-type="search"
          placeholder="请输入物料编码"
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
          v-model="warehouseCode"
          class="query-input alone"
          type="text"
          placeholder="仓库编码（可选）"
          :disabled="loading"
          @confirm="onCodeSearch"
        />
      </view>
      <text class="hint">须填写物料编码后查询，仓库为可选筛选条件</text>
    </view>

    <!-- 扫码查询：侧键扫码写入输入框，无摄像头 -->
    <view v-else class="panel scan-panel">
      <ScanSearchBar
        ref="scanInputRef"
        v-model="barcode"
        placeholder="侧键扫码或输入条码"
        action-text="查询"
        :disabled="loading"
        @scan="onBarcodeScan"
        @search="onBarcodeScan"
      />
      <text class="hint">保持输入框聚焦，按设备侧键扫码后自动查询</text>
    </view>

    <!-- 汇总 -->
    <view v-if="hasSummary" class="summary">
      <view class="summary-head">
        <text class="summary-name">{{ summary.materialName || summary.materialCode || '库存汇总' }}</text>
        <text v-if="summary.materialCode" class="summary-code">{{ summary.materialCode }}</text>
      </view>
      <view class="summary-stats">
        <view class="stat">
          <text class="stat-label">总库存</text>
          <text class="stat-value">{{ formatQty(summary.totalStockQty) }}</text>
        </view>
        <view class="stat-divider" />
        <view class="stat">
          <text class="stat-label">可用</text>
          <text class="stat-value accent">{{ formatQty(summary.totalAvailableQty) }}</text>
        </view>
        <view v-if="list.length" class="stat-divider" />
        <view v-if="list.length" class="stat">
          <text class="stat-label">明细行</text>
          <text class="stat-value">{{ list.length }}</text>
        </view>
      </view>
    </view>

    <!-- 明细列表 -->
    <scroll-view v-if="list.length" class="list-scroll" scroll-y :show-scrollbar="false">
      <view
        v-for="(item, idx) in list"
        :key="rowKey(item, idx)"
        class="stock-card"
      >
        <view class="stock-top">
          <text class="mat-code">{{ item.materialCode || summary.materialCode || '-' }}</text>
          <text class="qty-badge">{{ formatQty(item.stockQty ?? item.totalStockQty ?? item.availableQty) }}</text>
        </view>
        <text class="mat-name">{{ item.materialName || summary.materialName || '-' }}</text>
        <view class="meta-grid">
          <text class="meta">仓 {{ item.warehouseCode || '-' }}</text>
          <text class="meta">位 {{ item.locationCode || '-' }}</text>
          <text class="meta">批 {{ item.batchNo || '-' }}</text>
          <text v-if="item.availableQty != null" class="meta avail">
            可用 {{ formatQty(item.availableQty) }}
          </text>
        </view>
      </view>
      <view class="list-end">共 {{ list.length }} 条</view>
    </scroll-view>

    <view v-else-if="searched && !loading" class="empty">
      <text class="empty-icon">📭</text>
      <text class="empty-text">未查到库存</text>
      <text class="empty-hint">请确认编码/条码是否正确</text>
    </view>

    <view v-else-if="!searched && !loading" class="empty idle">
      <text class="empty-icon">📦</text>
      <text class="empty-text">请输入条件后查询</text>
      <text class="empty-hint">不支持无条件全量查询</text>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import ScanSearchBar from '@/components/ScanSearchBar.vue'
import { queryInventoryGet, queryInventoryPost } from '@/api/mobile.js'
import usePageAlive from '@/composables/usePageAlive.js'

const mode = ref('code')
const materialCode = ref('')
const warehouseCode = ref('')
const barcode = ref('')
const list = ref([])
const summary = reactive({})
const searched = ref(false)
const loading = ref(false)
const scanInputRef = ref(null)
const { alive, refocusScanInput } = usePageAlive()

const hasSummary = computed(() =>
  !!(summary.materialCode || summary.materialName
    || Number(summary.totalStockQty) > 0
    || Number(summary.totalAvailableQty) > 0),
)

function formatQty(v) {
  if (v == null || v === '') return '0'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  return Number.isInteger(n) ? String(n) : String(Math.round(n * 1000) / 1000)
}

function rowKey(item, idx) {
  return [item.materialCode, item.warehouseCode, item.locationCode, item.batchNo, idx].join('|')
}

function clearResult() {
  searched.value = false
  list.value = []
  Object.keys(summary).forEach((k) => delete summary[k])
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

function toast(title) {
  uni.showToast({ title, icon: 'none' })
}

function applyCodeResult(res) {
  const rows = Array.isArray(res) ? res : (res?.records || res?.stocks || [])
  list.value = rows
  if (rows.length) {
    const first = rows[0]
    summary.materialCode = materialCode.value.trim() || first.materialCode
    summary.materialName = first.materialName
    let totalStock = 0
    let totalAvail = 0
    rows.forEach((r) => {
      totalStock += Number(r.stockQty ?? r.totalStockQty ?? 0) || 0
      totalAvail += Number(r.availableQty ?? r.totalAvailableQty ?? r.stockQty ?? 0) || 0
    })
    summary.totalStockQty = totalStock
    summary.totalAvailableQty = totalAvail
  }
}

function applyBarcodeResult(res) {
  Object.assign(summary, res || {})
  list.value = res?.stocks || []
}

async function onCodeSearch() {
  if (!alive.value || loading.value) return
  const code = (materialCode.value || '').trim()
  if (!code) {
    toast('请输入物料编码')
    return
  }
  loading.value = true
  searched.value = true
  Object.keys(summary).forEach((k) => delete summary[k])
  list.value = []
  try {
    const res = await queryInventoryGet({
      materialCode: code,
      warehouseCode: (warehouseCode.value || '').trim() || undefined,
    })
    applyCodeResult(res)
    if (!list.value.length) toast('未查到库存')
  } catch (e) {
    toast(e?.message || '查询失败')
  } finally {
    loading.value = false
  }
}

async function onBarcodeScan(raw) {
  if (!alive.value || loading.value) return
  const code = (raw || '').trim()
  if (!code) {
    toast('请先扫码')
    return
  }
  barcode.value = code
  loading.value = true
  searched.value = true
  Object.keys(summary).forEach((k) => delete summary[k])
  list.value = []
  try {
    const res = await queryInventoryPost({
      barcode: code,
      queryType: 'MATERIAL',
    })
    applyBarcodeResult(res)
    if (!list.value.length) toast(res?.message || '未查到库存')
  } catch (e) {
    toast(e?.message || '查询失败')
  } finally {
    loading.value = false
    refocusScanInput(scanInputRef, 300)
  }
}

onLoad(() => uni.setNavigationBarTitle({ title: '库存查询' }))
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
.tab-item.active .tab-text {
  color: #1e3a8a;
}
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
.query-row.secondary {
  margin-top: 16rpx;
}
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
.query-input.alone {
  width: 100%;
}
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
.query-btn[disabled] {
  background: #94a3b8;
}

.hint {
  display: block;
  margin-top: 14rpx;
  font-size: 22rpx;
  color: #94a3b8;
  line-height: 1.4;
}

.summary {
  flex-shrink: 0;
  background: linear-gradient(135deg, #1d4ed8, #3b82f6);
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 16rpx;
  color: #fff;
}
.summary-head {
  margin-bottom: 16rpx;
}
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
.summary-stats {
  display: flex;
  align-items: stretch;
  background: rgba(255, 255, 255, 0.12);
  border-radius: 12rpx;
  padding: 16rpx 8rpx;
}
.stat {
  flex: 1;
  text-align: center;
}
.stat-label {
  display: block;
  font-size: 20rpx;
  opacity: 0.8;
}
.stat-value {
  display: block;
  margin-top: 4rpx;
  font-size: 34rpx;
  font-weight: 700;
}
.stat-value.accent {
  color: #bbf7d0;
}
.stat-divider {
  width: 1rpx;
  background: rgba(255, 255, 255, 0.25);
  margin: 4rpx 0;
}

.list-scroll {
  flex: 1;
  height: 0;
}
.stock-card {
  background: #fff;
  border: 1rpx solid #e2e8f0;
  border-radius: 14rpx;
  padding: 20rpx;
  margin-bottom: 12rpx;
}
.stock-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12rpx;
}
.mat-code {
  font-size: 26rpx;
  font-weight: 700;
  color: #0f172a;
  font-family: monospace;
}
.qty-badge {
  flex-shrink: 0;
  font-size: 28rpx;
  font-weight: 700;
  color: #1d4ed8;
  background: #eff6ff;
  padding: 4rpx 14rpx;
  border-radius: 999rpx;
}
.mat-name {
  display: block;
  margin-top: 6rpx;
  font-size: 26rpx;
  color: #334155;
}
.meta-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx 20rpx;
  margin-top: 12rpx;
}
.meta {
  font-size: 22rpx;
  color: #64748b;
}
.meta.avail {
  color: #15803d;
  font-weight: 600;
}
.list-end {
  text-align: center;
  color: #cbd5e1;
  font-size: 22rpx;
  padding: 12rpx 0 32rpx;
}

.empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80rpx 24rpx;
}
.empty-icon {
  font-size: 64rpx;
  opacity: 0.35;
}
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
