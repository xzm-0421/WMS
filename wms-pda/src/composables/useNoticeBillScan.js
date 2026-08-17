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
import { getNoticeBillType } from '@/constants/noticeBillTypes.js'
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

export function useNoticeBillScan(billTypeRef, billNoRef) {
  const loading = ref(false)
  const submitting = ref(false)
  const detail = ref(null)
  const lines = ref([])
  const lastHighlightLineNo = ref(null)

  const billLock = useBillExclusiveLock({
    heartbeat: () => heartbeatNoticeBillLock(billTypeRef.value, billNoRef.value),
    release: () => releaseNoticeBillLock(billTypeRef.value, billNoRef.value),
  })

  const typeConfig = computed(() => getNoticeBillType(billTypeRef.value))
  const isInbound = computed(() => typeConfig.value.direction === 'INBOUND')

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
    if (!billTypeRef.value || !billNoRef.value) return null
    loading.value = true
    try {
      const data = await getNoticeBillDetail(billTypeRef.value, billNoRef.value)
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
    if (type === 'MATERIAL_NOT_ON_BILL') {
      return msg || '该物料不在本单据中'
    }
    if (type === 'LINE_ALREADY_FULL') {
      return msg || '该物料已收满'
    }
    if (type === 'BILL_LINE_NOT_SYNCED' || type === 'BILL_LINES_NOT_READY') {
      return msg || '单据明细未就绪，请返回后重新进入'
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
    }
  }

  async function handleScan(barcode) {
    if (!barcode?.trim()) return null
    if (submitting.value) {
      uni.showToast({ title: '正在提交，请稍候', icon: 'none' })
      return null
    }
    if (loading.value) {
      uni.showToast({ title: '单据加载中，请稍后再扫', icon: 'none' })
      return null
    }
    try {
      const updated = await scanNoticeLine(billTypeRef.value, billNoRef.value, barcode.trim())
      lastHighlightLineNo.value = updated.lineNo
      mergeLine(updated)
      if (detail.value) {
        detail.value.scanStatus = detail.value.scanStatus === 'COMPLETED'
          ? detail.value.scanStatus
          : 'SCANNING'
      }
      uni.showToast({ title: '扫描成功', icon: 'success', duration: 800 })
      return updated
    } catch (e) {
      if (isBillLockedError(e)) {
        handleLockDenied(e)
        return null
      }
      uni.showToast({ title: formatScanError(e), icon: 'none', duration: 2500 })
      return null
    }
  }

  async function toggleCheck(lineNo, checked) {
    try {
      const updated = await toggleNoticeLine(billTypeRef.value, billNoRef.value, lineNo, checked)
      mergeLine(updated)
    } catch (e) {
      uni.showToast({ title: e?.message || '操作失败', icon: 'none' })
    }
  }

  async function updateQty(lineNo, qty, auxQty) {
    try {
      const updated = await updateNoticeLineQty(billTypeRef.value, billNoRef.value, lineNo, qty, auxQty)
      mergeLine(updated)
      return true
    } catch (e) {
      uni.showToast({ title: e?.message || '更新数量失败', icon: 'none' })
      return false
    }
  }

  async function submit(getWarehousePayload) {
    if (submitting.value) return null
    if (!submitableCount.value) {
      uni.showToast({ title: '请先扫码或手动填写数量后再提交', icon: 'none' })
      return null
    }
    submitting.value = true
    try {
      const wh = typeof getWarehousePayload === 'function' ? getWarehousePayload() : {}
      const manual = isInbound.value && wh?.autoAssignWarehouse === false
      const result = await submitNoticeBill(billTypeRef.value, billNoRef.value, {
        supplierCode: detail.value?.supplierCode,
        supplierName: detail.value?.supplierName,
        autoAssignWarehouse: isInbound.value ? !manual : undefined,
        warehouseCode: manual ? wh?.warehouseCode : undefined,
        erpWarehouseCode: manual
          ? (wh?.erpWarehouseCode || wh?.warehouseCode)
          : detail.value?.erpWarehouseCode,
        autoAllocateLocation: isInbound.value ? !!wh?.autoAllocateLocation : undefined,
        locationCode: isInbound.value
          ? (wh?.locationCode || wh?.targetLocation || undefined)
          : undefined,
      })
      const feedback = await handleErpSubmitResult(result)
      // 失败或金蝶仍后台同步中：留在单据页并刷新
      if (!feedback.ok) {
        await loadDetail()
        return null
      }
      // 未审核工作流：金蝶成功后单据离开列表，再退回
      const auditExistingTypes = new Set([
        'PRODUCTION_RET_STOCK',
        'OTHER_IN',
        'OTHER_OUT',
        'SALES_DELIVERY',
        'PURCHASE_RETURN',
      ])
      if (auditExistingTypes.has(billTypeRef.value)) {
        await billLock.releaseLock()
        navigateBackAfterSubmit(400)
        return result
      }
      await loadDetail()
      if (isNoticeBillCompleted(detail.value)) {
        await billLock.releaseLock()
        navigateBackAfterSubmit(400)
      }
      return result
    } catch (e) {
      if (isBillLockedError(e)) {
        handleLockDenied(e)
        return null
      }
      await alertErpSubmitFailed(formatErpSubmitError(e))
      await loadDetail()
      return null
    } finally {
      submitting.value = false
    }
  }

  function formatQty(v, unitCode) {
    return formatQtyByUnit(v, unitCode)
  }

  function rowClass(line) {
    const submitted = Number(line.submittedQty) || 0
    const plan = Number(line.planQty) || 0
    const classes = []
    if (line.checked) classes.push('checked')
    if (line.lineNo === lastHighlightLineNo.value) classes.push('flash')
    if (submitted > 0 && submitted < plan) classes.push('partial')
    if (plan > 0 && submitted >= plan) classes.push('done')
    return classes.join(' ')
  }

  return {
    loading,
    submitting,
    detail,
    lines,
    typeConfig,
    isInbound,
    checkedCount,
    submitableCount,
    loadDetail,
    handleScan,
    toggleCheck,
    updateQty,
    formatQty,
    submit,
    rowClass,
    lastHighlightLineNo,
    isLabelScanned,
  }
}

export default useNoticeBillScan
