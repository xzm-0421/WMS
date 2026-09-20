<template>
  <view class="tab-bar">
    <view
      v-for="item in tabs"
      :key="item.key"
      class="tab-item"
      hover-class="tab-item-hover"
      :hover-stay-time="80"
      @click="go(item.url)"
    >
      <image
        class="tab-icon"
        :src="current === item.key ? item.activeIcon : item.icon"
        mode="aspectFit"
      />
      <text :class="['label', { active: current === item.key }]">{{ item.label }}</text>
    </view>
  </view>
</template>

<script setup>
const props = defineProps({
  current: { type: String, required: true },
})

const tabs = [
  {
    key: 'home',
    label: '首页',
    url: '/pages/home/home',
    icon: '/static/tab/home.svg',
    activeIcon: '/static/tab/home-active.svg',
  },
  {
    key: 'service',
    label: '服务',
    url: '/pages/service/service',
    icon: '/static/tab/service.svg',
    activeIcon: '/static/tab/service-active.svg',
  },
  {
    key: 'mine',
    label: '我的',
    url: '/pages/mine/mine',
    icon: '/static/tab/mine.svg',
    activeIcon: '/static/tab/mine-active.svg',
  },
]

function go(url) {
  const target = tabs.find((item) => item.url === url)
  if (!target || target.key === props.current) return
  uni.redirectTo({ url })
}
</script>

<style scoped>
.tab-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  height: calc(112rpx + env(safe-area-inset-bottom));
  padding-bottom: env(safe-area-inset-bottom);
  background: #fff;
  border-top: 1rpx solid #eeeef4;
  display: flex;
  z-index: 30;
}
.tab-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6rpx;
}
.tab-item-hover {
  opacity: 0.7;
}
.tab-icon {
  width: 48rpx;
  height: 48rpx;
}
.label {
  font-size: 22rpx;
  color: #9aa0b4;
  line-height: 1.2;
}
.label.active {
  color: #5c67f2;
  font-weight: 600;
}
</style>
