<template>
  <view class="page">
    <view class="search-top">
      <ScanSearchBar
        ref="scanInputRef"
        v-model="keyword"
        :disabled="loading"
        placeholder="扫盘点二维码或搜索单号"
        action-text="搜索"
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
        v-for="(item, index) in bills"
        :key="item.billNo || ('row-' + index)"
        class="bill-row"
        @click="openBill(item)"
      >
        <view class="row-main">
          <text class="bill-no">{{ item.billNo || '（单号缺失）' }}</text>
          <text class="bill-meta">
            仓库 {{ item.warehouseCode || '-' }}
            <text v-if="item.remark"> · {{ item.remark }}</text>
          </text>
          <text class="bill-sub">
            明细 {{ item.totalLines || 0 }} 行
            <text v-if="item.countedLines"> · 已盘 {{ item.countedLines }}</text>
            <text v-if="item.billDate"> · {{ item.billDate }}</text>
            <text v-if="item.locked && item.lockUserName" class="bill-lock"> · {{ item.lockUserName }}操作中</text>
          </text>
        </view>
        <view class="row-side">
          <text :class="['status-tag', statusClass(item)]">{{ statusLabel(item) }}</text>
          <text class="arrow">›</text>
        </view>
      </view>

      <view v-if="!bills.length && !loading" class="empty">
        <text class="empty-text">暂无未审核的盘点作业单</text>
        <text class="empty-hint">请确认金蝶盘点方案已生成作业且未审核，或下拉刷新</text>
      </view>
      <view v-if="loading && !bills.length" class="loading-tip">加载中...</view>

      <view v-if="bills.length" class="footer-tip">
        <text>已显示 {{ bills.length }} / {{ total }} 条</text>
        <view v-if="hasMore" class="load-more-btn" @click="onLoadMore">
          {{ loadingMore ? '加载中...' : '加载更多' }}
        </view>
        <text v-else class="end-tip">没有更多了</text>
      </view>
    </scroll-view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { onLoad, onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import ScanSearchBar from '@/components/ScanSearchBar.vue'
import useStockCountList from '@/composables/useStockCountList.js'
import usePageAlive from '@/composables/usePageAlive.js'

const scanInputRef = ref(null)
const { alive, refocusScanInput } = usePageAlive()
const {
  loading,
  loadingMore,
  keyword,
  bills,
  total,
  hasMore,
  loadList,
  loadMore,
  loadListOnShow,
  searchByBarcode,
  statusLabel,
  statusClass,
} = useStockCountList()

async function onScan(barcode) {
  if (!alive.value) return
  const result = await searchByBarcode(barcode)
  if (result?.action === 'open' && result.billNo) {
    openBill({ billNo: result.billNo })
  }
  refocusScanInput(scanInputRef, 300)
}

async function onSearch(val) {
  if (!alive.value) return
  const raw = (val || keyword.value || '').trim()
  keyword.value = raw
  // 输入/扫到单号时优先打开明细，与领料等未审核单据流程一致
  if (raw) {
    const result = await searchByBarcode(raw)
    if (result?.action === 'open' && result.billNo) {
      openBill({ billNo: result.billNo })
      refocusScanInput(scanInputRef, 300)
      return
    }
  }
  await loadList(keyword.value, { force: true })
  refocusScanInput(scanInputRef, 300)
}

function openBill(item) {
  if (!item?.billNo) {
    uni.showToast({ title: '单号无效', icon: 'none' })
    return
  }
  uni.navigateTo({
    url: `/pages/stockcheck/stockcheck-scan?billNo=${encodeURIComponent(item.billNo)}`,
  })
}

async function onLoadMore() {
  if (!hasMore.value || loadingMore.value) return
  await loadMore()
}

function onScrollToLower() {
  onLoadMore()
}

onLoad(() => uni.setNavigationBarTitle({ title: '盘点作业' }))
onShow(() => loadListOnShow())
onMounted(() => refocusScanInput(scanInputRef, 500))

onPullDownRefresh(async () => {
  try {
    await loadList(keyword.value, { force: true })
  } finally {
    uni.stopPullDownRefresh()
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
.search-top {
  padding: 16rpx 20rpx 8rpx;
  background: #fff;
  border-bottom: 1rpx solid #e2e8f0;
}
.list-scroll {
  flex: 1;
  height: 0;
  padding: 12rpx 20rpx 40rpx;
  box-sizing: border-box;
}
.bill-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-radius: 16rpx;
  padding: 24rpx 22rpx;
  margin-bottom: 16rpx;
  box-shadow: 0 2rpx 8rpx rgba(15, 23, 42, 0.04);
}
.row-main {
  flex: 1;
  min-width: 0;
}
.bill-no {
  display: block;
  font-size: 32rpx;
  font-weight: 700;
  color: #0f172a;
}
.bill-meta,
.bill-sub {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #64748b;
}
.row-side {
  display: flex;
  align-items: center;
  gap: 8rpx;
  margin-left: 16rpx;
}
.status-tag {
  font-size: 22rpx;
  padding: 6rpx 14rpx;
  border-radius: 999rpx;
  background: #e2e8f0;
  color: #475569;
}
.status-tag.new {
  background: #dbeafe;
  color: #1d4ed8;
}
.status-tag.progress {
  background: #fef3c7;
  color: #b45309;
}
.status-tag.done {
  background: #dcfce7;
  color: #15803d;
}
.arrow {
  font-size: 36rpx;
  color: #94a3b8;
}
.empty {
  padding: 120rpx 40rpx;
  text-align: center;
}
.empty-text {
  display: block;
  font-size: 30rpx;
  color: #64748b;
}
.empty-hint {
  display: block;
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #94a3b8;
}
.loading-tip,
.footer-tip {
  text-align: center;
  padding: 24rpx;
  font-size: 24rpx;
  color: #94a3b8;
}
.load-more-btn {
  margin-top: 16rpx;
  display: inline-block;
  padding: 12rpx 32rpx;
  background: #eff6ff;
  color: #1d4ed8;
  border-radius: 999rpx;
  font-size: 26rpx;
}
.end-tip {
  display: block;
  margin-top: 8rpx;
}
</style>
