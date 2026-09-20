<template>
  <view class="page">
    <view class="scan-top">
      <CompactScanBox
        ref="scanInputRef"
        :disabled="loading || submitting"
        @scan="onScan"
      />
    </view>

    <view v-if="detail" class="order-bar">
      <view class="order-info">
        <text class="order-no">{{ detail.billNo }}</text>
        <text class="order-sub">{{ detail.supplierName || detail.supplierCode || '-' }}</text>
        <text v-if="detail.erpBillNo" class="order-erp">生产退料单 {{ detail.erpBillNo }}</text>
      </view>
      <view class="order-stat-wrap">
        <text class="order-stat">{{ checkedCount }}/{{ lines.length }} 已勾</text>
        <text v-if="partialCount" class="order-partial">部分已退 {{ partialCount }}</text>
      </view>
    </view>

    <scroll-view class="list-scroll" scroll-y :show-scrollbar="false">
      <view
        v-for="line in lines"
        :key="line.lineNo"
        :class="['mat-row', rowClass(line)]"
        @click="onRowTap(line)"
      >
        <view class="row-header">
          <view class="check-box" @click.stop="onToggle(line)">
            <view :class="['check-inner', line.checked && 'on']">
              <text v-if="line.checked" class="check-mark">✓</text>
            </view>
          </view>
          <view class="row-main">
            <view class="name-row">
              <text class="mat-code">{{ line.materialCode }}</text>
              <text v-if="isPartialLine(line)" class="partial-tag">部分已退</text>
            </view>
            <text class="mat-name">{{ line.materialName || '-' }}</text>
            <text class="mat-spec">规格 {{ line.specification || '-' }}</text>
            <text class="mat-batch">批次 {{ line.batchNo || '-' }}</text>
            <text class="mat-wh">仓库 {{ line.erpStockCode || '-' }}</text>
          </view>
        </view>

        <view class="qty-panel" @click.stop>
          <view class="qty-grid">
            <view class="qty-cell">
              <text class="qty-label">计划</text>
              <text class="qty-value">{{ formatQty(line.planQty, line.unitCode) }}</text>
            </view>
            <view class="qty-cell">
              <text class="qty-label">已退</text>
              <text class="qty-value submitted">{{ formatQty(line.submittedQty, line.unitCode) }}</text>
            </view>
            <view class="qty-cell">
              <text class="qty-label">可退</text>
              <text class="qty-value remain">{{ formatQty(line.remainQty, line.unitCode) }}</text>
            </view>
            <view class="qty-cell unit-cell">
              <text class="qty-label">单位</text>
              <text class="qty-value unit">{{ line.unitCode || 'PCS' }}</text>
            </view>
          </view>
          <view v-if="!isDoneLine(line)" class="qty-edit">
            <text class="qty-edit-label">本次退料</text>
            <input
              class="qty-input"
              type="digit"
              :value="getQtyDraft(line)"
              :disabled="updatingLineNo === line.lineNo"
              placeholder="0"
              @input="onQtyInput(line, $event)"
              @focus="pauseScanAutoFocus"
              @blur="onQtyBlur(line)"
              @confirm="onQtyBlur(line)"
            />
            <text class="qty-edit-unit">{{ line.unitCode || 'PCS' }}</text>
          </view>
          <view v-else class="qty-done-tip">已全部退完</view>
        </view>
      </view>

      <view v-if="!lines.length && !loading" class="empty">
        <text class="empty-icon">📦</text>
      </view>

      <view v-if="detail" class="wh-section">
        <WarehousePicker
          ref="warehousePickerRef"
          :suggest-code="suggestWarehouseCode"
          @change="onWarehouseChange"
        />
      </view>

      <view class="scroll-bottom-pad" />
    </scroll-view>

    <view class="footer">
      <button
        class="submit-btn"
        type="primary"
        :loading="submitting"
        :disabled="!submitableCount"
        @click="onSubmit"
      >
        确认退料{{ submitableCount ? ` (${submitableCount})` : '' }}
      </button>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, reactive } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import CompactScanBox from '@/components/CompactScanBox.vue'
import WarehousePicker from '@/components/WarehousePicker.vue'
import useProductionReturnScan from '@/composables/useProductionReturnScan.js'
import usePageAlive from '@/composables/usePageAlive.js'
import { pauseScanAutoFocus, resumeScanAutoFocus } from '@/utils/scanFocusGuard.js'
import { sanitizeDecimalInput } from '@/utils/decimalInput.js'
import { qtyDecimalScale, formatQtyInput } from '@/utils/formatQty.js'

const billNo = ref('')
const scanInputRef = ref(null)
const warehousePickerRef = ref(null)
const warehousePayload = ref({ autoAssignWarehouse: true })
const qtyDrafts = reactive({})
const updatingLineNo = ref(null)
const { alive, refocusScanInput } = usePageAlive()

const {
  loading,
  submitting,
  detail,
  lines,
  checkedCount,
  submitableCount,
  loadDetail,
  handleScan,
  toggleCheck,
  updateQty,
  formatQty,
  submit,
  rowClass,
} = useProductionReturnScan(billNo)

const partialCount = computed(() =>
  lines.value.filter((l) => isPartialLine(l)).length,
)

const suggestWarehouseCode = computed(() => {
  const pending = lines.value.find((l) => l.checked && (Number(l.pendingSubmitQty) || 0) > 0)
  if (pending?.erpStockCode) return pending.erpStockCode
  return detail.value?.erpWarehouseCode || detail.value?.warehouseCode || ''
})

function onWarehouseChange(payload) {
  warehousePayload.value = payload || { autoAssignWarehouse: true }
}

function isDoneLine(line) {
  const submitted = Number(line.submittedQty) || 0
  const plan = Number(line.planQty) || 0
  return plan > 0 && submitted >= plan
}

function isPartialLine(line) {
  const submitted = Number(line.submittedQty) || 0
  const plan = Number(line.planQty) || 0
  return submitted > 0 && submitted < plan
}

function syncQtyDrafts() {
  lines.value.forEach((line) => {
    qtyDrafts[line.lineNo] = formatQtyInput(line.pendingSubmitQty || 0, line.unitCode)
  })
}

function getQtyDraft(line) {
  if (qtyDrafts[line.lineNo] == null) {
    qtyDrafts[line.lineNo] = formatQtyInput(line.pendingSubmitQty || 0, line.unitCode)
  }
  return qtyDrafts[line.lineNo]
}

function onQtyInput(line, e) {
  qtyDrafts[line.lineNo] = sanitizeDecimalInput(e.detail.value, qtyDecimalScale(line.unitCode))
}

async function onQtyBlur(line) {
  resumeScanAutoFocus()
  const raw = qtyDrafts[line.lineNo]
  const num = raw === '' || raw == null ? 0 : Number(raw)
  if (Number.isNaN(num) || num < 0) {
    qtyDrafts[line.lineNo] = formatQtyInput(line.pendingSubmitQty || 0, line.unitCode)
    return
  }
  const current = Number(line.pendingSubmitQty) || 0
  if (num === current) return
  updatingLineNo.value = line.lineNo
  const ok = await updateQty(line.lineNo, num)
  updatingLineNo.value = null
  if (ok) {
    const updated = lines.value.find((l) => l.lineNo === line.lineNo)
    if (updated) qtyDrafts[line.lineNo] = formatQtyInput(updated.pendingSubmitQty || 0, updated.unitCode)
  } else {
    qtyDrafts[line.lineNo] = formatQtyInput(line.pendingSubmitQty || 0, line.unitCode)
  }
}

async function onScan(barcode) {
  if (!alive.value) return
  await handleScan(barcode)
  syncQtyDrafts()
  refocusScanInput(scanInputRef, 300)
}

function onToggle(line) {
  toggleCheck(line.lineNo, !line.checked)
}

function onRowTap(line) {
  if (!line.checked && !isDoneLine(line)) toggleCheck(line.lineNo, true)
}

async function onSubmit() {
  const ok = await submit(() => warehousePickerRef.value?.getPayload?.() || warehousePayload.value)
  if (ok) syncQtyDrafts()
}

onLoad((options) => {
  billNo.value = decodeURIComponent(options?.billNo || '')
  uni.setNavigationBarTitle({ title: '退料确认' })
})

onShow(async () => {
  if (billNo.value) {
    await loadDetail()
    syncQtyDrafts()
  }
  refocusScanInput(scanInputRef, 400)
})
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f1f5f9;
}
.scan-top {
  flex-shrink: 0;
  padding: 20rpx 24rpx 12rpx;
  background: #fff;
  border-bottom: 1rpx solid #e2e8f0;
}
.order-bar {
  flex-shrink: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12rpx 24rpx;
  background: #fff;
  border-bottom: 1rpx solid #e2e8f0;
}
.order-no { font-size: 26rpx; font-weight: 700; color: #1d4ed8; display: block; }
.order-sub { font-size: 22rpx; color: #64748b; display: block; }
.order-erp { font-size: 20rpx; color: #16a34a; display: block; margin-top: 4rpx; }
.order-stat-wrap { text-align: right; }
.order-stat { font-size: 22rpx; color: #3b82f6; font-weight: 600; display: block; }
.order-partial { font-size: 20rpx; color: #d97706; display: block; margin-top: 2rpx; }
.list-scroll {
  flex: 1;
  height: 0;
  padding: 16rpx 24rpx 0;
}
.mat-row {
  background: #fff;
  border: 1rpx solid #e2e8f0;
  border-radius: 12rpx;
  padding: 16rpx;
  margin-bottom: 12rpx;
}
.mat-row.checked { border-left: 6rpx solid #3b82f6; }
.mat-row.partial { border-left: 6rpx solid #f59e0b; }
.mat-row.done { border-left: 6rpx solid #22c55e; opacity: 0.9; }
.mat-row.flash { animation: flash 0.5s ease; }
@keyframes flash { 50% { background: #eff6ff; } }
.row-header {
  display: flex;
  align-items: flex-start;
  gap: 12rpx;
}
.check-box { flex-shrink: 0; padding: 4rpx 8rpx 0 0; }
.check-inner {
  width: 40rpx;
  height: 40rpx;
  border: 2rpx solid #cbd5e1;
  border-radius: 8rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.check-inner.on {
  background: #1d4ed8;
  border-color: #1d4ed8;
}
.check-mark { color: #fff; font-size: 24rpx; font-weight: bold; }
.row-main { flex: 1; min-width: 0; }
.name-row {
  display: flex;
  align-items: center;
  gap: 8rpx;
  flex-wrap: wrap;
}
.mat-code {
  font-size: 24rpx;
  font-weight: 700;
  color: #0f172a;
  font-family: monospace;
}
.partial-tag {
  font-size: 18rpx;
  color: #b45309;
  background: #fef3c7;
  padding: 2rpx 10rpx;
  border-radius: 6rpx;
}
.mat-name {
  display: block;
  font-size: 26rpx;
  color: #334155;
  margin-top: 4rpx;
  line-height: 1.35;
}
.mat-spec, .mat-batch, .mat-wh {
  display: block;
  font-size: 22rpx;
  color: #64748b;
  margin-top: 4rpx;
}
.qty-panel {
  margin-top: 14rpx;
  padding: 14rpx 12rpx;
  background: #f8fafc;
  border-radius: 10rpx;
  border: 1rpx solid #e2e8f0;
}
.qty-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx 0;
}
.qty-cell {
  width: 25%;
  min-width: 0;
  padding-right: 8rpx;
  box-sizing: border-box;
}
.qty-label {
  display: block;
  font-size: 20rpx;
  color: #94a3b8;
  line-height: 1.2;
}
.qty-value {
  display: block;
  font-size: 28rpx;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.3;
  word-break: break-all;
}
.qty-value.submitted { color: #16a34a; }
.qty-value.remain { color: #1d4ed8; }
.qty-value.unit { font-size: 24rpx; font-weight: 600; }
.qty-edit {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-top: 12rpx;
  padding-top: 12rpx;
  border-top: 1rpx dashed #cbd5e1;
}
.qty-edit-label {
  flex-shrink: 0;
  font-size: 24rpx;
  color: #475569;
  font-weight: 600;
}
.qty-input {
  flex: 1;
  min-width: 0;
  height: 64rpx;
  padding: 0 16rpx;
  font-size: 32rpx;
  font-weight: 700;
  color: #1d4ed8;
  background: #fff;
  border: 2rpx solid #93c5fd;
  border-radius: 10rpx;
  text-align: center;
}
.qty-edit-unit {
  flex-shrink: 0;
  font-size: 24rpx;
  color: #64748b;
  min-width: 56rpx;
}
.qty-done-tip {
  margin-top: 10rpx;
  font-size: 22rpx;
  color: #16a34a;
  text-align: center;
}
.empty { padding: 100rpx 0; text-align: center; }
.empty-icon { font-size: 64rpx; opacity: 0.25; }
.wh-section { margin-bottom: 12rpx; }
.scroll-bottom-pad { height: 140rpx; }
.footer {
  flex-shrink: 0;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background: #fff;
  border-top: 1rpx solid #e2e8f0;
}
.submit-btn {
  background: #1d4ed8;
  color: #fff;
  border-radius: 12rpx;
  font-weight: 600;
}
.submit-btn[disabled] { background: #94a3b8; }
</style>
