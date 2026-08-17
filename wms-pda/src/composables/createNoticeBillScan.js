import { ref, computed } from 'vue'
import {
  getNoticeBillDetail,
  scanNoticeLine,
  toggleNoticeLine,
  updateNoticeLineQty,
  submitNoticeBill,
  heartbeatNoticeBillLock,
  releaseNoticeBillLock,
} from '@/api/noticeBill.js'
import { isNoticeBillCompleted } from '@/utils/noticeBill.js'
import { matchLocalBillLine } from '@/utils/localBarcodeMatch.js'
import { cacheGet, cacheSet, cacheDel } from '@/utils/ttlCache.js'
import { DETAIL_CACHE_TTL_MS, SHOW_THROTTLE_MS } from '@/utils/noticeListPaging.js'
import { isLabelScanned } from '@/utils/labelScanGate.js'
import { useBillExclusiveLock, isBillLockedError } from '@/composables/useBillExclusiveLock.js'
import { formatQty as formatQtyByUnit, isWeightUnit } from '@/utils/formatQty.js'
import {
  handleErpSubmitResult,
  formatErpSubmitError,
  alertErpSubmitFailed,
} from '@/utils/erpSyncFeedback.js'
import { navigateBackAfterSubmit } from '@/utils/listKeywordReset.js'

/**
 * 通知单明细扫码公共逻辑（缓存 / 本地匹配 / 异步同步容忍）。
 */
export function createNoticeBillScan(billType, messages = {}) {
  const notOnBillMsg = messages.notOnBill || '该物料不在本单据中'
  const linesNotReadyMsg = messages.linesNotReady || '单据明细未加载完成，请返回重新进入'
  const alreadyFullMsg = messages.alreadyFull || '该物料已领满'
  /** 提交成功即返回列表（生产领料/退料 Submit+Audit 后单据离开未审核列表） */
  const backOnSubmitSuccess = messages.backOnSubmitSuccess === true

  return function useNoticeBillScanImpl(billNo) {
    const loading = ref(false)
    const submitting = ref(false)
    const detail = ref(null)
    const lines = ref([])
    const lastHighlightLineNo = ref(null)
    let lastLoadAt = 0

    const billLock = useBillExclusiveLock({
      heartbeat: () => heartbeatNoticeBillLock(billType, billNo.value),
      release: () => releaseNoticeBillLock(billType, billNo.value),
    })

    /** 已勾选且库存/计价任一侧有待提交数量 */
    function hasPendingSubmit(line) {
      if (!line?.checked) return false
      if ((Number(line.pendingSubmitQty) || 0) > 0) return true
      return (Number(line.pendingSubmitAuxQty) || 0) > 0
    }

    const checkedCount = computed(() =>
      lines.value.filter((l) => l.checked).length,
    )
    const submitableCount = computed(() =>
      lines.value.filter((l) => hasPendingSubmit(l)).length,
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
        pendingSubmitAuxQty: line.pendingSubmitAuxQty ?? 0,
      }
    }

    function applyDetail(data) {
      detail.value = data
      lines.value = (data?.lines || []).map(normalizeLine)
    }

    function handleLockDenied(e) {
      cacheDel(detailCacheKey())
      billLock.stop()
      const msg = e?.message || '单据正被其他人操作'
      uni.showToast({ title: msg, icon: 'none', duration: 2500 })
      setTimeout(() => uni.navigateBack({ fail: () => {} }), 400)
    }

    async function loadDetail(options = {}) {
      if (!billNo.value) return null
      const force = options.force === true
      const cacheKey = detailCacheKey()
      if (!force) {
        const cached = cacheGet(cacheKey)
        if (cached) {
          applyDetail(cached)
          // 缓存命中仍须续租/抢占校验，避免多人同时操作
          try {
            await heartbeatNoticeBillLock(billType, billNo.value)
            billLock.start()
          } catch (e) {
            if (isBillLockedError(e)) {
              handleLockDenied(e)
              return null
            }
          }
          return cached
        }
      }
      loading.value = true
      try {
        const data = await getNoticeBillDetail(billType, billNo.value, { refresh: force })
        applyDetail(data)
        cacheSet(cacheKey, data, DETAIL_CACHE_TTL_MS)
        lastLoadAt = Date.now()
        billLock.start()
        return data
      } catch (e) {
        if (isBillLockedError(e)) {
          handleLockDenied(e)
          return null
        }
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
        return msg || alreadyFullMsg
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

    /** 数量保留小数，避免 kg 累加产生浮点误差（重量按 6 位） */
    function roundQty(n, unitCode) {
      if (!Number.isFinite(n)) return 0
      const scale = isWeightUnit(unitCode) ? 1e6 : 1e4
      return Math.round(n * scale) / scale
    }

    function applyLocalScan(matched, barcodeRaw) {
      const line = { ...matched.line }
      const unit = line.unitCode
      const plan = Number(line.planQty) || 0
      const submitted = Number(line.submittedQty) || 0
      const remain = roundQty(Math.max(0, plan - submitted), unit)
      if (remain <= 0) {
        throw Object.assign(new Error(alreadyFullMsg), { errorType: 'LINE_ALREADY_FULL' })
      }
      let addQty = matched.parsed.qty != null ? Number(matched.parsed.qty) : 1
      if (!Number.isFinite(addQty) || addQty <= 0) addQty = 1
      addQty = roundQty(addQty, unit)
      if (addQty > remain) addQty = remain
      const pending = Number(line.pendingSubmitQty) || 0
      const nextPending = roundQty(Math.min(remain, pending + addQty), unit)
      line.checked = true
      line.labelScanned = true
      line.scannedBarcode = barcodeRaw || matched.parsed?.barcodeContent || line.scannedBarcode || ''
      line.pendingSubmitQty = nextPending
      line.scannedQty = roundQty(submitted + nextPending, unit)
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
          applyLocalScan(local, raw)
          lastHighlightLineNo.value = local.line.lineNo
        }
        const line = await scanNoticeLine(billType, billNo.value, raw)
        mergeLine(line)
        lastHighlightLineNo.value = line.lineNo
        const qty = line.scannedBarcodeQty ?? line.pendingSubmitQty
        const qtyText = qty != null && qty !== '' ? ` ×${formatQty(qty, line.unitCode)}` : ''
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

    async function updateQty(lineNo, qty, auxQty) {
      const num = Number(qty)
      if (Number.isNaN(num) || num < 0) {
        uni.showToast({ title: '请输入有效数量', icon: 'none' })
        return false
      }
      try {
        const line = await updateNoticeLineQty(billType, billNo.value, lineNo, num, auxQty)
        mergeLine(line)
        return true
      } catch (e) {
        uni.showToast({ title: e?.message || '更新数量失败', icon: 'none' })
        return false
      }
    }

    function formatQty(val, unitCode) {
      return formatQtyByUnit(val, unitCode)
    }

    async function submit() {
      if (!submitableCount.value) {
        uni.showToast({ title: '请先扫码或手动填写数量后再提交', icon: 'none' })
        return false
      }
      submitting.value = true
      try {
        const result = await submitNoticeBill(billType, billNo.value, {
          supplierCode: detail.value?.supplierCode,
          supplierName: detail.value?.supplierName,
        })
        const feedback = await handleErpSubmitResult(result)
        if (!feedback.ok) {
          cacheDel(detailCacheKey())
          await loadDetail({ force: true })
          return false
        }
        cacheDel(detailCacheKey())
        if (backOnSubmitSuccess) {
          // 金蝶审核成功后单据已不在未审核列表，再退回
          await billLock.releaseLock()
          navigateBackAfterSubmit(400)
          return true
        }
        await loadDetail({ force: true })
        if (isNoticeBillCompleted(detail.value)) {
          await billLock.releaseLock()
          navigateBackAfterSubmit(400)
        }
        return true
      } catch (e) {
        if (isBillLockedError(e)) {
          handleLockDenied(e)
          return false
        }
        await alertErpSubmitFailed(formatErpSubmitError(e))
        cacheDel(detailCacheKey())
        await loadDetail({ force: true })
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
      isLabelScanned,
    }
  }
}

export default createNoticeBillScan
