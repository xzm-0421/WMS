<template>
  <view class="shell">
    <view class="stage" :class="{ fit: canvas.fit }" :style="stageStyle">
      <view class="tool-bar" :style="{ paddingTop: statusBarHeight + 'px' }">
        <text class="tool-title">MES-tablet</text>
        <view class="tool-actions">
          <text class="res-hint">{{ sizeHint }}</text>
          <text class="tool-btn" @click="openSettings">显示设置</text>
          <text class="tool-btn" @click="goSystemSettings">系统设置</text>
          <text class="tool-btn" @click="logout">退出</text>
        </view>
      </view>

      <view class="panel process-panel">
        <view class="panel-head">
          <text>工序信息</text>
        </view>
        <view class="search-row">
          <input
            v-model="keyword"
            class="search-input"
            placeholder="名称、信息……"
            confirm-type="search"
            @confirm="placeholder('检索工序')"
          />
        </view>
        <view class="process-body">
          <text class="empty-hint">工单 / 工序明细将在此展示</text>
        </view>
      </view>

      <view class="summary-bar">
        <view class="total-box">
          <text>共计：—</text>
        </view>
      </view>

      <view class="bottom-row">
        <view class="panel weigh-panel">
          <view class="panel-head">
            <text>称重区域</text>
          </view>
          <view class="weigh-frame">
            <text class="weigh-value">—.—</text>
            <text class="weigh-unit">kg</text>
            <text class="weigh-tip">电子秤读数（待对接）</text>
          </view>
        </view>

        <view class="panel print-panel">
          <view class="panel-head">
            <text>物料标签打印</text>
          </view>
          <scroll-view class="print-list" scroll-y>
            <view v-for="box in boxes" :key="box.seq" class="print-row">
              <view class="print-label">
                <text>{{ box.label }}</text>
                <text class="print-qty">{{ box.qty == null ? '—' : box.qty }}</text>
              </view>
              <view class="print-btn" @click="placeholder('打印第' + box.seq + '框标签')">
                <text>标签打印</text>
              </view>
            </view>
          </scroll-view>
        </view>
      </view>
    </view>

    <view v-if="settingsVisible" class="mask" @click="settingsVisible = false">
      <view class="sheet" @click.stop>
        <text class="sheet-title">显示设置</text>
        <text class="sheet-desc">默认铺满当前屏幕；也可固定分辨率，画面按比例缩放居中。</text>
        <view
          v-for="item in presets"
          :key="item.id"
          class="preset-item"
          :class="{ active: display.presetId === item.id }"
          @click="onPresetChange(item.id)"
        >
          <text>{{ item.label }}</text>
        </view>
        <view v-if="display.presetId === 'custom'" class="custom-row">
          <input v-model="draftWidth" class="size-input" type="number" placeholder="宽度" />
          <text class="size-x">×</text>
          <input v-model="draftHeight" class="size-input" type="number" placeholder="高度" />
          <view class="apply-btn" @click="applyCustomSize">
            <text>应用</text>
          </view>
        </view>
        <view class="sheet-close" @click="settingsVisible = false">
          <text>关闭</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { clearSession, hasSession } from '@/utils/authStorage.js'
import {
  TABLET_PRESETS,
  loadTabletDisplay,
  resolveTabletCanvas,
  saveTabletDisplay,
} from '@/utils/tabletDisplay.js'

const CN_ORDINAL = ['一', '二', '三', '四', '五', '六', '七', '八', '九', '十']
const presets = TABLET_PRESETS
const display = reactive(loadTabletDisplay())
const keyword = ref('')
const settingsVisible = ref(false)
const statusBarHeight = ref(0)
const winW = ref(1280)
const winH = ref(800)
const draftWidth = ref(String(display.customWidth))
const draftHeight = ref(String(display.customHeight))

const boxes = computed(() =>
  Array.from({ length: 6 }, (_, index) => ({
    seq: index + 1,
    label: `第${CN_ORDINAL[index] || index + 1}框件数`,
    qty: null,
  })),
)

const canvas = computed(() => resolveTabletCanvas(display, winW.value, winH.value))

const sizeHint = computed(() =>
  canvas.value.fit ? '自适应屏幕' : `${canvas.value.width} × ${canvas.value.height}`,
)

const stageStyle = computed(() => {
  if (canvas.value.fit) {
    return {
      width: '100%',
      height: '100%',
      left: '0',
      top: '0',
      transform: 'none',
    }
  }
  return {
    width: canvas.value.width + 'px',
    height: canvas.value.height + 'px',
    left: '50%',
    top: '50%',
    transform: `translate(-50%, -50%) scale(${canvas.value.scale})`,
  }
})

function syncWindow() {
  const info = uni.getSystemInfoSync()
  winW.value = info.windowWidth || info.screenWidth
  winH.value = info.windowHeight || info.screenHeight
  statusBarHeight.value = info.statusBarHeight || 0
}

function persist() {
  saveTabletDisplay(display)
}

function openSettings() {
  draftWidth.value = String(display.customWidth)
  draftHeight.value = String(display.customHeight)
  settingsVisible.value = true
}

function onPresetChange(id) {
  display.presetId = id
  persist()
}

function applyCustomSize() {
  const width = Math.max(800, Number(draftWidth.value) || 1280)
  const height = Math.max(480, Number(draftHeight.value) || 800)
  display.customWidth = width
  display.customHeight = height
  display.presetId = 'custom'
  persist()
  uni.showToast({ title: `${width} × ${height}`, icon: 'none' })
}

function placeholder(action) {
  uni.showToast({ title: `${action}（后续接入）`, icon: 'none' })
}

function goSystemSettings() {
  uni.navigateTo({ url: '/pages/settings/settings' })
}

function logout() {
  uni.showModal({
    title: '退出登录',
    content: '确定退出 MES-tablet？',
    success(res) {
      if (!res.confirm) return
      clearSession()
      uni.reLaunch({ url: '/pages/login/login' })
    },
  })
}

onShow(() => {
  if (!hasSession()) {
    uni.reLaunch({ url: '/pages/login/login' })
    return
  }
  Object.assign(display, loadTabletDisplay())
  syncWindow()
})

onMounted(() => {
  syncWindow()
  uni.onWindowResize?.(syncWindow)
})
onUnmounted(() => {
  uni.offWindowResize?.(syncWindow)
})
</script>

<style scoped>
.shell {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  background: #1c1c1c;
  overflow: hidden;
}

.stage {
  position: absolute;
  display: flex;
  flex-direction: column;
  padding: 10px 12px 12px;
  box-sizing: border-box;
  background: #fff;
  color: #111;
  transform-origin: center center;
}

.tool-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  min-height: 36px;
}

.tool-title {
  font-size: 16px;
  font-weight: 600;
}

.tool-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.res-hint {
  color: #909399;
  font-size: 13px;
}

.tool-btn {
  padding: 6px 12px;
  border: 1px solid #222;
  font-size: 14px;
}

.panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  border: 1px solid #222;
  background: #fff;
}

.panel-head {
  padding: 8px 12px;
  background: #d9d9d9;
  font-size: 16px;
  font-weight: 600;
  border-bottom: 1px solid #222;
}

.process-panel {
  flex: 1.15;
  min-height: 0;
}

.search-row {
  padding: 8px 10px 0;
}

.search-input {
  height: 40px;
  padding: 0 12px;
  border: 1px solid #222;
  font-size: 16px;
}

.process-body {
  flex: 1;
  margin: 8px 10px 10px;
  border: 1px solid #222;
  display: flex;
  align-items: center;
  justify-content: center;
}

.empty-hint {
  color: #a8abb2;
  font-size: 15px;
}

.summary-bar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 8px 2px;
}

.total-box {
  min-width: 160px;
  height: 40px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  background: #cfcfcf;
  font-size: 16px;
  font-weight: 600;
}

.bottom-row {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 10px;
}

.weigh-panel,
.print-panel {
  flex: 1;
  min-height: 0;
}

.weigh-frame {
  flex: 1;
  margin: 10px;
  border: 3px solid #2f6fed;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.weigh-value {
  font-size: 64px;
  font-weight: 700;
  line-height: 1;
}

.weigh-unit {
  margin-top: 8px;
  font-size: 22px;
}

.weigh-tip {
  margin-top: 12px;
  color: #909399;
  font-size: 13px;
}

.print-list {
  flex: 1;
  min-height: 0;
  height: 100%;
}

.print-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 52px;
  padding: 6px 12px;
  border-bottom: 1px solid #dcdcdc;
}

.print-label {
  font-size: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.print-qty {
  color: #909399;
}

.print-btn {
  min-width: 108px;
  height: 36px;
  background: #e8e8e8;
  border: 1px solid #bbb;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
}

.mask {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: flex-end;
  z-index: 20;
}

.sheet {
  width: 360px;
  max-width: 86%;
  height: 100%;
  background: #fff;
  padding: 20px 16px;
  box-sizing: border-box;
  overflow: auto;
}

.sheet-title {
  display: block;
  font-size: 18px;
  font-weight: 700;
  margin-bottom: 8px;
}

.sheet-desc {
  display: block;
  color: #606266;
  font-size: 13px;
  line-height: 1.5;
  margin-bottom: 16px;
}

.preset-item {
  height: 42px;
  border: 1px solid #dcdcdc;
  margin-bottom: 8px;
  padding: 0 12px;
  display: flex;
  align-items: center;
}

.preset-item.active {
  border-color: #111;
  background: #f3f3f3;
  font-weight: 600;
}

.custom-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 12px 0;
}

.size-input {
  flex: 1;
  height: 36px;
  border: 1px solid #222;
  padding: 0 8px;
}

.size-x {
  color: #909399;
}

.apply-btn {
  height: 36px;
  padding: 0 12px;
  background: #1d4ed8;
  color: #fff;
  display: flex;
  align-items: center;
}

.sheet-close {
  margin-top: 16px;
  height: 40px;
  border: 1px solid #222;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
