/**
 * 解析物料条码并匹配本单据明细（本地优先，减少扫一枪等接口）。
 * 支持：编码|批次|数量、纯编码、编码+批次模糊。
 */
export function parseMaterialBarcode(raw) {
  const text = String(raw || '').trim()
  if (!text) return null
  const parts = text.split('|').map((s) => s.trim()).filter(Boolean)
  if (parts.length >= 2) {
    const qty = parts.length >= 3 ? Number(parts[2]) : null
    return {
      materialCode: parts[0],
      batchNo: parts[1] || '',
      qty: Number.isFinite(qty) && qty > 0 ? qty : null,
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

export default { parseMaterialBarcode, matchLocalBillLine }
