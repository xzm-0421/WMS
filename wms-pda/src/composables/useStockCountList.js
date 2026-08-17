import { ref } from 'vue'
import { listStockCountBills, resolveStockCountBarcode } from '@/api/stockCount.js'
import { parseNoticePage, PAGE_SIZE, SHOW_THROTTLE_MS, mergeNoticeRecords } from '@/utils/noticeListPaging.js'
import { cacheGet, cacheSet, cacheDel } from '@/utils/ttlCache.js'
import { consumeClearListKeyword } from '@/utils/listKeywordReset.js'

const LIST_CACHE_TTL_MS = 120000

export function useStockCountList() {
  const loading = ref(false)
  const loadingMore = ref(false)
  const keyword = ref('')
  const bills = ref([])
  const current = ref(1)
  const total = ref(0)
  const hasMore = ref(false)
  let lastShowAt = 0

  function cacheKey(kw) {
    return `stockcount-list-page1:${String(kw || '').trim().toLowerCase()}`
  }

  function applyPage(parsed, append) {
    total.value = parsed.total
    current.value = parsed.current
    hasMore.value = parsed.hasMore
    if (append) {
      bills.value = mergeNoticeRecords(bills.value, parsed.valid)
    } else {
      bills.value = parsed.valid
    }
  }

  async function loadList(kw = keyword.value, options = {}) {
    const force = options.force === true
    keyword.value = kw
    current.value = 1
    const key = cacheKey(kw)
    const cached = cacheGet(key)
    if (!force && cached?.records?.length) {
      bills.value = cached.records
      total.value = cached.total || cached.records.length
      current.value = 1
      hasMore.value = bills.value.length < total.value
      refreshInBackground(kw, key)
      return bills.value
    }
    loading.value = true
    try {
      const pageData = await listStockCountBills({
        keyword: kw || undefined,
        current: 1,
        size: PAGE_SIZE,
      })
      const parsed = parseNoticePage(pageData)
      applyPage(parsed, false)
      cacheSet(key, { records: bills.value, total: total.value }, LIST_CACHE_TTL_MS)
      lastShowAt = Date.now()
      return bills.value
    } catch (e) {
      uni.showToast({ title: e?.message || '加载失败，请检查网络', icon: 'none' })
      return bills.value
    } finally {
      loading.value = false
    }
  }

  function refreshInBackground(kw, key) {
    if (Date.now() - lastShowAt < SHOW_THROTTLE_MS) return
    lastShowAt = Date.now()
    listStockCountBills({
      keyword: kw || undefined,
      current: 1,
      size: PAGE_SIZE,
    }).then((pageData) => {
      const parsed = parseNoticePage(pageData)
      if (current.value <= 1) {
        applyPage(parsed, false)
      } else {
        bills.value = mergeNoticeRecords(parsed.valid, bills.value)
        total.value = parsed.total
        hasMore.value = bills.value.length < total.value
      }
      cacheSet(key, { records: parsed.valid, total: parsed.total }, LIST_CACHE_TTL_MS)
    }).catch(() => {})
  }

  async function loadMore() {
    if (loading.value || loadingMore.value || !hasMore.value) return bills.value
    loadingMore.value = true
    try {
      const next = current.value + 1
      const pageData = await listStockCountBills({
        keyword: keyword.value || undefined,
        current: next,
        size: PAGE_SIZE,
      })
      const parsed = parseNoticePage(pageData)
      applyPage(parsed, true)
      return bills.value
    } catch (e) {
      uni.showToast({ title: e?.message || '加载更多失败', icon: 'none' })
      return bills.value
    } finally {
      loadingMore.value = false
    }
  }

  async function loadListOnShow() {
    if (consumeClearListKeyword()) {
      cacheDel(cacheKey(keyword.value))
      cacheDel(cacheKey(''))
      return loadList('', { force: true })
    }
    const key = cacheKey(keyword.value)
    const cached = cacheGet(key)
    if (cached?.records?.length) {
      if (!bills.value.length) {
        bills.value = cached.records
        total.value = cached.total || cached.records.length
        current.value = 1
        hasMore.value = bills.value.length < total.value
      }
      if (Date.now() - lastShowAt >= SHOW_THROTTLE_MS) {
        refreshInBackground(keyword.value, key)
      }
      return bills.value
    }
    return loadList(keyword.value)
  }

  /**
   * 扫盘点二维码：解析单号后直接进作业页；解析失败则按关键字搜索。
   */
  async function searchByBarcode(barcode) {
    const raw = (barcode || '').trim()
    cacheDel(cacheKey(keyword.value))
    if (!raw) return { action: 'list', bills: await loadList('', { force: true }) }
    try {
      const res = await resolveStockCountBarcode(raw)
      const billNo = (res?.billNo || '').trim()
      if (billNo) {
        return { action: 'open', billNo }
      }
    } catch {
      // fallback search
    }
    keyword.value = raw
    return { action: 'list', bills: await loadList(raw, { force: true }) }
  }

  function statusLabel(item) {
    const s = item.scanStatus
    if (s === 'COMPLETED') return '已完成'
    if (s === 'COUNTING' || item.inProgress) return '盘点中'
    return '待盘点'
  }

  function statusClass(item) {
    const s = item.scanStatus
    if (s === 'COMPLETED') return 'done'
    if (s === 'COUNTING' || item.inProgress) return 'progress'
    return 'new'
  }

  return {
    loading,
    loadingMore,
    keyword,
    bills,
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

export default useStockCountList
