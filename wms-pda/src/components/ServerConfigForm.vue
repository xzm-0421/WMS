<template>
  <view class="server-form">
    <text v-if="showTitle" class="form-title">服务器配置</text>
    <text class="hint">请输入服务器地址，如 192.168.1.100:9980</text>
    <input
      v-model="serverInput"
      class="input"
      placeholder="http://192.168.1.100:9980"
    />
    <text class="current">当前: {{ displayUrl }}</text>
    <view class="btn-row">
      <button size="mini" class="btn-test" :loading="testing" @click="testConnection">测试连接</button>
      <button size="mini" class="btn-save" type="primary" @click="save">保存</button>
      <button v-if="hasCustom" size="mini" @click="reset">恢复默认</button>
    </view>
    <view v-if="testResult" :class="['test-result', testResult.ok ? 'ok' : 'fail']">
      {{ testResult.message }}
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import {
  getBaseUrl,
  getServerDisplay,
  getServerInputValue,
  hasCustomServer,
  normalizeBaseUrl,
  resetBaseUrl,
  setBaseUrl,
} from '@/utils/server.js'

const props = defineProps({
  showTitle: { type: Boolean, default: true },
})

const emit = defineEmits(['saved'])

const serverInput = ref('')
const testing = ref(false)
const testResult = ref(null)
const hasCustom = ref(false)

const displayUrl = computed(() => getServerDisplay())

onMounted(refresh)

function refresh() {
  serverInput.value = getServerInputValue()
  hasCustom.value = hasCustomServer()
  testResult.value = null
}

function save() {
  if (!serverInput.value.trim() && !hasCustomServer()) {
    uni.showToast({ title: '请输入服务器地址', icon: 'none' })
    return
  }
  const saved = setBaseUrl(serverInput.value || '/api/v1')
  hasCustom.value = hasCustomServer()
  testResult.value = { ok: true, message: `已保存: ${getServerDisplay()}` }
  uni.showToast({ title: '服务器已更新', icon: 'success' })
  emit('saved', saved)
}

function reset() {
  resetBaseUrl()
  serverInput.value = getServerInputValue()
  hasCustom.value = false
  testResult.value = { ok: true, message: '已恢复默认配置' }
  uni.showToast({ title: '已恢复默认', icon: 'none' })
  emit('saved', getBaseUrl())
}

async function testConnection() {
  const base = serverInput.value.trim()
    ? normalizeBaseUrl(serverInput.value)
    : getBaseUrl()
  testing.value = true
  testResult.value = null
  try {
    await new Promise((resolve, reject) => {
      uni.request({
        url: base + '/auth/captcha',
        method: 'GET',
        timeout: 8000,
        success(res) {
          if (res.statusCode === 200 && res.data?.code === 200) resolve()
          else reject(new Error(res.data?.message || `HTTP ${res.statusCode}`))
        },
        fail: reject,
      })
    })
    testResult.value = { ok: true, message: '连接成功，服务器可用' }
  } catch (e) {
    testResult.value = { ok: false, message: `连接失败: ${e.message || '网络错误'}` }
  } finally {
    testing.value = false
  }
}

defineExpose({ refresh })
</script>

<style scoped>
.server-form { width: 100%; }
.form-title { font-weight: bold; font-size: 30rpx; display: block; margin-bottom: 12rpx; }
.hint { color: #94a3b8; font-size: 22rpx; display: block; margin-bottom: 12rpx; }
.input {
  border: 1px solid #e2e8f0;
  border-radius: 8rpx;
  padding: 16rpx;
  margin-bottom: 12rpx;
  font-size: 26rpx;
}
.current { color: #64748b; font-size: 24rpx; display: block; margin-bottom: 16rpx; }
.btn-row { display: flex; gap: 12rpx; flex-wrap: wrap; }
.btn-test { background: #f1f5f9; color: #475569; }
.btn-save { background: #1d4ed8; color: #fff; }
.test-result {
  margin-top: 16rpx;
  padding: 16rpx;
  border-radius: 8rpx;
  font-size: 24rpx;
}
.test-result.ok { background: #f0fdf4; color: #15803d; }
.test-result.fail { background: #fef2f2; color: #dc2626; }
</style>
