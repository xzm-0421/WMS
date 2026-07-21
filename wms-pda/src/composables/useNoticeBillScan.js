import { ref, computed } from 'vue'
import {
  getNoticeBillDetail,
  scanNoticeLine,
  toggleNoticeLine,
  updateNoticeLineQty,
  submitNoticeBill,
} from '@/api/noticeBill.js'
import { getNoticeBillType } from '@/constants/noticeBillTypes.js'
import { isNoticeBillCompleted } from '@/utils/noticeBill.js'

export function useNoticeBillScan(billTypeRef, billNoRef) {
  const loading = ref(false)
  const submitting = ref(false)
  const detail = ref(null)
  const lines = ref([])
  const lastHighlightLineNo = ref(null)

  const typeConfig = computed(() => getNoticeBillType(billTypeRef.value))
  const isInbound = computed(() => typeConfig.value.direction === 'INBOUND')

  const checkedCount = computed(() => lines.value.filter((l) => l.checked).length)
  const submitableCount = computed(() =>
    lines.value.filter((l) => l.checked && (Number(l.pendingSubmitQty) || 0) > 0).length,
  )

  async function loadDetail() {
    if (!billTypeRef.value || !billNoRef.value) return null
    loading.value = true
    try {
      const data = await getNoticeBillDetail(billTypeRef.value, billNoRef.value)
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
    if (type === 'MATERIAL_NOT_ON_BILL') {
      return msg || '该物料不在本单据中'
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
    if (!barcode?.trim() || submitting.value) return null
    try {
      const updated = await scanNoticeLine(billTypeRef.value, billNoRef.value, barcode.trim())
      lastHighlightLineNo.value = updated.lineNo
      mergeLine(updated)
      uni.showToast({ title: '扫描成功', icon: 'success', duration: 800 })
      return updated
    } catch (e) {
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

  async function updateQty(lineNo, qty) {
    try {
      const updated = await updateNoticeLineQty(billTypeRef.value, billNoRef.value, lineNo, qty)
      mergeLine(updated)
      return true
    } catch (e) {
      uni.showToast({ title: e?.message || '更新数量失败', icon: 'none' })
      return false
    }
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
    if (submitting.value || !submitableCount.value) return null
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
      })
      if (result?.erpSyncStatus && result.erpSyncStatus !== 'SUCCESS' && result.erpSyncStatus !== 'PENDING') {
        uni.showToast({
          title: result.erpSyncMessage || '金蝶同步失败，数量未变更',
          icon: 'none',
          duration: 3500,
        })
        return null
      }
      uni.showToast({
        title: result?.message || (isInbound.value ? '提交入库成功' : '提交出库成功'),
        icon: 'success',
      })
      await loadDetail()
      if (isNoticeBillCompleted(detail.value)) {
        setTimeout(() => uni.navigateBack(), 600)
      }
      return result
    } catch (e) {
      uni.showToast({ title: formatSubmitError(e), icon: 'none', duration: 3500 })
      return null
    } finally {
      submitting.value = false
    }
  }

  function formatQty(v) {
    const n = Number(v)
    if (Number.isNaN(n)) return '0'
    return Number.isInteger(n) ? String(n) : n.toFixed(2).replace(/\.?0+$/, '')
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
  }
}

export default useNoticeBillScan
