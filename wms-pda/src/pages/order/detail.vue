<template>
  <view class="page">
    <view v-if="order" class="header-card">
      <view class="row-top">
        <text class="order-no">{{ order.orderNo }}</text>
        <text class="status" :style="{ color: statusColor }">{{ statusLabel }}</text>
      </view>
      <text class="type-tag">{{ moduleConfig?.label }}</text>
      <text class="meta">仓库: {{ order.warehouseCode }}</text>
      <text v-if="order.supplierCode" class="meta">供应商: {{ order.supplierCode }}</text>
      <text v-if="order.productionOrderNo" class="meta">工单: {{ order.productionOrderNo }}</text>
      <text v-if="order.sourceOrderNo" class="meta">来源单: {{ order.sourceOrderNo }}</text>
      <text v-if="order.customerCode" class="meta">客户: {{ order.customerCode }}</text>
      <text v-if="order.remark" class="meta">备注: {{ order.remark }}</text>
      <text class="meta">创建: {{ order.creatorName || '-' }} · {{ order.createTime || '-' }}</text>
      <text v-if="order.auditorName" class="meta">审核: {{ order.auditorName }} · {{ order.auditTime || '-' }}</text>
    </view>

    <!-- 状态流转 -->
    <view class="flow-bar">
      <view v-for="(step, i) in flowSteps" :key="step.key" class="flow-step">
        <view :class="['dot', step.done && 'done', step.current && 'current']" />
        <text :class="['step-label', step.current && 'current']">{{ step.label }}</text>
        <view v-if="i < flowSteps.length - 1" class="line" />
      </view>
    </view>

    <text class="section-title">明细 ({{ details.length }})</text>
    <view v-for="line in details" :key="line.lineNo" class="line-card">
      <text class="name">{{ line.materialName || line.materialCode }}</text>
      <text class="code">{{ line.materialCode }}</text>
      <text v-if="direction === 'inbound'">
        计划 {{ line.orderQty }} / 已收 {{ line.receivedQty || 0 }}
        · {{ line.lineStatus || 'PENDING' }}
      </text>
      <text v-else>
        需求 {{ line.demandQty || line.orderQty }} / 已出 {{ line.issuedQty || 0 }}
        · {{ line.lineStatus || 'PENDING' }}
      </text>
    </view>

    <view class="action-bar">
      <button v-if="canSubmitStatus" size="mini" type="primary" @click="doSubmit">提交审核</button>
      <button v-if="canAuditStatus" size="mini" @click="doAudit">审核通过</button>
      <button v-if="canScanStatus" size="mini" type="warn" @click="goScan">扫码作业</button>
      <button v-if="canCancelStatus" size="mini" @click="doCancel">取消单据</button>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { getOrderDetail, submitOrder, auditOrder, cancelOrder } from '@/api/order.js'
import { getModuleConfig } from '@/constants/orderModules.js'
import {
  getStatusLabel,
  getStatusColor,
  canSubmit,
  canAudit,
  canScanInbound,
  canScanOutbound,
} from '@/constants/orderStatus.js'

const moduleId = ref('')
const direction = ref('inbound')
const orderNo = ref('')
const moduleConfig = ref(null)
const order = ref(null)
const details = ref([])

onLoad((options) => {
  moduleId.value = options?.module || 'purchase'
  direction.value = options?.direction || 'inbound'
  orderNo.value = options?.orderNo || ''
  moduleConfig.value = getModuleConfig(moduleId.value, direction.value)
  uni.setNavigationBarTitle({ title: '单据详情' })
})

onShow(() => {
  if (orderNo.value) loadDetail()
})

const statusLabel = computed(() =>
  order.value ? getStatusLabel(order.value.status, direction.value) : '',
)
const statusColor = computed(() =>
  order.value ? getStatusColor(order.value.status, direction.value) : '#64748b',
)
const canSubmitStatus = computed(() => order.value && canSubmit(order.value.status))
const canAuditStatus = computed(() => order.value && canAudit(order.value.status))
const canScanStatus = computed(() =>
  order.value
    && (direction.value === 'inbound'
      ? canScanInbound(order.value.status)
      : canScanOutbound(order.value.status)),
)
const canCancelStatus = computed(() =>
  order.value && !['COMPLETED', 'CLOSED', 'CANCELLED'].includes(order.value.status),
)

const flowSteps = computed(() => {
  const s = order.value?.status
  const isOut = direction.value === 'outbound'
  const steps = isOut
    ? [
        { key: 'DRAFT', label: '草稿' },
        { key: 'PENDING', label: '待审核' },
        { key: 'PICKING', label: '拣货' },
        { key: 'COMPLETED', label: '完成' },
      ]
    : [
        { key: 'DRAFT', label: '草稿' },
        { key: 'PENDING', label: '待审核' },
        { key: 'INBOUND', label: '入库' },
        { key: 'COMPLETED', label: '完成' },
      ]
  const orderFlow = ['DRAFT', 'PENDING', isOut ? 'PICKING' : 'INBOUND', 'OUTBOUND', 'INBOUND', 'COMPLETED']
  const idx = orderFlow.indexOf(s)
  return steps.map((step, i) => ({
    ...step,
    done: idx > i || s === 'COMPLETED',
    current: s === step.key || (s === 'OUTBOUND' && step.key === 'PICKING'),
  }))
})

async function loadDetail() {
  const data = await getOrderDetail(direction.value, orderNo.value)
  order.value = data.order || data
  details.value = data.details || []
}

async function doSubmit() {
  await submitOrder(direction.value, orderNo.value)
  uni.showToast({ title: '已提交', icon: 'success' })
  loadDetail()
}

async function doAudit() {
  await auditOrder(direction.value, orderNo.value)
  uni.showToast({ title: '审核通过', icon: 'success' })
  loadDetail()
}

async function doCancel() {
  uni.showModal({
    title: '确认取消',
    content: '确定取消此单据？',
    success: async (res) => {
      if (res.confirm) {
        await cancelOrder(direction.value, orderNo.value)
        uni.showToast({ title: '已取消', icon: 'success' })
        loadDetail()
      }
    },
  })
}

function goScan() {
  const page = direction.value === 'outbound' ? 'outbound' : 'inbound'
  uni.navigateTo({
    url: `/pages/${page}/${page}?orderNo=${orderNo.value}&module=${moduleId.value}`,
  })
}
</script>

<style scoped>
.page { padding: 24rpx; padding-bottom: 120rpx; }
.header-card {
  background: #fff;
  padding: 24rpx;
  border-radius: 12rpx;
  margin-bottom: 24rpx;
}
.row-top { display: flex; justify-content: space-between; margin-bottom: 8rpx; }
.order-no { font-weight: bold; font-size: 32rpx; }
.status { font-size: 26rpx; font-weight: 500; }
.type-tag {
  display: inline-block;
  background: #eff6ff;
  color: #1d4ed8;
  padding: 4rpx 16rpx;
  border-radius: 6rpx;
  font-size: 22rpx;
  margin-bottom: 12rpx;
}
.meta { display: block; color: #64748b; font-size: 24rpx; margin-top: 4rpx; }
.flow-bar {
  display: flex;
  justify-content: space-between;
  background: #fff;
  padding: 24rpx 16rpx;
  border-radius: 12rpx;
  margin-bottom: 24rpx;
}
.flow-step { flex: 1; text-align: center; position: relative; }
.dot {
  width: 20rpx;
  height: 20rpx;
  border-radius: 50%;
  background: #e2e8f0;
  margin: 0 auto 8rpx;
}
.dot.done { background: #22c55e; }
.dot.current { background: #1d4ed8; box-shadow: 0 0 0 6rpx rgba(29,78,216,0.2); }
.step-label { font-size: 20rpx; color: #94a3b8; }
.step-label.current { color: #1d4ed8; font-weight: bold; }
.section-title { font-weight: bold; margin-bottom: 16rpx; display: block; }
.line-card {
  background: #fff;
  padding: 24rpx;
  border-radius: 12rpx;
  margin-bottom: 16rpx;
}
.name { font-weight: bold; display: block; }
.code { color: #64748b; font-size: 24rpx; display: block; margin: 4rpx 0; }
.action-bar {
  position: fixed;
  left: 24rpx;
  right: 24rpx;
  bottom: 32rpx;
  display: flex;
  gap: 12rpx;
  flex-wrap: wrap;
}
.action-bar button { flex: 1; min-width: 140rpx; }
</style>
