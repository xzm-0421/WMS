<template>
  <view class="page">
    <ScanInput
      v-model="scanCode"
      placeholder="扫描拣配发料单二维码"
      @scan="onScan"
    />
    <view v-if="issue" class="card">
      <text class="title">发料单 {{ issue.issueNo }}</text>
      <text>仓库：{{ issue.warehouseCode }}</text>
      <text>状态：{{ issue.status }}</text>
      <text>交接区：{{ issue.handoverArea || '-' }}</text>
    </view>
    <view v-if="lines.length" class="card">
      <text class="subtitle">明细</text>
      <view v-for="line in lines" :key="line.lineNo" class="line">
        <text>{{ line.materialCode }} · 应拣 {{ line.pickQty }} / 已拣 {{ line.pickedQty || 0 }}</text>
        <text class="loc">库位 {{ line.sourceLocation || '-' }}</text>
      </view>
    </view>
    <button v-if="issue" class="btn primary" @click="confirmPickup">确认领料</button>
    <view v-if="lastPickup" class="tip">已生成领料单：{{ lastPickup }}</view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import ScanInput from '@/components/ScanInput.vue'
import { confirmMaterialPickup, getPickIssueDetail } from '@/api/picking.js'

const scanCode = ref('')
const issue = ref(null)
const lines = ref([])
const lastPickup = ref('')

function parseIssueNo(code) {
  const c = (code || '').trim()
  if (c.startsWith('PI:')) return c.slice(3)
  if (c.startsWith('PI')) return c
  return c
}

async function onScan(code) {
  const issueNo = parseIssueNo(code)
  const data = await getPickIssueDetail(issueNo)
  issue.value = data.issue
  lines.value = data.lines || []
}

async function confirmPickup() {
  if (!issue.value) return
  const pickupNo = await confirmMaterialPickup(issue.value.issueNo, 'PDA操作员')
  lastPickup.value = pickupNo
  uni.showToast({ title: '领料确认成功', icon: 'success' })
}
</script>

<style scoped>
.page { padding: 24rpx; }
.card { background: #fff; border-radius: 12rpx; padding: 24rpx; margin-top: 24rpx; }
.title { font-size: 32rpx; font-weight: 600; display: block; margin-bottom: 12rpx; }
.subtitle { font-weight: 600; display: block; margin-bottom: 8rpx; }
.line { padding: 8rpx 0; border-bottom: 1px solid #f1f5f9; }
.loc { color: #64748b; font-size: 24rpx; margin-left: 16rpx; }
.btn { margin-top: 32rpx; }
.primary { background: #1d4ed8; color: #fff; }
.tip { margin-top: 24rpx; color: #16a34a; font-size: 26rpx; }
</style>
