/**
 * 统一扫码出库：扫条码 → 预览库存 → 点击确认扣减
 */
import { ref } from 'vue'
import { scanOutboundDirect } from '@/api/scan.js'

export const OUTBOUND_SCAN_QTY = 1

export function useScanOutbound() {
  const processing = ref(false)
  const pendingConfirm = ref(null)
  const scanLog = ref([])
  const scanCount = ref(0)

  function addLog(entry) {
    scanLog.value.unshift({ ...entry, time: new Date().toLocaleTimeString() })
    if (scanLog.value.length > 30) scanLog.value.pop()
  }

  function showError(message) {
    uni.showToast({ title: message, icon: 'none', duration: 2500 })
    addLog({ ok: false, msg: message })
  }

  async function handleScan(barcode, warehouseCode) {
    if (!barcode?.trim() || processing.value) return
    processing.value = true
    try {
      const preview = await scanOutboundDirect({
        barcodeContent: barcode.trim(),
        warehouseCode,
        quantity: OUTBOUND_SCAN_QTY,
        confirm: false,
      })
      if (!preview.matched) {
        showError(preview.message || '未找到可用库存')
        return null
      }
      pendingConfirm.value = { barcode: barcode.trim(), preview, warehouseCode }
      return preview
    } catch (e) {
      showError(e?.message || '条码解析失败，请重新扫描')
      return null
    } finally {
      processing.value = false
    }
  }

  async function confirmOutbound() {
    if (!pendingConfirm.value || processing.value) return null
    processing.value = true
    try {
      const { barcode, preview, warehouseCode } = pendingConfirm.value
      const result = await scanOutboundDirect({
        barcodeContent: barcode,
        warehouseCode,
        quantity: OUTBOUND_SCAN_QTY,
        sourceLocation: preview.sourceLocation || preview.recommendedLocation,
        confirm: true,
      })
      scanCount.value += 1
      const label = result.materialName || result.materialCode
      addLog({
        ok: true,
        barcode,
        msg: `${label} -${result.quantity} @${result.sourceLocation}`,
      })
      pendingConfirm.value = null
      uni.showToast({ title: '出库成功', icon: 'success', duration: 800 })
      return result
    } catch (e) {
      showError(e?.message || '出库失败')
      return null
    } finally {
      processing.value = false
    }
  }

  function cancelConfirm() {
    pendingConfirm.value = null
  }

  function clearLog() {
    scanLog.value = []
  }

  return {
    processing,
    pendingConfirm,
    scanLog,
    scanCount,
    handleScan,
    confirmOutbound,
    cancelConfirm,
    clearLog,
  }
}

export default useScanOutbound
