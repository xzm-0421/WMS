const QUEUE_KEY = 'offline_queue'

export function getOfflineQueue() {
  return uni.getStorageSync(QUEUE_KEY) || []
}

export function enqueueOffline(item) {
  const queue = getOfflineQueue()
  queue.push({
    clientId: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    clientTime: new Date().toISOString(),
    ...item,
  })
  uni.setStorageSync(QUEUE_KEY, queue)
  return queue.length
}

/** 执行 API；失败时写入离线队列 */
export async function withOfflineFallback(operationType, orderNo, payload, apiFn) {
  try {
    return await apiFn()
  } catch (err) {
    const isNetwork = !err?.code || err?.message === '网络错误'
    if (isNetwork) {
      const count = enqueueOffline({ operationType, orderNo, payload })
      uni.showToast({ title: `已离线缓存(${count})`, icon: 'none' })
      return { offline: true }
    }
    throw err
  }
}

export default { getOfflineQueue, enqueueOffline, withOfflineFallback }
