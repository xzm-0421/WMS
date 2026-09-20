<template>
  <view class="ripple-bg" aria-hidden="true">
    <view class="ring r1"></view>
    <view class="ring r2"></view>
    <view class="ring r3"></view>
    <view class="ring r4"></view>
  </view>
</template>

<style scoped>
.ripple-bg {
  position: absolute;
  right: -80rpx;
  top: -60rpx;
  width: 480rpx;
  height: 480rpx;
  pointer-events: none;
}
.ring {
  position: absolute;
  border-radius: 50%;
  border: 3rpx solid rgba(255, 255, 255, 0.55);
  box-sizing: border-box;
}
.r1 { right: 40rpx; top: 20rpx; width: 160rpx; height: 160rpx; }
.r2 { right: -20rpx; top: -40rpx; width: 280rpx; height: 280rpx; opacity: 0.85; }
.r3 { right: -80rpx; top: -100rpx; width: 400rpx; height: 400rpx; opacity: 0.65; }
.r4 { right: -140rpx; top: -160rpx; width: 520rpx; height: 520rpx; opacity: 0.4; }
</style>
