/**
 * 解析物料条码并匹配本单据明细（本地优先，减少扫一枪等接口）。
 * 支持：编码|批次|数量、编码|数量、纯编码；数量可为小数（kg 等），可带单位后缀。
 */

/** 解析数量段：1.25 / 1.25kg / 0.5KG */
export function parseQtyToken(token) {
  const text = String(token || '').trim()
  if (!text) return null
  const m = text.match(/^(\d+(?:\.\d+)?)\s*(?:kg|g|t|吨|千克|公斤|pcs|pc|ea)?$/i)
  if (!m) return null
  const n = Number(m[1])
  return Number.isFinite(n) && n > 0 ? n : null
}

export function parseMaterialBarcode(raw) {
  const text = String(raw || '').trim()
  if (!text) return null
  const parts = text.split('|').map((s) => s.trim()).filter(Boolean)
  if (parts.length >= 2) {
    const lastQty = parseQtyToken(parts[parts.length - 1])
    let batchNo = ''
    let qty = null
    if (lastQty != null) {
      qty = lastQty
      // 编码|数量 或 编码|批次|…|数量（与后端取末段数量一致）
      batchNo = parts.length >= 3 ? parts[1] || '' : ''
    } else {
      batchNo = parts[1] || ''
      if (parts.length >= 3) {
        qty = parseQtyToken(parts[2])
      }
    }
    return {
      materialCode: parts[0],
      batchNo,
      qty,
      mode: 'pipe',
    }
  }
  return { materialCode: text, batchNo: '', qty: null, mode: 'code' }
}

export function matchLocalBillLine(lines, barcode) {
  const parsed = parseMaterialBarcode(barcode)
  if (!parsed || !Array.isArray(lines) || !lines.length) return null
  const code = String(parsed.materialCode || '').toLowerCase()
  if (!code) return null

  let candidates = lines.filter((l) => String(l.materialCode || '').toLowerCase() === code)
  if (!candidates.length) {
    candidates = lines.filter((l) => String(l.materialCode || '').toLowerCase().startsWith(code))
  }
  if (!candidates.length) return null

  if (parsed.batchNo) {
    const batch = String(parsed.batchNo).toLowerCase()
    const byBatch = candidates.filter((l) => String(l.batchNo || '').toLowerCase() === batch)
    if (byBatch.length) candidates = byBatch
  }

  const open = candidates.find((l) => {
    const plan = Number(l.planQty) || 0
    const submitted = Number(l.submittedQty) || 0
    return plan <= 0 || submitted < plan
  })
  const line = open || candidates[0]
  return { line, parsed }
}

export default { parseMaterialBarcode, matchLocalBillLine, parseQtyToken }
