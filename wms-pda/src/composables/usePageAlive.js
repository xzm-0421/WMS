import { ref } from 'vue'
import { onUnload } from '@dcloudio/uni-app'

/**
 * 页面存活守卫：避免页面销毁后 setTimeout / 异步回调仍操作已卸载实例
 */
export function usePageAlive() {
  const alive = ref(true)
  const timers = []

  onUnload(() => {
    alive.value = false
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
    schedule(() => scanInputRef.value?.focusInput?.(), delay)
  }

  return { alive, schedule, refocusScanInput }
}

export default usePageAlive
