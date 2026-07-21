<template>
  <view class="page">
    <view class="section">
      <text class="section-title">入库业务</text>
      <view class="quick-card" @click="goDirect">
        <text class="icon">⚡</text>
        <view class="quick-info">
          <text class="name">快速入库（无单）</text>
          <text class="desc">扫码即入，无需前置单据</text>
        </view>
        <text class="arrow">›</text>
      </view>
      <view class="module-grid">
        <view
          v-for="mod in modules"
          :key="'in-' + mod.id"
          class="module-card"
          :style="{ borderLeftColor: mod.color }"
          @click="goScan(mod.id, 'inbound')"
        >
          <text class="icon">{{ mod.icon }}</text>
          <view class="module-info">
            <text class="name">{{ mod.inbound.label }}</text>
            <text v-if="mod.id === 'purchase'" class="desc">扫采购单 → 逐项确认 → 提交</text>
          </view>
          <text class="arrow">›</text>
        </view>
      </view>
    </view>

    <view class="section">
      <text class="section-title">出库业务</text>
      <view class="module-grid">
        <view
          v-for="mod in modules"
          :key="'out-' + mod.id"
          class="module-card"
          :style="{ borderLeftColor: mod.color }"
          @click="goScan(mod.id, 'outbound')"
        >
          <text class="icon">{{ mod.icon }}</text>
          <text class="name">{{ mod.outbound.label }}</text>
          <text class="arrow">›</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ORDER_MODULES } from '@/constants/orderModules.js'
import { navigateToScan } from '@/utils/scanEntry.js'

const modules = ORDER_MODULES

function goScan(moduleId, direction) {
  navigateToScan(moduleId, direction)
}

function goDirect() {
  uni.navigateTo({ url: '/pages/inbound/direct' })
}
</script>

<style scoped>
.page { padding: 24rpx; }
.section { margin-bottom: 32rpx; }
.section-title {
  font-weight: bold;
  font-size: 30rpx;
  margin-bottom: 16rpx;
  display: block;
}
.module-grid { display: flex; flex-direction: column; gap: 16rpx; }
.quick-card {
  background: linear-gradient(135deg, #1d4ed8, #3b82f6);
  padding: 28rpx 24rpx; border-radius: 12rpx;
  display: flex; align-items: center; gap: 16rpx; margin-bottom: 16rpx;
}
.quick-card .icon { font-size: 40rpx; }
.quick-info { flex: 1; }
.quick-card .name { font-size: 28rpx; font-weight: 600; color: #fff; display: block; }
.quick-card .desc { font-size: 22rpx; color: rgba(255,255,255,0.8); display: block; margin-top: 4rpx; }
.quick-card .arrow { color: rgba(255,255,255,0.7); font-size: 36rpx; }
.module-card {
  background: #fff;
  padding: 28rpx 24rpx;
  border-radius: 12rpx;
  border-left: 8rpx solid #3b82f6;
  display: flex;
  align-items: center;
  gap: 16rpx;
}
.icon { font-size: 40rpx; }
.module-info { flex: 1; }
.name { font-size: 28rpx; font-weight: 500; display: block; }
.desc { display: block; font-size: 22rpx; color: #94a3b8; margin-top: 4rpx; }
.arrow { color: #94a3b8; font-size: 36rpx; }
</style>
