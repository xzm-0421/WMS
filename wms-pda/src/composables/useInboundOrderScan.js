import { ref, computed } from 'vue'
import { getInboundOrder, completeInbound } from '@/api/mobile.js'
import { recognizeBarcode, scanInboundByBarcode } from '@/api/scan.js'
import { getMaterial } from '@/api/order.js'
import { parseBarcodeLocal } from '@/utils/scan.js'
import { parseInboundOrderQr, isInboundOrderBarcode } from '@/utils/inboundScan.js'

const SCANNABLE_STATUS = ['INBOUND', 'PENDING', 'APPROVED', 'OPEN', 'PARTIAL']

function normalizeQty(val) {
  const n = Number(val)
  return Number.isFinite(n) ? n : 0
}

function mapEmbeddedLine(raw, index) {
  return {
    lineNo: raw.lineNo ?? index + 1,
    materialCode: raw.materialCode || raw.code || '',
    materialName: raw.materialName || raw.name || '',
    specification: raw.specification || raw.spec || '',
    unitCode: raw.unitCode || raw.unit || '',
    orderQty: normalizeQty(raw.orderQty ?? raw.qty ?? raw.quantity),
    receivedQty: normalizeQty(raw.receivedQty ?? raw.received),
    batchNo: raw.batchNo || '',
    lineStatus: raw.lineStatus || 'PENDING',
  }
}

/**
 * 入库单扫码：扫单加载明细，扫物料收货
 */
export function useInboundOrderScan() {
  const phase = ref('scan_order') // scan_order | receive_items
  const processing = ref(false)
  const inboundOrder = ref(null)
  const lines = ref([])
  const lastHighlightLineNo = ref(null)

  const totalCount = computed(() => lines.value.length)
  const completedCount = computed(() =>
    lines.value.filter((l) => l.receivedQty >= l.orderQty && l.orderQty > 0).length,
  )
  const allCompleted = computed(
    () => totalCount.value > 0 && completedCount.value === totalCount.value,
  )

  async function enrichLine(detail, index) {
    const orderQty = normalizeQty(detail.orderQty)
    const receivedQty = normalizeQty(detail.receivedQty)
    let specification = detail.specification || ''
    if (!specification && detail.materialCode) {
      try {
        const mat = await getMaterial(detail.materialCode)
        specification = mat.specification || ''
      } catch {
        // ignore
      }
    }
    return {
      lineNo: detail.lineNo ?? index + 1,
      materialCode: detail.materialCode || '',
      materialName: detail.materialName || '',
      specification,
      unitCode: detail.unitCode || '',
      orderQty,
      receivedQty,
      pendingQty: Math.max(0, orderQty - receivedQty),
      batchNo: detail.batchNo || '',
      lineStatus: detail.lineStatus || 'PENDING',
    }
  }

  async function applyOrderData(order, rawLines) {
    if (!order?.orderNo && !rawLines?.length) {
      throw new Error('入库单数据无效')
    }
    const status = order?.status
    if (status && !SCANNABLE_STATUS.includes(status)) {
      throw new Error(`入库单状态不可收货（${status}）`)
    }
    const enriched = []
    for (let i = 0; i < rawLines.length; i++) {
      enriched.push(await enrichLine(rawLines[i], i))
    }
    if (!enriched.length) {
      throw new Error('该入库单无物料明细')
    }
    inboundOrder.value = order || { orderNo: '', warehouseCode: 'WH01' }
    lines.value = enriched
    phase.value = 'receive_items'
    lastHighlightLineNo.value = null
    return inboundOrder.value
  }

  async function loadInboundOrder(orderNo) {
    const data = await getInboundOrder(orderNo)
    const order = {
      orderNo: data.orderNo,
      orderType: data.orderType,
      warehouseCode: data.warehouseCode,
      supplierCode: data.supplierCode,
      status: data.status,
    }
    return applyOrderData(order, data.details || [])
  }

  async function loadFromQr(barcode) {
    const parsed = parseInboundOrderQr(barcode)
    if (parsed.type === 'embedded') {
      const order = {
        orderNo: parsed.orderNo || '内嵌明细',
        warehouseCode: 'WH01',
        status: 'INBOUND',
      }
      const rawLines = parsed.lines.map(mapEmbeddedLine)
      await applyOrderData(order, rawLines)
      if (parsed.orderNo && /^IN\d/i.test(parsed.orderNo)) {
        try {
          await loadInboundOrder(parsed.orderNo)
        } catch {
          // 内嵌明细已展示，服务端拉取失败不阻断
        }
      }
      return inboundOrder.value
    }
    if (parsed.type === 'orderNo' && parsed.orderNo) {
      return loadInboundOrder(parsed.orderNo)
    }
    throw new Error('无法识别入库单二维码')
  }

  async function handleOrderScan(barcode) {
    processing.value = true
    try {
      await loadFromQr(barcode)
      uni.showToast({ title: '明细已加载', icon: 'success' })
      return true
    } catch (e) {
      uni.showToast({ title: e?.message || '加载入库单失败', icon: 'none' })
      return false
    } finally {
      processing.value = false
    }
  }

  async function parseMaterialBarcode(barcode) {
    const local = parseBarcodeLocal(barcode)
    const wh = inboundOrder.value?.warehouseCode
    try {
      const data = await recognizeBarcode(barcode, wh)
      return {
        materialCode: data.materialCode || local.materialCode,
        batchNo: data.batchNo || local.batchNo,
        materialName: data.materialName || '',
        specification: data.specification || '',
        parseFailed: !data.materialCode && !local.materialCode,
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

  async function handleMaterialScan(barcode) {
    if (phase.value !== 'receive_items' || !inboundOrder.value?.orderNo) {
      uni.showToast({ title: '请先扫描入库单二维码', icon: 'none' })
      return false
    }
    processing.value = true
    lastHighlightLineNo.value = null
    try {
      const parsed = await parseMaterialBarcode(barcode)
      if (parsed.parseFailed || !parsed.materialCode) {
        uni.showToast({ title: '条码解析失败', icon: 'none' })
        return false
      }

      const target = lines.value.find(
        (l) => l.materialCode === parsed.materialCode && l.pendingQty > 0,
      )
      if (!target) {
        const done = lines.value.find((l) => l.materialCode === parsed.materialCode)
        uni.showToast({
          title: done ? '该物料已收满' : '物料不在本单明细中',
          icon: 'none',
        })
        return false
      }

      const qty = Math.min(1, target.pendingQty)
      const result = await scanInboundByBarcode(inboundOrder.value.orderNo, {
        barcodeContent: barcode,
        quantity: qty,
        lineNo: target.lineNo,
      })

      target.receivedQty = normalizeQty(result.receivedQty ?? target.receivedQty + qty)
      target.pendingQty = Math.max(0, target.orderQty - target.receivedQty)
      if (parsed.batchNo) target.batchNo = parsed.batchNo
      if (parsed.materialName && !target.materialName) target.materialName = parsed.materialName
      if (parsed.specification && !target.specification) target.specification = parsed.specification
      lastHighlightLineNo.value = target.lineNo

      uni.showToast({
        title: `✓ ${target.materialName || target.materialCode}`,
        icon: 'success',
        duration: 800,
      })
      return true
    } catch (e) {
      uni.showToast({ title: e?.message || '收货失败', icon: 'none' })
      return false
    } finally {
      processing.value = false
    }
  }

  async function handleScan(barcode) {
    if (!barcode?.trim() || processing.value) return
    const raw = barcode.trim()
    if (phase.value === 'scan_order' || isInboundOrderBarcode(raw)) {
      return handleOrderScan(raw)
    }
    return handleMaterialScan(raw)
  }

  async function finishInbound() {
    if (!inboundOrder.value?.orderNo) return false
    if (!allCompleted.value) {
      uni.showModal({
        title: '尚未收满',
        content: `还有 ${totalCount.value - completedCount.value} 项未完成，是否仍要完结？`,
        success: async (res) => {
          if (res.confirm) await doComplete()
        },
      })
      return false
    }
    return doComplete()
  }

  async function doComplete() {
    processing.value = true
    try {
      await completeInbound(inboundOrder.value.orderNo)
      uni.showModal({
        title: '入库完成',
        content: `入库单 ${inboundOrder.value.orderNo} 已完结`,
        showCancel: false,
        success: () => reset(),
      })
      return true
    } catch (e) {
      uni.showToast({ title: e?.message || '完结失败', icon: 'none' })
      return false
    } finally {
      processing.value = false
    }
  }

  function reset() {
    phase.value = 'scan_order'
    inboundOrder.value = null
    lines.value = []
    lastHighlightLineNo.value = null
  }

  return {
    phase,
    processing,
    inboundOrder,
    lines,
    lastHighlightLineNo,
    totalCount,
    completedCount,
    allCompleted,
    handleScan,
    finishInbound,
    reset,
  }
}

export default useInboundOrderScan
