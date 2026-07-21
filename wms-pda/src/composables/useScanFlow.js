import { ref } from 'vue'
import { scanInboundByBarcode, scanOutboundByBarcode } from '@/api/scan.js'
import { withOfflineFallback } from '@/utils/offline.js'

/** 入库固定每次扫码数量为 1 */
export const INBOUND_SCAN_QTY = 1

/**
 * 统一扫码业务流
 * @param {'inbound'|'outbound'} mode
 * @param {string} orderNo
 */
export function useScanFlow(mode, orderNo) {
  const processing = ref(false)
  const scanLog = ref([])
  const pendingConfirm = ref(null)
  const scanCount = ref(0)
  const lastMaterialCode = ref('')

  async function handleScan(barcode, warehouseCode) {
    if (!barcode?.trim() || processing.value) return
    processing.value = true
    try {
      if (mode === 'inbound') {
        return await doInboundScan(barcode.trim(), warehouseCode)
      }
      return await doOutboundPreview(barcode.trim(), warehouseCode)
    } finally {
      processing.value = false
    }
  }

  async function doInboundScan(barcode, warehouseCode) {
    const payload = {
      barcodeContent: barcode,
      quantity: INBOUND_SCAN_QTY,
    }
    let result
    await withOfflineFallback('INBOUND_SCAN', orderNo, payload, async () => {
      result = await scanInboundByBarcode(orderNo, payload)
      return result
    })
    if (result?.offline) {
      addLog({ ok: true, barcode, msg: '已离线缓存' })
      return { offline: true }
    }
    if (result?.materialCode) lastMaterialCode.value = result.materialCode
    scanCount.value += 1
    const batchHint = result.batchNo ? ` 批:${result.batchNo}` : ''
    addLog({
      ok: true,
      barcode,
      msg: `${result.materialName || result.materialCode} +${result.scannedQty}${batchHint}`,
    })
    return result
  }

  async function doOutboundPreview(barcode, warehouseCode) {
    const preview = await scanOutboundByBarcode(orderNo, {
      barcodeContent: barcode,
      quantity: INBOUND_SCAN_QTY,
      confirm: false,
    })
    pendingConfirm.value = { barcode, preview, warehouseCode }
    return preview
  }

  async function confirmOutbound() {
    if (!pendingConfirm.value) return
    processing.value = true
    try {
      const { barcode, preview } = pendingConfirm.value
      const payload = {
        barcodeContent: barcode,
        quantity: preview.quantity,
        sourceLocation: preview.sourceLocation,
        lineNo: preview.lineNo,
        confirm: true,
      }
      let result
      await withOfflineFallback('OUTBOUND_SCAN', orderNo, payload, async () => {
        result = await scanOutboundByBarcode(orderNo, payload)
        return result
      })
      if (result?.offline) {
        addLog({ ok: true, barcode, msg: '已离线缓存' })
      } else {
        addLog({
          ok: true,
          barcode,
          msg: `${preview.materialName} -${preview.quantity}`,
        })
      }
      pendingConfirm.value = null
      return result
    } finally {
      processing.value = false
    }
  }

  function cancelConfirm() {
    pendingConfirm.value = null
  }

  function addLog(entry) {
    scanLog.value.unshift({
      ...entry,
      time: new Date().toLocaleTimeString(),
    })
    if (scanLog.value.length > 50) scanLog.value.pop()
  }

  function clearLog() {
    scanLog.value = []
  }

  return {
    processing,
    scanLog,
    pendingConfirm,
    scanCount,
    lastMaterialCode,
    handleScan,
    confirmOutbound,
    cancelConfirm,
    clearLog,
  }
}
