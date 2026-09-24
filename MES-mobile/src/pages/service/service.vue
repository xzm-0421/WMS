<template>
  <view class="page">
    <view class="head" :style="{ paddingTop: statusBarHeight + 16 + 'px' }">
      <RippleBg />
      <text class="page-title">服务</text>
    </view>

    <view class="section">
      <text class="section-title">移动工单服务</text>
      <view class="grid">
        <view
          v-for="item in orderMenus"
          :key="item.label"
          class="cell"
          hover-class="cell-hover"
          :hover-stay-time="80"
          @click="onCell(item)"
        >
          <view class="icon-plate">
            <view class="icon-box" :style="{ background: item.bg }">
              <image class="icon-img" :src="item.icon" mode="aspectFit" />
            </view>
          </view>
          <text class="cell-label">{{ item.label }}</text>
        </view>
      </view>
    </view>

    <view class="section">
      <text class="section-title">统计分析</text>
      <view class="grid">
        <view
          v-for="item in statMenus"
          :key="item.label"
          class="cell"
          hover-class="cell-hover"
          :hover-stay-time="80"
          @click="comingSoon(item.label)"
        >
          <view class="icon-plate">
            <view class="icon-box pale">
              <image class="icon-img" :src="item.icon" mode="aspectFit" />
            </view>
          </view>
          <text class="cell-label">{{ item.label }}</text>
        </view>
      </view>
    </view>

    <AppTabBar current="service" />
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import AppTabBar from '@/components/AppTabBar.vue'
import RippleBg from '@/components/RippleBg.vue'
import { requireSession } from '@/utils/authStorage.js'
import { comingSoon } from '@/utils/ui.js'

const statusBarHeight = ref(uni.getSystemInfoSync().statusBarHeight || 20)

const orderMenus = [
  { label: '工单报工', icon: '/static/service/wrench.svg', bg: '#5b8cff', url: '/pages/report/report' },
  { label: '工序转移', icon: '/static/service/clipboard.svg', bg: '#7b93c4', url: '/pages/report/transfer' },
  { label: '发起返工', icon: '/static/service/user-doc.svg', bg: '#6d74f0', url: '/pages/report/rework' },
  { label: '我的报工', icon: '/static/service/clock-doc.svg', bg: '#8b96e0', url: '/pages/report/records' },
]

function onCell(item) {
  if (item.url) {
    uni.navigateTo({ url: item.url })
    return
  }
  comingSoon(item.label)
}

const statMenus = [
  { label: '报表', icon: '/static/service/grid.svg' },
  { label: '大屏', icon: '/static/service/screen.svg' },
]

onShow(() => {
  requireSession()
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #fff;
  padding-bottom: calc(120rpx + env(safe-area-inset-bottom));
}
.head {
  position: relative;
  overflow: hidden;
  height: 200rpx;
  padding-left: 36rpx;
  background: linear-gradient(180deg, #efe7fb 0%, #ffffff 100%);
}
.page-title {
  position: relative;
  z-index: 1;
  font-size: 48rpx;
  font-weight: 800;
  color: #1a1a1a;
}
.section {
  padding: 12rpx 28rpx 8rpx;
}
.section-title {
  display: block;
  font-size: 30rpx;
  color: #4a4a4a;
  font-weight: 600;
  margin-bottom: 24rpx;
  padding-left: 8rpx;
}
.grid {
  display: flex;
  flex-wrap: wrap;
}
.cell {
  width: 25%;
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 36rpx;
}
.cell-hover {
  opacity: 0.82;
}
.icon-plate {
  width: 112rpx;
  height: 112rpx;
  border-radius: 28rpx;
  background: #f3f5fb;
  display: flex;
  align-items: center;
  justify-content: center;
}
.icon-box {
  width: 88rpx;
  height: 88rpx;
  border-radius: 22rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8rpx 16rpx rgba(91, 103, 242, 0.18);
}
.icon-box.pale {
  background: #e8ebf3;
  box-shadow: none;
}
.icon-img {
  width: 46rpx;
  height: 46rpx;
}
.cell-label {
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #666;
  line-height: 1.2;
}
</style>
