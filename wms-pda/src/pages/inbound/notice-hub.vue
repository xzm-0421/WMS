<template>
  <view class="page">
    <view class="header">
      <text class="title">选择入库单据类型</text>
      <text class="sub">所有入库均采用通知单扫码模式：列表 → 扫码 → 分批提交</text>
    </view>
    <view class="type-grid">
      <view
        v-for="item in types"
        :key="item.code"
        class="type-card"
        :style="{ borderLeftColor: item.color }"
        @click="openType(item)"
      >
        <text class="icon">{{ item.icon }}</text>
        <view class="info">
          <text class="label">{{ item.label }}</text>
          <text class="desc">{{ returnDesc(item.code) }}</text>
        </view>
        <text class="arrow">›</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { listNoticeBillTypes } from '@/constants/noticeBillTypes.js'

const types = ref(listNoticeBillTypes('INBOUND'))

function returnDesc(code) {
  if (code === 'PRODUCTION_IN') {
    return '扫已审核生产汇报单 · 严格控量 · 生成入库并反写审核'
  }
  if (code === 'PRODUCTION_RETURN') return '扫未审核退料单 · 核对物料 · 提交审核'
  if (code === 'OUTSOURCE_RETURN') return '扫未审核委外退料单 · 核对物料 · 提交审核'
  if (code === 'SALES_RETURN') return '扫已审核退货通知 · 核对物料 · 生成退货并审核'
  if (code === 'OTHER_IN') {
    return '扫未审核单据 · 核对物料 · 提交审核'
  }
  return '扫码 · 勾选 · 提交审核'
}

function openType(item) {
  if (item.code === 'PRODUCTION_RETURN') {
    uni.navigateTo({ url: '/pages/picking/production-return' })
    return
  }
  if (item.code === 'OUTSOURCE_RETURN') {
    uni.navigateTo({ url: '/pages/picking/outsource-return' })
    return
  }
  uni.navigateTo({
    url: `/pages/notice/list?billType=${encodeURIComponent(item.code)}&direction=INBOUND`,
  })
}

onLoad(() => uni.setNavigationBarTitle({ title: '入库业务' }))
onMounted(() => {
  types.value = listNoticeBillTypes('INBOUND')
})
</script>

<style scoped>
.page { min-height: 100vh; background: #f1f5f9; padding: 24rpx; }
.header { margin-bottom: 24rpx; }
.title { display: block; font-size: 34rpx; font-weight: 700; color: #0f172a; }
.sub { display: block; margin-top: 8rpx; font-size: 24rpx; color: #64748b; line-height: 1.4; }
.type-grid { display: flex; flex-direction: column; gap: 16rpx; }
.type-card {
  display: flex; align-items: center; gap: 16rpx;
  background: #fff; border-radius: 12rpx; padding: 24rpx;
  border-left: 8rpx solid #3b82f6;
  box-shadow: 0 2rpx 8rpx rgba(15,23,42,0.06);
}
.icon { font-size: 44rpx; }
.info { flex: 1; }
.label { display: block; font-size: 28rpx; font-weight: 600; color: #0f172a; }
.desc { display: block; font-size: 22rpx; color: #94a3b8; margin-top: 4rpx; }
.arrow { font-size: 36rpx; color: #cbd5e1; }
</style>
