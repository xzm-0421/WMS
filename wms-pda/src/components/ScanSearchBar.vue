<template>
  <view class="scan-search" :class="{ active: focused && !disabled, disabled }">
    <view class="icon-wrap" @click="focusInput">
      <view class="scan-frame-icon">
        <view class="corner tl" />
        <view class="corner tr" />
        <view class="corner bl" />
        <view class="corner br" />
        <view class="scan-line" />
      </view>
    </view>
    <input
      class="search-input"
      type="text"
      :focus="focused"
      :disabled="disabled"
      :value="innerValue"
      :placeholder="placeholder"
      confirm-type="search"
      :hold-keyboard="false"
      :adjust-position="false"
      @input="onInput"
      @confirm="onConfirm"
      @blur="handleBlur"
      @focus="handleFocus"
    />
    <text v-if="innerValue && !disabled" class="clear-btn" @click.stop="clear">×</text>
    <text class="search-btn" @click.stop="onConfirm">{{ actionText }}</text>
  </view>
</template>

<script setup>
import { watch, onMounted, onActivated, onUnmounted } from 'vue'
import { useScannerInput } from '@/composables/useScannerInput.js'

const props = defineProps({
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: '扫码或搜索单号/供应商' },
  disabled: { type: Boolean, default: false },
  autoFocus: { type: Boolean, default: true },
  actionText: { type: String, default: '搜索' },
})

const emit = defineEmits(['update:modelValue', 'scan', 'search'])

let mounted = false
const localTimers = []

function safeTimeout(fn, delay) {
  const id = setTimeout(() => {
    const idx = localTimers.indexOf(id)
    if (idx >= 0) localTimers.splice(idx, 1)
    fn()
  }, delay)
  localTimers.push(id)
  return id
}

function clearLocalTimers() {
  localTimers.forEach((id) => clearTimeout(id))
  localTimers.length = 0
}

const {
  innerValue,
  focused,
  focusInputOnce,
  resetInputState,
  onInput,
  onBlur,
  onFocus,
} = useScannerInput(
  (code) => {
    emit('update:modelValue', code)
    emit('scan', code)
  },
  { getDisabled: () => props.disabled },
)

watch(
  () => props.modelValue,
  (v) => {
    if (v !== innerValue.value) innerValue.value = v || ''
  },
  { immediate: true },
)

watch(innerValue, (v) => {
  emit('update:modelValue', v)
})

function handleFocus() {
  onFocus()
}

function handleBlur() {
  onBlur()
}

function focusInput() {
  if (!props.disabled) focusInputOnce()
}

/**
 * 点「搜索/打开」或扫码枪 Enter：只触发一次业务回调，避免 search+scan 双发。
 */
function onConfirm() {
  const val = (innerValue.value || '').trim()
  if (!val) return
  emit('update:modelValue', val)
  emit('scan', val)
  resetInputState()
}

function clear() {
  resetInputState()
  emit('update:modelValue', '')
  focusInputOnce()
}

watch(
  () => props.disabled,
  (v, oldV) => {
    if (oldV && !v && props.autoFocus) safeTimeout(focusInputOnce, 200)
  },
)

onMounted(() => {
  if (props.autoFocus && !mounted) {
    mounted = true
    safeTimeout(focusInputOnce, 400)
  }
})

onActivated(() => {
  if (props.autoFocus && !props.disabled) safeTimeout(focusInputOnce, 300)
})

onUnmounted(() => {
  clearLocalTimers()
})

defineExpose({
  focusInput: focusInputOnce,
  clear: () => {
    resetInputState()
    emit('update:modelValue', '')
  },
})
</script>

<style scoped>
.scan-search {
  display: flex;
  align-items: center;
  gap: 12rpx;
  background: #f8fafc;
  border: 2rpx solid #e2e8f0;
  border-radius: 14rpx;
  padding: 10rpx 14rpx;
  min-height: 80rpx;
  box-sizing: border-box;
}
.scan-search.active {
  border-color: #3b82f6;
  background: #fff;
  box-shadow: 0 0 0 4rpx rgba(59, 130, 246, 0.1);
}
.scan-search.disabled { opacity: 0.6; }

.icon-wrap {
  width: 56rpx;
  height: 56rpx;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.scan-frame-icon {
  position: relative;
  width: 40rpx;
  height: 40rpx;
}
.corner {
  position: absolute;
  width: 12rpx;
  height: 12rpx;
  border-color: #3b82f6;
  border-style: solid;
}
.corner.tl { top: 0; left: 0; border-width: 3rpx 0 0 3rpx; }
.corner.tr { top: 0; right: 0; border-width: 3rpx 3rpx 0 0; }
.corner.bl { bottom: 0; left: 0; border-width: 0 0 3rpx 3rpx; }
.corner.br { bottom: 0; right: 0; border-width: 0 3rpx 3rpx 0; }
.scan-line {
  position: absolute;
  left: 6rpx;
  right: 6rpx;
  top: 50%;
  height: 2rpx;
  background: linear-gradient(90deg, transparent, #60a5fa, transparent);
  transform: translateY(-50%);
}

.search-input {
  flex: 1;
  height: 64rpx;
  font-size: 28rpx;
  color: #0f172a;
  background: transparent;
  border: none;
  min-width: 0;
}
.search-input::placeholder { color: #94a3b8; }

.clear-btn {
  width: 40rpx;
  height: 40rpx;
  line-height: 38rpx;
  text-align: center;
  font-size: 32rpx;
  color: #94a3b8;
  flex-shrink: 0;
}

.search-btn {
  flex-shrink: 0;
  font-size: 26rpx;
  color: #1d4ed8;
  font-weight: 600;
  padding: 8rpx 4rpx;
}
</style>
