import { ref, computed } from 'vue'
import {
  getReceiveNoticeDetail,
  scanReceiveLine,
  toggleReceiveLine,
  updateReceiveLineQty,
  submitReceiveInbound,
  heartbeatReceiveNoticeLock,
  releaseReceiveNoticeLock,
} from '@/api/receiveNotice.js'
import { isNoticeBillCompleted } from '@/utils/noticeBill.js'
import { isLabelScanned } from '@/utils/labelScanGate.js'
import { useBillExclusiveLock, isBillLockedError } from '@/composables/useBillExclusiveLock.js'
import { formatQty as formatQtyByUnit } from '@/utils/formatQty.js'
import {
  handleErpSubmitResult,
  formatErpSubmitError,
  alertErpSubmitFailed,
} from '@/utils/erpSyncFeedback.js'
import { navigateBackAfterSubmit } from '@/utils/listKeywordReset.js'

export function useReceiveNoticeScan(billNo) {
  const loading = ref(false)
  const submitting = ref(false)
  const detail = ref(null)
  const lines = ref([])
  const lastHighlightLineNo = ref(null)

  const billLock = useBillExclusiveLock({
    heartbeat: () => heartbeatReceiveNoticeLock(billNo.value),
    release: () => releaseReceiveNoticeLock(billNo.value),
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

  function handleLockDenied(e) {
    billLock.stop()
    const msg = e?.message || '单据正被其他人操作'
    uni.showToast({ title: msg, icon: 'none', duration: 2500 })
    setTimeout(() => uni.navigateBack({ fail: () => {} }), 400)
  }

  async function loadDetail() {
    if (!billNo.value) return null
    loading.value = true
    try {
      const data = await getReceiveNoticeDetail(billNo.value)
      detail.value = data
      lines.value = (data.lines || []).map(normalizeLine)
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

  function normalizeLine(line) {
    if (!line) return line
    return {
      ...line,
      checked: line.checked === true || line.checked === 1,
      pendingSubmitQty: line.pendingSubmitQty ?? 0,
      pendingSubmitAuxQty: line.pendingSubmitAuxQty ?? 0,
    }
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
      return msg || '该物料不在本收料通知单中'
    }
    if (type === 'BILL_LINES_NOT_READY' || type === 'BILL_LINE_NOT_SYNCED') {
      return msg || '收料单明细未加载完成，请返回重新进入'
    }
    if (type === 'LINE_ALREADY_FULL') {
      return msg || '该物料已收满'
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
    }
  }

  async function handleScan(barcode) {
    if (!barcode?.trim() || submitting.value) return null
    loading.value = true
    lastHighlightLineNo.value = null
    try {
      const line = await scanReceiveLine(billNo.value, barcode.trim())
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
      const line = await toggleReceiveLine(billNo.value, lineNo, checked)
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
      const line = await updateReceiveLineQty(billNo.value, lineNo, num, auxQty)
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

  async function submit(getSubmitPayload) {
    if (!submitableCount.value) {
      uni.showToast({ title: '请先扫码或手动填写数量后再提交', icon: 'none' })
      return false
    }
    submitting.value = true
    try {
      const payload = typeof getSubmitPayload === 'function' ? getSubmitPayload() : {}
      const manual = payload?.autoAssignWarehouse === false
      const result = await submitReceiveInbound(billNo.value, {
        supplierCode: detail.value?.supplierCode,
        supplierName: detail.value?.supplierName,
        autoAssignWarehouse: !manual,
        warehouseCode: manual ? payload?.warehouseCode : undefined,
        erpWarehouseCode: manual
          ? (payload?.erpWarehouseCode || payload?.warehouseCode)
          : detail.value?.erpWarehouseCode,
        autoAllocateLocation: !!payload?.autoAllocateLocation,
        locationCode: payload?.locationCode || payload?.targetLocation || undefined,
      })
      const feedback = await handleErpSubmitResult(result)
      if (!feedback.ok) {
        await loadDetail()
        return false
      }
      await loadDetail()
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
      await loadDetail()
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
    handleScan,
    toggleCheck,
    updateQty,
    formatQty,
    submit,
    rowClass,
    isLabelScanned,
  }
}

export default useReceiveNoticeScan
