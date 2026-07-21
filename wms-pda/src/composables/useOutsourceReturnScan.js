import { ref, computed } from 'vue'
import {
  getNoticeBillDetail,
  scanNoticeLine,
  toggleNoticeLine,
  updateNoticeLineQty,
  submitNoticeBill,
} from '@/api/noticeBill.js'
import { isNoticeBillCompleted } from '@/utils/noticeBill.js'

const BILL_TYPE = 'OUTSOURCE_RETURN'

export function useOutsourceReturnScan(billNo) {
  const loading = ref(false)
  const submitting = ref(false)
  const detail = ref(null)
  const lines = ref([])
  const lastHighlightLineNo = ref(null)

  const checkedCount = computed(() => lines.value.filter((l) => l.checked).length)
  const submitableCount = computed(() =>
    lines.value.filter((l) => l.checked && (Number(l.pendingSubmitQty) || 0) > 0).length,
  )

  async function loadDetail() {
    if (!billNo.value) return null
    loading.value = true
    try {
      const data = await getNoticeBillDetail(BILL_TYPE, billNo.value)
      detail.value = data
      lines.value = (data.lines || []).map(normalizeLine)
      return data
    } catch (e) {
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
      return msg || '该物料不在本委外领料单中'
    }
    if (type === 'BILL_LINES_NOT_READY' || type === 'BILL_LINE_NOT_SYNCED') {
      return msg || '领料单明细未加载完成，请返回重新进入'
    }
    if (type === 'LINE_ALREADY_FULL') {
      return msg || '该物料已退满'
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
      const line = await scanNoticeLine(BILL_TYPE, billNo.value, barcode.trim())
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
      const line = await toggleNoticeLine(BILL_TYPE, billNo.value, lineNo, checked)
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
      const line = await updateNoticeLineQty(BILL_TYPE, billNo.value, lineNo, num)
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

  async function submit(getWarehousePayload) {
    if (!submitableCount.value && !checkedCount.value) {
      uni.showToast({ title: '请先扫描勾选物料', icon: 'none' })
      return false
    }
    submitting.value = true
    try {
      const wh = typeof getWarehousePayload === 'function' ? getWarehousePayload() : {}
      const manual = wh?.autoAssignWarehouse === false
      const result = await submitNoticeBill(BILL_TYPE, billNo.value, {
        supplierCode: detail.value?.supplierCode,
        supplierName: detail.value?.supplierName,
        autoAssignWarehouse: !manual,
        warehouseCode: manual ? wh?.warehouseCode : undefined,
        erpWarehouseCode: manual
          ? (wh?.erpWarehouseCode || wh?.warehouseCode)
          : detail.value?.erpWarehouseCode,
      })
      if (result?.erpSyncStatus && result.erpSyncStatus !== 'SUCCESS' && result.erpSyncStatus !== 'PENDING') {
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
      await loadDetail()
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
    handleScan,
    toggleCheck,
    updateQty,
    formatQty,
    submit,
    rowClass,
  }
}

export default useOutsourceReturnScan
