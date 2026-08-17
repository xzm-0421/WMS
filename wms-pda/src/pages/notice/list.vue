<template>
  <view class="page">
    <view class="search-top">
      <ScanSearchBar
        ref="scanInputRef"
        v-model="keyword"
        :disabled="busy"
        :placeholder="typeConfig.searchPlaceholder"
        action-text="打开"
        @scan="onScan"
        @search="onSearch"
      />
    </view>

    <scroll-view class="list-scroll" scroll-y :show-scrollbar="false">
      <view
        v-for="(item, index) in notices"
        :key="item.billNo || ('row-' + index)"
        class="bill-row"
        @click="openBill(item)"
      >
        <view class="row-main">
          <text class="bill-no">{{ item.billNo || '（单号缺失）' }}</text>
          <text class="bill-supplier">{{ item.supplierName || item.supplierCode || '-' }}</text>
          <text class="bill-meta">
            <text v-if="formatMaterialLineCount(item)" class="bill-lines">{{ formatMaterialLineCount(item) }}</text>
            <text v-if="item.inProgress"> · 已勾 {{ item.checkedLines || 0 }}</text>
            <text v-if="item.locked && item.lockUserName" class="bill-lock"> · {{ item.lockUserName }}操作中</text>
          </text>
        </view>
        <view class="row-side">
          <text :class="['status-tag', statusClass(item)]">{{ statusLabel(item) }}</text>
          <text class="arrow">›</text>
        </view>
      </view>

      <view v-if="!notices.length && !loading" class="empty">
        <text class="empty-icon">📋</text>
        <text class="empty-text">暂无{{ typeConfig.label }}</text>
        <text class="empty-hint">可直接扫描单据二维码进入明细</text>
      </view>
      <view v-if="loading && !notices.length" class="loading-tip">加载中...</view>
      <view v-else-if="notices.length" class="loading-tip end-tip">共 {{ notices.length }} 条</view>
    </scroll-view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import ScanSearchBar from '@/components/ScanSearchBar.vue'
import { resolveNoticeBarcode } from '@/api/noticeBill.js'
import useNoticeBillList from '@/composables/useNoticeBillList.js'
import usePageAlive from '@/composables/usePageAlive.js'
import { getNoticeBillType } from '@/constants/noticeBillTypes.js'
import formatMaterialLineCount from '@/utils/materialLineCount.js'

const billType = ref('PURCHASE_RECEIVE')
const direction = ref('INBOUND')
const scanInputRef = ref(null)
const busy = ref(false)
const { alive, refocusScanInput } = usePageAlive()
const {
  loading,
  keyword,
  notices,
  typeConfig,
  loadListOnShow,
  statusLabel,
  statusClass,
} = useNoticeBillList(billType)

/** 扫码 / 回车 / 点打开：解析单号后直接进明细 */
async function openByBarcode(barcode) {
  if (!alive.value) return
  if (busy.value) {
    uni.showToast({ title: '正在打开，请稍候', icon: 'none' })
    return
  }
  const raw = (barcode || '').trim()
  if (!raw) {
    uni.showToast({ title: '请扫描单据二维码', icon: 'none' })
    return
  }

  busy.value = true
  uni.showLoading({ title: '打开中...', mask: true })
  try {
    let billNo = raw
    try {
      const res = await resolveNoticeBarcode(billType.value, raw)
      if (res?.billNo) billNo = String(res.billNo).trim()
    } catch (e) {
      // 解析失败仍用原始内容尝试打开；若明显不是单号则提示
      console.warn('resolveNoticeBarcode failed', e)
    }
    billNo = String(billNo || '').trim()
    if (!billNo) {
      uni.showToast({ title: '无法识别单据号', icon: 'none' })
      return
    }
    // 去掉 JSON/URL 残留，只保留可导航的单号
    if (billNo.startsWith('{') || billNo.includes('://') || billNo.length > 64) {
      uni.showToast({ title: '无法识别单据号，请重扫', icon: 'none', duration: 2500 })
      return
    }
    keyword.value = billNo
    openBill({ billNo })
  } finally {
    uni.hideLoading()
    busy.value = false
    refocusScanInput(scanInputRef, 300)
  }
}

function onScan(barcode) {
  openByBarcode(barcode)
}

function onSearch(val) {
  // 与扫码同样处理；ScanSearchBar confirm 可能同时触发 search+scan，busy 会拦住第二次
  const raw = (val || keyword.value || '').trim()
  if (!raw) return
  openByBarcode(raw)
}

function openBill(item) {
  if (!item?.billNo) return
  uni.navigateTo({
    url: `/pages/notice/scan?billType=${encodeURIComponent(billType.value)}&billNo=${encodeURIComponent(item.billNo)}&direction=${direction.value}`,
  })
}

onLoad((options) => {
  billType.value = options?.billType || 'PURCHASE_RECEIVE'
  direction.value = options?.direction || getNoticeBillType(billType.value).direction
  uni.setNavigationBarTitle({ title: getNoticeBillType(billType.value).label })
})

onShow(() => loadListOnShow())
onMounted(() => refocusScanInput(scanInputRef, 500))
</script>

<style scoped>
.page { display: flex; flex-direction: column; height: 100vh; background: #f1f5f9; }
.search-top { flex-shrink: 0; padding: 20rpx 24rpx 12rpx; background: #fff; border-bottom: 1rpx solid #e2e8f0; }
.list-scroll { flex: 1; height: 0; padding: 16rpx 24rpx; }
.bill-row {
  display: flex; justify-content: space-between; align-items: center;
  background: #fff; border-radius: 12rpx; padding: 20rpx 24rpx; margin-bottom: 12rpx;
  border: 1rpx solid #e2e8f0;
}
.bill-no { font-size: 28rpx; font-weight: 700; color: #1d4ed8; display: block; }
.bill-supplier { font-size: 24rpx; color: #334155; display: block; margin-top: 4rpx; }
.bill-meta { font-size: 22rpx; color: #94a3b8; display: block; margin-top: 4rpx; }
.bill-lines { color: #2563eb; font-weight: 600; }
.bill-lock { color: #dc2626; font-weight: 600; }
.row-side { display: flex; align-items: center; gap: 8rpx; }
.status-tag { font-size: 22rpx; padding: 4rpx 12rpx; border-radius: 8rpx; }
.status-tag.new { background: #f1f5f9; color: #64748b; }
.status-tag.progress { background: #dbeafe; color: #1d4ed8; }
.status-tag.done { background: #dcfce7; color: #16a34a; }
.arrow { font-size: 32rpx; color: #cbd5e1; }
.empty { padding: 120rpx 0; text-align: center; }
.empty-icon { font-size: 64rpx; opacity: 0.3; display: block; }
.empty-text { font-size: 26rpx; color: #94a3b8; margin-top: 16rpx; display: block; }
.empty-hint { font-size: 22rpx; color: #cbd5e1; margin-top: 8rpx; display: block; }
.loading-tip { text-align: center; padding: 24rpx; color: #94a3b8; font-size: 24rpx; }
.end-tip { color: #cbd5e1; }
</style>
