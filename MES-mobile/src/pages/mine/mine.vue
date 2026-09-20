<template>
  <view class="page">
    <view class="header" :style="{ paddingTop: statusBarHeight + 24 + 'px' }">
      <RippleBg />
      <view class="profile">
        <view class="avatar">
          <view class="hair"></view>
          <view class="face">
            <view class="brow left"></view>
            <view class="brow right"></view>
            <view class="eye e-left"></view>
            <view class="eye e-right"></view>
            <view class="blush b-left"></view>
            <view class="blush b-right"></view>
            <view class="mouth"></view>
          </view>
          <view class="collar"></view>
        </view>
        <view class="info">
          <view class="name-row">
            <text class="name">{{ user.name }}</text>
            <text v-if="user.role" class="role">{{ user.role }}</text>
          </view>
          <text v-if="user.phone" class="phone">{{ user.phone }}</text>
        </view>
      </view>
    </view>

    <view class="sheet">
      <view
        v-for="item in menus"
        :key="item.key"
        class="menu-row"
        @click="onMenu(item)"
      >
        <view class="menu-icon" :class="item.key">
          <text class="mi">{{ item.icon }}</text>
        </view>
        <text class="menu-label">{{ item.label }}</text>
        <text v-if="item.extra" class="extra">{{ item.extra }}</text>
        <text class="arrow">›</text>
      </view>
    </view>

    <AppTabBar current="mine" />
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getUserInfo } from '@/api/auth.js'
import AppTabBar from '@/components/AppTabBar.vue'
import RippleBg from '@/components/RippleBg.vue'
import {
  clearSession,
  formatRoleNames,
  getUser,
  requireSession,
  saveUserInfo,
} from '@/utils/authStorage.js'
import { comingSoon } from '@/utils/ui.js'

const statusBarHeight = ref(uni.getSystemInfoSync().statusBarHeight || 20)
const session = ref(getUser() || {})

const user = computed(() => ({
  name: session.value.realName || session.value.username || '未登录',
  role: formatRoleNames(session.value),
  phone: session.value.phone || '',
}))

const menus = [
  { key: 'help', label: '使用帮助', icon: '💡' },
  { key: 'password', label: '修改密码', icon: '🔒' },
  { key: 'version', label: '版本号', icon: 'ℹ', extra: '1.0.1' },
  { key: 'logout', label: '退出登录', icon: '⏻' },
]

function onMenu(item) {
  if (item.key === 'password') {
    uni.navigateTo({ url: '/pages/password/password' })
    return
  }
  if (item.key === 'logout') {
    uni.showModal({
      title: '退出登录',
      content: '确定退出当前账号？',
      success(res) {
        if (!res.confirm) return
        clearSession()
        uni.reLaunch({ url: '/pages/login/login' })
      },
    })
    return
  }
  if (item.key === 'version') {
    uni.showToast({ title: '当前版本 1.0.1', icon: 'none' })
    return
  }
  comingSoon(item.label)
}

onShow(async () => {
  if (!requireSession()) return
  session.value = getUser() || {}
  try {
    const info = await getUserInfo()
    saveUserInfo(info)
    session.value = info
  } catch {
    session.value = getUser() || {}
  }
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #fff;
}
.header {
  position: relative;
  overflow: hidden;
  min-height: 280rpx;
  padding: 0 40rpx 48rpx;
  background: linear-gradient(120deg, #f4d9ef 0%, #e4ecfb 70%);
}
.profile {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 24rpx;
}
.avatar {
  width: 128rpx;
  height: 128rpx;
  border-radius: 50%;
  background: #fff;
  position: relative;
  overflow: hidden;
  box-shadow: 0 8rpx 20rpx rgba(90, 80, 160, 0.12);
}
.hair {
  position: absolute;
  left: 18rpx;
  right: 18rpx;
  top: 10rpx;
  height: 40rpx;
  background: #3a3530;
  border-radius: 40rpx 40rpx 8rpx 8rpx;
}
.face {
  position: absolute;
  left: 26rpx;
  right: 26rpx;
  top: 32rpx;
  height: 70rpx;
  background: #f7d7b8;
  border-radius: 40rpx 40rpx 36rpx 36rpx;
}
.brow {
  position: absolute;
  top: 14rpx;
  width: 16rpx;
  height: 4rpx;
  background: #3a3530;
  border-radius: 4rpx;
}
.brow.left { left: 14rpx; transform: rotate(-12deg); }
.brow.right { right: 14rpx; transform: rotate(12deg); }
.eye {
  position: absolute;
  top: 24rpx;
  width: 8rpx;
  height: 10rpx;
  background: #2b2b2b;
  border-radius: 50%;
}
.e-left { left: 18rpx; }
.e-right { right: 18rpx; }
.blush {
  position: absolute;
  top: 34rpx;
  width: 12rpx;
  height: 8rpx;
  background: #f3b3a4;
  border-radius: 50%;
  opacity: 0.7;
}
.b-left { left: 8rpx; }
.b-right { right: 8rpx; }
.mouth {
  position: absolute;
  left: 28rpx;
  right: 28rpx;
  bottom: 12rpx;
  height: 10rpx;
  border-bottom: 3rpx solid #d9897a;
  border-radius: 0 0 12rpx 12rpx;
}
.collar {
  position: absolute;
  left: 28rpx;
  right: 28rpx;
  bottom: 0;
  height: 28rpx;
  background: #5c67f2;
  border-radius: 8rpx 8rpx 0 0;
}
.info {
  flex: 1;
}
.name-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}
.name {
  font-size: 36rpx;
  font-weight: 800;
  color: #222;
}
.role {
  font-size: 22rpx;
  color: #fff;
  background: #8a8dfe;
  padding: 4rpx 14rpx;
  border-radius: 20rpx;
}
.phone {
  display: block;
  margin-top: 10rpx;
  font-size: 26rpx;
  color: #8a8a8a;
}
.sheet {
  margin-top: -12rpx;
  background: #fff;
  border-radius: 32rpx 32rpx 0 0;
  padding: 12rpx 8rpx calc(140rpx + env(safe-area-inset-bottom));
  position: relative;
  z-index: 2;
  min-height: 60vh;
}
.menu-row {
  display: flex;
  align-items: center;
  padding: 28rpx 32rpx;
}
.menu-icon {
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 20rpx;
}
.menu-icon.help { background: #ffe8e4; }
.menu-icon.password { background: #ece6ff; }
.menu-icon.version { background: #e7f0ff; }
.menu-icon.logout { background: #ece6ff; }
.mi { font-size: 28rpx; }
.menu-label {
  flex: 1;
  font-size: 30rpx;
  color: #333;
}
.extra {
  font-size: 26rpx;
  color: #9aa0b4;
  margin-right: 8rpx;
}
.arrow {
  font-size: 40rpx;
  color: #c5c7d4;
  line-height: 1;
}
</style>
