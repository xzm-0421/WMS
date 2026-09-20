import { ref } from 'vue'

/**
 * 手动编辑数量等输入框时暂停扫码框自动抢焦点。
 * App 端 document.activeElement 不可靠，且 uni-app :focus 会持续抢焦点。
 */
const scanAutoFocusPaused = ref(false)

export function pauseScanAutoFocus() {
  scanAutoFocusPaused.value = true
}

export function resumeScanAutoFocus() {
  scanAutoFocusPaused.value = false
}

export function isScanAutoFocusPaused() {
  return scanAutoFocusPaused.value
}

export function useScanAutoFocusPaused() {
  return scanAutoFocusPaused
}
