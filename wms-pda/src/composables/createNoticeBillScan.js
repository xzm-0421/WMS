import { ref, computed } from 'vue'
import {
  getNoticeBillDetail,
  scanNoticeLine,
  toggleNoticeLine,
  updateNoticeLineQty,
  submitNoticeBill,
} from '@/api/noticeBill.js'
import { isNoticeBillCompleted } from '@/utils/noticeBill.js'
import { matchLocalBillLine } from '@/utils/localBarcodeMatch.js'
import { cacheGet, cacheSet, cacheDel } from '@/utils/ttlCache.js'
import { DETAIL_CACHE_TTL_MS, SHOW_THROTTLE_MS } from '@/utils/noticeListPaging.js'

/**
 * 通知单明细扫码公共逻辑（缓存 / 本地匹配 / 异步同步容忍）。
 */
export function createNoticeBillScan(billType, messages = {}) {
  const notOnBillMsg = messages.notOnBill || '该物料不在本单据中'
  const linesNotReadyMsg = messages.linesNotReady || '单据明细未加载完成，请返回重新进入'

  return function useNoticeBillScanImpl(billNo) {
    const loading = ref(false)
    const submitting = ref(false)
    const detail = ref(null)
    const lines = ref([])
    const lastHighlightLineNo = ref(null)
    let lastLoadAt = 0

    const checkedCount = computed(() => lines.value.filter((l) => l.checked).length)
    const submitableCount = computed(() =>
      lines.value.filter((l) => l.checked && (Number(l.pendingSubmitQty) || 0) > 0).length,
    )

    function detailCacheKey() {
      return `notice-detail:${billType}:${billNo.value || ''}`
    }

    function normalizeLine(line) {
      if (!line) return line
      return {
        ...line,
        checked: line.checked === true || line.checked === 1,
        pendingSubmitQty: line.pendingSubmitQty ?? 0,
      }
    }

    function applyDetail(data) {
      detail.value = data
      lines.value = (data?.lines || []).map(normalizeLine)
    }

    async function loadDetail(options = {}) {
      if (!billNo.value) return null
      const force = options.force === true
      const cacheKey = detailCacheKey()
      if (!force) {
        const cached = cacheGet(cacheKey)
        if (cached) {
          applyDetail(cached)
          return cached
        }
      }
      loading.value = true
      try {
        const data = await getNoticeBillDetail(billType, billNo.value, { refresh: force })
        applyDetail(data)
        cacheSet(cacheKey, data, DETAIL_CACHE_TTL_MS)
        lastLoadAt = Date.now()
        return data
      } catch (e) {
        uni.showToast({ title: e?.message || '加载明细失败', icon: 'none' })
        return null
      } finally {
        loading.value = false
      }
    }

    async function loadDetailOnShow() {
      if (Date.now() - lastLoadAt < SHOW_THROTTLE_MS && lines.value.length) {
        return detail.value
      }
      return loadDetail()
    }

    function formatScanError(e) {
      const type = e?.data?.errorType || e?.errorType
      const msg = e?.message || e?.data?.message
      if (type === 'BARCODE_EMPTY' || type === 'BARCODE_PARSE_FAILED') {
        return msg || '条码格式错误，请扫描物料标签二维码'
      }
      if (type === 'BARCODE_QTY_INVALID') {
        return msg || '二维码数量无效，请检查标签或重新打印'
      }
      if (type === 'MATERIAL_NOT_ON_BILL') {
        return msg || notOnBillMsg
      }
      if (type === 'BILL_LINES_NOT_READY' || type === 'BILL_LINE_NOT_SYNCED') {
        return msg || linesNotReadyMsg
      }
      if (type === 'LINE_ALREADY_FULL') {
        return msg || '该物料已领满'
      }
      if (type === 'NO_STOCK') {
        return msg || '未找到可出库库存'
      }
      return msg || '扫描失败'
    }

    function mergeLine(updated) {
      const normalized = normalizeLine(updated)
      const idx = lines.value.findIndex((l) => l.lineNo === normalized.lineNo)
      if (idx >= 0) {
        lines.value[idx] = { ...lines.value[idx], ...normalized }
      }
      if (detail.value) {
        detail.value.checkedLines = lines.value.filter((l) => l.checked).length
        cacheSet(detailCacheKey(), { ...detail.value, lines: lines.value }, DETAIL_CACHE_TTL_MS)
      }
    }

    function applyLocalScan(matched) {
      const line = { ...matched.line }
      const plan = Number(line.planQty) || 0
      const submitted = Number(line.submittedQty) || 0
      const remain = Math.max(0, plan - submitted)
      if (remain <= 0) {
        throw Object.assign(new Error('该物料已领满'), { errorType: 'LINE_ALREADY_FULL' })
      }
      let addQty = matched.parsed.qty != null ? Number(matched.parsed.qty) : 1
      if (!Number.isFinite(addQty) || addQty <= 0) addQty = 1
      if (addQty > remain) addQty = remain
      const pending = Number(line.pendingSubmitQty) || 0
      const nextPending = Math.min(remain, pending + addQty)
      line.checked = true
      line.pendingSubmitQty = nextPending
      line.scannedQty = submitted + nextPending
      line.scannedBarcodeQty = addQty
      if (matched.parsed.batchNo) line.batchNo = matched.parsed.batchNo
      mergeLine(line)
      return line
    }

    async function handleScan(barcode) {
      if (!barcode?.trim() || submitting.value) return null
      loading.value = true
      lastHighlightLineNo.value = null
      const raw = barcode.trim()
      try {
        const local = matchLocalBillLine(lines.value, raw)
        if (local) {
          // 本地先反馈；后台仍落库（后端已跳过重复金蝶 View）
          applyLocalScan(local)
          lastHighlightLineNo.value = local.line.lineNo
        }
        const line = await scanNoticeLine(billType, billNo.value, raw)
        mergeLine(line)
        lastHighlightLineNo.value = line.lineNo
        const qty = line.scannedBarcodeQty ?? line.pendingSubmitQty
        const qtyText = qty != null && qty !== '' ? ` ×${formatQty(qty)}` : ''
        uni.showToast({
          title: `✓ ${line.materialName || line.materialCode}${qtyText}`,
          icon: 'success',
          duration: 1200,
        })
        return line
      } catch (e) {
        uni.showToast({ title: formatScanError(e), icon: 'none', duration: 2500 })
        return null
      } finally {
        loading.value = false
      }
    }

    async function toggleCheck(lineNo, checked) {
      try {
        const line = await toggleNoticeLine(billType, billNo.value, lineNo, checked)
        mergeLine(line)
      } catch (e) {
        uni.showToast({ title: e?.message || '操作失败', icon: 'none' })
      }
    }

    async function updateQty(lineNo, qty) {
      const num = Number(qty)
      if (Number.isNaN(num) || num < 0) {
        uni.showToast({ title: '请输入有效数量', icon: 'none' })
        return false
      }
      try {
        const line = await updateNoticeLineQty(billType, billNo.value, lineNo, num)
        mergeLine(line)
        return true
      } catch (e) {
        uni.showToast({ title: e?.message || '更新数量失败', icon: 'none' })
        return false
      }
    }

    function formatQty(val) {
      if (val == null || val === '') return '0'
      const n = Number(val)
      if (Number.isNaN(n)) return String(val)
      return Number.isInteger(n) ? String(n) : String(n)
    }

    function formatSubmitError(e) {
      const type = e?.data?.errorType || e?.errorType
      const msg = e?.message || e?.data?.message
      if (type === 'ERP_SYNC_FAILED' || type === 'ERP_IN_STOCK_QTY_EXCEEDED') {
        return msg || '金蝶同步失败，数量未变更'
      }
      return msg || '提交失败'
    }

    async function submit() {
      if (!submitableCount.value && !checkedCount.value) {
        uni.showToast({ title: '请先扫描勾选物料', icon: 'none' })
        return false
      }
      submitting.value = true
      try {
        const result = await submitNoticeBill(billType, billNo.value, {
          supplierCode: detail.value?.supplierCode,
          supplierName: detail.value?.supplierName,
        })
        const syncStatus = result?.erpSyncStatus
        if (syncStatus && syncStatus !== 'SUCCESS' && syncStatus !== 'PENDING') {
          uni.showToast({
            title: result.erpSyncMessage || '金蝶同步失败，数量未变更',
            icon: 'none',
            duration: 3500,
          })
          return false
        }
        uni.showToast({
          title: result?.erpBillNo
            ? `已同步 ${result.erpBillNo}`
            : (result?.message || `已提交 ${result.lineCount || 0} 项`),
          icon: 'success',
        })
        cacheDel(detailCacheKey())
        await loadDetail({ force: true })
        if (isNoticeBillCompleted(detail.value)) {
          setTimeout(() => uni.navigateBack(), 600)
        }
        return true
      } catch (e) {
        uni.showToast({ title: formatSubmitError(e), icon: 'none', duration: 3500 })
        return false
      } finally {
        submitting.value = false
      }
    }

    function rowClass(line) {
      if (line.lineNo === lastHighlightLineNo.value) return 'flash'
      const submitted = Number(line.submittedQty) || 0
      const plan = Number(line.planQty) || 0
      if (submitted >= plan && plan > 0) return 'done'
      if (submitted > 0 && submitted < plan) return 'partial'
      if (line.checked) return 'checked'
      return ''
    }

    return {
      loading,
      submitting,
      detail,
      lines,
      lastHighlightLineNo,
      checkedCount,
      submitableCount,
      loadDetail,
      loadDetailOnShow,
      handleScan,
      toggleCheck,
      updateQty,
      formatQty,
      submit,
      rowClass,
    }
  }
}

export default createNoticeBillScan
