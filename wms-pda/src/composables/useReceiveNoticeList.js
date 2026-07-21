import { ref } from 'vue'
import { listReceiveNotices, resolveReceiveBarcode } from '@/api/receiveNotice.js'
import { parseNoticePage, PAGE_SIZE, SHOW_THROTTLE_MS, mergeNoticeRecords } from '@/utils/noticeListPaging.js'
import { cacheGet, cacheSet, cacheDel } from '@/utils/ttlCache.js'

const RECEIVE_LIST_CACHE_TTL_MS = 120000

export function useReceiveNoticeList() {
  const loading = ref(false)
  const loadingMore = ref(false)
  const keyword = ref('')
  const notices = ref([])
  const current = ref(1)
  const total = ref(0)
  const hasMore = ref(false)
  let lastShowAt = 0

  function cacheKey(kw) {
    return `receive-list-page1:${String(kw || '').trim().toLowerCase()}`
  }

  function applyPage(parsed, append) {
    total.value = parsed.total
    current.value = parsed.current
    hasMore.value = parsed.hasMore
    if (append) {
      notices.value = mergeNoticeRecords(notices.value, parsed.valid)
    } else {
      notices.value = parsed.valid
    }
  }

  async function loadList(kw = keyword.value, options = {}) {
    const force = options.force === true
    keyword.value = kw
    current.value = 1
    const key = cacheKey(kw)
    const cached = cacheGet(key)
    if (!force && cached?.records?.length) {
      notices.value = cached.records
      total.value = cached.total || cached.records.length
      current.value = 1
      hasMore.value = notices.value.length < total.value
      refreshInBackground(kw, key)
      return notices.value
    }
    loading.value = true
    try {
      const pageData = await listReceiveNotices({
        keyword: kw || undefined,
        current: 1,
        size: PAGE_SIZE,
      })
      const parsed = parseNoticePage(pageData)
      applyPage(parsed, false)
      cacheSet(key, { records: notices.value, total: total.value }, RECEIVE_LIST_CACHE_TTL_MS)
      lastShowAt = Date.now()
      return notices.value
    } catch (e) {
      uni.showToast({ title: e?.message || '加载失败', icon: 'none' })
      return notices.value
    } finally {
      loading.value = false
    }
  }

  function refreshInBackground(kw, key) {
    if (Date.now() - lastShowAt < SHOW_THROTTLE_MS) return
    lastShowAt = Date.now()
    listReceiveNotices({
      keyword: kw || undefined,
      current: 1,
      size: PAGE_SIZE,
    }).then((pageData) => {
      const parsed = parseNoticePage(pageData)
      // 静默刷新仅更新首页，保留已加载更多的条目
      if (current.value <= 1) {
        applyPage(parsed, false)
      } else {
        notices.value = mergeNoticeRecords(parsed.valid, notices.value)
        total.value = parsed.total
        hasMore.value = notices.value.length < total.value
      }
      cacheSet(key, { records: parsed.valid, total: parsed.total }, RECEIVE_LIST_CACHE_TTL_MS)
    }).catch(() => {})
  }

  async function loadMore() {
    if (loading.value || loadingMore.value || !hasMore.value) return notices.value
    loadingMore.value = true
    try {
      const next = current.value + 1
      const pageData = await listReceiveNotices({
        keyword: keyword.value || undefined,
        current: next,
        size: PAGE_SIZE,
      })
      const parsed = parseNoticePage(pageData)
      applyPage(parsed, true)
      return notices.value
    } catch (e) {
      uni.showToast({ title: e?.message || '加载更多失败', icon: 'none' })
      return notices.value
    } finally {
      loadingMore.value = false
    }
  }

  async function loadListOnShow() {
    const key = cacheKey(keyword.value)
    const cached = cacheGet(key)
    if (cached?.records?.length) {
      if (!notices.value.length) {
        notices.value = cached.records
        total.value = cached.total || cached.records.length
        current.value = 1
        hasMore.value = notices.value.length < total.value
      }
      if (Date.now() - lastShowAt >= SHOW_THROTTLE_MS) {
        refreshInBackground(keyword.value, key)
      }
      return notices.value
    }
    return loadList(keyword.value)
  }

  async function searchByBarcode(barcode) {
    const raw = (barcode || '').trim()
    cacheDel(cacheKey(keyword.value))
    if (!raw) return loadList('', { force: true })
    try {
      const { billNo } = await resolveReceiveBarcode(raw)
      if (billNo) {
        keyword.value = billNo
        return loadList(billNo, { force: true })
      }
    } catch {
      // fallback
    }
    keyword.value = raw
    return loadList(raw, { force: true })
  }

  function statusLabel(item) {
    const s = item.scanStatus || item.status
    if (s === 'COMPLETED') return '已完成'
    if (s === 'PARTIAL_SUBMITTED') return '部分入库'
    if (s === 'SCANNING') return '扫码中'
    if (item.inProgress) return '进行中'
    return '待收料'
  }

  function statusClass(item) {
    const s = item.scanStatus
    if (s === 'COMPLETED') return 'done'
    if (s === 'PARTIAL_SUBMITTED' || s === 'SCANNING') return 'progress'
    return 'new'
  }

  return {
    loading,
    loadingMore,
    keyword,
    notices,
    current,
    total,
    hasMore,
    loadList,
    loadMore,
    loadListOnShow,
    searchByBarcode,
    statusLabel,
    statusClass,
  }
}

export default useReceiveNoticeList
