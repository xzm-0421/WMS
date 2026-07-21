import { ref, onUnmounted } from 'vue'

/** PDA 扫码枪输入：Enter 提交 + 快速连扫自动提交 */
export function useScannerInput(onSubmit) {
  const innerValue = ref('')
  /** 保持 true，避免反复 toggle 导致与扫码枪抢焦点 */
  const focused = ref(false)

  let scanTimer = null
  let firstKeyTime = 0
  let refocusTimer = null
  let focusOnceTimer = null
  let destroyed = false

  function clearTimer() {
    if (scanTimer) {
      clearTimeout(scanTimer)
      scanTimer = null
    }
  }

  function clearAllTimers() {
    clearTimer()
    if (refocusTimer) {
      clearTimeout(refocusTimer)
      refocusTimer = null
    }
    if (focusOnceTimer) {
      clearTimeout(focusOnceTimer)
      focusOnceTimer = null
    }
  }

  /**
   * 温和聚焦：已聚焦时不操作；失焦后延迟一次恢复，并防抖
   */
  function focusInput() {
    if (destroyed || focused.value) return
    if (refocusTimer) clearTimeout(refocusTimer)
    refocusTimer = setTimeout(() => {
      refocusTimer = null
      if (!destroyed) focused.value = true
    }, 120)
  }

  /** 首次进入页面时调用，只做一次 false→true */
  function focusInputOnce() {
    if (destroyed || focused.value) return
    focused.value = false
    if (focusOnceTimer) clearTimeout(focusOnceTimer)
    focusOnceTimer = setTimeout(() => {
      focusOnceTimer = null
      if (!destroyed) focused.value = true
    }, 80)
  }

  function resetInputState() {
    innerValue.value = ''
    firstKeyTime = 0
    clearTimer()
  }

  function submitValue(raw) {
    if (destroyed) return
    const code = (raw || innerValue.value || '').replace(/[\r\n\t]/g, '').trim()
    if (!code) return
    resetInputState()
    onSubmit(code)
  }

  function onInput(e) {
    if (destroyed) return
    const val = e.detail?.value ?? innerValue.value
    innerValue.value = val

    if (/[\r\n]/.test(val)) {
      submitValue(val.replace(/[\r\n]/g, ''))
      return
    }

    const now = Date.now()
    if (!firstKeyTime) firstKeyTime = now
    const elapsed = now - firstKeyTime
    const isScannerBurst = elapsed < 400 && val.length >= 3

    clearTimer()
    scanTimer = setTimeout(() => {
      scanTimer = null
      if (destroyed) return
      if (innerValue.value.trim() && isScannerBurst) {
        submitValue(innerValue.value)
      }
      firstKeyTime = 0
    }, isScannerBurst ? 80 : 600)
  }

  function onConfirm() {
    if (destroyed) return
    clearTimer()
    firstKeyTime = 0
    submitValue(innerValue.value)
  }

  function onBlur() {
    if (destroyed) return
    clearTimer()
    focused.value = false
  }

  function onFocus() {
    if (destroyed) return
    focused.value = true
  }

  onUnmounted(() => {
    destroyed = true
    clearAllTimers()
  })

  return {
    innerValue,
    focused,
    focusInput,
    focusInputOnce,
    resetInputState,
    onInput,
    onConfirm,
    onBlur,
    onFocus,
    submitValue,
  }
}
