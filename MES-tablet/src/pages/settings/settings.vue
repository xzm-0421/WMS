<template>
  <view class="page">
    <view class="section-card">
      <ServerConfigForm ref="serverFormRef" @saved="onServerSaved" />
    </view>

    <view v-if="isLoggedIn" class="section-card">
      <text class="section-title">账号安全</text>
      <text class="label">原密码</text>
      <input v-model="pwd.old" class="input" password placeholder="原密码" />
      <text class="label">新密码</text>
      <input v-model="pwd.new1" class="input" password placeholder="新密码" />
      <text class="label">确认新密码</text>
      <input v-model="pwd.new2" class="input" password placeholder="确认新密码" />
      <button class="btn-primary" @click="handleChangePwd">保存密码</button>
    </view>

    <view class="section-card">
      <text class="section-title">平板显示</text>
      <text class="hint">现场作业页默认铺满屏幕，也可在此固定分辨率。</text>
      <view
        v-for="item in tabletPresets"
        :key="item.id"
        class="preset-row"
        :class="{ active: tabletDisplay.presetId === item.id }"
        @click="onTabletPreset(item.id)"
      >
        <text>{{ item.label }}</text>
      </view>
      <view v-if="tabletDisplay.presetId === 'custom'" class="custom-size">
        <input v-model="tabletW" class="input" type="number" placeholder="宽度 px" />
        <input v-model="tabletH" class="input" type="number" placeholder="高度 px" />
        <button class="btn-primary" @click="applyTabletCustom">应用自定义分辨率</button>
      </view>
    </view>

    <view class="section-card">
      <text class="section-title">设备信息</text>
      <text class="info-row">设备编号: {{ deviceNo }}</text>
      <text class="info-row">应用: MES-tablet 1.0.0</text>
    </view>

    <button v-if="isLoggedIn && !fromLogin" class="btn-logout" @click="logout">退出登录</button>
    <button v-if="fromLogin" class="btn-back" @click="goBack">返回登录</button>
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { changePassword } from '@/api/auth.js'
import ServerConfigForm from '@/components/ServerConfigForm.vue'
import { clearSession, hasSession } from '@/utils/authStorage.js'
import defaultConfig from '@/utils/config.js'
import { TABLET_PRESETS, loadTabletDisplay, saveTabletDisplay } from '@/utils/tabletDisplay.js'

const serverFormRef = ref(null)
const fromLogin = ref(false)
const pwd = reactive({ old: '', new1: '', new2: '' })
const deviceNo = defaultConfig.deviceNo
const tabletPresets = TABLET_PRESETS
const tabletDisplay = reactive(loadTabletDisplay())
const tabletW = ref(String(tabletDisplay.customWidth))
const tabletH = ref(String(tabletDisplay.customHeight))
const isLoggedIn = computed(() => hasSession())

onLoad((options) => {
  fromLogin.value = options?.from === 'login'
})

onShow(() => {
  serverFormRef.value?.refresh?.()
  Object.assign(tabletDisplay, loadTabletDisplay())
  tabletW.value = String(tabletDisplay.customWidth)
  tabletH.value = String(tabletDisplay.customHeight)
})

function onTabletPreset(id) {
  tabletDisplay.presetId = id
  saveTabletDisplay(tabletDisplay)
  uni.showToast({ title: '已保存', icon: 'none' })
}

function applyTabletCustom() {
  tabletDisplay.customWidth = Math.max(800, Number(tabletW.value) || 1280)
  tabletDisplay.customHeight = Math.max(480, Number(tabletH.value) || 800)
  tabletDisplay.presetId = 'custom'
  saveTabletDisplay(tabletDisplay)
  uni.showToast({ title: `${tabletDisplay.customWidth} × ${tabletDisplay.customHeight}`, icon: 'none' })
}

function onServerSaved(payload) {
  if (payload?.needRelogin && !fromLogin.value) {
    uni.reLaunch({ url: '/pages/login/login' })
  }
}

async function handleChangePwd() {
  if (!pwd.old || !pwd.new1) {
    uni.showToast({ title: '请填写密码', icon: 'none' })
    return
  }
  if (pwd.new1 !== pwd.new2) {
    uni.showToast({ title: '两次密码不一致', icon: 'none' })
    return
  }
  await changePassword(pwd.old, pwd.new1)
  pwd.old = ''
  pwd.new1 = ''
  pwd.new2 = ''
  uni.showToast({ title: '密码已修改', icon: 'success' })
}

function logout() {
  clearSession()
  uni.reLaunch({ url: '/pages/login/login' })
}

function goBack() {
  uni.navigateBack()
}
</script>

<style scoped>
.page { padding: 24px; }
.section-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 16px;
}
.section-title { font-weight: bold; font-size: 16px; margin-bottom: 10px; display: block; }
.label { display: block; color: #64748b; font-size: 13px; margin: 8px 0 6px; }
.hint { display: block; color: #64748b; font-size: 13px; margin-bottom: 12px; }
.input {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 10px;
  margin-bottom: 8px;
}
.btn-primary { background: #0f766e; color: #fff; margin-top: 8px; }
.preset-row {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 8px;
}
.preset-row.active {
  border-color: #0f766e;
  background: #f0fdfa;
  font-weight: bold;
}
.custom-size { margin-top: 8px; }
.info-row { display: block; color: #64748b; font-size: 14px; margin-top: 6px; }
.btn-logout { background: #fff; color: #b91c1c; margin-top: 8px; }
.btn-back { background: #f1f5f9; color: #475569; margin-top: 8px; }
</style>
