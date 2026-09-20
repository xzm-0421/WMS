import { ref, onUnmounted } from 'vue'
import { onShow, onHide } from '@dcloudio/uni-app'
import {
  isScanAutoFocusPaused,
  resumeScanAutoFocus,
} from '@/utils/scanFocusGuard.js'
import {
  bindPlusKey,
  bindAndroidScanBroadcast,
  keyCodeToChar,
} from '@/utils/pdaHardwareScan.js'

let scannerSeq = 0
let activeScannerSeq = 0

/**
 * PDA 扫码枪输入：Enter 提交 + 停顿自动提交。
 *
 * 单据列表（getBillScan=true）：停顿后有内容即打开；并监听 plus.key / 厂商广播，
 * 避免「设备已扫到码、页面无反应」。
 *
 * @param {(code: string) => void} onSubmit
 * @param {{ getDisabled?: () => boolean, globalCapture?: boolean, getBillScan?: () => boolean, billScan?: boolean }} [options]
 */
export function useScannerInput(onSubmit, options = {}) {
  const getDisabled = typeof options.getDisabled === 'function' ? options.getDisabled : () => false
  const globalCapture = options.globalCapture !== false
  const isBillScan = () => {
    if (typeof options.getBillScan === 'function') return !!options.getBillScan()
    return !!options.billScan
  }

  const innerValue = ref('')
  const focused = ref(false)

  let scanTimer = null
  let firstKeyTime = 0
  let refocusTimer = null
  let focusOnceTimer = null
  let destroyed = false
  let wedgeBuffer = ''
  let wedgeFirstKeyTime = 0
  let wedgeTimer = null
  let lastInputAt = 0
  let pageActive = true
  const myScannerSeq = ++scannerSeq
  activeScannerSeq = myScannerSeq
  let unbindPlus = () => {}
  let unbindBroadcast = () => {}

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

  function canAutoFocusScan() {
    return !destroyed && !getDisabled() && !isScanAutoFocusPaused() && !isOtherEditableActive()
  }

  function focusInput() {
    if (!canAutoFocusScan()) return
    focusInputOnce()
  }

  /**
   * 每次进入页面都必须 false→true 拨一次 :focus。
   * uni-app 的 :focus 只有变化时才会真正聚焦；已是 true 时不拨，扫码枪打不到框。
   */
  function focusInputOnce() {
    if (destroyed || getDisabled() || isScanAutoFocusPaused()) return
    if (isOtherEditableActive()) return
    if (refocusTimer) {
      clearTimeout(refocusTimer)
      refocusTimer = null
    }
    if (focusOnceTimer) {
      clearTimeout(focusOnceTimer)
      focusOnceTimer = null
    }
    focused.value = false
    focusOnceTimer = setTimeout(() => {
      focusOnceTimer = null
      if (canAutoFocusScan()) focused.value = true
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

  function looksLikeBillNo(text) {
    const t = String(text || '').trim()
    if (t.length < 6 || t.length > 64) return false
    if (/\s/.test(t)) return false
    if (/^[A-Za-z]{1,12}\d{4,}[A-Za-z0-9\-_]*$/.test(t)) return true
    if (/^[A-Za-z0-9][A-Za-z0-9\-_]{5,63}$/.test(t) && /\d/.test(t)) return true
    return false
  }

  function shouldAutoSubmit(text, elapsedMs) {
    if (!text || text.length < 3) return false
    const t = text.trim()
    if (isBillScan() && t.length >= 4) return true
    if ((t.startsWith('{') && t.includes('}')) || /billno|fbillno|formid/i.test(t)) {
      return true
    }
    if (/^https?:\/\//i.test(t) || t.includes('://')) {
      return true
    }
    if (looksLikeBillNo(t)) {
      return true
    }
    const avg = text.length > 0 ? elapsedMs / text.length : elapsedMs
    if (avg <= 100 && text.length >= 3) return true
    if (text.length >= 8 && elapsedMs <= 3000) return true
    return false
  }

  function idleDelayMs(text) {
    const len = String(text || '').length
    if (isBillScan()) return Math.min(900, Math.max(350, 280 + len * 3))
    return 200
  }

  function inputIsLive() {
    return Date.now() - lastInputAt < 120
  }

  function onInput(e) {
    if (destroyed) return
    lastInputAt = Date.now()
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
    const delay = idleDelayMs(val)
    scanTimer = setTimeout(() => {
      scanTimer = null
      if (destroyed) return
      const text = (innerValue.value || '').trim()
      const totalElapsed = firstKeyTime ? Date.now() - firstKeyTime : 0
      if (text && shouldAutoSubmit(text, totalElapsed || elapsed)) {
        submitValue(innerValue.value)
      }
      firstKeyTime = 0
    }, delay)
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
    if (refocusTimer) clearTimeout(refocusTimer)
    refocusTimer = setTimeout(() => {
      refocusTimer = null
      if (!canAutoFocusScan() || focused.value) return
      focusInputOnce()
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
      || classStr.includes('compact-scan') || classStr.includes('scan-search')
  }

  function isOtherEditableActive() {
    if (typeof document === 'undefined') return false
    const el = document.activeElement
    if (!el) return false
    const tag = (el.tagName || '').toUpperCase()
    if (tag === 'TEXTAREA' || tag === 'UNI-TEXTAREA') return true
    if (tag === 'INPUT' || tag === 'UNI-INPUT') {
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

  function pushWedgeChar(ch) {
    const now = Date.now()
    if (!wedgeFirstKeyTime) wedgeFirstKeyTime = now
    wedgeBuffer += ch
    innerValue.value = wedgeBuffer
    scheduleWedgeSubmit()
  }

  function scheduleWedgeSubmit() {
    clearWedgeTimer()
    const delay = idleDelayMs(wedgeBuffer)
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
    }, delay)
  }

  function isCurrentScanner() {
    return pageActive && activeScannerSeq === myScannerSeq
  }

  function handleScanKey(key, evt) {
    if (destroyed || !isCurrentScanner() || getDisabled() || !globalCapture) return
    if (isScanAutoFocusPaused() || isOtherEditableActive()) return
    if (!key) return

    if (key === 'Enter') {
      evt?.preventDefault?.()
      evt?.stopPropagation?.()
      clearTimer()
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

    if (inputIsLive()) return

    if (key === 'Escape' || key === 'Tab' || key === 'Backspace' || key === 'Delete') {
      if (key === 'Backspace' && wedgeBuffer) {
        evt?.preventDefault?.()
        wedgeBuffer = wedgeBuffer.slice(0, -1)
        innerValue.value = wedgeBuffer
      }
      return
    }

    if (key.length === 1) {
      evt?.preventDefault?.()
      evt?.stopPropagation?.()
      pushWedgeChar(key)
    }
  }

  function onGlobalKeydown(e) {
    if (!e?.key) return
    if (e.ctrlKey || e.altKey || e.metaKey) return
    handleScanKey(e.key, e)
  }

  function onPlusKeydown(e) {
    if (inputIsLive()) return
    const mapped = keyCodeToChar(e?.keyCode)
    if (!mapped) return
    handleScanKey(mapped, null)
  }

  function onBroadcastCode(code) {
    if (destroyed || !isCurrentScanner() || getDisabled()) return
    if (isScanAutoFocusPaused() || isOtherEditableActive()) return
    submitValue(code)
  }

  onShow(() => {
    pageActive = true
    activeScannerSeq = myScannerSeq
  })
  onHide(() => {
    pageActive = false
    wedgeBuffer = ''
    wedgeFirstKeyTime = 0
    clearWedgeTimer()
  })

  if (globalCapture && typeof window !== 'undefined' && typeof window.addEventListener === 'function') {
    window.addEventListener('keydown', onGlobalKeydown, true)
  }
  unbindPlus = bindPlusKey(onPlusKeydown)
  unbindBroadcast = bindAndroidScanBroadcast(onBroadcastCode)

  onUnmounted(() => {
    destroyed = true
    clearAllTimers()
    unbindPlus()
    unbindBroadcast()
    if (typeof window !== 'undefined' && typeof window.removeEventListener === 'function') {
      window.removeEventListener('keydown', onGlobalKeydown, true)
    }
  })

  return {
    innerValue,
    focused,
    wantFocus: focused,
    focusInput,
    focusInputOnce,
    resetInputState,
    onInput,
    onConfirm,
    onBlur,
    onFocus,
    submitValue,
    resumeScanAutoFocus,
  }
}
