<template>
  <view class="scan-bar" :class="{ disabled }">
    <view class="scan-frame" :class="{ active: focused && !disabled, disabled }">
      <view class="scan-icon-wrap">
        <text class="scan-icon">⌁</text>
      </view>
      <input
        ref="inputRef"
        v-model="innerValue"
        class="scan-input"
        type="text"
        :focus="nativeFocus"
        :disabled="disabled"
        :placeholder="placeholder"
        confirm-type="done"
        :hold-keyboard="false"
        :adjust-position="false"
        :cursor-spacing="0"
        @input="onInput"
        @confirm="onConfirm"
        @blur="handleBlur"
        @focus="handleFocus"
      />
      <view v-if="innerValue && !disabled" class="clear-btn" @click.stop="clearInput">×</view>
    </view>
    <text class="status-hint">{{ statusText }}</text>
  </view>
</template>

<script setup>
import { ref, computed, watch, onMounted, onActivated, onUnmounted } from 'vue'
import { useScannerInput } from '@/composables/useScannerInput.js'
import { resumeScanAutoFocus, useScanAutoFocusPaused } from '@/utils/scanFocusGuard.js'

const props = defineProps({
  placeholder: { type: String, default: '扫描条码自动录入' },
  disabled: { type: Boolean, default: false },
  autoFocus: { type: Boolean, default: true },
})

const emit = defineEmits(['scan'])

const ready = ref(false)
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
}, { getDisabled: () => props.disabled })

const scanPaused = useScanAutoFocusPaused()
const nativeFocus = computed(() => !!(focused.value && !props.disabled && !scanPaused.value))

const statusText = computed(() => {
  if (props.disabled) return '处理中，请稍候...'
  if (ready.value) return '扫码枪已就绪，扫描后自动录入'
  return '无需点击输入框，直接扫码即可'
})

function handleFocus() {
  resumeScanAutoFocus()
  onFocus()
  ready.value = true
}

function handleBlur() {
  onBlur()
  ready.value = false
}

function clearInput() {
  resumeScanAutoFocus()
  resetInputState()
  focusInputOnce()
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
  resumeScanAutoFocus()
  if (props.autoFocus && !mounted) {
    mounted = true
    safeTimeout(() => {
      if (!scanPaused.value) focusInputOnce()
    }, 400)
  }
})

onActivated(() => {
  resumeScanAutoFocus()
  if (props.autoFocus && !props.disabled && !scanPaused.value) {
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
.scan-bar { width: 100%; }
.scan-bar.disabled { opacity: 0.85; }

.scan-frame {
  display: flex;
  align-items: center;
  gap: 12rpx;
  background: #0f172a;
  border: 3rpx solid #334155;
  border-radius: 16rpx;
  padding: 8rpx 16rpx;
  min-height: 96rpx;
}
.scan-frame.active {
  border-color: #3b82f6;
  box-shadow: 0 0 0 4rpx rgba(59, 130, 246, 0.25);
}
.scan-frame.disabled {
  background: #1e293b;
  border-color: #475569;
}

.scan-icon-wrap {
  width: 64rpx;
  height: 64rpx;
  border-radius: 12rpx;
  background: rgba(59, 130, 246, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.scan-icon {
  font-size: 36rpx;
  color: #60a5fa;
  font-weight: bold;
}

.scan-input {
  flex: 1;
  height: 72rpx;
  font-size: 32rpx;
  color: #f8fafc;
  background: transparent;
  border: none;
}
.scan-input::placeholder {
  color: #64748b;
}

.clear-btn {
  width: 48rpx;
  height: 48rpx;
  line-height: 44rpx;
  text-align: center;
  font-size: 36rpx;
  color: #94a3b8;
  flex-shrink: 0;
}

.status-hint {
  display: block;
  margin-top: 12rpx;
  font-size: 22rpx;
  color: #64748b;
  text-align: center;
}
</style>
