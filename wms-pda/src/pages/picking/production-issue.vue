<template>
  <view class="page">
    <view class="search-top">
      <ScanSearchBar
        ref="scanInputRef"
        v-model="keyword"
        :disabled="loading"
        placeholder="扫码或搜索生产用料清单号/车间"
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
            <text v-if="item.erpBillNo" class="bill-erp"> · 领料 {{ item.erpBillNo }}</text>
          </text>
        </view>
        <view class="row-side">
          <text :class="['status-tag', statusClass(item)]">{{ statusLabel(item) }}</text>
          <text class="arrow">›</text>
        </view>
      </view>

      <view v-if="!notices.length && !loading" class="empty">
        <text class="empty-icon">📋</text>
        <text class="empty-text">暂无已审核的生产用料清单</text>
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
import useNoticeBillList from '@/composables/useNoticeBillList.js'
import usePageAlive from '@/composables/usePageAlive.js'
import formatMaterialLineCount from '@/utils/materialLineCount.js'

const BILL_TYPE = 'PRODUCTION_ISSUE'

const scanInputRef = ref(null)
const billType = ref(BILL_TYPE)
const { alive, refocusScanInput } = usePageAlive()
const {
  loading,
  keyword,
  notices,
  loadList,
  loadListOnShow,
  searchByBarcode,
  statusLabel,
  statusClass,
} = useNoticeBillList(billType)

async function onScan(barcode) {
  if (!alive.value) return
  const list = await searchByBarcode(barcode)
  // 扫码命中单条用料清单时直接进入物料明细
  if (list && list.length === 1 && list[0]?.billNo) {
    openBill(list[0])
    return
  }
  refocusScanInput(scanInputRef, 300)
}

function onSearch(val) {
  keyword.value = val || keyword.value
  loadList(keyword.value)
}

function openBill(item) {
  uni.navigateTo({
    url: `/pages/picking/production-issue-scan?billNo=${encodeURIComponent(item.billNo)}`,
  })
}

onLoad(() => uni.setNavigationBarTitle({ title: '生产领料' }))
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
.end-tip { color: #cbd5e1; }
</style>
