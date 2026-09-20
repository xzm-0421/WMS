<template>
  <view class="page">
    <view class="scan-panel">
      <ScanSearchBar
        ref="scanInputRef"
        v-model="scanCode"
        placeholder="侧键扫物料条码 / 发料单号"
        action-text="解析"
        :disabled="submitting"
        @scan="onScan"
        @search="onScan"
      />
      <text class="hint">侧键扫码自动填入；发料单以 PI 开头，物料支持 编码|批次</text>
    </view>

    <scroll-view class="form-scroll" scroll-y :show-scrollbar="false">
      <view class="card">
        <view class="card-head">
          <text class="card-title">退库信息</text>
          <text class="card-link" @click="resetForm">清空</text>
        </view>

        <view class="field">
          <text class="label">原发料单</text>
          <input
            v-model="form.issueNo"
            class="input"
            placeholder="可选，如 PI2026..."
            :disabled="submitting"
            @focus="pauseScanAutoFocus"
            @blur="resumeScanAutoFocus"
          />
        </view>
        <view class="field">
          <text class="label">仓库编码 <text class="req">*</text></text>
          <input
            v-model="form.warehouseCode"
            class="input"
            placeholder="请输入仓库"
            :disabled="submitting"
            @focus="pauseScanAutoFocus"
            @blur="resumeScanAutoFocus"
          />
        </view>
        <view class="field">
          <text class="label">物料编码 <text class="req">*</text></text>
          <input
            v-model="form.materialCode"
            class="input mono"
            placeholder="扫码或手输"
            :disabled="submitting"
            @focus="pauseScanAutoFocus"
            @blur="resumeScanAutoFocus"
          />
        </view>
        <view class="field">
          <text class="label">批次</text>
          <input
            v-model="form.batchNo"
            class="input mono"
            placeholder="可选"
            :disabled="submitting"
            @focus="pauseScanAutoFocus"
            @blur="resumeScanAutoFocus"
          />
        </view>
      </view>

      <view class="card">
        <view class="card-head">
          <text class="card-title">退库数量</text>
        </view>
        <view class="qty-row">
          <button class="qty-step" :disabled="submitting" @click="adjustQty(-1)">−</button>
          <input
            v-model="qtyText"
            class="qty-input"
            type="text"
            inputmode="decimal"
            :disabled="submitting"
            @focus="pauseScanAutoFocus"
            @blur="normalizeQty"
          />
          <button class="qty-step" :disabled="submitting" @click="adjustQty(1)">＋</button>
        </view>
        <text class="hint tight">默认 1，可手动调整</text>
      </view>

      <view class="card">
        <view class="card-head">
          <text class="card-title">退库原因</text>
        </view>
        <view class="reason-chips">
          <text
            v-for="item in reasonOptions"
            :key="item"
            :class="['chip', form.returnReason === item && 'on']"
            @click="form.returnReason = item"
          >
            {{ item }}
          </text>
        </view>
        <input
          v-model="form.returnReason"
          class="input"
          placeholder="可自定义原因"
          :disabled="submitting"
        />
      </view>

      <view v-if="lastNo" class="success-card">
        <text class="success-title">退库成功</text>
        <text class="success-no">单号 {{ lastNo }}</text>
        <text class="success-hint">可继续扫下一件物料</text>
      </view>

      <view class="scroll-pad" />
    </scroll-view>

    <view class="footer">
      <button
        class="submit-btn"
        type="primary"
        :loading="submitting"
        :disabled="submitting || !canSubmit"
        @click="submit"
      >
        确认退库{{ qtyText ? ` × ${qtyText}` : '' }}
      </button>
    </view>
  </view>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import ScanSearchBar from '@/components/ScanSearchBar.vue'
import { submitWorkshopReturn } from '@/api/picking.js'
import usePageAlive from '@/composables/usePageAlive.js'
import { pauseScanAutoFocus, resumeScanAutoFocus } from '@/utils/scanFocusGuard.js'

const reasonOptions = ['余料退库', '多领退回', '换料退库', '质量退回']

const scanCode = ref('')
const lastNo = ref('')
const qtyText = ref('1')
const submitting = ref(false)
const scanInputRef = ref(null)
const { alive, refocusScanInput } = usePageAlive()

const form = reactive({
  issueNo: '',
  warehouseCode: 'WH001',
  materialCode: '',
  batchNo: '',
  returnReason: '余料退库',
})

const canSubmit = computed(() =>
  !!(form.materialCode || '').trim() && !!(form.warehouseCode || '').trim(),
)

function toast(title) {
  uni.showToast({ title, icon: 'none' })
}

function normalizeQty() {
  resumeScanAutoFocus()
  let n = Number(qtyText.value)
  if (Number.isNaN(n) || n <= 0) n = 1
  qtyText.value = String(Math.round(n * 1000) / 1000)
}

function adjustQty(delta) {
  let n = Number(qtyText.value)
  if (Number.isNaN(n) || n <= 0) n = 1
  n = Math.max(0.001, Math.round((n + delta) * 1000) / 1000)
  qtyText.value = String(n)
}

function resetForm() {
  form.issueNo = ''
  form.materialCode = ''
  form.batchNo = ''
  form.returnReason = '余料退库'
  qtyText.value = '1'
  scanCode.value = ''
  lastNo.value = ''
  refocusScanInput(scanInputRef, 200)
}

function onScan(code) {
  if (!alive.value || submitting.value) return
  const c = (code || '').trim()
  if (!c) return
  scanCode.value = c

  const upper = c.toUpperCase()
  if (upper.startsWith('PI:') || upper.startsWith('PI')) {
    form.issueNo = upper.startsWith('PI:') ? c.slice(3) : c
    toast('已填入发料单')
    refocusScanInput(scanInputRef, 200)
    return
  }

  const parts = c.split('|')
  form.materialCode = (parts[0] || c).trim()
  if (parts[1]) form.batchNo = parts[1].trim()
  if (parts[2] && !form.warehouseCode) form.warehouseCode = parts[2].trim()
  toast('已填入物料')
  refocusScanInput(scanInputRef, 200)
}

async function submit() {
  if (!alive.value || submitting.value) return
  const materialCode = (form.materialCode || '').trim()
  const warehouseCode = (form.warehouseCode || '').trim()
  if (!materialCode) {
    toast('请扫描或输入物料编码')
    return
  }
  if (!warehouseCode) {
    toast('请输入仓库编码')
    return
  }
  normalizeQty()
  const returnQty = Number(qtyText.value)
  if (!returnQty || returnQty <= 0) {
    toast('退库数量须大于 0')
    return
  }

  submitting.value = true
  try {
    const returnNo = await submitWorkshopReturn({
      issueNo: (form.issueNo || '').trim() || undefined,
      warehouseCode,
      materialCode,
      batchNo: (form.batchNo || '').trim() || undefined,
      returnReason: (form.returnReason || '').trim() || '余料退库',
      returnQty,
      autoConfirm: true,
    })
    lastNo.value = returnNo
    uni.showToast({ title: '退库成功', icon: 'success' })
    form.materialCode = ''
    form.batchNo = ''
    qtyText.value = '1'
    scanCode.value = ''
  } catch (e) {
    toast(e?.message || '退库失败')
  } finally {
    submitting.value = false
    refocusScanInput(scanInputRef, 300)
  }
}

onLoad(() => uni.setNavigationBarTitle({ title: '车间退库' }))
onShow(() => refocusScanInput(scanInputRef, 300))
onMounted(() => refocusScanInput(scanInputRef, 400))
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f1f5f9;
}
.scan-panel {
  flex-shrink: 0;
  padding: 20rpx 24rpx 12rpx;
  background: #fff;
  border-bottom: 1rpx solid #e2e8f0;
}
.hint {
  display: block;
  margin-top: 12rpx;
  font-size: 22rpx;
  color: #94a3b8;
  line-height: 1.4;
}
.hint.tight { margin-top: 10rpx; }

.form-scroll {
  flex: 1;
  height: 0;
  padding: 16rpx 24rpx 0;
  box-sizing: border-box;
}
.card {
  background: #fff;
  border: 1rpx solid #e2e8f0;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 16rpx;
}
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16rpx;
}
.card-title {
  font-size: 28rpx;
  font-weight: 700;
  color: #0f172a;
}
.card-link {
  font-size: 24rpx;
  color: #2563eb;
}
.field { margin-bottom: 18rpx; }
.field:last-child { margin-bottom: 0; }
.label {
  display: block;
  font-size: 24rpx;
  color: #64748b;
  margin-bottom: 8rpx;
}
.req { color: #ef4444; }
.input {
  height: 72rpx;
  padding: 0 20rpx;
  font-size: 28rpx;
  color: #0f172a;
  background: #f8fafc;
  border: 2rpx solid #e2e8f0;
  border-radius: 12rpx;
  box-sizing: border-box;
}
.input.mono { font-family: monospace; }

.qty-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
}
.qty-step {
  width: 80rpx;
  height: 80rpx;
  margin: 0;
  padding: 0;
  line-height: 80rpx;
  font-size: 40rpx;
  font-weight: 600;
  color: #1d4ed8;
  background: #eff6ff;
  border-radius: 12rpx;
}
.qty-input {
  flex: 1;
  height: 80rpx;
  text-align: center;
  font-size: 36rpx;
  font-weight: 700;
  color: #1d4ed8;
  background: #fff;
  border: 2rpx solid #93c5fd;
  border-radius: 12rpx;
}

.reason-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-bottom: 16rpx;
}
.chip {
  font-size: 24rpx;
  color: #475569;
  background: #f1f5f9;
  padding: 10rpx 20rpx;
  border-radius: 999rpx;
  border: 2rpx solid transparent;
}
.chip.on {
  color: #1d4ed8;
  background: #eff6ff;
  border-color: #93c5fd;
  font-weight: 600;
}

.success-card {
  background: #f0fdf4;
  border: 1rpx solid #bbf7d0;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 16rpx;
}
.success-title {
  display: block;
  font-size: 28rpx;
  font-weight: 700;
  color: #15803d;
}
.success-no {
  display: block;
  margin-top: 8rpx;
  font-size: 26rpx;
  color: #166534;
  font-family: monospace;
}
.success-hint {
  display: block;
  margin-top: 6rpx;
  font-size: 22rpx;
  color: #86efac;
}
.scroll-pad { height: 24rpx; }

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
  font-size: 30rpx;
}
.submit-btn[disabled] { background: #94a3b8; }
</style>
