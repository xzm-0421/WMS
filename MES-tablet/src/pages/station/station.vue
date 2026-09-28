<template>
  <view class="shell">
    <view class="stage" :class="{ fit: canvas.fit }" :style="stageStyle">
      <view class="tool-bar" :style="{ paddingTop: statusBarHeight + 'px' }">
        <text class="tool-title">MES-tablet</text>
        <view v-if="useMockData" class="mock-badge">
          <text>🧪 模拟数据模式</text>
        </view>
        <view class="tool-actions">
          <text class="res-hint">{{ sizeHint }}</text>
          <text 
            class="tool-btn" 
            :class="{ 'printer-connected': bluetoothConnected }"
            @click="handleConnectPrinter"
          >
            {{ bluetoothConnected ? '🖨️ 已连接' : '🖨️ 连接打印机' }}
          </text>
          <text class="tool-btn" @click="openSettings">显示设置</text>
          <text class="tool-btn" @click="goSystemSettings">系统设置</text>
          <text class="tool-btn" @click="logout">退出</text>
        </view>
      </view>

      <!-- 工序信息区域 -->
      <view class="panel process-panel">
        <view class="panel-head">
          <text>工序信息</text>
          <view class="refresh-btn" @click="searchPlans">
            <text>🔄 刷新</text>
          </view>
        </view>
        <view class="search-row">
          <input
            v-model="keyword"
            class="search-input"
            placeholder="搜索工单号、产品编码......"
            confirm-type="search"
            @confirm="searchPlans"
          />
        </view>
        <view class="process-body">
          <view v-if="loading" class="loading-hint">
            <text>加载中...</text>
          </view>
          <view v-else-if="planList.length === 0" class="empty-hint">
            <text>暂无工序数据</text>
          </view>
          <scroll-view v-else class="plan-list" scroll-y>
            <view
              v-for="plan in planList"
              :key="plan.id"
              class="plan-card"
              :class="{ selected: selectedPlan?.id === plan.id }"
              @click="selectPlan(plan)"
            >
              <view class="plan-main">
                <text class="plan-mo-no">{{ plan.moNo }}</text>
                <view class="plan-product-info">
                  <text class="plan-code">{{ plan.productCode }}</text>
                  <text class="plan-separator">·</text>
                  <text class="plan-name">{{ plan.productName }}</text>
                </view>
              </view>
              <view class="plan-status-area">
                <view class="status-tag" :class="getStatusClass(plan.status)">
                  <text>{{ getStatusText(plan.status) }}</text>
                </view>
                <text class="plan-qty-text">数量: {{ plan.planQty }}</text>
              </view>
            </view>
          </scroll-view>
        </view>
      </view>

      <!-- 底部统计栏 -->
      <view class="summary-bar">
        <text>共计：{{ planList.length }} 条工单</text>
      </view>

      <view class="bottom-row">
        <!-- 称重区域 -->
        <view class="panel weigh-panel">
          <view class="panel-head">
            <text>称重区域</text>
            <view v-if="selectedPlan" class="clear-btn" @click="clearSelection">
              <text>✕ 清除</text>
            </view>
          </view>
          <view v-if="!selectedPlan" class="weigh-empty">
            <text>请点击上方工单选择</text>
          </view>
          <view v-else class="weigh-content">
            <view class="weigh-plan-info">
              <text class="weigh-mo-no">{{ selectedPlan.moNo }}</text>
              <text class="weigh-product">{{ selectedPlan.productName }}</text>
            </view>
            <view class="weigh-input-row">
              <view class="weigh-label">
                <text>重量 (kg)</text>
              </view>
              <input
                v-model="inputWeight"
                class="weight-input"
                type="digit"
                placeholder="输入或等待电子秤"
                @confirm="confirmWeigh"
              />
              <view class="confirm-weight-btn" @click="confirmWeigh">
                <text>确认称重</text>
              </view>
            </view>
            <view class="weigh-tip">
              <text>💡 阶段一：手动输入；后续对接电子秤自动读取</text>
            </view>
          </view>
        </view>

        <!-- 物料标签打印区域 -->
        <view class="panel print-panel">
          <view class="panel-head">
            <text>物料标签打印 ({{ printQueue.length }})</text>
          </view>
          <scroll-view class="print-list" scroll-y>
            <view v-if="printQueue.length === 0" class="print-empty">
              <text>暂无待打印标签</text>
            </view>
            <view v-for="(item, index) in printQueue" :key="item.reportNo || index" class="print-card">
              <view class="print-card-header">
                <text class="print-card-title">{{ item.productName }}</text>
              </view>
              <view class="print-card-code">
                <text>{{ item.productCode }}</text>
              </view>
              <view class="print-card-info">
                <text class="info-item">数量: <text class="info-value">{{ item.qty }}</text></text>
                <text class="info-item">重量: <text class="info-value">{{ item.weightKg }} kg</text></text>
              </view>
              <view class="print-action">
                <view class="print-btn" :class="{ printing: item.printing }" @click="handlePrint(item)">
                  <text>{{ item.printing ? '打印中...' : '🖨️ 打印' }}</text>
                </view>
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
import { getMesPlanList, submitReport, printLabel, printMesLabel } from '@/api/mes.js'
import {
  initBluetooth,
  selectPrinter,
  connectPrinter,
  disconnectPrinter,
  sendToPrinter,
  isPrinterConnected,
} from '@/utils/bluetoothPrinter.js'

const presets = TABLET_PRESETS
const display = reactive(loadTabletDisplay())
const keyword = ref('')
const settingsVisible = ref(false)
const statusBarHeight = ref(0)
const winW = ref(1280)
const winH = ref(800)
const draftWidth = ref(String(display.customWidth))
const draftHeight = ref(String(display.customHeight))

const planList = ref([])
const selectedPlan = ref(null)
const loading = ref(false)
const inputWeight = ref('')
const printQueue = ref([])
let useMockData = false
const bluetoothConnected = ref(false)
const connectingPrinter = ref(false)

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

const MOCK_PLAN_DATA = [
  { id: 1, moNo: 'MO20260923001', productCode: 'P001', productName: '产品A-电机组件', planQty: 100, status: 'PENDING' },
  { id: 2, moNo: 'MO20260923002', productCode: 'P002', productName: '产品B-控制板', planQty: 50, status: 'PROCESSING' },
  { id: 3, moNo: 'MO20260923003', productCode: 'P003', productName: '产品C-传感器', planQty: 200, status: 'PENDING' },
  { id: 4, moNo: 'MO20260923004', productCode: 'P004', productName: '产品D-外壳', planQty: 80, status: 'COMPLETED' },
  { id: 5, moNo: 'MO20260922005', productCode: 'P005', productName: '产品E-连接器', planQty: 150, status: 'PENDING' },
]

function getStatusClass(status) {
  const map = {
    'PENDING': 'status-pending',
    'PROCESSING': 'status-processing',
    'COMPLETED': 'status-completed',
  }
  return map[status] || 'status-pending'
}

function getStatusText(status) {
  const map = {
    'PENDING': '待处理',
    'PROCESSING': '进行中',
    'COMPLETED': '已完成',
  }
  return map[status] || '待处理'
}

async function searchPlans() {
  loading.value = true
  try {
    const params = {
      current: 1,
      size: 50,
    }
    if (keyword.value.trim()) {
      params.keyword = keyword.value.trim()
    }
    const res = await getMesPlanList(params)
    const realData = res.records || []
    
    if (realData.length > 0) {
      planList.value = realData
      useMockData = false
      console.log('✅ 使用真实数据:', realData.length, '条')
    } else {
      const filtered = MOCK_PLAN_DATA.filter(item => 
        !keyword.value.trim() || 
        item.moNo.toLowerCase().includes(keyword.value.trim().toLowerCase()) ||
        item.productName.includes(keyword.value.trim()) ||
        item.productCode.toLowerCase().includes(keyword.value.trim().toLowerCase())
      )
      planList.value = filtered
      useMockData = true
      console.log('ℹ️ 使用模拟数据:', filtered.length, '条')
    }
  } catch (e) {
    console.error('获取工序计划失败，使用模拟数据:', e)
    const filtered = MOCK_PLAN_DATA.filter(item => 
      !keyword.value.trim() || 
      item.moNo.toLowerCase().includes(keyword.value.trim().toLowerCase()) ||
      item.productName.includes(keyword.value.trim()) ||
      item.productCode.toLowerCase().includes(keyword.value.trim().toLowerCase())
    )
    planList.value = filtered
    useMockData = true
  } finally {
    loading.value = false
  }
}

function selectPlan(plan) {
  selectedPlan.value = plan
  inputWeight.value = ''
}

function clearSelection() {
  selectedPlan.value = null
  inputWeight.value = ''
}

async function confirmWeigh() {
  if (!selectedPlan.value) {
    uni.showToast({ title: '请先选择工单', icon: 'none' })
    return
  }
  
  const weight = parseFloat(inputWeight.value)
  if (!weight || weight <= 0) {
    uni.showToast({ title: '请输入有效的重量', icon: 'none' })
    return
  }

  try {
    uni.showLoading({ title: useMockData ? '模拟提交中...' : '提交报工中...' })
    
    if (!selectedPlan.value.processCode) {
      uni.showToast({ title: '该工单未配置工序编码', icon: 'none' })
      return
    }

    const reportData = {
      moNo: selectedPlan.value.moNo,
      processCode: selectedPlan.value.processCode,
      reportType: 'NORMAL',
      qty: weight,
      weightKg: weight,
      equipmentCode: '',
      remark: `平板端称重 - ${new Date().toLocaleString()}`,
    }
    
    let result
    if (useMockData) {
      await new Promise(resolve => setTimeout(resolve, 500))
      result = {
        reportNo: `MOCK-${Date.now()}`,
        id: Math.floor(Math.random() * 10000),
      }
      console.log('ℹ️ 模拟报工成功:', result.reportNo)
    } else {
      result = await submitReport(reportData)
    }
    
    const printItem = {
      reportNo: result.reportNo,
      moNo: selectedPlan.value.moNo,
      productCode: selectedPlan.value.productCode,
      productName: selectedPlan.value.productName,
      weightKg: weight.toFixed(2),
      qty: reportData.qty,
      reportTime: new Date(),
      printing: false,
    }
    
    printQueue.value.unshift(printItem)
    
    uni.hideLoading()
    uni.showToast({ 
      title: `称重成功: ${weight.toFixed(2)}kg`, 
      icon: 'success' 
    })
    
    inputWeight.value = ''
    
  } catch (e) {
    uni.hideLoading()
    console.error('报工失败:', e)
    uni.showToast({ title: '报工失败: ' + (e.message || '未知错误'), icon: 'none' })
  }
}

async function handlePrint(item) {
  if (item.printing) return

  item.printing = true

  try {
    if (useMockData) {
      await new Promise(resolve => setTimeout(resolve, 800))
      console.log('ℹ️ 模拟打印成功:', item.reportNo)
    } else {
      const labelData = {
        reportNo: item.reportNo,
        moNo: item.moNo,
        productCode: item.productCode,
        productName: item.productName,
        weightKg: item.weightKg,
        qty: item.qty,
      }

      await printMesLabel(labelData)

      if (bluetoothConnected.value) {
        try {
          await sendToPrinter(labelData)
        } catch (printError) {
          console.warn('⚠️ 蓝牙打印失败，已记录打印任务:', printError.message)
        }
      }
    }

    uni.showToast({
      title: '标签打印成功',
      icon: 'success'
    })

  } catch (e) {
    console.error('打印失败:', e)
    uni.showToast({ title: '打印失败: ' + (e.message || '未知错误'), icon: 'none' })
  } finally {
    item.printing = false
  }
}

async function handleConnectPrinter() {
  if (connectingPrinter.value) return

  connectingPrinter.value = true

  try {
    if (bluetoothConnected.value) {
      await disconnectPrinter()
      bluetoothConnected.value = false
      uni.showToast({ title: '打印机已断开', icon: 'success' })
      return
    }

    // 初始化蓝牙（自动检测环境：Web Bluetooth 或 UniApp）
    await initBluetooth()

    // 选择打印机（自动适配不同环境的交互方式）
    const printer = await selectPrinter()

    if (!printer) return

    // 连接选中的打印机
    uni.showLoading({ title: `连接 ${printer.name}...` })
    await connectPrinter(printer)
    uni.hideLoading()

    bluetoothConnected.value = true
    uni.showToast({
      title: `已连接: ${printer.name}`,
      icon: 'success'
    })

  } catch (error) {
    uni.hideLoading()
    console.error('连接打印机失败:', error)

    // 用户取消选择设备（Web Bluetooth）
    if (error.name === 'NotFoundError') {
      return
    }

    // 环境不支持蓝牙
    if (error.message?.includes('不支持')) {
      uni.showModal({
        title: '提示',
        content: '当前浏览器或环境不支持 Web Bluetooth。\n\n' +
                 '✅ 支持的环境：\n' +
                 '   • Chrome 浏览器（推荐）\n' +
                 '   • Edge 浏览器（Chromium内核）\n' +
                 '   • Android 手机 Chrome\n' +
                 '   • 原生 APP\n\n' +
                 '❌ 不支持：\n' +
                 '   • Safari (iOS)\n' +
                 '   • Firefox\n' +
                 '   • 微信内置浏览器\n\n' +
                 '💡 提示：打印任务会正常记录到后端系统。',
        showCancel: false,
        confirmText: '我知道了'
      })
      return
    }

    uni.showToast({
      title: error.message || '连接失败',
      icon: 'none'
    })
  } finally {
    connectingPrinter.value = false
  }
}

onShow(async () => {
  if (!hasSession()) {
    uni.reLaunch({ url: '/pages/login/login' })
    return
  }
  Object.assign(display, loadTabletDisplay())
  syncWindow()
  await searchPlans()
})

onMounted(() => {
  syncWindow()
  uni.onWindowResize?.(syncWindow)
})

onUnmounted(() => {
  uni.offWindowResize?.(syncWindow)
  if (bluetoothConnected.value) {
    disconnectPrinter().catch(console.error)
    bluetoothConnected.value = false
  }
})
</script>

<style scoped>
.shell {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  background: #f5f5f5;
  overflow: hidden;
}

.stage {
  position: absolute;
  display: flex;
  flex-direction: column;
  padding: 10px 12px 12px;
  box-sizing: border-box;
  background: #fff;
  color: #333;
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
  color: #333;
}

.tool-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.res-hint {
  color: #999;
  font-size: 13px;
}

.tool-btn {
  padding: 6px 12px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 14px;
  color: #666;
  background: #fff;
}

.printer-connected {
  border-color: #52c41a;
  color: #52c41a;
  background: #f6ffed;
  font-weight: 600;
}

.mock-badge {
  padding: 4px 12px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.7; }
}

.panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  border: 1px solid #e0e0e0;
  border-radius: 4px;
  background: #fff;
  overflow: hidden;
}

.panel-head {
  padding: 10px 12px;
  background: #f0f0f0;
  font-size: 15px;
  font-weight: 600;
  color: #333;
  border-bottom: 1px solid #e0e0e0;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.process-panel {
  flex: 1.2;
  min-height: 0;
}

.refresh-btn {
  padding: 4px 12px;
  background: #1890ff;
  color: #fff;
  border-radius: 4px;
  font-size: 13px;
  cursor: pointer;
}

.search-row {
  padding: 10px 12px;
  border-bottom: 1px solid #f0f0f0;
}

.search-input {
  width: 100%;
  height: 38px;
  padding: 0 12px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 14px;
  box-sizing: border-box;
}

.process-body {
  flex: 1;
  overflow: hidden;
  position: relative;
}

.plan-list {
  width: 100%;
  height: 100%;
}

.plan-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
  transition: background-color 0.2s;
}

.plan-card:hover {
  background-color: #fafafa;
}

.plan-card.selected {
  background-color: #e6f7ff;
  border-left: 3px solid #1890ff;
}

.plan-main {
  flex: 1;
  min-width: 0;
}

.plan-mo-no {
  display: block;
  font-size: 15px;
  font-weight: 600;
  color: #333;
  margin-bottom: 4px;
}

.plan-product-info {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #666;
}

.plan-code {
  color: #999;
}

.plan-separator {
  color: #ccc;
}

.plan-name {
  color: #666;
}

.plan-status-area {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  margin-left: 16px;
}

.status-tag {
  padding: 2px 10px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}

.status-pending {
  background: #e6f7ff;
  color: #1890ff;
  border: 1px solid #91d5ff;
}

.status-processing {
  background: #fff7e6;
  color: #fa8c16;
  border: 1px solid #ffd591;
}

.status-completed {
  background: #f6ffed;
  color: #52c41a;
  border: 1px solid #b7eb8f;
}

.plan-qty-text {
  font-size: 12px;
  color: #999;
}

.loading-hint,
.empty-hint {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #999;
  font-size: 14px;
  padding: 40px 20px;
}

.summary-bar {
  padding: 10px 12px;
  background: #e8e8e8;
  text-align: center;
  font-size: 14px;
  font-weight: 600;
  color: #666;
  border-radius: 4px;
  margin-top: 8px;
}

.bottom-row {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 10px;
  margin-top: 8px;
}

.weigh-panel,
.print-panel {
  flex: 1;
  min-height: 0;
}

.clear-btn {
  padding: 4px 10px;
  background: #ff4d4f;
  color: #fff;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
}

.weigh-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #999;
  font-size: 14px;
  padding: 40px 20px;
}

.weigh-content {
  padding: 16px;
}

.weigh-plan-info {
  margin-bottom: 20px;
}

.weigh-mo-no {
  display: block;
  font-size: 16px;
  font-weight: 700;
  color: #333;
  margin-bottom: 4px;
}

.weigh-product {
  display: block;
  font-size: 14px;
  color: #666;
}

.weigh-input-row {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 12px;
}

.weigh-label {
  font-size: 14px;
  color: #333;
  white-space: nowrap;
  font-weight: 500;
}

.weight-input {
  flex: 1;
  height: 40px;
  padding: 0 12px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 15px;
}

.confirm-weight-btn {
  height: 40px;
  padding: 0 20px;
  background: #1890ff;
  color: #fff;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
}

.weigh-tip {
  padding: 10px;
  background: #fffbe6;
  border: 1px solid #ffe58f;
  border-radius: 4px;
  font-size: 12px;
  color: #ad8b00;
}

.print-list {
  flex: 1;
  min-height: 0;
  height: 100%;
}

.print-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #999;
  font-size: 14px;
  padding: 40px 20px;
}

.print-card {
  margin: 10px 12px;
  padding: 12px;
  background: #fafafa;
  border: 1px solid #e8e8e8;
  border-radius: 6px;
}

.print-card-header {
  margin-bottom: 6px;
}

.print-card-title {
  font-size: 15px;
  font-weight: 600;
  color: #333;
}

.print-card-code {
  margin-bottom: 8px;
  font-size: 13px;
  color: #999;
}

.print-card-info {
  display: flex;
  gap: 16px;
  margin-bottom: 10px;
  font-size: 13px;
  color: #666;
}

.info-value {
  color: #333;
  font-weight: 600;
}

.print-action {
  display: flex;
  justify-content: flex-end;
}

.print-btn {
  padding: 6px 16px;
  background: #52c41a;
  color: #fff;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.2s;
}

.print-btn:hover {
  opacity: 0.85;
}

.print-btn.printing {
  background: #999;
  cursor: not-allowed;
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
  color: #333;
}

.sheet-desc {
  display: block;
  color: #666;
  font-size: 13px;
  line-height: 1.5;
  margin-bottom: 16px;
}

.preset-item {
  height: 42px;
  border: 1px solid #d9d9d9;
  margin-bottom: 8px;
  padding: 0 12px;
  display: flex;
  align-items: center;
  border-radius: 4px;
  cursor: pointer;
}

.preset-item.active {
  border-color: #1890ff;
  background: #e6f7ff;
  font-weight: 600;
  color: #1890ff;
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
  border: 1px solid #d9d9d9;
  padding: 0 8px;
  border-radius: 4px;
}

.size-x {
  color: #999;
}

.apply-btn {
  height: 36px;
  padding: 0 12px;
  background: #1890ff;
  color: #fff;
  display: flex;
  align-items: center;
  border-radius: 4px;
  cursor: pointer;
}

.sheet-close {
  margin-top: 16px;
  height: 40px;
  border: 1px solid #d9d9d9;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  cursor: pointer;
  background: #f5f5f5;
}
</style>