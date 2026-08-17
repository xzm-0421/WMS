<template>
  <view class="page">
    <view class="form-card">
      <text class="label">仓库编码</text>
      <input v-model="form.warehouseCode" class="input" placeholder="WH01" />

      <text v-if="moduleConfig?.partnerLabel" class="label">{{ moduleConfig.partnerLabel }}</text>
      <input
        v-if="moduleConfig?.partnerKey"
        v-model="form.partner"
        class="input"
        :placeholder="moduleConfig.partnerPlaceholder || '可选'"
      />

      <text class="label">备注</text>
      <input v-model="form.remark" class="input" placeholder="备注信息" />
    </view>

    <view class="section-header">
      <text class="section-title">物料明细</text>
      <button size="mini" type="primary" @click="scanMaterial">扫码添加</button>
    </view>

    <view v-for="(line, idx) in lines" :key="idx" class="line-card">
      <text class="name">{{ line.materialName || line.materialCode }}</text>
      <text class="code">{{ line.materialCode }} · 批次: {{ line.batchNo || '-' }}</text>
      <view class="row">
        <input v-model="line.qty" class="qty-input" type="text" inputmode="decimal" placeholder="数量" />
        <button size="mini" type="warn" @click="removeLine(idx)">删除</button>
      </view>
    </view>
    <view v-if="!lines.length" class="empty">请扫码或手动添加物料</view>

    <view class="footer">
      <button class="btn-save" @click="saveDraft">保存草稿</button>
      <button class="btn-submit" type="primary" @click="saveAndSubmit">保存并提交</button>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { createOrder, getMaterial, submitOrder } from '@/api/order.js'
import { getModuleConfig } from '@/constants/orderModules.js'
import { scanAndParse } from '@/utils/scan.js'

const moduleId = ref('')
const direction = ref('inbound')
const moduleConfig = ref(null)
const form = reactive({
  warehouseCode: 'WH01',
  partner: '',
  remark: '',
})
const lines = ref([])

onLoad((options) => {
  moduleId.value = options?.module || 'purchase'
  direction.value = options?.direction || 'inbound'
  moduleConfig.value = getModuleConfig(moduleId.value, direction.value)
  uni.setNavigationBarTitle({ title: `新建${moduleConfig.value?.label || '单据'}` })
})

async function scanMaterial() {
  try {
    const parsed = await scanAndParse('扫描物料条码')
    if (!parsed.materialCode) {
      uni.showToast({ title: '未识别物料', icon: 'none' })
      return
    }
    let materialName = ''
    try {
      const mat = await getMaterial(parsed.materialCode)
      materialName = mat.materialName || ''
    } catch {
      // 物料可能不存在，仍允许录入
    }
    lines.value.push({
      materialCode: parsed.materialCode,
      materialName,
      batchNo: parsed.batchNo || '',
      qty: '1',
    })
  } catch {
    // cancel
  }
}

function removeLine(idx) {
  lines.value.splice(idx, 1)
}

function buildPayload() {
  if (!form.warehouseCode) {
    uni.showToast({ title: '请填写仓库', icon: 'none' })
    return null
  }
  if (!lines.value.length) {
    uni.showToast({ title: '请添加物料明细', icon: 'none' })
    return null
  }
  for (const line of lines.value) {
    if (!line.materialCode || !line.qty || Number(line.qty) <= 0) {
      uni.showToast({ title: '请填写有效数量', icon: 'none' })
      return null
    }
  }
  const cfg = moduleConfig.value
  const payload = {
    orderType: cfg.orderType,
    warehouseCode: form.warehouseCode,
    planDate: new Date().toISOString().slice(0, 10),
    remark: form.remark,
    details: lines.value.map((line, i) => {
      const d = {
        lineNo: i + 1,
        materialCode: line.materialCode,
        materialName: line.materialName,
        batchNo: line.batchNo,
      }
      if (direction.value === 'outbound') {
        d.demandQty = Number(line.qty)
        d.issuedQty = 0
      } else {
        d.orderQty = Number(line.qty)
        d.receivedQty = 0
      }
      return d
    }),
  }
  if (cfg.partnerKey && form.partner) {
    payload[cfg.partnerKey] = form.partner
  }
  return payload
}

async function saveDraft() {
  const payload = buildPayload()
  if (!payload) return
  const orderNo = await createOrder(direction.value, payload)
  uni.showToast({ title: '草稿已保存', icon: 'success' })
  setTimeout(() => {
    uni.redirectTo({
      url: `/pages/order/detail?module=${moduleId.value}&direction=${direction.value}&orderNo=${orderNo}`,
    })
  }, 600)
}

async function saveAndSubmit() {
  const payload = buildPayload()
  if (!payload) return
  const orderNo = await createOrder(direction.value, payload)
  await submitOrder(direction.value, orderNo)
  uni.showToast({ title: '已提交审核', icon: 'success' })
  setTimeout(() => {
    uni.redirectTo({
      url: `/pages/order/detail?module=${moduleId.value}&direction=${direction.value}&orderNo=${orderNo}`,
    })
  }, 600)
}
</script>

<style scoped>
.page { padding: 24rpx; padding-bottom: 160rpx; }
.form-card {
  background: #fff;
  padding: 24rpx;
  border-radius: 12rpx;
  margin-bottom: 24rpx;
}
.label { display: block; font-weight: bold; font-size: 26rpx; margin: 12rpx 0 8rpx; color: #475569; }
.input {
  border: 1px solid #e2e8f0;
  border-radius: 8rpx;
  padding: 16rpx;
  margin-bottom: 8rpx;
}
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16rpx;
}
.section-title { font-weight: bold; }
.line-card {
  background: #fff;
  padding: 24rpx;
  border-radius: 12rpx;
  margin-bottom: 16rpx;
}
.name { font-weight: bold; display: block; margin-bottom: 4rpx; }
.code { color: #64748b; font-size: 24rpx; display: block; margin-bottom: 12rpx; }
.row { display: flex; align-items: center; gap: 16rpx; }
.qty-input {
  flex: 1;
  border: 1px solid #e2e8f0;
  border-radius: 8rpx;
  padding: 12rpx;
}
.empty { text-align: center; color: #94a3b8; padding: 40rpx; }
.footer {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 24rpx;
  background: #fff;
  display: flex;
  gap: 16rpx;
  box-shadow: 0 -4rpx 16rpx rgba(0,0,0,0.06);
}
.btn-save { flex: 1; background: #e2e8f0; color: #334155; }
.btn-submit { flex: 1; background: #1d4ed8; color: #fff; }
</style>
