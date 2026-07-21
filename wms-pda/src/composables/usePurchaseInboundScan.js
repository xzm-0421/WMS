import { ref, computed } from 'vue'
import { getPurchaseOrder, createDeliveryNote, generateInboundFromDelivery } from '@/api/purchase.js'
import { getInboundOrder, completeInbound } from '@/api/mobile.js'
import { recognizeBarcode, scanInboundByBarcode } from '@/api/scan.js'
import { getMaterial } from '@/api/order.js'
import { parsePurchaseOrderNo, parseBarcodeLocal, resolveBarcode } from '@/utils/scan.js'

const SCANNABLE_PO_STATUS = ['OPEN', 'PARTIAL']

/**
 * 采购扫码入库：先扫采购单加载明细，再逐项扫物料确认，最后提交入库
 */
export function usePurchaseInboundScan() {
  const phase = ref('scan_order') // scan_order | confirm_items
  const processing = ref(false)
  const purchaseOrder = ref(null)
  const lines = ref([])
  const scanLog = ref([])
  const lastHighlightLineNo = ref(null)
  const lastError = ref('')

  const confirmedCount = computed(() => lines.value.filter((l) => l.confirmed).length)
  const totalCount = computed(() => lines.value.length)
  const allConfirmed = computed(
    () => totalCount.value > 0 && confirmedCount.value === totalCount.value,
  )
  const progressPercent = computed(() => {
    if (!totalCount.value) return 0
    return Math.round((confirmedCount.value / totalCount.value) * 100)
  })

  function addLog(entry) {
    scanLog.value.unshift({
      ...entry,
      time: new Date().toLocaleTimeString(),
    })
    if (scanLog.value.length > 30) scanLog.value.pop()
  }

  function showError(message) {
    lastError.value = message
    uni.showToast({ title: message, icon: 'none', duration: 2500 })
    addLog({ ok: false, msg: message })
  }

  function normalizeQty(val) {
    const n = Number(val)
    return Number.isFinite(n) ? n : 0
  }

  async function enrichLine(poLine) {
    const orderQty = normalizeQty(poLine.orderQty)
    const receivedQty = normalizeQty(poLine.receivedQty)
    const pendingQty = Math.max(0, orderQty - receivedQty)
    let specification = ''
    try {
      const mat = await getMaterial(poLine.materialCode)
      specification = mat.specification || ''
    } catch {
      // 物料主数据缺失时仍展示采购行
    }
    return {
      lineNo: poLine.lineNo,
      materialCode: poLine.materialCode,
      materialName: poLine.materialName || '',
      specification,
      unitCode: poLine.unitCode || '',
      orderQty,
      receivedQty,
      pendingQty,
      batchNo: poLine.batchNo || '',
      confirmed: false,
      scannedBarcode: '',
      scannedBatchNo: '',
      confirmedAt: '',
    }
  }

  async function loadPurchaseOrder(orderNo) {
    const vo = await getPurchaseOrder(orderNo)
    const order = vo.order || vo
    const rawLines = vo.lines || []

    if (!order?.orderNo) {
      throw new Error('采购单数据无效')
    }
    if (!SCANNABLE_PO_STATUS.includes(order.status)) {
      throw new Error(`采购单状态不可收货（${order.status}）`)
    }

    const enriched = []
    for (const line of rawLines) {
      const item = await enrichLine(line)
      if (item.pendingQty > 0) enriched.push(item)
    }
    if (!enriched.length) {
      throw new Error('该采购单无可收货明细')
    }

    purchaseOrder.value = order
    lines.value = enriched
    phase.value = 'confirm_items'
    lastError.value = ''
    addLog({ ok: true, msg: `已加载采购单 ${order.orderNo}，共 ${enriched.length} 项待确认` })
    return order
  }

  async function handleOrderScan(barcode) {
    const orderNo = parsePurchaseOrderNo(barcode)
    if (!orderNo) {
      showError('条码为空，请重新扫描')
      return false
    }
    processing.value = true
    try {
      await loadPurchaseOrder(orderNo)
      uni.showToast({ title: '采购单已加载', icon: 'success' })
      return true
    } catch (e) {
      showError(e?.message || `未找到采购单：${orderNo}`)
      return false
    } finally {
      processing.value = false
    }
  }

  async function parseMaterialBarcode(barcode) {
    const local = parseBarcodeLocal(barcode)
    try {
      const data = await recognizeBarcode(barcode, purchaseOrder.value?.warehouseCode)
      return {
        materialCode: data.materialCode || local.materialCode,
        batchNo: data.batchNo || local.batchNo,
        materialName: data.materialName || '',
        specification: data.specification || '',
        parseFailed: !data.materialCode && !local.materialCode,
      }
    } catch {
      try {
        const resolved = await resolveBarcode(barcode)
        return {
          materialCode: resolved.materialCode,
          batchNo: resolved.batchNo,
          materialName: '',
          specification: '',
          parseFailed: !resolved.materialCode,
        }
      } catch {
        return {
          materialCode: local.materialCode,
          batchNo: local.batchNo,
          materialName: '',
          specification: '',
          parseFailed: !local.materialCode,
        }
      }
    }
  }

  async function handleMaterialScan(barcode) {
    if (phase.value !== 'confirm_items') {
      showError('请先扫描采购单条码')
      return false
    }
    processing.value = true
    lastHighlightLineNo.value = null
    try {
      const parsed = await parseMaterialBarcode(barcode)
      if (parsed.parseFailed || !parsed.materialCode) {
        showError('条码解析失败，无法识别物料，请重新扫描')
        return false
      }

      const pending = lines.value.filter(
        (l) => !l.confirmed && l.materialCode === parsed.materialCode,
      )
      if (!pending.length) {
        const already = lines.value.find(
          (l) => l.confirmed && l.materialCode === parsed.materialCode,
        )
        if (already) {
          showError(`物料 ${parsed.materialCode} 已确认，无需重复扫描`)
        } else {
          showError(`物料 ${parsed.materialCode} 不在本采购单明细中`)
        }
        return false
      }

      const target = pending[0]
      target.confirmed = true
      target.scannedBarcode = barcode
      target.scannedBatchNo = parsed.batchNo || target.batchNo
      if (parsed.materialName && !target.materialName) target.materialName = parsed.materialName
      if (parsed.specification && !target.specification) target.specification = parsed.specification
      target.confirmedAt = new Date().toLocaleTimeString()
      lastHighlightLineNo.value = target.lineNo

      const label = target.materialName || target.materialCode
      addLog({ ok: true, barcode, msg: `已确认：${label}` })
      uni.showToast({ title: `✓ ${label}`, icon: 'success', duration: 1000 })
      return true
    } finally {
      processing.value = false
    }
  }

  async function handleScan(barcode) {
    if (!barcode?.trim() || processing.value) return
    if (phase.value === 'scan_order') {
      return handleOrderScan(barcode.trim())
    }
    return handleMaterialScan(barcode.trim())
  }

  async function submitInbound() {
    if (!allConfirmed.value) {
      const left = totalCount.value - confirmedCount.value
      uni.showModal({
        title: '尚未全部确认',
        content: `还有 ${left} 项未确认，请继续扫描物料条码后再提交。`,
        showCancel: false,
      })
      return false
    }
    if (processing.value) return false

    processing.value = true
    try {
      const po = purchaseOrder.value
      const deliveryNo = await createDeliveryNote({
        purchaseOrderNo: po.orderNo,
        supplierCode: po.supplierCode,
        remark: 'PDA扫码确认入库',
        lines: lines.value.map((l) => ({
          materialCode: l.materialCode,
          actualQty: l.pendingQty,
        })),
      })

      const inboundNo = await generateInboundFromDelivery(deliveryNo)
      const inbound = await getInboundOrder(inboundNo)

      for (const line of lines.value) {
        const inboundLine = (inbound.details || []).find(
          (d) => d.materialCode === line.materialCode,
        )
        await scanInboundByBarcode(inboundNo, {
          barcodeContent: line.scannedBarcode || line.materialCode,
          quantity: line.pendingQty,
          lineNo: inboundLine?.lineNo,
        })
      }

      await completeInbound(inboundNo)
      addLog({ ok: true, msg: `入库完成：${inboundNo}` })
      uni.showModal({
        title: '提交成功',
        content: `入库单 ${inboundNo} 已生成并完成收货`,
        showCancel: false,
        success: () => {
          reset()
        },
      })
      return true
    } catch (e) {
      showError(e?.message || '提交入库失败')
      return false
    } finally {
      processing.value = false
    }
  }

  function reset() {
    phase.value = 'scan_order'
    purchaseOrder.value = null
    lines.value = []
    lastHighlightLineNo.value = null
    lastError.value = ''
    scanLog.value = []
  }

  function clearLog() {
    scanLog.value = []
  }

  return {
    phase,
    processing,
    purchaseOrder,
    lines,
    scanLog,
    lastHighlightLineNo,
    lastError,
    confirmedCount,
    totalCount,
    allConfirmed,
    progressPercent,
    handleScan,
    submitInbound,
    reset,
    clearLog,
  }
}

export default usePurchaseInboundScan
