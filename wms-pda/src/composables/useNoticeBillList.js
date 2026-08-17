import { ref, computed } from 'vue'
import { listNoticeBills, resolveNoticeBarcode } from '@/api/noticeBill.js'
import { getNoticeBillType } from '@/constants/noticeBillTypes.js'
import { parseNoticePage, PAGE_SIZE, LIST_CACHE_TTL_MS, SHOW_THROTTLE_MS } from '@/utils/noticeListPaging.js'
import { cacheGet, cacheSet, cacheDelByPrefix } from '@/utils/ttlCache.js'
import { consumeClearListKeyword } from '@/utils/listKeywordReset.js'

export function useNoticeBillList(billTypeRef) {
  const loading = ref(false)
  const keyword = ref('')
  const notices = ref([])
  let lastShowAt = 0

  const typeConfig = computed(() => getNoticeBillType(billTypeRef.value))

  function listCacheKey(kw) {
    return `notice-list:${billTypeRef.value}:${String(kw || '').trim().toLowerCase()}`
  }

  async function loadList(kw = keyword.value, options = {}) {
    if (!billTypeRef.value) return []
    const force = options.force === true
    keyword.value = kw
    const cacheKey = listCacheKey(kw)
    if (!force) {
      const cached = cacheGet(cacheKey)
      if (cached) {
        notices.value = cached
        return notices.value
      }
    }
    loading.value = true
    try {
      const pageData = await listNoticeBills(billTypeRef.value, {
        keyword: kw || undefined,
        current: 1,
        size: PAGE_SIZE,
      })
      const parsed = parseNoticePage(pageData)
      notices.value = parsed.valid
      cacheSet(cacheKey, notices.value, LIST_CACHE_TTL_MS)
      lastShowAt = Date.now()
      return notices.value
    } catch (e) {
      uni.showToast({ title: e?.message || '加载失败', icon: 'none' })
      return notices.value
    } finally {
      loading.value = false
    }
  }

  /** onShow：短时间内重复进入不发请求；提交返回则清空单号并刷新全量列表 */
  async function loadListOnShow() {
    if (consumeClearListKeyword()) {
      invalidateListCache()
      return loadList('', { force: true })
    }
    const now = Date.now()
    if (now - lastShowAt < SHOW_THROTTLE_MS && notices.value.length) {
      return notices.value
    }
    return loadList(keyword.value)
  }

  function invalidateListCache() {
    cacheDelByPrefix(`notice-list:${billTypeRef.value}:`)
  }

  async function searchByBarcode(barcode) {
    const raw = (barcode || '').trim()
    if (!raw) return loadList('', { force: true })
    try {
      const { billNo } = await resolveNoticeBarcode(billTypeRef.value, raw)
      if (billNo) {
        keyword.value = billNo
        return loadList(billNo, { force: true })
      }
    } catch {
      // fallback local filter
    }
    keyword.value = raw
    return loadList(raw, { force: true })
  }

  function statusLabel(item) {
    const s = item.scanStatus || item.status
    const inbound = typeConfig.value.direction === 'INBOUND'
    if (s === 'COMPLETED' || s === 'PARTIAL_SUBMITTED') return inbound ? '已完成' : '已出完'
    if (s === 'SCANNING') return '扫码中'
    if (s === 'NEW' || !s) return inbound ? '待收料' : '待出库'
    if (item.inProgress) return '进行中'
    return inbound ? '待收料' : '待出库'
  }

  function statusClass(item) {
    const s = item.scanStatus
    if (s === 'COMPLETED' || s === 'PARTIAL_SUBMITTED') return 'done'
    if (s === 'SCANNING') return 'progress'
    return 'new'
  }

  return {
    loading,
    keyword,
    notices,
    typeConfig,
    loadList,
    loadListOnShow,
    invalidateListCache,
    searchByBarcode,
    statusLabel,
    statusClass,
  }
}

export default useNoticeBillList
