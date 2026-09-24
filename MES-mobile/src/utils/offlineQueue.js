import config from './config.js'

const QUEUE_KEY = 'mes_mobile_offline_queue'

export function getQueue() {
  return uni.getStorageSync(QUEUE_KEY) || []
}

export function getQueueCount() {
  return getQueue().length
}

export function enqueue(item) {
  const queue = getQueue()
  queue.push(item)
  uni.setStorageSync(QUEUE_KEY, queue)
  return queue.length
}

export function removeByClientIds(clientIds) {
  const set = new Set(clientIds)
  const next = getQueue().filter((item) => !set.has(item.clientReportNo))
  uni.setStorageSync(QUEUE_KEY, next)
  return next.length
}

export function clearQueue() {
  uni.removeStorageSync(QUEUE_KEY)
}

export function genClientReportNo() {
  return `MB-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

/** 判断是否为网络类错误（用于离线兜底） */
export function isNetworkError(err) {
  if (!err) return true
  if (!err.code) return true
  return err.message === '网络错误'
}

/** 批量补传本地队列；成功项从队列移除 */
export async function flushQueue(syncFn) {
  const queue = getQueue()
  if (!queue.length) {
    return { totalSynced: 0, successCount: 0, failCount: 0, results: [] }
  }
  const items = queue.map((item) => ({ clientId: item.clientReportNo, ...item }))
  const res = await syncFn(items, config.deviceNo)
  const okIds = (res?.results || []).filter((r) => r.success).map((r) => r.clientId)
  if (okIds.length) {
    removeByClientIds(okIds)
  }
  return res
}

export default { getQueue, getQueueCount, enqueue, removeByClientIds, clearQueue, genClientReportNo, isNetworkError, flushQueue }
