import { ref } from 'vue'
import { onShow, onUnload } from '@dcloudio/uni-app'
import { resumeScanAutoFocus } from '@/utils/scanFocusGuard.js'

/**
 * 页面存活守卫：避免页面销毁后 setTimeout / 异步回调仍操作已卸载实例。
 * 每次 onShow 恢复扫码焦点许可，避免明细页 pause 后返回列表扫码全无反应。
 */
export function usePageAlive() {
  const alive = ref(true)
  const timers = []

  onShow(() => {
    resumeScanAutoFocus()
  })

  onUnload(() => {
    alive.value = false
    resumeScanAutoFocus()
    timers.forEach((id) => clearTimeout(id))
    timers.length = 0
  })

  function schedule(fn, delay = 300) {
    const id = setTimeout(() => {
      const idx = timers.indexOf(id)
      if (idx >= 0) timers.splice(idx, 1)
      if (!alive.value) return
      fn()
    }, delay)
    timers.push(id)
    return id
  }

  function refocusScanInput(scanInputRef, delay = 300) {
    schedule(() => {
      resumeScanAutoFocus()
      scanInputRef.value?.focusInput?.()
    }, delay)
  }

  return { alive, schedule, refocusScanInput }
}

export default usePageAlive
