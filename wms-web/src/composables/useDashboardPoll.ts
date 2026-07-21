import { onBeforeUnmount, onMounted } from 'vue'

/** 看板定时刷新，默认 30 秒 */
export function useDashboardPoll(loadFn: () => void | Promise<void>, intervalMs = 30000) {
  let timer: ReturnType<typeof setInterval> | null = null

  onMounted(() => {
    loadFn()
    timer = setInterval(() => loadFn(), intervalMs)
  })

  onBeforeUnmount(() => {
    if (timer) clearInterval(timer)
  })
}
