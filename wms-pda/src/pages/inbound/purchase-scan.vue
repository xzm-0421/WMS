<template>
  <view class="page">
    <!-- 固定扫码区 -->
    <view class="scan-bar-fixed">
      <view class="scan-bar-inner">
        <text class="scan-phase">{{ phaseLabel }}</text>
        <ScanInput
          ref="scanInputRef"
          :disabled="processing"
          :placeholder="scanPlaceholder"
          @scan="onScan"
        />
        <view v-if="phase === 'confirm_items'" class="progress-row">
          <text>确认进度</text>
          <text class="progress-val">{{ confirmedCount }}/{{ totalCount }}</text>
          <view class="progress-bar">
            <view class="progress-fill" :style="{ width: progressPercent + '%' }" />
          </view>
        </view>
        <text v-if="phase === 'confirm_items'" class="wh-hint">
          入库仓库 {{ purchaseOrder?.warehouseCode || 'WH01' }}
        </text>
      </view>
    </view>
    <view class="scan-bar-spacer" :class="{ confirm: phase === 'confirm_items' }" />

    <!-- 采购单信息 -->
    <view v-if="purchaseOrder" class="po-header">
      <view class="po-title-row">
        <text class="po-no">{{ purchaseOrder.orderNo }}</text>
        <text class="po-reset" @click="onReset">换单</text>
      </view>
      <text class="po-meta">
        供应商 {{ purchaseOrder.supplierCode || '-' }}
        · 仓库 {{ purchaseOrder.warehouseCode || '-' }}
      </text>
    </view>

    <!-- 待扫采购单提示 -->
    <view v-else class="hint-card">
      <text class="hint-icon">📋</text>
      <text class="hint-title">请扫描采购单条码</text>
      <text class="hint-desc">扫描后将自动解析并加载采购明细，无需先选择入库单</text>
    </view>

    <!-- 明细列表 -->
    <view v-if="lines.length" class="detail-section">
      <text class="section-title">采购明细（{{ totalCount }} 项）</text>
      <view
        v-for="line in lines"
        :key="line.lineNo"
        :class="['line-card', line.confirmed ? 'confirmed' : 'pending', { flash: line.lineNo === lastHighlightLineNo }]"
      >
        <view class="line-head">
          <text class="status-badge">{{ line.confirmed ? '已确认' : '待确认' }}</text>
          <text v-if="line.confirmed" class="confirm-time">{{ line.confirmedAt }}</text>
        </view>
        <text class="mat-name">{{ line.materialName || line.materialCode }}</text>
        <text class="mat-code">编号 {{ line.materialCode }}</text>
        <text class="mat-spec">规格 {{ line.specification || '-' }}</text>
        <view class="mat-qty-row">
          <text>数量 {{ line.pendingQty }} {{ line.unitCode }}</text>
          <text v-if="line.scannedBatchNo" class="batch">批次 {{ line.scannedBatchNo }}</text>
        </view>
      </view>
    </view>

    <!-- 扫码日志 -->
    <view v-if="scanLog.length" class="log-section">
      <view class="log-header">
        <text class="log-title">操作记录</text>
        <text class="log-clear" @click="clearLog">清空</text>
      </view>
      <view v-for="(item, i) in scanLog" :key="i" :class="['log-item', item.ok ? 'ok' : 'fail']">
        <text>{{ item.time }} {{ item.barcode || '' }}</text>
        <text>{{ item.msg }}</text>
      </view>
    </view>

    <!-- 底部提交 -->
    <view v-if="phase === 'confirm_items'" class="footer">
      <button
        class="submit-btn"
        type="primary"
        :loading="processing"
        :disabled="!allConfirmed || processing"
        @click="onSubmit"
      >
        {{ allConfirmed ? '提交入库' : `提交（还差 ${totalCount - confirmedCount} 项）` }}
      </button>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import ScanInput from '@/components/ScanInput.vue'
import usePurchaseInboundScan from '@/composables/usePurchaseInboundScan.js'

const scanInputRef = ref(null)
const lastScannedMaterial = ref('')

const {
  phase,
  processing,
  purchaseOrder,
  lines,
  scanLog,
  lastHighlightLineNo,
  confirmedCount,
  totalCount,
  allConfirmed,
  progressPercent,
  handleScan,
  submitInbound,
  reset,
  clearLog,
} = usePurchaseInboundScan()

const phaseLabel = computed(() =>
  phase.value === 'scan_order' ? '① 扫描采购单' : '② 逐项扫描物料确认',
)

const scanPlaceholder = computed(() =>
  phase.value === 'scan_order' ? '扫描采购单条码' : '扫描物料条码确认',
)

async function onScan(barcode) {
  await handleScan(barcode)
  if (phase.value === 'confirm_items') {
    const last = lines.value.find((l) => l.lineNo === lastHighlightLineNo.value)
    if (last) lastScannedMaterial.value = last.materialCode
  }
  setTimeout(() => scanInputRef.value?.focusInput?.(), 300)
}

function onReset() {
  uni.showModal({
    title: '重新扫单',
    content: '将清空当前明细，重新扫描采购单？',
    success: (res) => {
      if (res.confirm) {
        reset()
        lastScannedMaterial.value = ''
        setTimeout(() => scanInputRef.value?.focusInput?.(), 300)
      }
    },
  })
}

async function onSubmit() {
  await submitInbound()
}

onLoad(() => {
  uni.setNavigationBarTitle({ title: '采购扫码入库' })
})

let shownOnce = false
onShow(() => {
  if (!shownOnce) {
    shownOnce = true
    setTimeout(() => scanInputRef.value?.focusInput?.(), 500)
  }
})
</script>

<style scoped>
.page { padding: 0 24rpx 160rpx; }

.scan-bar-fixed {
  position: fixed; left: 0; right: 0;
  top: var(--window-top, 44px); z-index: 200;
  background: linear-gradient(180deg, #1e293b, #0f172a);
  padding: 16rpx 24rpx 20rpx;
  box-shadow: 0 4rpx 24rpx rgba(0,0,0,0.15);
}
.scan-bar-inner { max-width: 750rpx; margin: 0 auto; }
.scan-phase {
  display: block; text-align: center;
  color: #60a5fa; font-size: 24rpx; font-weight: 600; margin-bottom: 12rpx;
}
.scan-bar-spacer { height: 200rpx; }
.scan-bar-spacer.confirm { height: 260rpx; }

.progress-row {
  display: flex; align-items: center; gap: 12rpx;
  margin-top: 16rpx; font-size: 22rpx; color: #94a3b8;
}
.progress-val { color: #22c55e; font-weight: 700; font-size: 26rpx; }
.progress-bar {
  flex: 1; height: 8rpx; background: #334155;
  border-radius: 4rpx; overflow: hidden;
}
.progress-fill {
  height: 100%; background: #22c55e;
  transition: width 0.3s;
}
.wh-hint {
  display: block;
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #1d4ed8;
  font-weight: 600;
}

.hint-card {
  background: #fff; border-radius: 16rpx;
  padding: 48rpx 32rpx; text-align: center; margin-bottom: 24rpx;
}
.hint-icon { font-size: 64rpx; display: block; margin-bottom: 16rpx; }
.hint-title { font-size: 32rpx; font-weight: bold; display: block; margin-bottom: 8rpx; }
.hint-desc { font-size: 24rpx; color: #64748b; display: block; }

.po-header {
  background: #fff; border-radius: 16rpx;
  padding: 24rpx; margin-bottom: 20rpx;
  border-left: 8rpx solid #3b82f6;
}
.po-title-row { display: flex; justify-content: space-between; align-items: center; }
.po-no { font-size: 32rpx; font-weight: bold; color: #1d4ed8; }
.po-reset { font-size: 24rpx; color: #64748b; padding: 8rpx 16rpx; }
.po-meta { font-size: 24rpx; color: #64748b; display: block; margin-top: 8rpx; }

.section-title { font-weight: bold; font-size: 28rpx; display: block; margin-bottom: 16rpx; }

.line-card {
  background: #fff; border-radius: 12rpx;
  padding: 20rpx 24rpx; margin-bottom: 16rpx;
  border: 2rpx solid #e2e8f0;
  transition: border-color 0.3s, background 0.3s;
}
.line-card.pending { border-left: 8rpx solid #f59e0b; }
.line-card.confirmed {
  border-left: 8rpx solid #22c55e;
  background: #f0fdf4;
  border-color: #bbf7d0;
}
.line-card.flash {
  animation: flashPulse 0.6s ease;
}
@keyframes flashPulse {
  0%, 100% { box-shadow: none; }
  50% { box-shadow: 0 0 0 6rpx rgba(34, 197, 94, 0.35); }
}

.line-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8rpx; }
.status-badge {
  font-size: 22rpx; padding: 4rpx 12rpx; border-radius: 6rpx; font-weight: 600;
}
.pending .status-badge { background: #fef3c7; color: #d97706; }
.confirmed .status-badge { background: #dcfce7; color: #15803d; }
.confirm-time { font-size: 20rpx; color: #64748b; }

.mat-name { font-size: 28rpx; font-weight: bold; display: block; }
.mat-code { font-size: 24rpx; color: #475569; display: block; margin-top: 4rpx; }
.mat-spec { font-size: 24rpx; color: #64748b; display: block; margin-top: 4rpx; }
.mat-qty-row {
  display: flex; justify-content: space-between; align-items: center;
  margin-top: 8rpx; font-size: 24rpx; color: #334155;
}
.batch { color: #1d4ed8; font-size: 22rpx; }

.log-section {
  background: #fff; border-radius: 16rpx; padding: 24rpx; margin-top: 16rpx;
}
.log-header { display: flex; justify-content: space-between; margin-bottom: 12rpx; }
.log-title { font-weight: bold; font-size: 26rpx; }
.log-clear { color: #94a3b8; font-size: 24rpx; }
.log-item { padding: 12rpx 16rpx; border-radius: 8rpx; margin-bottom: 8rpx; font-size: 24rpx; }
.log-item.ok { background: #f0fdf4; color: #15803d; }
.log-item.fail { background: #fef2f2; color: #dc2626; }
.log-item text { display: block; }

.footer {
  position: fixed; left: 0; right: 0; bottom: 0;
  padding: 24rpx; background: #fff;
  box-shadow: 0 -4rpx 16rpx rgba(0,0,0,0.06);
}
.submit-btn {
  background: #1d4ed8; color: #fff; border-radius: 12rpx;
  font-size: 30rpx; font-weight: 600;
}
.submit-btn[disabled] { background: #94a3b8; color: #e2e8f0; }
</style>
