<template>
  <view class="page">
    <RippleBg />
    <view class="nav" :style="{ paddingTop: statusBarHeight + 'px' }">
      <view class="back" @click="goBack">
        <view class="chevron"></view>
      </view>
    </view>

    <view class="inner">
      <text class="title">修改密码</text>
      <text class="hint">{{ PASSWORD_HINT }}</text>

      <view class="form">
        <CapsuleField v-model="oldPwd" placeholder="请输入原始密码" is-password />
        <CapsuleField v-model="newPwd" placeholder="请输入新密码" is-password />
        <CapsuleField v-model="confirmPwd" placeholder="请再次输入新密码" is-password />
        <view class="btn" @click="handleSubmit">
          <text>提交</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { changePassword } from '@/api/auth.js'
import CapsuleField from '@/components/CapsuleField.vue'
import RippleBg from '@/components/RippleBg.vue'
import { requireSession } from '@/utils/authStorage.js'
import { PASSWORD_HINT, PASSWORD_RULE, toast } from '@/utils/ui.js'

const oldPwd = ref('')
const newPwd = ref('')
const confirmPwd = ref('')
const statusBarHeight = ref(uni.getSystemInfoSync().statusBarHeight || 20)

onShow(() => {
  requireSession()
})

function goBack() {
  uni.navigateBack({
    fail: () => uni.redirectTo({ url: '/pages/mine/mine' }),
  })
}

async function handleSubmit() {
  if (!oldPwd.value) {
    toast('请输入原始密码')
    return
  }
  if (!PASSWORD_RULE.test(newPwd.value)) {
    toast('新密码不符合复杂度要求')
    return
  }
  if (newPwd.value !== confirmPwd.value) {
    toast('两次输入的新密码不一致')
    return
  }
  if (newPwd.value === oldPwd.value) {
    toast('新密码不能与原密码相同')
    return
  }
  try {
    await changePassword(oldPwd.value, newPwd.value)
    toast('密码已修改')
    setTimeout(() => {
      uni.navigateBack({
        fail: () => uni.redirectTo({ url: '/pages/mine/mine' }),
      })
    }, 400)
  } catch {
    // http.js 已 toast
  }
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: linear-gradient(180deg, #eadff8 0%, #e6edfb 26%, #ffffff 48%);
  position: relative;
  overflow: hidden;
}
.nav {
  position: relative;
  z-index: 2;
  padding-left: 24rpx;
  height: 88rpx;
  display: flex;
  align-items: center;
}
.back {
  width: 72rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.chevron {
  width: 22rpx;
  height: 22rpx;
  border-left: 5rpx solid #222;
  border-bottom: 5rpx solid #222;
  transform: rotate(45deg);
  margin-left: 10rpx;
}
.inner {
  position: relative;
  z-index: 1;
  padding: 24rpx 56rpx 80rpx;
}
.title {
  display: block;
  font-size: 56rpx;
  font-weight: 800;
  color: #2a2870;
}
.hint {
  display: block;
  margin-top: 20rpx;
  margin-bottom: 56rpx;
  font-size: 26rpx;
  line-height: 1.6;
  color: #6f7288;
}
.btn {
  margin-top: 12rpx;
  height: 96rpx;
  border-radius: 48rpx;
  background: #5c67f2;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 32rpx;
  font-weight: 700;
  box-shadow: 0 16rpx 32rpx rgba(92, 103, 242, 0.28);
}
</style>
