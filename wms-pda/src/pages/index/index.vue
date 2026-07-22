<template>
  <view class="app-shell">
    <!-- 内容区：各模块独立面板，v-show 保持状态 -->
    <view class="content-area">
      <!-- 主页 -->
      <scroll-view
        v-show="activeTab === 'home'"
        scroll-y
        class="tab-scroll"
        :scroll-top="scrollTopRestore.home"
        :scroll-with-animation="false"
        :enable-back-to-top="false"
        refresher-enabled
        :refresher-triggered="refreshing"
        @refresherrefresh="onRefresherRefresh"
        @scroll="(e) => onScroll('home', e)"
      >
        <view class="panel home-panel">
          <view class="welcome-card">
            <text class="welcome-hi">你好，{{ userName }}</text>
            <text class="welcome-sub">{{ todayStr }} · WMS 仓储作业终端</text>
            <view class="total-pending">
              <text class="total-num">{{ totalPending }}</text>
              <text class="total-label">待办任务合计</text>
            </view>
          </view>

          <text class="section-label">待办概览</text>
          <view class="stats">
            <view
              v-for="mod in overviewModules"
              :key="mod.key"
              :class="['stat', mod.color]"
              @click="onOverviewClick(mod.key)"
            >
              <text class="num">{{ getCount(mod.key) }}</text>
              <text class="label">{{ mod.label }}</text>
            </view>
          </view>

          <text class="section-label">快捷功能</text>
          <view class="menu-grid">
            <view class="menu-item wide inbound" @click="goInbound">
              <text class="menu-icon">📥</text>
              <text>入库业务</text>
            </view>
            <view class="menu-item wide outbound" @click="goOutbound">
              <text class="menu-icon">📤</text>
              <text>出库业务</text>
            </view>
            <view class="menu-item" @click="goStockCount">
              <text class="menu-icon">📋</text>
              <text>盘点</text>
              <text v-if="getCount('stockcheck') > 0" class="badge">{{ getCount('stockcheck') }}</text>
            </view>
            <view class="menu-item" @click="goTaskList('qc')">
              <text class="menu-icon">✅</text>
              <text>质检</text>
              <text v-if="getCount('qc') > 0" class="badge">{{ getCount('qc') }}</text>
            </view>
            <view class="menu-item" @click="goPage('/pages/panel/panel')">
              <text class="menu-icon">🏷️</text>
              <text>条码校验</text>
            </view>
            <view class="menu-item" @click="goPage('/pages/inventory/inventory')">
              <text class="menu-icon">📦</text>
              <text>库存查询</text>
            </view>
            <view class="menu-item" @click="goPage('/pages/transfer/transfer')">
              <text class="menu-icon">🔄</text>
              <text>移库</text>
            </view>
            <view class="menu-item" @click="goPage('/pages/trace/trace')">
              <text class="menu-icon">🔍</text>
              <text>批次追溯</text>
            </view>
            <view class="menu-item" @click="goPage('/pages/picking/issue-pick')">
              <text class="menu-icon">🛒</text>
              <text>扫码拣货</text>
            </view>
            <view class="menu-item" @click="goPage('/pages/picking/pickup')">
              <text class="menu-icon">📤</text>
              <text>领料确认</text>
            </view>
            <view class="menu-item" @click="goPage('/pages/picking/workshop-return')">
              <text class="menu-icon">↩️</text>
              <text>车间退库</text>
            </view>
            <view class="menu-item" @click="goPage('/pages/picking/production-return')">
              <text class="menu-icon">📥</text>
              <text>生产退料</text>
            </view>
            <view class="menu-item" @click="goPage('/pages/picking/outsource-return')">
              <text class="menu-icon">🔁</text>
              <text>委外退料</text>
            </view>
            <view class="menu-item" @click="goPage('/pages/messages/messages')">
              <text class="menu-icon">🔔</text>
              <text>消息</text>
              <text v-if="unreadCount > 0" class="badge">{{ unreadCount }}</text>
            </view>
          </view>
        </view>
      </scroll-view>

      <!-- 入库面板 -->
      <scroll-view
        v-show="activeTab === 'inbound'"
        scroll-y
        class="tab-scroll"
        :scroll-top="scrollTopRestore.inbound"
        :scroll-with-animation="false"
        @scroll="(e) => onScroll('inbound', e)"
      >
        <view class="panel task-panel">
          <view class="action-card inbound" @click="goInbound">
            <text class="action-icon">📥</text>
            <view class="action-info">
              <text class="action-title">入库业务</text>
              <text class="action-desc">按单据类型 · 通知单扫码 · 分批入库</text>
            </view>
            <text class="action-arrow">›</text>
          </view>
        </view>
      </scroll-view>

      <!-- 出库面板 -->
      <scroll-view
        v-show="activeTab === 'outbound'"
        scroll-y
        class="tab-scroll"
        :scroll-top="scrollTopRestore.outbound"
        :scroll-with-animation="false"
        @scroll="(e) => onScroll('outbound', e)"
      >
        <view class="panel task-panel">
          <view class="action-card outbound" @click="goOutbound">
            <text class="action-icon">📤</text>
            <view class="action-info">
              <text class="action-title">出库业务</text>
              <text class="action-desc">按单据类型 · 通知单扫码 · 分批出库</text>
            </view>
            <text class="action-arrow">›</text>
          </view>
        </view>
      </scroll-view>

      <!-- 我的 -->
      <scroll-view
        v-show="activeTab === 'profile'"
        scroll-y
        class="tab-scroll"
        :scroll-top="scrollTopRestore.profile"
        :scroll-with-animation="false"
        :enable-back-to-top="false"
        @scroll="(e) => onScroll('profile', e)"
      >
        <view class="panel">
          <ProfilePanel ref="profilePanelRef" />
        </view>
      </scroll-view>
    </view>

    <!-- 底部导航 -->
    <view class="tab-bar">
      <view
        v-for="tab in bottomTabs"
        :key="tab.key"
        :class="['tab-item', activeTab === tab.key && 'active']"
        @click="switchTab(tab.key)"
      >
        <text class="tab-icon">{{ tab.icon }}</text>
        <text class="tab-label">{{ tab.label }}</text>
        <text v-if="tab.taskKey && getCount(tab.taskKey) > 0" class="tab-badge">
          {{ getCount(tab.taskKey) > 99 ? '99+' : getCount(tab.taskKey) }}
        </text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getTasks, getUnreadCount } from '@/api/mobile.js'
import { navigateToInbound, navigateToOutbound } from '@/utils/scanEntry.js'
import ProfilePanel from '@/components/ProfilePanel.vue'

const STORAGE_TAB_KEY = 'wms_active_tab'
const STORAGE_SCROLL_KEY = 'wms_tab_scroll'

const bottomTabs = [
  { key: 'home', label: '主页', icon: '🏠' },
  { key: 'inbound', label: '入库', icon: '📥', taskKey: 'inbound' },
  { key: 'outbound', label: '出库', icon: '📤', taskKey: 'outbound' },
  { key: 'profile', label: '我的', icon: '👤' },
]

const overviewModules = [
  { key: 'inbound', label: '待入库', color: 'blue' },
  { key: 'outbound', label: '待出库', color: 'orange' },
  { key: 'stockcheck', label: '待盘点', color: 'green' },
  { key: 'qc', label: '待质检', color: 'purple' },
]

const taskTabModules = []

const activeTab = ref(uni.getStorageSync(STORAGE_TAB_KEY) || 'home')
const profilePanelRef = ref(null)
const tasks = ref({})
const unreadCount = ref(0)
const userName = ref('操作员')
const tabLoaded = reactive({
  home: true,
  inbound: false,
  outbound: false,
  profile: false,
})
/** 仅记录滚动位置，不绑定到 scroll-top（避免反馈循环抖动） */
const scrollPositions = reactive({
  home: 0,
  inbound: 0,
  outbound: 0,
  profile: 0,
})
/** 仅在冷启动恢复时短暂赋值，随后置 null 释放控制权 */
const scrollTopRestore = reactive({
  home: null,
  inbound: null,
  outbound: null,
  profile: null,
})

const refreshing = ref(false)
let scrollSaveTimer = null

const taskListCache = reactive({
  inbound: [],
  outbound: [],
  stockcheck: [],
  qc: [],
})

const todayStr = computed(() => {
  const d = new Date()
  const week = ['日', '一', '二', '三', '四', '五', '六']
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日 周${week[d.getDay()]}`
})

const totalPending = computed(() =>
  overviewModules.reduce((sum, m) => sum + getCount(m.key), 0),
)

function getCount(key) {
  return tasks.value[key]?.count || 0
}

function getTaskList(key) {
  return taskListCache[key] || []
}

function normalizeTask(item, key) {
  if (key === 'inbound' || key === 'outbound') {
    return {
      ...item,
      _key: item.orderNo,
      _title: item.orderNo,
      _meta: `${item.warehouseCode || '-'} · ${item.status || '-'}`,
    }
  }
  if (key === 'stockcheck') {
    return {
      ...item,
      _key: item.taskNo,
      _title: item.taskNo,
      _meta: `${item.warehouseCode || '-'} · ${item.status || '-'}`,
    }
  }
  return {
    ...item,
    _key: item.qcNo,
    _title: item.qcNo,
    _meta: `${item.materialCode || '-'} · ${item.status || '-'}`,
  }
}

function rebuildTaskCache() {
  ;['inbound', 'outbound', 'stockcheck', 'qc'].forEach((key) => {
    const raw = tasks.value[key]?.tasks || []
    taskListCache[key] = raw.map((item) => normalizeTask(item, key))
  })
}

function restoreScrollTops() {
  try {
    const saved = uni.getStorageSync(STORAGE_SCROLL_KEY)
    if (saved && typeof saved === 'object') {
      Object.keys(saved).forEach((k) => {
        if (k in scrollPositions) {
          scrollPositions[k] = saved[k]
          scrollTopRestore[k] = saved[k]
        }
      })
      // 恢复后释放 scroll-top 绑定，避免与 @scroll 形成反馈环
      nextTick(() => {
        setTimeout(() => {
          Object.keys(scrollTopRestore).forEach((k) => {
            scrollTopRestore[k] = null
          })
        }, 120)
      })
    }
  } catch {
    // ignore
  }
}

async function onRefresherRefresh() {
  if (refreshing.value) return
  refreshing.value = true
  try {
    await refreshTab(activeTab.value)
  } finally {
    refreshing.value = false
  }
}

function onScroll(tabKey, e) {
  scrollPositions[tabKey] = e.detail.scrollTop
  if (scrollSaveTimer) clearTimeout(scrollSaveTimer)
  scrollSaveTimer = setTimeout(() => {
    try {
      const saved = uni.getStorageSync(STORAGE_SCROLL_KEY) || {}
      saved[tabKey] = scrollPositions[tabKey]
      uni.setStorageSync(STORAGE_SCROLL_KEY, saved)
    } catch {
      // ignore
    }
    scrollSaveTimer = null
  }, 300)
}

function switchTab(key) {
  if (activeTab.value === key) return
  activeTab.value = key
  uni.setStorageSync(STORAGE_TAB_KEY, key)
  if (key === 'profile') {
    loadProfileTab()
  } else if (key !== 'home' && !tabLoaded[key]) {
    loadTabData(key)
  }
}

function loadProfileTab() {
  tabLoaded.profile = true
  profilePanelRef.value?.loadProfile?.()
}

function onOverviewClick(key) {
  if (key === 'inbound') goInbound()
  else if (key === 'outbound') goOutbound()
  else if (key === 'stockcheck') goStockCount()
  else goTaskList(key)
}

function goTaskList(type) {
  uni.navigateTo({ url: `/pages/tasklist/tasklist?type=${type}` })
}

function goStockCount() {
  uni.navigateTo({ url: '/pages/stockcheck/stockcheck-list' })
}

function ensureLogin() {
  const token = uni.getStorageSync('wms_token')
  if (!token) {
    uni.reLaunch({ url: '/pages/login/login' })
    return false
  }
  return true
}

async function loadHomeData() {
  const user = uni.getStorageSync('wms_user') || {}
  userName.value = user.realName || user.username || '操作员'
  try {
    const [taskData, unread] = await Promise.all([
      getTasks(),
      getUnreadCount().catch(() => ({ count: 0 })),
    ])
    tasks.value = taskData
    unreadCount.value = unread.count || 0
    rebuildTaskCache()
  } catch {
    tasks.value = {}
  }
}

async function loadTabData(key) {
  if (!ensureLogin()) return
  if (!tasks.value[key]) {
    await loadHomeData()
  } else {
    rebuildTaskCache()
  }
  tabLoaded[key] = true
}

async function refreshTab(key) {
  if (key === 'home') {
    await loadHomeData()
  } else if (key === 'profile') {
    await loadHomeData()
    loadProfileTab()
  } else {
    await loadHomeData()
    tabLoaded[key] = true
  }
  uni.showToast({ title: '已刷新', icon: 'success', duration: 800 })
}

function goInbound() {
  navigateToInbound()
}

function goOutbound() {
  navigateToOutbound()
}

function goPage(url) {
  uni.navigateTo({ url })
}

onMounted(() => {
  restoreScrollTops()
  if (ensureLogin()) {
    loadHomeData()
    if (activeTab.value === 'inbound' || activeTab.value === 'outbound') {
      loadTabData(activeTab.value)
    } else if (activeTab.value === 'profile') {
      loadProfileTab()
    }
  }
})

onShow(() => {
  if (!ensureLogin()) return
  loadHomeData()
  if (activeTab.value === 'inbound' || activeTab.value === 'outbound') {
    if (tabLoaded[activeTab.value]) rebuildTaskCache()
  } else if (activeTab.value === 'profile' && tabLoaded.profile) {
    profilePanelRef.value?.loadProfile?.()
  }
})

</script>

<style scoped>
.app-shell {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f1f5f9;
}

.content-area {
  flex: 1;
  overflow: hidden;
  position: relative;
}

.tab-scroll {
  height: 100%;
  box-sizing: border-box;
}

.panel {
  padding: 24rpx;
  padding-bottom: 32rpx;
  min-height: 100%;
  box-sizing: border-box;
}

/* 主页 */
.welcome-card {
  background: linear-gradient(135deg, #1d4ed8, #3b82f6);
  border-radius: 20rpx;
  padding: 40rpx 32rpx;
  color: #fff;
  margin-bottom: 32rpx;
}
.welcome-hi { font-size: 40rpx; font-weight: bold; display: block; }
.welcome-sub { font-size: 24rpx; opacity: 0.85; margin-top: 8rpx; display: block; }
.total-pending {
  margin-top: 32rpx;
  padding-top: 24rpx;
  border-top: 1px solid rgba(255,255,255,0.25);
  display: flex;
  align-items: baseline;
  gap: 12rpx;
}
.total-num { font-size: 56rpx; font-weight: bold; }
.total-label { font-size: 26rpx; opacity: 0.9; }

.section-label {
  font-size: 26rpx;
  font-weight: bold;
  color: #475569;
  margin-bottom: 16rpx;
  display: block;
}

.stats {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-bottom: 32rpx;
}
.stat {
  width: calc(50% - 8rpx);
  padding: 24rpx;
  border-radius: 16rpx;
  color: #fff;
  text-align: center;
  box-sizing: border-box;
}
.blue { background: #3b82f6; }
.orange { background: #f97316; }
.green { background: #22c55e; }
.purple { background: #8b5cf6; }
.num { display: block; font-size: 44rpx; font-weight: bold; }
.label { font-size: 24rpx; }

.menu-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}
.menu-item {
  width: calc(33.33% - 12rpx);
  background: #fff;
  border-radius: 12rpx;
  padding: 28rpx 16rpx;
  text-align: center;
  font-size: 26rpx;
  position: relative;
  box-sizing: border-box;
}
.menu-item.wide {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
  padding: 32rpx;
  background: #fff;
  color: #1d4ed8;
  font-weight: bold;
  border: 2rpx solid #bfdbfe;
}
.menu-item.wide.inbound {
  background: linear-gradient(135deg, #1d4ed8, #3b82f6);
  color: #fff;
  border: none;
}
.menu-item.wide.outbound {
  background: linear-gradient(135deg, #ea580c, #f97316);
  color: #fff;
  border: none;
}
.badge {
  position: absolute;
  top: 8rpx;
  right: 8rpx;
  background: #ef4444;
  color: #fff;
  font-size: 20rpx;
  min-width: 32rpx;
  height: 32rpx;
  line-height: 32rpx;
  border-radius: 16rpx;
  padding: 0 8rpx;
}
.menu-icon { display: block; font-size: 40rpx; margin-bottom: 8rpx; }

/* 任务面板 */
.task-panel { background: transparent; padding-top: 16rpx; }
.action-card {
  background: #fff;
  border-radius: 16rpx;
  padding: 36rpx 28rpx;
  display: flex;
  align-items: center;
  gap: 20rpx;
  box-shadow: 0 4rpx 16rpx rgba(0,0,0,0.06);
}
.action-card.inbound { border-left: 8rpx solid #3b82f6; }
.action-card.outbound { border-left: 8rpx solid #f97316; }
.action-icon { font-size: 48rpx; }
.action-info { flex: 1; }
.action-title { font-size: 32rpx; font-weight: bold; display: block; }
.action-desc { font-size: 24rpx; color: #64748b; display: block; margin-top: 6rpx; }
.action-arrow { font-size: 40rpx; color: #94a3b8; }
.panel-header {
  display: flex;
  align-items: center;
  gap: 16rpx;
  background: #fff;
  padding: 24rpx;
  border-radius: 16rpx;
  margin-bottom: 16rpx;
}
.panel-icon { font-size: 48rpx; }
.panel-info { flex: 1; }
.panel-title { font-weight: bold; font-size: 32rpx; display: block; }
.panel-desc { color: #94a3b8; font-size: 24rpx; }
.refresh-btn { background: #eff6ff; color: #1d4ed8; font-size: 22rpx; }

.task-card {
  background: #fff;
  padding: 24rpx;
  border-radius: 12rpx;
  margin-bottom: 16rpx;
  border-left: 6rpx solid #1d4ed8;
}
.order-no { font-weight: bold; display: block; font-size: 28rpx; }
.meta { color: #64748b; font-size: 24rpx; margin-top: 4rpx; display: block; }

.empty, .loading-hint {
  text-align: center;
  color: #94a3b8;
  padding: 80rpx 24rpx;
  background: #fff;
  border-radius: 16rpx;
}
.empty { display: flex; flex-direction: column; align-items: center; gap: 16rpx; }
.empty-icon { font-size: 64rpx; opacity: 0.5; }

/* 底部导航 */
.tab-bar {
  flex-shrink: 0;
  display: flex;
  background: #fff;
  border-top: 1px solid #e2e8f0;
  padding-bottom: env(safe-area-inset-bottom);
  box-shadow: 0 -4rpx 20rpx rgba(0, 0, 0, 0.06);
}
.tab-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 12rpx 0 10rpx;
  position: relative;
  color: #94a3b8;
  transition: color 0.2s;
}
.tab-item.active { color: #1d4ed8; }
.tab-item.active .tab-icon { transform: scale(1.08); }
.tab-icon { font-size: 38rpx; line-height: 1.2; }
.tab-label { font-size: 20rpx; margin-top: 2rpx; }
.tab-badge {
  position: absolute;
  top: 4rpx;
  right: calc(50% - 44rpx);
  background: #ef4444;
  color: #fff;
  font-size: 18rpx;
  min-width: 28rpx;
  height: 28rpx;
  line-height: 28rpx;
  text-align: center;
  border-radius: 14rpx;
  padding: 0 6rpx;
}
</style>
