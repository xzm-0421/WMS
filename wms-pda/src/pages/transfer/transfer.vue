<template>
  <view class="page">
    

    <!-- 步骤1：扫源库 -->
    <view class="card">
      <view class="card-head">
        <text class="card-title">源库位</text>
        <text v-if="form.sourceLocation" class="link" @click="resetSource">重扫源库</text>
      </view>
      <ScanSearchBar
        v-if="step === 1"
        ref="sourceScanRef"
        v-model="sourceInput"
        placeholder="扫码或输入源库位"
        action-text="确定"
        :disabled="busy"
        @scan="onSourceScan"
        @search="onSourceScan"
      />
      <text v-if="step === 1" class="hint">请扫描或输入源库位后点确定</text>
      <view v-else class="value-box">
        <text class="value-main">{{ form.sourceLocation }}</text>
        <text class="value-sub">仓库 {{ form.sourceWarehouse || '-' }} · 库存行 {{ stocks.length }}</text>
      </view>
    </view>

    <!-- 物料列表 / 已选物料 -->
    <view v-if="step >= 2" class="card">
      <view class="card-head">
        <text class="card-title">移库物料</text>
        <text v-if="step >= 3 && !qtyConfirmed" class="link" @click="reopenQty">修改数量</text>
      </view>

      <view v-if="!selectedStock" class="stock-list">
        <view
          v-for="(item, idx) in stocks"
          :key="idx"
          class="stock-row"
          @click="selectStock(item)"
        >
          <view class="stock-main">
            <text class="mat-code">{{ item.materialCode }}</text>
            <text class="mat-name">{{ item.materialName || '-' }}</text>
            <text class="mat-meta">批次 {{ item.batchNo || '-' }} · 可用 {{ formatQty(item.availableQty) }}</text>
          </view>
          <text class="arrow">›</text>
        </view>
        <view v-if="!stocks.length" class="empty">该库位暂无可用库存</view>
      </view>

      <view v-else class="selected-box">
        <text class="mat-code">{{ form.materialCode }}</text>
        <text class="mat-name">{{ form.materialName || '-' }}</text>
        <text class="mat-meta">批次 {{ form.batchNo || '-' }} · 可用 {{ formatQty(form.availableQty) }}</text>
      </view>
    </view>

    <!-- 步骤2：数量输入（确认前） -->
    <view v-if="step === 2 && selectedStock && !qtyConfirmed" class="card qty-card">
      <text class="card-title">移库数量</text>
      <text class="hint">可手输数量，或扫描/输入物料标签后点确定自动填入</text>
      <ScanSearchBar
        ref="qtyScanRef"
        v-model="qtyScanInput"
        placeholder="扫码或输入物料标签获取数量"
        action-text="填入"
        :disabled="busy"
        @scan="onQtyScan"
        @search="onQtyScan"
      />
      <view class="qty-row">
        <input
          v-model="form.transferQty"
          class="qty-input"
          type="text"
          inputmode="decimal"
          placeholder="输入移库数量"
          :disabled="busy"
        />
        <text class="unit">{{ form.unitCode || '' }}</text>
      </view>
      <button class="primary-btn" type="primary" :loading="busy" @click="confirmQty">确认数量</button>
    </view>

    <view v-if="qtyConfirmed" class="card">
      <text class="card-title">已确认数量</text>
      <text class="qty-confirmed">{{ formatQty(form.transferQty) }} {{ form.unitCode || '' }}</text>
    </view>

    <!-- 步骤3：目标库 -->
    <view v-if="step >= 3" class="card">
      <view class="card-head">
        <text class="card-title">目标库位</text>
        <text v-if="form.targetLocation" class="link" @click="resetTarget">重扫目标库</text>
      </view>
      <ScanSearchBar
        v-if="!form.targetLocation"
        ref="targetScanRef"
        v-model="targetInput"
        placeholder="扫码或输入目标库位"
        action-text="确定"
        :disabled="busy || !qtyConfirmed"
        @scan="onTargetScan"
        @search="onTargetScan"
      />
      <text v-if="!form.targetLocation" class="hint">确认数量后请扫描或输入目标库位</text>
      <view v-else class="value-box">
        <text class="value-main">{{ form.targetLocation }}</text>
      </view>
    </view>

    <!-- 步骤4：提交 -->
    <view v-if="step >= 4" class="footer">
      <view class="summary">
        <text>{{ form.sourceLocation }} → {{ form.targetLocation }}</text>
        <text>{{ form.materialCode }} × {{ formatQty(form.transferQty) }}</text>
      </view>
      <button
        class="submit-btn"
        type="primary"
        :loading="busy"
        @click="submitTransfer"
      >
        确认移库
      </button>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive, computed, watch, nextTick } from 'vue'
import { onLoad, onShow, onHide } from '@dcloudio/uni-app'
import ScanSearchBar from '@/components/ScanSearchBar.vue'
import usePageAlive from '@/composables/usePageAlive.js'
import { queryInventoryPost, transferStock } from '@/api/mobile.js'
import { resolveBarcode } from '@/utils/scan.js'

const DRAFT_KEY = 'pda-transfer-draft'
const DRAFT_TTL_MS = 2 * 60 * 60 * 1000

const busy = ref(false)
const stocks = ref([])
const selectedStock = ref(null)
const qtyConfirmed = ref(false)
const sourceScanRef = ref(null)
const qtyScanRef = ref(null)
const targetScanRef = ref(null)
const sourceInput = ref('')
const qtyScanInput = ref('')
const targetInput = ref('')
const { alive, refocusScanInput } = usePageAlive()

const form = reactive({
  sourceLocation: '',
  sourceWarehouse: '',
  targetLocation: '',
  materialCode: '',
  materialName: '',
  batchNo: '',
  unitCode: '',
  availableQty: 0,
  transferQty: '',
})

const step = computed(() => {
  if (!form.sourceLocation) return 1
  if (!qtyConfirmed.value) return 2
  if (!form.targetLocation) return 3
  return 4
})

watch(step, (val) => {
  nextTick(() => focusByStep(val))
})

function formatQty(val) {
  if (val == null || val === '') return '0'
  const n = Number(val)
  return Number.isNaN(n) ? String(val) : String(n)
}

function toast(title, icon = 'none') {
  uni.showToast({ title, icon, duration: 2200 })
}

function focusByStep(val) {
  if (!alive.value) return
  if (val === 1) refocusScanInput(sourceScanRef, 200)
  else if (val === 2 && !qtyConfirmed.value) refocusScanInput(qtyScanRef, 200)
  else if (val === 3 && !form.targetLocation) refocusScanInput(targetScanRef, 200)
}

function persistDraft() {
  const payload = {
    savedAt: Date.now(),
    form: { ...form },
    stocks: stocks.value,
    selectedStock: selectedStock.value,
    qtyConfirmed: qtyConfirmed.value,
  }
  try {
    uni.setStorageSync(DRAFT_KEY, JSON.stringify(payload))
  } catch {
    // ignore
  }
}

function restoreDraft() {
  try {
    const raw = uni.getStorageSync(DRAFT_KEY)
    if (!raw) return false
    const data = typeof raw === 'string' ? JSON.parse(raw) : raw
    if (!data?.savedAt || Date.now() - data.savedAt > DRAFT_TTL_MS) {
      clearDraft()
      return false
    }
    Object.assign(form, data.form || {})
    stocks.value = data.stocks || []
    selectedStock.value = data.selectedStock || null
    qtyConfirmed.value = !!data.qtyConfirmed
    return true
  } catch {
    return false
  }
}

function clearDraft() {
  try {
    uni.removeStorageSync(DRAFT_KEY)
  } catch {
    // ignore
  }
}

function resetAll() {
  Object.assign(form, {
    sourceLocation: '',
    sourceWarehouse: '',
    targetLocation: '',
    materialCode: '',
    materialName: '',
    batchNo: '',
    unitCode: '',
    availableQty: 0,
    transferQty: '',
  })
  stocks.value = []
  selectedStock.value = null
  qtyConfirmed.value = false
  sourceInput.value = ''
  qtyScanInput.value = ''
  targetInput.value = ''
  clearDraft()
}

function resetSource() {
  resetAll()
  persistDraft()
  nextTick(() => focusByStep(1))
}

function resetTarget() {
  form.targetLocation = ''
  persistDraft()
  nextTick(() => focusByStep(3))
}

function reopenQty() {
  qtyConfirmed.value = false
  form.targetLocation = ''
  persistDraft()
  nextTick(() => focusByStep(2))
}

function parseLocationCode(raw, parsed) {
  const code = (parsed?.locationCode || raw || '').trim()
  if (!code) return ''
  // 库位码常见：含字母数字，避免误把物料码当库位
  return code
}

async function loadSourceStocks(locationCode) {
  busy.value = true
  try {
    const res = await queryInventoryPost({
      barcode: locationCode,
      locationCode,
      queryType: 'LOCATION',
    })
    const list = res?.stocks || []
    form.sourceLocation = locationCode
    form.sourceWarehouse = list[0]?.warehouseCode || form.sourceWarehouse || ''
    stocks.value = list
    selectedStock.value = null
    qtyConfirmed.value = false
    form.targetLocation = ''
    form.materialCode = ''
    form.materialName = ''
    form.batchNo = ''
    form.unitCode = ''
    form.availableQty = 0
    form.transferQty = ''

    if (!list.length) {
      toast(res?.message || '该库位暂无可用库存')
      persistDraft()
      return
    }
    if (list.length === 1) {
      selectStock(list[0])
      toast(`已加载 ${list[0].materialCode}`)
    } else {
      toast(`已加载 ${list.length} 条库存，请选择物料`)
    }
    persistDraft()
  } catch (e) {
    toast(e?.message || '源库位查询失败')
  } finally {
    busy.value = false
    nextTick(() => focusByStep(step.value))
  }
}

async function onSourceScan(barcode) {
  if (!alive.value || busy.value) return
  const raw = String(barcode || '').trim()
  if (!raw) return
  sourceInput.value = ''
  try {
    const parsed = await resolveBarcode(raw)
    const locationCode = parseLocationCode(raw, parsed)
    if (!locationCode) {
      toast('未识别到库位编码')
      return
    }
    await loadSourceStocks(locationCode)
  } catch (e) {
    toast(e?.message || '扫码失败')
    refocusScanInput(sourceScanRef, 300)
  }
}

function selectStock(item) {
  if (!item) return
  selectedStock.value = item
  form.materialCode = item.materialCode || ''
  form.materialName = item.materialName || ''
  form.batchNo = item.batchNo || ''
  form.unitCode = item.unitCode || ''
  form.availableQty = Number(item.availableQty ?? item.stockQty ?? 0)
  form.sourceWarehouse = item.warehouseCode || form.sourceWarehouse
  if (!form.transferQty) {
    form.transferQty = ''
  }
  qtyConfirmed.value = false
  form.targetLocation = ''
  persistDraft()
  nextTick(() => focusByStep(2))
}

function qtyFromParsed(parsed) {
  const segments = parsed?.segments || {}
  const raw = segments.qty ?? segments.quantity ?? segments.actualQty ?? segments.transferQty
  if (raw == null || raw === '') return ''
  const n = Number(raw)
  return Number.isNaN(n) ? '' : String(n)
}

async function onQtyScan(barcode) {
  if (!alive.value || busy.value || !selectedStock.value) return
  const raw = String(barcode || '').trim()
  if (!raw) return
  qtyScanInput.value = ''
  try {
    const parsed = await resolveBarcode(raw)
    const mat = (parsed.materialCode || '').trim()
    if (mat && form.materialCode && mat !== form.materialCode) {
      toast(`标签物料 ${mat} 与当前选择不一致`)
      return
    }
    if (parsed.batchNo && form.batchNo && parsed.batchNo !== form.batchNo) {
      toast('标签批次与当前选择不一致')
      return
    }
    const qty = qtyFromParsed(parsed)
    if (!qty) {
      toast('标签未解析到数量，请手输')
      return
    }
    form.transferQty = qty
    toast(`已填入数量 ${qty}`)
    persistDraft()
  } catch (e) {
    toast(e?.message || '数量扫码失败')
  } finally {
    refocusScanInput(qtyScanRef, 300)
  }
}

function confirmQty() {
  const qty = Number(form.transferQty)
  if (!form.materialCode) {
    toast('请先选择物料')
    return
  }
  if (!form.transferQty || Number.isNaN(qty) || qty <= 0) {
    toast('移库数量不合法')
    return
  }
  if (qty > Number(form.availableQty || 0)) {
    toast(`数量超过可用库存（可用 ${formatQty(form.availableQty)}）`)
    return
  }
  qtyConfirmed.value = true
  persistDraft()
  toast('数量已确认，请扫目标库位', 'success')
  nextTick(() => focusByStep(3))
}

async function onTargetScan(barcode) {
  if (!alive.value || busy.value || !qtyConfirmed.value) {
    toast('请先确认移库数量')
    return
  }
  const raw = String(barcode || '').trim()
  if (!raw) return
  targetInput.value = ''
  try {
    const parsed = await resolveBarcode(raw)
    const locationCode = parseLocationCode(raw, parsed)
    if (!locationCode) {
      toast('未识别到目标库位')
      return
    }
    if (locationCode === form.sourceLocation) {
      toast('目标库位不能与源库位相同')
      return
    }
    form.targetLocation = locationCode
    persistDraft()
    toast('目标库位已确认', 'success')
  } catch (e) {
    toast(e?.message || '目标库位扫码失败')
  } finally {
    nextTick(() => focusByStep(step.value))
  }
}

async function submitTransfer() {
  if (!form.sourceLocation) {
    toast('请先扫描源库位')
    return
  }
  if (!form.materialCode) {
    toast('请选择移库物料')
    return
  }
  if (!form.transferQty || Number(form.transferQty) <= 0) {
    toast('移库数量不合法')
    return
  }
  if (!form.targetLocation) {
    toast('请先扫描目标库位')
    return
  }
  busy.value = true
  try {
    const res = await transferStock({
      sourceLocation: form.sourceLocation,
      targetLocation: form.targetLocation,
      materialCode: form.materialCode,
      batchNo: form.batchNo || undefined,
      transferQty: Number(form.transferQty),
    })
    toast(res?.transferNo ? `移库成功 ${res.transferNo}` : '移库成功', 'success')
    resetAll()
    nextTick(() => focusByStep(1))
  } catch (e) {
    toast(e?.message || '移库失败')
  } finally {
    busy.value = false
  }
}

onLoad(() => {
  uni.setNavigationBarTitle({ title: '移库作业' })
  restoreDraft()
})

onShow(() => {
  restoreDraft()
  nextTick(() => focusByStep(step.value))
})

onHide(() => {
  persistDraft()
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 20rpx 24rpx 160rpx;
  background: #f1f5f9;
}
.steps {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8rpx;
  margin-bottom: 16rpx;
}
.step {
  font-size: 22rpx;
  color: #94a3b8;
  background: #e2e8f0;
  padding: 6rpx 14rpx;
  border-radius: 999rpx;
}
.step.on {
  color: #fff;
  background: #1d4ed8;
}
.sep { color: #cbd5e1; font-size: 22rpx; }
.card {
  background: #fff;
  border: 1rpx solid #e2e8f0;
  border-radius: 12rpx;
  padding: 20rpx;
  margin-bottom: 16rpx;
}
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12rpx;
}
.card-title {
  font-size: 28rpx;
  font-weight: 700;
  color: #0f172a;
}
.link {
  font-size: 24rpx;
  color: #2563eb;
}
.hint {
  display: block;
  margin-top: 12rpx;
  font-size: 22rpx;
  color: #94a3b8;
  text-align: center;
}
.value-box {
  padding: 12rpx 0;
}
.value-main {
  display: block;
  font-size: 32rpx;
  font-weight: 700;
  color: #1d4ed8;
}
.value-sub {
  display: block;
  margin-top: 6rpx;
  font-size: 22rpx;
  color: #64748b;
}
.stock-row {
  display: flex;
  align-items: center;
  padding: 16rpx 0;
  border-bottom: 1rpx solid #f1f5f9;
}
.stock-main { flex: 1; min-width: 0; }
.mat-code {
  display: block;
  font-size: 26rpx;
  font-weight: 700;
  color: #0f172a;
  font-family: monospace;
}
.mat-name {
  display: block;
  margin-top: 4rpx;
  font-size: 24rpx;
  color: #334155;
}
.mat-meta {
  display: block;
  margin-top: 4rpx;
  font-size: 22rpx;
  color: #64748b;
}
.arrow { color: #cbd5e1; font-size: 36rpx; }
.selected-box {
  padding: 8rpx 0;
}
.empty {
  text-align: center;
  color: #94a3b8;
  padding: 32rpx 0;
  font-size: 24rpx;
}
.qty-card .qty-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-top: 16rpx;
}
.qty-input {
  flex: 1;
  height: 72rpx;
  border: 2rpx solid #93c5fd;
  border-radius: 10rpx;
  text-align: center;
  font-size: 36rpx;
  font-weight: 700;
  color: #1d4ed8;
  background: #fff;
}
.unit {
  min-width: 48rpx;
  color: #64748b;
  font-size: 24rpx;
}
.qty-confirmed {
  display: block;
  margin-top: 8rpx;
  font-size: 40rpx;
  font-weight: 700;
  color: #16a34a;
}
.primary-btn {
  margin-top: 20rpx;
  background: #1d4ed8;
  color: #fff;
  border-radius: 12rpx;
  font-weight: 600;
}
.footer {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background: #fff;
  border-top: 1rpx solid #e2e8f0;
}
.summary {
  margin-bottom: 12rpx;
  font-size: 22rpx;
  color: #64748b;
}
.summary text {
  display: block;
  line-height: 1.5;
}
.submit-btn {
  background: #1d4ed8;
  color: #fff;
  border-radius: 12rpx;
  font-weight: 700;
}
</style>
