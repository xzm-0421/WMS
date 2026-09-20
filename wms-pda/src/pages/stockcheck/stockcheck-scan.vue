<template>
  <view class="page">
    <view class="scan-top">
      <ScanSearchBar
        ref="scanInputRef"
        v-model="scanInput"
        :disabled="busy || loading"
        placeholder="扫物料条码或输入物料编码"
        action-text="匹配"
        @scan="onScan"
        @search="onScan"
      />
    </view>

    <view v-if="detail" class="order-bar">
      <view class="order-info">
        <text class="order-no">{{ detail.billNo }}</text>
        <text class="order-sub">仓库 {{ detail.warehouseCode || '-' }} · {{ detail.remark || '未审核盘点' }}</text>
      </view>
      <text class="order-stat">已盘 {{ countedCount }}/{{ lines.length }}</text>
    </view>

    <view v-if="matchedLine" class="match-card">
      <text class="match-title">已匹配明细 #{{ matchedLine.lineNo }}</text>
      <text class="match-row">物料 {{ matchedLine.materialCode }}</text>
      <text class="match-row">名称 {{ matchedLine.materialName || '-' }}</text>
      <text class="match-row">规格 {{ matchedLine.specification || '-' }}</text>
      <text class="match-row">应有 {{ formatQty(matchedLine.bookQty) }} {{ matchedLine.unitCode || '' }}</text>
      <view class="qty-edit">
        <text class="qty-label">实盘数量</text>
        <input
          v-model="actualQtyInput"
          class="qty-input"
          type="text"
          inputmode="decimal"
          placeholder="输入实盘数"
          :disabled="busy"
          @focus="pauseScanAutoFocus"
          @blur="resumeScanAutoFocus"
          @confirm="submitMatched"
        />
        <button class="mini-btn" type="primary" size="mini" :loading="busy" @click="submitMatched">
          提交
        </button>
      </view>
    </view>
    <view v-else-if="matchFailTip" class="match-card warn">
      <text class="match-title">未匹配到盘点明细</text>
      <text class="match-row">{{ matchFailTip }}</text>
      <text class="match-row">请核对条码后重新扫描物料标签</text>
    </view>
    <view v-else class="match-card idle">
      <text class="match-title">扫码或点选明细</text>
      <text class="match-row">可扫码匹配，也可点选下方明细手动录入实盘数量</text>
    </view>

    <scroll-view class="list-scroll" scroll-y :show-scrollbar="false">
      <view v-if="loading && !lines.length" class="loading-tip">加载盘点明细...</view>
      <view
        v-for="line in lines"
        :key="line.lineNo"
        :class="['mat-row', line.counted && 'done', highlightLineNo === line.lineNo && 'flash']"
        @click="selectLine(line)"
      >
        <view class="row-main">
          <view class="name-row">
            <text class="mat-code">{{ line.materialCode }}</text>
            <text :class="['tag', line.counted ? 'ok' : 'pending']">
              {{ line.counted ? '已盘' : '未盘' }}
            </text>
          </view>
          <text class="mat-name">{{ line.materialName || '-' }}</text>
          <text class="mat-spec">规格 {{ line.specification || '-' }}</text>
          <text class="mat-meta">
            批次 {{ line.batchNo || '-' }}
            <text v-if="line.locationCode"> · 库位 {{ line.locationCode }}</text>
          </text>
          <text class="mat-qty">
            应有 {{ formatQty(line.bookQty) }}
            <text v-if="line.counted"> · 实盘 {{ formatQty(line.actualQty) }}</text>
            <text v-if="line.counted && line.diffQty != null" :class="diffClass(line.diffQty)">
              · 差 {{ formatQty(line.diffQty) }}
            </text>
          </text>
        </view>
        <view class="row-side" @click.stop>
          <input
            v-model="line._actual"
            class="line-qty"
            type="text"
            inputmode="decimal"
            placeholder="实盘"
            :disabled="busy"
            @focus="pauseScanAutoFocus"
            @blur="resumeScanAutoFocus"
          />
          <button size="mini" type="primary" :disabled="busy" @click="submitLine(line)">改</button>
        </view>
      </view>

      <view v-if="!loading && !lines.length" class="empty">暂无盘点明细</view>
      <view class="scroll-pad" />
    </scroll-view>

    <view class="footer">
      <button class="refresh-btn" :disabled="busy || loading" @click="reload(true)">刷新</button>
      <button class="complete-btn" type="warn" :loading="busy" @click="onComplete">提交审核</button>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import ScanSearchBar from '@/components/ScanSearchBar.vue'
import usePageAlive from '@/composables/usePageAlive.js'
import { pauseScanAutoFocus, resumeScanAutoFocus } from '@/utils/scanFocusGuard.js'
import { useBillExclusiveLock, isBillLockedError } from '@/composables/useBillExclusiveLock.js'
import {
  getStockCountDetail,
  matchStockCountLine,
  scanStockCountLine,
  updateStockCountLineQty,
  completeStockCount,
  heartbeatStockCountLock,
  releaseStockCountLock,
} from '@/api/stockCount.js'
import { navigateBackAfterSubmit } from '@/utils/listKeywordReset.js'

const billNo = ref('')
const detail = ref(null)
const lines = ref([])
const loading = ref(false)
const busy = ref(false)
const scanInput = ref('')
const scanInputRef = ref(null)
const matchedLine = ref(null)
const matchFailTip = ref('')
const actualQtyInput = ref('')
const highlightLineNo = ref(null)
/** 本会话已扫过标签的行号 */
const scannedLineNos = ref(new Set())
const { alive, refocusScanInput } = usePageAlive()
const billLock = useBillExclusiveLock({
  heartbeat: () => heartbeatStockCountLock(billNo.value),
  release: () => releaseStockCountLock(billNo.value),
})

const countedCount = computed(() => lines.value.filter((l) => l.counted).length)

function handleLockDenied(e) {
  billLock.stop()
  toast(e?.message || '单据正被其他人操作')
  setTimeout(() => uni.navigateBack({ fail: () => {} }), 400)
}

function isLineLabelScanned(line) {
  if (!line) return false
  return scannedLineNos.value.has(line.lineNo) || line.labelScanned === true
}

function markLineScanned(lineNo) {
  if (lineNo == null) return
  const next = new Set(scannedLineNos.value)
  next.add(lineNo)
  scannedLineNos.value = next
}

function formatQty(val) {
  if (val == null || val === '') return '0'
  const n = Number(val)
  return Number.isNaN(n) ? String(val) : String(n)
}

function diffClass(diff) {
  const n = Number(diff)
  if (Number.isNaN(n) || n === 0) return 'diff-zero'
  return n > 0 ? 'diff-up' : 'diff-down'
}

function toast(title, icon = 'none') {
  uni.showToast({ title, icon, duration: 2200 })
}

function applyDetail(data) {
  detail.value = data
  const list = (data?.lines || []).map((l) => ({
    ...l,
    _actual: l.actualQty != null ? String(l.actualQty) : '',
  }))
  lines.value = list
}

async function reload(force = false) {
  if (!billNo.value) return
  loading.value = true
  try {
    const data = await getStockCountDetail(billNo.value, force)
    applyDetail(data)
    billLock.start()
  } catch (e) {
    if (isBillLockedError(e)) {
      handleLockDenied(e)
      return
    }
    toast(e?.message || '加载失败，请检查网络后重试')
  } finally {
    loading.value = false
    nextTick(() => refocusScanInput(scanInputRef, 200))
  }
}

function selectLine(line) {
  matchedLine.value = line
  matchFailTip.value = ''
  actualQtyInput.value = line._actual || (line.actualQty != null ? String(line.actualQty) : '')
  highlightLineNo.value = line.lineNo
}

async function onScan(barcode) {
  if (!alive.value || busy.value || loading.value) return
  const raw = String(barcode || '').trim()
  if (!raw) return
  scanInput.value = ''
  busy.value = true
  matchFailTip.value = ''
  try {
    const matched = await matchStockCountLine(billNo.value, { barcodeContent: raw })
    markLineScanned(matched.lineNo)
    matched.labelScanned = true
    matchedLine.value = matched
    actualQtyInput.value =
      matched.actualQty != null && matched.actualQty !== ''
        ? String(matched.actualQty)
        : matched.bookQty != null
          ? String(matched.bookQty)
          : ''
    highlightLineNo.value = matched.lineNo
    toast(`已匹配 ${matched.materialCode}`, 'success')
  } catch (e) {
    matchedLine.value = null
    matchFailTip.value = e?.message || '未匹配到明细'
    toast(matchFailTip.value)
  } finally {
    busy.value = false
    refocusScanInput(scanInputRef, 300)
  }
}

async function submitMatched() {
  if (!matchedLine.value) {
    toast('请先扫码或点选明细')
    return
  }
  const qty = Number(actualQtyInput.value)
  if (actualQtyInput.value === '' || Number.isNaN(qty) || qty < 0) {
    toast('请输入合法实盘数量')
    return
  }
  busy.value = true
  try {
    const updated = await scanStockCountLine(billNo.value, {
      lineNo: matchedLine.value.lineNo,
      actualQty: qty,
    })
    markLineScanned(updated.lineNo)
    patchLine(updated)
    matchedLine.value = updated
    actualQtyInput.value = updated.actualQty != null ? String(updated.actualQty) : String(qty)
    toast('实盘已保存', 'success')
  } catch (e) {
    toast(e?.message || '提交失败')
  } finally {
    busy.value = false
    refocusScanInput(scanInputRef, 300)
  }
}

async function submitLine(line) {
  const qty = Number(line._actual)
  if (line._actual === '' || Number.isNaN(qty) || qty < 0) {
    toast('请输入合法实盘数量')
    return
  }
  busy.value = true
  try {
    const updated = await updateStockCountLineQty(billNo.value, line.lineNo, qty)
    markLineScanned(line.lineNo)
    patchLine(updated)
    toast('数量已修正', 'success')
  } catch (e) {
    toast(e?.message || '修正失败')
  } finally {
    busy.value = false
  }
}

function patchLine(updated) {
  if (!updated) return
  const idx = lines.value.findIndex((l) => l.lineNo === updated.lineNo)
  if (idx >= 0) {
    lines.value[idx] = {
      ...lines.value[idx],
      ...updated,
      _actual: updated.actualQty != null ? String(updated.actualQty) : '',
    }
  }
  if (detail.value) {
    detail.value.countedLines = lines.value.filter((l) => l.counted).length
  }
  highlightLineNo.value = updated.lineNo
}

function onComplete() {
  const pending = lines.value.filter((l) => !l.counted).length
  if (pending > 0) {
    toast(`还有 ${pending} 行未盘点，请先完成`)
    return
  }
  uni.showModal({
    title: '提交审核',
    content: '确认回写实盘数量并对该盘点单提交审核？',
    success: async (res) => {
      if (!res.confirm) return
      busy.value = true
      try {
        await completeStockCount(billNo.value)
        await billLock.releaseLock()
        toast('已提交审核', 'success')
        navigateBackAfterSubmit(600)
      } catch (e) {
        if (isBillLockedError(e)) {
          handleLockDenied(e)
          return
        }
        toast(e?.message || '提交失败')
      } finally {
        busy.value = false
      }
    },
  })
}

onLoad((query) => {
  billNo.value = decodeURIComponent(query?.billNo || '').trim()
  uni.setNavigationBarTitle({ title: '盘点作业' })
  if (!billNo.value) {
    toast('缺少盘点单号')
    return
  }
  reload(false)
})

onShow(() => {
  if (billNo.value && detail.value) {
    refocusScanInput(scanInputRef, 300)
  }
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
  padding: 16rpx 20rpx 8rpx;
  background: #fff;
}
.order-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16rpx 24rpx;
  background: #eff6ff;
  border-bottom: 1rpx solid #bfdbfe;
}
.order-no {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
  color: #1e3a8a;
}
.order-sub {
  display: block;
  margin-top: 4rpx;
  font-size: 22rpx;
  color: #64748b;
}
.order-stat {
  font-size: 26rpx;
  color: #1d4ed8;
  font-weight: 600;
}
.match-card {
  margin: 12rpx 20rpx 0;
  padding: 20rpx;
  background: #ecfdf5;
  border: 2rpx solid #6ee7b7;
  border-radius: 14rpx;
}
.match-card.idle {
  border-color: #cbd5e1;
  background: #f8fafc;
}
.match-card.warn {
  background: #fff7ed;
  border-color: #fdba74;
}
.match-title {
  display: block;
  font-size: 28rpx;
  font-weight: 700;
  color: #065f46;
  margin-bottom: 8rpx;
}
.match-card.warn .match-title {
  color: #9a3412;
}
.match-row {
  display: block;
  font-size: 24rpx;
  color: #334155;
  margin-top: 4rpx;
}
.qty-edit {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-top: 16rpx;
}
.qty-label {
  font-size: 24rpx;
  color: #475569;
}
.qty-input {
  flex: 1;
  height: 64rpx;
  padding: 0 16rpx;
  background: #fff;
  border: 1rpx solid #cbd5e1;
  border-radius: 10rpx;
  font-size: 28rpx;
}
.mini-btn {
  margin: 0;
}
.list-scroll {
  flex: 1;
  height: 0;
  padding: 12rpx 20rpx;
  box-sizing: border-box;
}
.mat-row {
  display: flex;
  gap: 12rpx;
  background: #fff;
  border-radius: 14rpx;
  padding: 20rpx;
  margin-bottom: 14rpx;
  border: 2rpx solid transparent;
}
.mat-row.done {
  background: #f8fafc;
}
.mat-row.flash {
  border-color: #3b82f6;
}
.row-main {
  flex: 1;
  min-width: 0;
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
.mat-meta,
.mat-qty {
  display: block;
  margin-top: 6rpx;
  font-size: 24rpx;
  color: #64748b;
}
.mat-qty {
  color: #334155;
}
.diff-up {
  color: #dc2626;
}
.diff-down {
  color: #ea580c;
}
.diff-zero {
  color: #16a34a;
}
.row-side {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 8rpx;
  width: 140rpx;
}
.need-scan {
  font-size: 22rpx;
  color: #94a3b8;
  text-align: center;
  padding: 16rpx 0;
}
.line-qty {
  height: 56rpx;
  padding: 0 10rpx;
  border: 1rpx solid #cbd5e1;
  border-radius: 8rpx;
  font-size: 24rpx;
  text-align: center;
  background: #fff;
}
.footer {
  display: flex;
  gap: 16rpx;
  padding: 16rpx 20rpx calc(16rpx + env(safe-area-inset-bottom));
  background: #fff;
  border-top: 1rpx solid #e2e8f0;
}
.refresh-btn,
.complete-btn {
  flex: 1;
  margin: 0;
}
.empty,
.loading-tip {
  text-align: center;
  padding: 80rpx 20rpx;
  color: #94a3b8;
  font-size: 28rpx;
}
.scroll-pad {
  height: 40rpx;
}
</style>
