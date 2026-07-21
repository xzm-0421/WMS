/**
 * 进程内 TTL 缓存（PDA 端），用于列表/明细短缓存。
 */
const store = new Map()

export function cacheGet(key) {
  if (!key) return null
  const hit = store.get(key)
  if (!hit) return null
  if (hit.expireAt <= Date.now()) {
    store.delete(key)
    return null
  }
  return hit.value
}

export function cacheSet(key, value, ttlMs = 30000) {
  if (!key) return
  store.set(key, { value, expireAt: Date.now() + ttlMs })
  if (store.size > 80) {
    const now = Date.now()
    for (const [k, v] of store.entries()) {
      if (v.expireAt <= now) store.delete(k)
    }
  }
}

export function cacheDel(key) {
  if (key) store.delete(key)
}

export function cacheDelByPrefix(prefix) {
  if (!prefix) return
  for (const k of store.keys()) {
    if (k.startsWith(prefix)) store.delete(k)
  }
}

export default { cacheGet, cacheSet, cacheDel, cacheDelByPrefix }
