/**
 * 统一快速入库：自动识别采购单 / 物料条码
 */
import { ref, computed } from 'vue'
import usePurchaseInboundScan from '@/composables/usePurchaseInboundScan.js'
import { recognizePdaInbound, submitPdaInbound } from '@/api/pdaInbound.js'
import { parseBarcodeLocal, parsePurchaseOrderNo } from '@/utils/scan.js'
import { INBOUND_SCAN_QTY } from '@/composables/useScanFlow.js'

const PO_PATTERN = /^PO[\w-]{4,}/i

export function useQuickInbound() {
  const mode = ref('material') // material | purchase
  const processing = ref(false)
  const scanCount = ref(0)
  const warehouseCode = ref('WH01')
  const lastMaterial = ref({ materialCode: '', materialName: '', batchNo: '' })
  const recentRecords = ref([])

  const purchase = usePurchaseInboundScan()

  const phaseLabel = computed(() => {
    if (mode.value === 'purchase') {
      return purchase.phase.value === 'scan_order' ? '① 扫描采购单' : '② 逐项确认物料'
    }
    return '扫描物料条码 · 即扫即入'
  })

  function isPurchaseBarcode(barcode) {
    const raw = (barcode || '').trim()
    if (!raw) return false
    if (PO_PATTERN.test(raw)) return true
    const po = parsePurchaseOrderNo(raw)
    return PO_PATTERN.test(po)
  }

  function detectMode(barcode) {
    if (purchase.phase.value === 'confirm_items') return 'purchase'
    if (isPurchaseBarcode(barcode)) return 'purchase'
    return 'material'
  }

  async function handleScan(barcode) {
    if (!barcode?.trim() || processing.value || purchase.processing.value) return
    const detected = detectMode(barcode)
    mode.value = detected

    if (detected === 'purchase') {
      await purchase.handleScan(barcode)
      return
    }

    await scanMaterialInbound(barcode)
  }

  async function scanMaterialInbound(barcode) {
    const local = parseBarcodeLocal(barcode)
    processing.value = true
    try {
      const data = await recognizePdaInbound(barcode, warehouseCode.value)
      const materialCode = data.materialCode || local.materialCode || ''
      const batchNo = data.batchNo || local.batchNo || ''
      if (!materialCode) {
        uni.showToast({ title: '条码解析失败，请重新扫描', icon: 'none' })
        return
      }

      lastMaterial.value = {
        materialCode,
        materialName: data.materialName || '',
        batchNo,
      }

      const result = await submitPdaInbound({
        barcodeContent: barcode,
        materialCode,
        batchNo: batchNo || undefined,
        warehouseCode: warehouseCode.value,
        quantity: INBOUND_SCAN_QTY,
      })

      scanCount.value += 1
      recentRecords.value.unshift(result)
      uni.showToast({ title: `入库 +${INBOUND_SCAN_QTY}`, icon: 'success', duration: 800 })
    } catch (e) {
      uni.showToast({ title: e?.message || '入库失败', icon: 'none' })
    } finally {
      processing.value = false
    }
  }

  function resetPurchase() {
    purchase.reset()
    mode.value = 'material'
  }

  return {
    mode,
    processing,
    scanCount,
    warehouseCode,
    lastMaterial,
    recentRecords,
    phaseLabel,
    purchasePhase: purchase.phase,
    purchaseOrder: purchase.purchaseOrder,
    purchaseLines: purchase.lines,
    purchaseProcessing: purchase.processing,
    purchaseConfirmedCount: purchase.confirmedCount,
    purchaseTotalCount: purchase.totalCount,
    purchaseAllConfirmed: purchase.allConfirmed,
    purchaseProgressPercent: purchase.progressPercent,
    purchaseLastHighlight: purchase.lastHighlightLineNo,
    purchaseScanLog: purchase.scanLog,
    submitPurchaseInbound: purchase.submitInbound,
    resetPurchase: purchase.reset,
    handleScan,
    INBOUND_SCAN_QTY,
  }
}

export default useQuickInbound
