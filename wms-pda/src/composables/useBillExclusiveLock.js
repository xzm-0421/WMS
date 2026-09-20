import { onHide, onShow, onUnload } from '@dcloudio/uni-app'

/**
 * PDA 单据排他锁：进入后心跳续租，离开页面释放。
 * 后端超时 3 分钟未续租可被他人抢占。
 */
export function isBillLockedError(e) {
  return (e?.data?.errorType || e?.errorType) === 'BILL_LOCKED'
}

export function useBillExclusiveLock({ heartbeat, release, intervalMs = 60000 } = {}) {
  let timer = null
  let active = false
  let releasing = false

  function stopTimer() {
    if (timer != null) {
      clearInterval(timer)
      timer = null
    }
  }

  async function ping() {
    if (!active || typeof heartbeat !== 'function') return true
    try {
      await heartbeat()
      return true
    } catch (e) {
      if (isBillLockedError(e)) {
        handleLost(e)
      }
      return false
    }
  }

  function handleLost(e) {
    stop()
    const msg = e?.message || e?.data?.message || '单据正被其他人操作'
    uni.showToast({ title: msg, icon: 'none', duration: 2500 })
    setTimeout(() => {
      uni.navigateBack({ fail: () => {} })
    }, 400)
  }

  function start() {
    active = true
    stopTimer()
    timer = setInterval(() => {
      ping()
    }, intervalMs)
  }

  function stop() {
    active = false
    stopTimer()
  }

  async function releaseLock() {
    if (releasing) return
    releasing = true
    stop()
    try {
      if (typeof release === 'function') {
        await release()
      }
    } catch {
      // 离开页释放失败忽略（依赖服务端 TTL）
    } finally {
      releasing = false
    }
  }

  onShow(() => {
    if (active) {
      ping()
    }
  })

  // 切到后台不释放，避免短暂熄屏丢锁；依赖心跳 + TTL
  onHide(() => {})

  onUnload(() => {
    releaseLock()
  })

  return {
    start,
    stop,
    releaseLock,
    ping,
    isBillLockedError,
  }
}

export default useBillExclusiveLock
