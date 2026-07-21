const PAGE_SIZE = 50
const LIST_CACHE_TTL_MS = 20000
const DETAIL_CACHE_TTL_MS = 25000
const SHOW_THROTTLE_MS = 2500

export function mergeNoticeRecords(existing, incoming) {
  const map = new Map()
  for (const item of existing || []) {
    const no = String(item?.billNo || '').trim()
    if (no) map.set(no, item)
  }
  for (const item of incoming || []) {
    const no = String(item?.billNo || '').trim()
    if (no) map.set(no, item)
  }
  return sortNoticeRecords(Array.from(map.values()))
}

export function sortNoticeRecords(records) {
  return [...(records || [])].sort((a, b) => {
    const dateA = String(a?.billDate || '')
    const dateB = String(b?.billDate || '')
    if (dateA !== dateB) return dateB.localeCompare(dateA)
    return String(b?.billNo || '').localeCompare(String(a?.billNo || ''))
  })
}

export function parseNoticePage(page) {
  const records = Array.isArray(page?.records) ? page.records : (Array.isArray(page) ? page : [])
  const valid = sortNoticeRecords(
    records.filter((item) => item && String(item.billNo || '').trim()),
  )
  const total = Number(page?.total ?? valid.length)
  const current = Number(page?.current ?? 1)
  const size = Number(page?.size ?? PAGE_SIZE)
  const hasMore = valid.length > 0 && current * size < total
  return { valid, total, current, size, hasMore }
}

export { PAGE_SIZE, LIST_CACHE_TTL_MS, DETAIL_CACHE_TTL_MS, SHOW_THROTTLE_MS }
