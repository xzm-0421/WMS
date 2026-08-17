<template>
  <view class="page">
    <view class="scan-top">
      <CompactScanBox
        ref="scanInputRef"
        :disabled="busy"
        @scan="onScan"
      />
      <text class="scan-hint">对准物料/库位标签连续扫码盘点</text>
    </view>

    <view v-if="task" class="header">
      <text class="task-no">{{ task.taskNo }}</text>
      <text class="task-meta">仓库 {{ task.warehouseCode }} · {{ statusLabel(task.status) }}</text>
      <text class="task-progress">已盘 {{ countedCount }}/{{ details.length }}</text>
    </view>

    <view class="tabs">
      <text :class="['tab', mode === 'scan' && 'active']" @click="switchMode('scan')">扫码盘点</text>
      <text :class="['tab', mode === 'line' && 'active']" @click="switchMode('line')">明细</text>
      <text :class="['tab', mode === 'gain' && 'active']" @click="switchMode('gain')">盘盈</text>
      <text :class="['tab', mode === 'empty' && 'active']" @click="switchMode('empty')">确认空</text>
    </view>

    <scroll-view class="body-scroll" scroll-y :show-scrollbar="false">
      <view v-if="mode === 'scan'" class="form-card">
        <view v-if="matchedLine" class="match-box">
          <text class="match-title">已匹配明细 #{{ matchedLine.lineNo }}</text>
          <text class="match-row">物料 {{ matchedLine.materialCode }}</text>
          <text class="match-row">库位 {{ matchedLine.locationCode || '-' }}</text>
          <text class="match-row">批次 {{ matchedLine.batchNo || '-' }}</text>
          <text class="match-row">账面 {{ formatQty(matchedLine.bookQty) }} · 状态 {{ lineStatusLabel(matchedLine.lineStatus) }}</text>
        </view>
        <view v-else-if="scanForm.materialCode || scanForm.locationCode" class="match-box warn">
          <text class="match-title">未匹配到盘点明细</text>
          <text class="match-row">可改数量后提交，或切到「盘盈」录入</text>
        </view>
        <view v-else class="empty-scan">
          <text>请扫描物料标签开始盘点</text>
        </view>

        <view class="field">
          <text class="label">库位</text>
          <input v-model="scanForm.locationCode" class="input" placeholder="可扫库位码或手输" />
        </view>
        <view class="field">
          <text class="label">物料</text>
          <input v-model="scanForm.materialCode" class="input" placeholder="扫码自动带出" />
        </view>
        <view class="field">
          <text class="label">批次</text>
          <input v-model="scanForm.batchNo" class="input" placeholder="可选" />
        </view>
        <view class="field qty-field">
          <text class="label">实盘数量</text>
          <input
            v-model="scanForm.actualQty"
            class="input qty-input"
            type="text" inputmode="decimal"
            placeholder="输入实盘数"
            @confirm="submitScanForm"
          />
        </view>
        <button class="primary-btn" type="primary" :loading="busy" @click="submitScanForm">
          提交本行盘点
        </button>
      </view>

      <view v-else-if="mode === 'line'">
        <view
          v-for="line in details"
          :key="line.lineNo"
          :class="['line-card', line.lineStatus === 'COUNTED' && 'done', highlightLineNo === line.lineNo && 'flash']"
        >
          <text class="name">{{ line.materialCode }}</text>
          <text class="meta">库位 {{ line.locationCode }} · 批次 {{ line.batchNo || '-' }}</text>
          <text class="meta">账面 {{ formatQty(line.bookQty) }} · {{ lineStatusLabel(line.lineStatus) }}</text>
          <view class="row">
            <input v-model="line._actual" class="qty-input" type="text" inputmode="decimal" placeholder="实盘数量" />
            <button size="mini" type="primary" :disabled="busy" @click="submitLine(line)">提交</button>
          </view>
        </view>
        <view v-if="!details.length" class="empty-scan">暂无盘点明细</view>
      </view>

      <view v-else-if="mode === 'gain'" class="form-card">
        <text class="section-tip">扫码可自动填充库位/物料/批次</text>
        <input v-model="gainForm.locationCode" class="input" placeholder="库位" />
        <input v-model="gainForm.materialCode" class="input" placeholder="物料编码" />
        <input v-model="gainForm.batchNo" class="input" placeholder="批次号" />
        <input v-model="gainForm.actualQty" class="input" type="text" inputmode="decimal" placeholder="盘盈数量" />
        <input v-model="gainForm.remark" class="input" placeholder="备注" />
        <button class="primary-btn" type="primary" :loading="busy" @click="submitGain">提交盘盈</button>
      </view>

      <view v-else class="form-card">
        <text class="section-tip">扫码可自动填充后确认无库存（实盘 0）</text>
        <input v-model="emptyForm.locationCode" class="input" placeholder="库位" />
        <input v-model="emptyForm.materialCode" class="input" placeholder="物料编码" />
        <input v-model="emptyForm.batchNo" class="input" placeholder="批次号(可选)" />
        <button class="warn-btn" type="warn" :loading="busy" @click="submitEmpty">确认无库存</button>
      </view>

      <view class="scroll-pad" />
    </scroll-view>

    <view class="footer">
      <button class="complete-btn" type="warn" :loading="busy" @click="complete">完成盘点</button>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import CompactScanBox from '@/components/CompactScanBox.vue'
import usePageAlive from '@/composables/usePageAlive.js'
import {
  completeStockcheck,
  confirmEmptyStockcheck,
  gainStockcheck,
  getStockcheckTask,
  scanStockcheck,
} from '@/api/mobile.js'
import { resolveBarcode } from '@/utils/scan.js'

const taskNo = ref('')
const task = ref(null)
const details = ref([])
const mode = ref('scan')
const busy = ref(false)
const highlightLineNo = ref(null)
const scanInputRef = ref(null)
const { alive, refocusScanInput } = usePageAlive()

const scanForm = reactive({
  locationCode: '',
  materialCode: '',
  batchNo: '',
  actualQty: '',
  barcodeContent: '',
})
const gainForm = reactive({
  locationCode: '',
  materialCode: '',
  batchNo: '',
  actualQty: '',
  remark: '',
})
const emptyForm = reactive({
  locationCode: '',
  materialCode: '',
  batchNo: '',
})

const countedCount = computed(() =>
  details.value.filter((d) => d.lineStatus === 'COUNTED').length,
)

const matchedLine = computed(() => findMatchLine(scanForm))

function statusLabel(status) {
  if (status === 'COUNTING') return '盘点中'
  if (status === 'COMPLETED') return '已完成'
  if (status === 'PENDING') return '待盘点'
  return status || '-'
}

function lineStatusLabel(status) {
  if (status === 'COUNTED') return '已盘'
  if (status === 'PENDING') return '未盘'
  return status || '未盘'
}

function formatQty(val) {
  if (val == null || val === '') return '0'
  const n = Number(val)
  return Number.isNaN(n) ? String(val) : String(n)
}

function findMatchLine(form) {
  const material = String(form.materialCode || '').trim()
  const location = String(form.locationCode || '').trim()
  const batch = String(form.batchNo || '').trim()
  if (!material && !location) return null
  const candidates = details.value.filter((d) => {
    if (material && String(d.materialCode || '').trim() !== material) return false
    if (location && String(d.locationCode || '').trim() !== location) return false
    if (batch && String(d.batchNo || '').trim() && String(d.batchNo || '').trim() !== batch) return false
    return true
  })
  if (!candidates.length) return null
  return candidates.find((d) => d.lineStatus !== 'COUNTED') || candidates[0]
}

function switchMode(next) {
  mode.value = next
  refocusScanInput(scanInputRef, 200)
}

function applyParsedToForm(parsed, form) {
  if (parsed.locationCode) form.locationCode = parsed.locationCode
  if (parsed.materialCode) form.materialCode = parsed.materialCode
  if (parsed.batchNo) form.batchNo = parsed.batchNo
}

function qtyFromParsed(parsed) {
  const segments = parsed?.segments || {}
  const raw = segments.qty ?? segments.quantity ?? segments.actualQty
  if (raw == null || raw === '') return ''
  const n = Number(raw)
  return Number.isNaN(n) ? '' : String(n)
}

async function onScan(barcode) {
  if (!alive.value || busy.value) return
  const raw = String(barcode || '').trim()
  if (!raw) return
  try {
    const parsed = await resolveBarcode(raw)
    if (mode.value === 'gain') {
      applyParsedToForm(parsed, gainForm)
      const qty = qtyFromParsed(parsed)
      if (qty) gainForm.actualQty = qty
      uni.showToast({ title: '已填充盘盈信息', icon: 'success' })
    } else if (mode.value === 'empty') {
      applyParsedToForm(parsed, emptyForm)
      uni.showToast({ title: '已填充确认空信息', icon: 'success' })
    } else {
      mode.value = 'scan'
      applyParsedToForm(parsed, scanForm)
      scanForm.barcodeContent = parsed.barcodeContent || raw
      const qty = qtyFromParsed(parsed)
      if (qty) {
        scanForm.actualQty = qty
      } else if (matchedLine.value && (matchedLine.value._actual || matchedLine.value.bookQty) != null) {
        // 默认带出账面，便于复核后改数提交
        if (!scanForm.actualQty) {
          scanForm.actualQty = formatQty(matchedLine.value.actualQty ?? matchedLine.value.bookQty)
        }
      }
      const match = findMatchLine(scanForm)
      highlightLineNo.value = match?.lineNo || null
      uni.showToast({
        title: match ? `匹配行 #${match.lineNo}` : '请确认库位物料后提交',
        icon: match ? 'success' : 'none',
      })
    }
  } catch (e) {
    uni.showToast({ title: e?.message || '条码解析失败', icon: 'none' })
  } finally {
    refocusScanInput(scanInputRef, 300)
  }
}

async function loadTask() {
  if (!taskNo.value) return
  busy.value = true
  try {
    const data = await getStockcheckTask(taskNo.value)
    task.value = data.task
    details.value = (data.details || []).map((d) => ({
      ...d,
      _actual: d.actualQty != null ? formatQty(d.actualQty) : '',
    }))
  } catch (e) {
    uni.showToast({ title: e?.message || '加载盘点任务失败', icon: 'none' })
  } finally {
    busy.value = false
  }
}

async function submitLine(line) {
  if (line._actual === '' && line._actual !== 0) {
    uni.showToast({ title: '请输入实盘数量', icon: 'none' })
    return
  }
  busy.value = true
  try {
    await scanStockcheck(taskNo.value, {
      lineNo: line.lineNo,
      actualQty: Number(line._actual),
    })
    uni.showToast({ title: '已提交', icon: 'success' })
    await loadTask()
  } catch (e) {
    uni.showToast({ title: e?.message || '提交失败', icon: 'none' })
  } finally {
    busy.value = false
    refocusScanInput(scanInputRef, 300)
  }
}

async function submitScanForm() {
  if (!scanForm.materialCode && !scanForm.locationCode) {
    uni.showToast({ title: '请先扫码', icon: 'none' })
    return
  }
  if (scanForm.actualQty === '' || scanForm.actualQty == null) {
    uni.showToast({ title: '请输入实盘数量', icon: 'none' })
    return
  }
  busy.value = true
  try {
    const payload = {
      locationCode: scanForm.locationCode || undefined,
      materialCode: scanForm.materialCode || undefined,
      batchNo: scanForm.batchNo || undefined,
      actualQty: Number(scanForm.actualQty),
      barcodeContent: scanForm.barcodeContent || undefined,
    }
    const match = findMatchLine(scanForm)
    if (match?.lineNo) {
      payload.lineNo = match.lineNo
    }
    await scanStockcheck(taskNo.value, payload)
    uni.showToast({ title: '扫码盘点已提交', icon: 'success' })
    scanForm.actualQty = ''
    scanForm.barcodeContent = ''
    await loadTask()
  } catch (e) {
    uni.showToast({ title: e?.message || '提交失败', icon: 'none' })
  } finally {
    busy.value = false
    refocusScanInput(scanInputRef, 300)
  }
}

async function submitGain() {
  if (!gainForm.locationCode || !gainForm.materialCode || !gainForm.actualQty) {
    uni.showToast({ title: '请填写盘盈信息', icon: 'none' })
    return
  }
  busy.value = true
  try {
    await gainStockcheck(taskNo.value, {
      locationCode: gainForm.locationCode,
      materialCode: gainForm.materialCode,
      batchNo: gainForm.batchNo,
      actualQty: Number(gainForm.actualQty),
      remark: gainForm.remark,
    })
    uni.showToast({ title: '盘盈已录入', icon: 'success' })
    Object.assign(gainForm, { locationCode: '', materialCode: '', batchNo: '', actualQty: '', remark: '' })
    await loadTask()
  } catch (e) {
    uni.showToast({ title: e?.message || '提交失败', icon: 'none' })
  } finally {
    busy.value = false
    refocusScanInput(scanInputRef, 300)
  }
}

async function submitEmpty() {
  if (!emptyForm.locationCode || !emptyForm.materialCode) {
    uni.showToast({ title: '请填写库位和物料', icon: 'none' })
    return
  }
  busy.value = true
  try {
    await confirmEmptyStockcheck(taskNo.value, {
      locationCode: emptyForm.locationCode,
      materialCode: emptyForm.materialCode,
      batchNo: emptyForm.batchNo,
    })
    uni.showToast({ title: '已确认无库存', icon: 'success' })
    Object.assign(emptyForm, { locationCode: '', materialCode: '', batchNo: '' })
    await loadTask()
  } catch (e) {
    uni.showToast({ title: e?.message || '提交失败', icon: 'none' })
  } finally {
    busy.value = false
    refocusScanInput(scanInputRef, 300)
  }
}

async function complete() {
  busy.value = true
  try {
    await completeStockcheck(taskNo.value)
    uni.showToast({ title: '盘点完成', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 800)
  } catch (e) {
    uni.showToast({ title: e?.message || '完成失败', icon: 'none' })
    busy.value = false
  }
}

onLoad((options) => {
  taskNo.value = options?.taskNo || ''
  uni.setNavigationBarTitle({ title: '扫码盘点' })
  loadTask()
})

onShow(() => {
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
.scan-hint {
  display: block;
  margin-top: 10rpx;
  font-size: 22rpx;
  color: #94a3b8;
  text-align: center;
}
.header {
  flex-shrink: 0;
  padding: 16rpx 24rpx;
  background: #fff;
  border-bottom: 1rpx solid #e2e8f0;
}
.task-no {
  display: block;
  font-size: 28rpx;
  font-weight: 700;
  color: #1d4ed8;
}
.task-meta, .task-progress {
  display: block;
  margin-top: 4rpx;
  font-size: 22rpx;
  color: #64748b;
}
.task-progress { color: #16a34a; font-weight: 600; }
.tabs {
  flex-shrink: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  padding: 16rpx 24rpx 8rpx;
}
.tab {
  padding: 10rpx 20rpx;
  background: #e2e8f0;
  border-radius: 8rpx;
  font-size: 24rpx;
  color: #475569;
}
.tab.active { background: #1d4ed8; color: #fff; }
.body-scroll {
  flex: 1;
  height: 0;
  padding: 8rpx 24rpx 0;
}
.form-card, .line-card {
  background: #fff;
  border: 1rpx solid #e2e8f0;
  border-radius: 12rpx;
  padding: 20rpx;
  margin-bottom: 12rpx;
}
.line-card.done { border-left: 6rpx solid #22c55e; }
.line-card.flash { animation: flash 0.5s ease; }
@keyframes flash { 50% { background: #eff6ff; } }
.match-box {
  margin-bottom: 16rpx;
  padding: 16rpx;
  border-radius: 10rpx;
  background: #eff6ff;
  border: 1rpx solid #bfdbfe;
}
.match-box.warn {
  background: #fff7ed;
  border-color: #fed7aa;
}
.match-title {
  display: block;
  font-size: 26rpx;
  font-weight: 700;
  color: #1e3a8a;
  margin-bottom: 6rpx;
}
.match-box.warn .match-title { color: #9a3412; }
.match-row {
  display: block;
  font-size: 22rpx;
  color: #475569;
  margin-top: 4rpx;
}
.empty-scan {
  padding: 40rpx 0;
  text-align: center;
  color: #94a3b8;
  font-size: 24rpx;
}
.section-tip {
  display: block;
  margin-bottom: 12rpx;
  font-size: 22rpx;
  color: #64748b;
}
.field { margin-bottom: 8rpx; }
.label {
  display: block;
  font-size: 22rpx;
  color: #94a3b8;
  margin-bottom: 4rpx;
}
.name { font-weight: 700; display: block; margin-bottom: 6rpx; color: #0f172a; }
.meta { display: block; font-size: 22rpx; color: #64748b; margin-top: 2rpx; }
.row { display: flex; align-items: center; gap: 16rpx; margin-top: 12rpx; }
.input, .qty-input {
  flex: 1;
  border: 1rpx solid #e2e8f0;
  border-radius: 8rpx;
  padding: 14rpx 16rpx;
  margin-bottom: 12rpx;
  background: #fff;
  font-size: 26rpx;
}
.qty-field .qty-input {
  font-size: 32rpx;
  font-weight: 700;
  color: #1d4ed8;
  border-color: #93c5fd;
  text-align: center;
}
.primary-btn {
  background: #1d4ed8;
  color: #fff;
  border-radius: 12rpx;
  font-weight: 600;
}
.warn-btn {
  border-radius: 12rpx;
  font-weight: 600;
}
.scroll-pad { height: 140rpx; }
.footer {
  flex-shrink: 0;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background: #fff;
  border-top: 1rpx solid #e2e8f0;
}
.complete-btn {
  background: #dc2626;
  color: #fff;
  border-radius: 12rpx;
  font-weight: 600;
}
</style>
