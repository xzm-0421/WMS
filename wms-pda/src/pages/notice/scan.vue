<template>
  <view class="page">
    <view class="scan-top">
      <CompactScanBox ref="scanInputRef" :disabled="loading || submitting" @scan="onScan" />
    </view>

    <view v-if="detail" class="order-bar">
      <view class="order-info">
        <text class="order-no">{{ detail.billNo }}</text>
        <text class="order-sub">{{ detail.supplierName || detail.billTypeLabel || '-' }}</text>
      </view>
      <view class="order-stat-wrap">
        <text class="order-stat">{{ checkedCount }}/{{ lines.length }} 已勾</text>
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
            <text class="mat-code">{{ line.materialCode }}</text>
            <text class="mat-name">{{ line.materialName || '-' }}</text>
            <text class="mat-spec">规格 {{ line.specification || '-' }}</text>
            <text v-if="isInbound" class="mat-wh">仓库 {{ line.erpStockCode || detail?.warehouseCode || '-' }}</text>
          </view>
        </view>
        <view class="qty-panel" @click.stop>
          <view class="qty-grid">
            <view class="qty-cell"><text class="qty-label">计划</text><text class="qty-value">{{ formatQty(line.planQty) }}</text></view>
            <view class="qty-cell"><text class="qty-label">已处理</text><text class="qty-value submitted">{{ formatQty(line.submittedQty) }}</text></view>
            <view class="qty-cell"><text class="qty-label">可处理</text><text class="qty-value remain">{{ formatQty(line.remainQty) }}</text></view>
            <view class="qty-cell"><text class="qty-label">单位</text><text class="qty-value unit">{{ line.unitCode || 'PCS' }}</text></view>
          </view>
          <view v-if="!isDoneLine(line)" class="qty-edit">
            <text class="qty-edit-label">本次数量</text>
            <input
              class="qty-input"
              type="digit"
              :value="getQtyDraft(line)"
              @input="onQtyInput(line, $event)"
              @blur="onQtyBlur(line)"
            />
          </view>
        </view>
      </view>

      <view v-if="isInbound && detail" class="wh-section">
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
        {{ submitLabel }}{{ submitableCount ? ` (${submitableCount})` : '' }}
      </button>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, reactive } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import CompactScanBox from '@/components/CompactScanBox.vue'
import WarehousePicker from '@/components/WarehousePicker.vue'
import useNoticeBillScan from '@/composables/useNoticeBillScan.js'
import usePageAlive from '@/composables/usePageAlive.js'
import { getNoticeBillType } from '@/constants/noticeBillTypes.js'

const billType = ref('PURCHASE_RECEIVE')
const billNo = ref('')
const scanInputRef = ref(null)
const warehousePickerRef = ref(null)
const warehousePayload = ref({ autoAssignWarehouse: true })
const qtyDrafts = reactive({})
const { alive, refocusScanInput } = usePageAlive()

const {
  loading, submitting, detail, lines, isInbound, checkedCount, submitableCount,
  loadDetail, handleScan, toggleCheck, updateQty, formatQty, submit, rowClass,
} = useNoticeBillScan(billType, billNo)

const submitLabel = computed(() => (isInbound.value ? '提交入库' : '提交出库'))

const suggestWarehouseCode = computed(() => {
  if (!isInbound.value) return detail.value?.warehouseCode || ''
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

function syncQtyDrafts() {
  lines.value.forEach((line) => { qtyDrafts[line.lineNo] = formatQty(line.pendingSubmitQty || 0) })
}
function getQtyDraft(line) {
  if (qtyDrafts[line.lineNo] == null) qtyDrafts[line.lineNo] = formatQty(line.pendingSubmitQty || 0)
  return qtyDrafts[line.lineNo]
}
function onQtyInput(line, e) { qtyDrafts[line.lineNo] = e.detail.value }
async function onQtyBlur(line) {
  const num = Number(qtyDrafts[line.lineNo] || 0)
  if (Number.isNaN(num) || num < 0) return
  const ok = await updateQty(line.lineNo, num)
  if (ok) syncQtyDrafts()
}
async function onScan(barcode) {
  if (!alive.value) return
  await handleScan(barcode)
  syncQtyDrafts()
  refocusScanInput(scanInputRef, 300)
}
function onToggle(line) { toggleCheck(line.lineNo, !line.checked) }
function onRowTap(line) { if (!line.checked && !isDoneLine(line)) toggleCheck(line.lineNo, true) }
async function onSubmit() {
  const ok = await submit(() => warehousePickerRef.value?.getPayload?.() || warehousePayload.value)
  if (ok) syncQtyDrafts()
}

onLoad((options) => {
  billType.value = options?.billType || 'PURCHASE_RECEIVE'
  billNo.value = decodeURIComponent(options?.billNo || '')
  uni.setNavigationBarTitle({ title: getNoticeBillType(billType.value).label + ' · 扫码' })
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
.page { display: flex; flex-direction: column; height: 100vh; background: #f1f5f9; }
.scan-top { flex-shrink: 0; padding: 20rpx 24rpx 12rpx; background: #fff; border-bottom: 1rpx solid #e2e8f0; }
.order-bar { flex-shrink: 0; display: flex; justify-content: space-between; padding: 12rpx 24rpx; background: #fff; border-bottom: 1rpx solid #e2e8f0; }
.order-no { font-size: 26rpx; font-weight: 700; color: #1d4ed8; display: block; }
.order-sub { font-size: 22rpx; color: #64748b; display: block; }
.order-stat { font-size: 22rpx; color: #3b82f6; font-weight: 600; }
.list-scroll { flex: 1; height: 0; padding: 16rpx 24rpx 0; }
.mat-row { background: #fff; border: 1rpx solid #e2e8f0; border-radius: 12rpx; padding: 16rpx; margin-bottom: 12rpx; }
.mat-row.checked { border-left: 6rpx solid #3b82f6; }
.row-header { display: flex; gap: 12rpx; }
.check-inner { width: 40rpx; height: 40rpx; border: 2rpx solid #cbd5e1; border-radius: 8rpx; display: flex; align-items: center; justify-content: center; }
.check-inner.on { background: #1d4ed8; border-color: #1d4ed8; }
.check-mark { color: #fff; font-size: 24rpx; }
.mat-code { font-size: 24rpx; font-weight: 700; color: #0f172a; display: block; }
.mat-name { font-size: 26rpx; color: #334155; display: block; margin-top: 4rpx; }
.mat-spec, .mat-wh { font-size: 22rpx; color: #64748b; display: block; margin-top: 4rpx; }
.qty-panel { margin-top: 14rpx; padding: 14rpx; background: #f8fafc; border-radius: 10rpx; }
.qty-grid { display: flex; flex-wrap: wrap; }
.qty-cell { width: 25%; }
.qty-label { font-size: 20rpx; color: #94a3b8; display: block; }
.qty-value { font-size: 28rpx; font-weight: 700; display: block; }
.qty-edit { display: flex; align-items: center; gap: 12rpx; margin-top: 12rpx; }
.qty-input { flex: 1; height: 64rpx; text-align: center; border: 2rpx solid #93c5fd; border-radius: 10rpx; }
.wh-section { margin-bottom: 12rpx; }
.scroll-bottom-pad { height: 140rpx; }
.footer { flex-shrink: 0; padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom)); background: #fff; border-top: 1rpx solid #e2e8f0; }
.submit-btn { background: #1d4ed8; color: #fff; border-radius: 12rpx; font-weight: 600; }
.submit-btn[disabled] { background: #94a3b8; }
</style>
