<template>
  <view class="compact-scan" :class="{ active: focused && !disabled, disabled }" @click="onTap">
    <view class="scan-frame-icon">
      <view class="corner tl" />
      <view class="corner tr" />
      <view class="corner bl" />
      <view class="corner br" />
      <view class="scan-line" />
    </view>
    <input
      class="scan-input"
      type="text"
      :focus="focused"
      :disabled="disabled"
      :value="innerValue"
      placeholder=""
      confirm-type="done"
      :hold-keyboard="false"
      :adjust-position="false"
      :cursor-spacing="0"
      @input="onInput"
      @confirm="onConfirm"
      @blur="handleBlur"
      @focus="handleFocus"
    />
  </view>
</template>

<script setup>
import { watch, onMounted, onActivated, onUnmounted } from 'vue'
import { useScannerInput } from '@/composables/useScannerInput.js'

const props = defineProps({
  disabled: { type: Boolean, default: false },
  autoFocus: { type: Boolean, default: true },
})

const emit = defineEmits(['scan'])

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
  onConfirm,
  onBlur,
  onFocus,
} = useScannerInput((code) => {
  emit('scan', code)
})

function handleFocus() {
  onFocus()
}

function handleBlur() {
  onBlur()
}

function onTap() {
  if (!props.disabled) focusInputOnce()
}

watch(
  () => props.disabled,
  (v, oldV) => {
    if (oldV && !v && props.autoFocus) {
      safeTimeout(focusInputOnce, 200)
    }
  },
)

onMounted(() => {
  if (props.autoFocus && !mounted) {
    mounted = true
    safeTimeout(focusInputOnce, 400)
  }
})

onActivated(() => {
  if (props.autoFocus && !props.disabled) {
    safeTimeout(focusInputOnce, 300)
  }
})

onUnmounted(() => {
  clearLocalTimers()
})

defineExpose({
  focusInput: focusInputOnce,
  clear: resetInputState,
})
</script>

<style scoped>
.compact-scan {
  position: relative;
  width: 112rpx;
  height: 88rpx;
  margin: 0 auto;
  border: 2rpx solid #cbd5e1;
  border-radius: 16rpx;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
}
.compact-scan.active {
  border-color: #3b82f6;
  box-shadow: 0 0 0 4rpx rgba(59, 130, 246, 0.12);
}
.compact-scan.disabled {
  opacity: 0.55;
  background: #f1f5f9;
}

.scan-icon {
  display: none;
}

.scan-frame-icon {
  position: relative;
  width: 44rpx;
  height: 44rpx;
}
.corner {
  position: absolute;
  width: 14rpx;
  height: 14rpx;
  border-color: #3b82f6;
  border-style: solid;
}
.corner.tl { top: 0; left: 0; border-width: 3rpx 0 0 3rpx; }
.corner.tr { top: 0; right: 0; border-width: 3rpx 3rpx 0 0; }
.corner.bl { bottom: 0; left: 0; border-width: 0 0 3rpx 3rpx; }
.corner.br { bottom: 0; right: 0; border-width: 0 3rpx 3rpx 0; }
.scan-line {
  position: absolute;
  left: 8rpx;
  right: 8rpx;
  top: 50%;
  height: 2rpx;
  background: linear-gradient(90deg, transparent, #60a5fa, transparent);
  transform: translateY(-50%);
}

.scan-input {
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  height: 100%;
  opacity: 0;
  font-size: 1rpx;
  color: transparent;
  background: transparent;
  border: none;
}
</style>
