<template>
  <view class="page">
    <view class="search-top">
      <ScanSearchBar
        ref="scanInputRef"
        v-model="keyword"
        :disabled="loading"
        placeholder="扫码或搜索收料通知单号/供应商"
        @scan="onScan"
        @search="onSearch"
      />
    </view>

    <scroll-view
      class="list-scroll"
      scroll-y
      :show-scrollbar="false"
      @scrolltolower="onScrollToLower"
    >
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
            <text v-if="item.erpBillNo" class="bill-erp"> · 入库 {{ item.erpBillNo }}</text>
          </text>
        </view>
        <view class="row-side">
          <text :class="['status-tag', statusClass(item)]">{{ statusLabel(item) }}</text>
          <text class="arrow">›</text>
        </view>
      </view>

      <view v-if="!notices.length && !loading" class="empty">
        <text class="empty-icon">📋</text>
        <text class="empty-text">暂无已审核的收料通知单</text>
      </view>
      <view v-if="loading && !notices.length" class="loading-tip">加载中...</view>

      <view v-if="notices.length" class="footer-tip">
        <text>已显示 {{ notices.length }} / {{ total }} 条</text>
        <view
          v-if="hasMore"
          class="load-more-btn"
          @click="onLoadMore"
        >
          {{ loadingMore ? '加载中...' : '加载更多' }}
        </view>
        <text v-else class="end-tip">没有更多了</text>
      </view>
    </scroll-view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import ScanSearchBar from '@/components/ScanSearchBar.vue'
import useReceiveNoticeList from '@/composables/useReceiveNoticeList.js'
import usePageAlive from '@/composables/usePageAlive.js'
import formatMaterialLineCount from '@/utils/materialLineCount.js'

const scanInputRef = ref(null)
const { alive, refocusScanInput } = usePageAlive()
const {
  loading,
  loadingMore,
  keyword,
  notices,
  total,
  hasMore,
  loadList,
  loadMore,
  loadListOnShow,
  searchByBarcode,
  statusLabel,
  statusClass,
} = useReceiveNoticeList()

async function onScan(barcode) {
  if (!alive.value) return
  await searchByBarcode(barcode)
  refocusScanInput(scanInputRef, 300)
}

function onSearch(val) {
  keyword.value = val || keyword.value
  loadList(keyword.value, { force: true })
}

function openBill(item) {
  uni.navigateTo({ url: `/pages/inbound/receive-scan?billNo=${encodeURIComponent(item.billNo)}` })
}

async function onLoadMore() {
  if (!hasMore.value || loadingMore.value) return
  await loadMore()
}

function onScrollToLower() {
  onLoadMore()
}

onLoad(() => uni.setNavigationBarTitle({ title: '收料通知单' }))
onShow(() => loadListOnShow())
onMounted(() => refocusScanInput(scanInputRef, 500))
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f1f5f9;
}
.search-top {
  flex-shrink: 0;
  padding: 16rpx 24rpx;
  background: #fff;
  border-bottom: 1rpx solid #e2e8f0;
}
.list-scroll {
  flex: 1;
  height: 0;
  padding: 16rpx 24rpx;
}
.bill-row {
  display: flex;
  align-items: center;
  background: #fff;
  border: 1rpx solid #e2e8f0;
  border-radius: 12rpx;
  padding: 20rpx;
  margin-bottom: 12rpx;
}
.row-main { flex: 1; min-width: 0; }
.bill-no {
  display: block;
  font-size: 28rpx;
  font-weight: 700;
  color: #0f172a;
}
.bill-supplier {
  display: block;
  font-size: 24rpx;
  color: #475569;
  margin-top: 4rpx;
}
.bill-meta {
  display: block;
  font-size: 22rpx;
  color: #94a3b8;
  margin-top: 4rpx;
}
.bill-lock { color: #dc2626; font-weight: 600; }
.bill-lines {
  color: #2563eb;
  font-weight: 600;
}
.bill-erp {
  color: #16a34a;
  font-weight: 600;
}
.row-side {
  display: flex;
  align-items: center;
  gap: 8rpx;
  flex-shrink: 0;
}
.status-tag {
  font-size: 20rpx;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
  background: #f1f5f9;
  color: #64748b;
}
.status-tag.progress { background: #fff7ed; color: #c2410c; }
.status-tag.done { background: #f0fdf4; color: #15803d; }
.arrow { font-size: 36rpx; color: #cbd5e1; }
.empty { padding: 120rpx 0; text-align: center; }
.empty-icon { font-size: 64rpx; opacity: 0.25; display: block; }
.empty-text { display: block; margin-top: 16rpx; font-size: 24rpx; color: #94a3b8; }
.loading-tip { text-align: center; color: #94a3b8; padding: 24rpx; font-size: 24rpx; }
.footer-tip {
  padding: 16rpx 0 40rpx;
  text-align: center;
  font-size: 22rpx;
  color: #94a3b8;
}
.load-more-btn {
  margin: 16rpx auto 0;
  width: 280rpx;
  height: 64rpx;
  line-height: 64rpx;
  background: #fff;
  border: 1rpx solid #cbd5e1;
  border-radius: 32rpx;
  color: #1d4ed8;
  font-size: 26rpx;
  font-weight: 600;
}
.end-tip {
  display: block;
  margin-top: 12rpx;
  color: #cbd5e1;
}
</style>
