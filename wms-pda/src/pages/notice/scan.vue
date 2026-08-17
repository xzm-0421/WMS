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
            <view class="qty-cell"><text class="qty-label">计划</text><text class="qty-value">{{ formatQty(inputPlanQty(line), line.inputUnitCode || line.unitCode) }}</text></view>
            <view class="qty-cell"><text class="qty-label">已处理</text><text class="qty-value submitted">{{ formatQty(inputSubmittedQty(line), line.inputUnitCode || line.unitCode) }}</text></view>
            <view class="qty-cell"><text class="qty-label">可处理</text><text class="qty-value remain">{{ formatQty(inputRemainQty(line), line.inputUnitCode || line.unitCode) }}</text></view>
            <view class="qty-cell"><text class="qty-label">单位</text><text class="qty-value unit">{{ line.inputUnitCode || line.unitCode || 'PCS' }}</text></view>
          </view>
          <view v-if="line.multiUnit" class="qty-grid aux-grid">
            <view class="qty-cell"><text class="qty-label">计划({{ line.autoUnitCode || line.auxUnitCode }})</text><text class="qty-value">{{ formatQty(autoPlanQty(line), line.autoUnitCode || line.auxUnitCode) }}</text></view>
            <view class="qty-cell"><text class="qty-label">已处理</text><text class="qty-value submitted">{{ formatQty(autoSubmittedQty(line), line.autoUnitCode || line.auxUnitCode) }}</text></view>
            <view class="qty-cell"><text class="qty-label">可处理</text><text class="qty-value remain">{{ formatQty(autoRemainQty(line), line.autoUnitCode || line.auxUnitCode) }}</text></view>
            <view class="qty-cell"><text class="qty-label">单位</text><text class="qty-value unit">{{ line.autoUnitCode || line.auxUnitCode }}</text></view>
          </view>
          <view v-if="canEditLine(line)" class="qty-edit">
            <text class="qty-edit-label">本次({{ line.inputUnitCode || line.unitCode || 'PCS' }})</text>
            <input
              class="qty-input"
              type="text"
              inputmode="decimal"
              :value="getQtyDraft(line)"
              @input="onQtyInput(line, $event)"
              @blur="onQtyBlur(line)"
            />
          </view>
          <view v-if="canEditLine(line) && line.multiUnit" class="qty-edit">
            <text class="qty-edit-label">换算KG({{ line.autoUnitCode || line.auxUnitCode }})</text>
            <input
              class="qty-input"
              type="text"
              inputmode="decimal"
              :value="getAuxQtyDraft(line)"
              @input="onAuxQtyInput(line, $event)"
              @blur="onAuxQtyBlur(line)"
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
        <LocationPicker
          ref="locationPickerRef"
          :warehouse-code="resolvedWarehouseCode"
          :material-code="suggestMaterialCode"
          :batch-no="suggestBatchNo"
          theme="light"
          @change="onLocationChange"
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
import LocationPicker from '@/components/LocationPicker.vue'
import useNoticeBillScan from '@/composables/useNoticeBillScan.js'
import usePageAlive from '@/composables/usePageAlive.js'
import { getNoticeBillType } from '@/constants/noticeBillTypes.js'
import { sanitizeDecimalInput } from '@/utils/decimalInput.js'
import { qtyDecimalScale, formatQtyInput } from '@/utils/formatQty.js'

const billType = ref('PURCHASE_RECEIVE')
const billNo = ref('')
const scanInputRef = ref(null)
const warehousePickerRef = ref(null)
const locationPickerRef = ref(null)
const warehousePayload = ref({ autoAssignWarehouse: true })
const locationPayload = ref({ autoAllocateLocation: false })
const qtyDrafts = reactive({})
const auxQtyDrafts = reactive({})
const { alive, refocusScanInput } = usePageAlive()

const {
  loading, submitting, detail, lines, isInbound, checkedCount, submitableCount,
  loadDetail, handleScan, toggleCheck, updateQty, formatQty, submit, rowClass, isLabelScanned,
} = useNoticeBillScan(billType, billNo)

const submitLabel = computed(() => (isInbound.value ? '提交入库' : '提交出库'))

const suggestWarehouseCode = computed(() => {
  if (!isInbound.value) return detail.value?.warehouseCode || ''
  const pending = lines.value.find((l) => l.checked && (Number(l.pendingSubmitQty) || 0) > 0)
  if (pending?.erpStockCode) return pending.erpStockCode
  return detail.value?.erpWarehouseCode || detail.value?.warehouseCode || ''
})

const resolvedWarehouseCode = computed(() => {
  const wh = warehousePayload.value
  if (wh?.autoAssignWarehouse === false && wh?.warehouseCode) {
    return wh.warehouseCode
  }
  return suggestWarehouseCode.value || ''
})

const suggestMaterialCode = computed(() => {
  const pending = lines.value.find((l) => l.checked && (Number(l.pendingSubmitQty) || 0) > 0)
  return pending?.materialCode || lines.value[0]?.materialCode || ''
})

const suggestBatchNo = computed(() => {
  const pending = lines.value.find((l) => l.checked && (Number(l.pendingSubmitQty) || 0) > 0)
  return pending?.batchNo || ''
})

function onWarehouseChange(payload) {
  warehousePayload.value = payload || { autoAssignWarehouse: true }
}

function onLocationChange(payload) {
  locationPayload.value = payload || { autoAllocateLocation: false }
}

function isDoneLine(line) {
  const pending = Number(line.pendingSubmitQty) || 0
  const pendingAux = Number(line.pendingSubmitAuxQty) || 0
  if (pending > 0 || pendingAux > 0) return false

  const submitted = Number(line.submittedQty) || 0
  const plan = Number(line.planQty) || 0
  if (plan > 0 && submitted >= plan) return true
  if (inputMapsToAux(line)) {
    const auxPlan = Number(line.planAuxQty) || 0
    const auxSubmitted = Number(line.submittedAuxQty) || 0
    if (plan <= 0 && auxPlan > 0 && auxSubmitted >= auxPlan) return true
  }
  return false
}

/** 仅可处理>0 或仍有待提交时可录入 */
function canEditLine(line) {
  const remain = Number(inputRemainQty(line)) || 0
  const pending = Number(inputPendingQty(line)) || 0
  return remain > 0 || pending > 0
}

function inputMapsToAux(line) {
  return !!(line?.multiUnit && line?.inputMapsToAux)
}

function inputPendingQty(line) {
  return inputMapsToAux(line) ? (line.pendingSubmitAuxQty || 0) : (line.pendingSubmitQty || 0)
}

function inputPlanQty(line) {
  return inputMapsToAux(line) ? line.planAuxQty : line.planQty
}

function inputSubmittedQty(line) {
  return inputMapsToAux(line) ? line.submittedAuxQty : line.submittedQty
}

function inputRemainQty(line) {
  return inputMapsToAux(line) ? line.remainAuxQty : line.remainQty
}

function autoPendingQty(line) {
  return inputMapsToAux(line) ? (line.pendingSubmitQty || 0) : (line.pendingSubmitAuxQty || 0)
}

function autoPlanQty(line) {
  return inputMapsToAux(line) ? line.planQty : line.planAuxQty
}

function autoSubmittedQty(line) {
  return inputMapsToAux(line) ? line.submittedQty : line.submittedAuxQty
}

function autoRemainQty(line) {
  return inputMapsToAux(line) ? line.remainQty : line.remainAuxQty
}

function convertByPlanRate(qty, fromPlan, toPlan) {
  const q = Number(qty) || 0
  const from = Number(fromPlan) || 0
  const to = Number(toPlan) || 0
  if (q <= 0) return 0
  if (from <= 0 || to <= 0) return q
  const n = (q * to) / from
  return Number.isInteger(n) ? n : Number(n.toFixed(6))
}

/** 件数 → KG：按计划件数/计划重量换算 */
function pcsToKg(line, pcs) {
  if (inputMapsToAux(line)) {
    return convertByPlanRate(pcs, line.planAuxQty, line.planQty)
  }
  return convertByPlanRate(pcs, line.planQty, line.planAuxQty)
}

/** KG → 件数 */
function kgToPcs(line, kg) {
  if (inputMapsToAux(line)) {
    return convertByPlanRate(kg, line.planQty, line.planAuxQty)
  }
  return convertByPlanRate(kg, line.planAuxQty, line.planQty)
}

function inputUnitOf(line) {
  return line.inputUnitCode || line.unitCode
}
function autoUnitOf(line) {
  return line.autoUnitCode || line.auxUnitCode
}
function syncQtyDrafts() {
  lines.value.forEach((line) => {
    qtyDrafts[line.lineNo] = formatQtyInput(inputPendingQty(line), inputUnitOf(line))
    if (line.multiUnit) {
      auxQtyDrafts[line.lineNo] = formatQtyInput(autoPendingQty(line), autoUnitOf(line))
    }
  })
}
function getQtyDraft(line) {
  const key = line.lineNo
  if (qtyDrafts[key] === undefined || qtyDrafts[key] === null) {
    return formatQtyInput(inputPendingQty(line), inputUnitOf(line))
  }
  return qtyDrafts[key]
}
function getAuxQtyDraft(line) {
  const key = line.lineNo
  if (auxQtyDrafts[key] === undefined || auxQtyDrafts[key] === null) {
    return formatQtyInput(autoPendingQty(line), autoUnitOf(line))
  }
  return auxQtyDrafts[key]
}
function onQtyInput(line, e) {
  const raw = sanitizeDecimalInput(e.detail.value, qtyDecimalScale(inputUnitOf(line)))
  qtyDrafts[line.lineNo] = raw
  // 改 PCS 即时带出 KG
  if (line.multiUnit) {
    const pcs = Number(raw || 0)
    auxQtyDrafts[line.lineNo] = formatQtyInput(
      Number.isNaN(pcs) || pcs < 0 ? 0 : pcsToKg(line, pcs),
      autoUnitOf(line),
    )
  }
}
function onAuxQtyInput(line, e) {
  auxQtyDrafts[line.lineNo] = sanitizeDecimalInput(e.detail.value, qtyDecimalScale(autoUnitOf(line)))
}
async function onQtyBlur(line) {
  const pcs = Number(qtyDrafts[line.lineNo] || 0)
  if (Number.isNaN(pcs) || pcs < 0) return
  let stockQty = pcs
  let auxQty
  if (line.multiUnit) {
    const kg = pcsToKg(line, pcs)
    auxQtyDrafts[line.lineNo] = formatQtyInput(kg, autoUnitOf(line))
    if (inputMapsToAux(line)) {
      stockQty = kg
      auxQty = pcs
    } else {
      stockQty = pcs
      auxQty = kg
    }
  }
  const ok = await updateQty(line.lineNo, stockQty, auxQty)
  if (ok) syncQtyDrafts()
}
async function onAuxQtyBlur(line) {
  const kg = Number(auxQtyDrafts[line.lineNo] || 0)
  if (Number.isNaN(kg) || kg < 0) return
  const pcs = kgToPcs(line, kg)
  qtyDrafts[line.lineNo] = formatQtyInput(pcs, inputUnitOf(line))
  let stockQty = pcs
  let auxQty = kg
  if (inputMapsToAux(line)) {
    stockQty = kg
    auxQty = pcs
  } else {
    stockQty = pcs
    auxQty = kg
  }
  const ok = await updateQty(line.lineNo, stockQty, auxQty)
  if (ok) syncQtyDrafts()
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
function onRowTap() {
  // 未扫标签不可手动勾选
}
async function onSubmit() {
  const ok = await submit(() => ({
    ...(warehousePickerRef.value?.getPayload?.() || warehousePayload.value),
    ...(locationPickerRef.value?.getPayload?.() || locationPayload.value),
  }))
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
.aux-grid { margin-top: 8rpx; padding-top: 8rpx; border-top: 1rpx dashed #e2e8f0; }
.qty-cell { width: 25%; }
.qty-label { font-size: 20rpx; color: #94a3b8; display: block; }
.qty-value { font-size: 28rpx; font-weight: 700; display: block; }
.qty-edit { display: flex; align-items: center; gap: 12rpx; margin-top: 12rpx; }
.qty-locked-tip { margin-top: 10rpx; font-size: 22rpx; color: #94a3b8; text-align: center; }
.qty-input { flex: 1; height: 64rpx; text-align: center; border: 2rpx solid #93c5fd; border-radius: 10rpx; }
.wh-section { margin-bottom: 12rpx; }
.scroll-bottom-pad { height: 140rpx; }
.footer { flex-shrink: 0; padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom)); background: #fff; border-top: 1rpx solid #e2e8f0; }
.submit-btn { background: #1d4ed8; color: #fff; border-radius: 12rpx; font-weight: 600; }
.submit-btn[disabled] { background: #94a3b8; }
</style>
