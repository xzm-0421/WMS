import { ref, onUnmounted } from 'vue'

/**
 * PDA 扫码枪输入：Enter 提交 + 停顿自动提交。
 * 支持全局键盘楔入：输入框未聚焦时也能收到扫码并写入/提交。
 * 注意：单据二维码（JSON/长串）往往超过 400ms，不能再用「短连扫窗口」限制。
 *
 * @param {(code: string) => void} onSubmit
 * @param {{ getDisabled?: () => boolean, globalCapture?: boolean }} [options]
 */
export function useScannerInput(onSubmit, options = {}) {
  const getDisabled = typeof options.getDisabled === 'function' ? options.getDisabled : () => false
  const globalCapture = options.globalCapture !== false

  const innerValue = ref('')
  /** 保持 true，避免反复 toggle 导致与扫码枪抢焦点 */
  const focused = ref(false)

  let scanTimer = null
  let firstKeyTime = 0
  let refocusTimer = null
  let focusOnceTimer = null
  let destroyed = false
  /** 全局楔入缓冲（失焦时用） */
  let wedgeBuffer = ''
  let wedgeFirstKeyTime = 0
  let wedgeTimer = null

  function clearTimer() {
    if (scanTimer) {
      clearTimeout(scanTimer)
      scanTimer = null
    }
  }

  function clearWedgeTimer() {
    if (wedgeTimer) {
      clearTimeout(wedgeTimer)
      wedgeTimer = null
    }
  }

  function clearAllTimers() {
    clearTimer()
    clearWedgeTimer()
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
    wedgeBuffer = ''
    wedgeFirstKeyTime = 0
    clearTimer()
    clearWedgeTimer()
  }

  function submitValue(raw) {
    if (destroyed) return
    const code = (raw || innerValue.value || '').replace(/[\r\n\t]/g, '').trim()
    if (!code) return
    resetInputState()
    onSubmit(code)
  }

  function shouldAutoSubmit(text, elapsedMs) {
    if (!text || text.length < 3) return false
    // JSON / URL 单据码：停顿即提交
    const t = text.trim()
    if ((t.startsWith('{') && t.includes('}')) || /billno|fbillno|formid/i.test(t)) {
      return true
    }
    if (/^https?:\/\//i.test(t) || t.includes('://')) {
      return true
    }
    // 扫码枪：总时长可超过 400ms，但平均每字符很快；或内容已足够长
    const avg = text.length > 0 ? elapsedMs / text.length : elapsedMs
    if (avg <= 100 && text.length >= 3) return true
    if (text.length >= 8 && elapsedMs <= 3000) return true
    return false
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

    clearTimer()
    // 统一用短防抖：最后一字符后停顿即判定扫码结束（兼容长短码）
    scanTimer = setTimeout(() => {
      scanTimer = null
      if (destroyed) return
      const text = (innerValue.value || '').trim()
      const totalElapsed = firstKeyTime ? Date.now() - firstKeyTime : 0
      if (text && shouldAutoSubmit(text, totalElapsed || elapsed)) {
        submitValue(innerValue.value)
      }
      firstKeyTime = 0
    }, 200)
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
    // 失焦后温和回焦，保证下次扫码无需再点输入框；数量等其它输入框聚焦时不抢
    if (refocusTimer) clearTimeout(refocusTimer)
    refocusTimer = setTimeout(() => {
      refocusTimer = null
      if (destroyed || getDisabled() || focused.value) return
      if (isOtherEditableActive()) return
      focused.value = false
      if (focusOnceTimer) clearTimeout(focusOnceTimer)
      focusOnceTimer = setTimeout(() => {
        focusOnceTimer = null
        if (!destroyed && !getDisabled() && !isOtherEditableActive()) {
          focused.value = true
        }
      }, 40)
    }, 280)
  }

  function onFocus() {
    if (destroyed) return
    focused.value = true
  }

  function isScanInputEl(el) {
    if (!el) return false
    const cls = el.className || ''
    const classStr = typeof cls === 'string' ? cls : String(cls)
    return classStr.includes('search-input') || classStr.includes('scan-input')
  }

  /** 其它可编辑框（数量等）获得焦点时，不抢扫码枪按键 */
  function isOtherEditableActive() {
    if (typeof document === 'undefined') return false
    const el = document.activeElement
    if (!el) return false
    const tag = (el.tagName || '').toUpperCase()
    if (tag === 'TEXTAREA') return true
    if (tag === 'INPUT') {
      if (isScanInputEl(el)) return false
      const type = (el.getAttribute?.('type') || el.type || 'text').toLowerCase()
      if (['button', 'checkbox', 'radio', 'submit', 'reset', 'file', 'hidden', 'range'].includes(type)) {
        return false
      }
      return true
    }
    if (el.isContentEditable) return true
    return false
  }

  function scheduleWedgeSubmit() {
    clearWedgeTimer()
    wedgeTimer = setTimeout(() => {
      wedgeTimer = null
      if (destroyed || getDisabled()) return
      const text = (wedgeBuffer || '').trim()
      const elapsed = wedgeFirstKeyTime ? Date.now() - wedgeFirstKeyTime : 0
      if (text && shouldAutoSubmit(text, elapsed)) {
        innerValue.value = text
        submitValue(text)
      }
      wedgeFirstKeyTime = 0
    }, 200)
  }

  function onGlobalKeydown(e) {
    if (destroyed || getDisabled() || !globalCapture) return
    // 扫码框已聚焦：交给 input/@confirm，避免重复提交
    if (focused.value) return
    if (isOtherEditableActive()) return

    const key = e.key
    if (!key) return

    if (key === 'Enter') {
      e.preventDefault?.()
      e.stopPropagation?.()
      clearWedgeTimer()
      const text = (wedgeBuffer || innerValue.value || '').trim()
      wedgeBuffer = ''
      wedgeFirstKeyTime = 0
      if (text) {
        innerValue.value = text
        submitValue(text)
      }
      return
    }

    if (key === 'Escape' || key === 'Tab' || key === 'Backspace' || key === 'Delete') {
      if (key === 'Backspace' && wedgeBuffer) {
        e.preventDefault?.()
        wedgeBuffer = wedgeBuffer.slice(0, -1)
        innerValue.value = wedgeBuffer
      }
      return
    }

    // 可打印单字符（扫码枪楔入）
    if (key.length === 1 && !e.ctrlKey && !e.altKey && !e.metaKey) {
      e.preventDefault?.()
      e.stopPropagation?.()
      const now = Date.now()
      if (!wedgeFirstKeyTime) wedgeFirstKeyTime = now
      wedgeBuffer += key
      innerValue.value = wedgeBuffer
      scheduleWedgeSubmit()
    }
  }

  if (globalCapture && typeof window !== 'undefined' && typeof window.addEventListener === 'function') {
    window.addEventListener('keydown', onGlobalKeydown, true)
  }

  onUnmounted(() => {
    destroyed = true
    clearAllTimers()
    if (typeof window !== 'undefined' && typeof window.removeEventListener === 'function') {
      window.removeEventListener('keydown', onGlobalKeydown, true)
    }
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
