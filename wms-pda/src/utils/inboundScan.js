/**
 * 从二维码内容解析入库单号或内嵌明细
 */
export function parseInboundOrderNo(barcode) {
  const raw = (barcode || '').trim()
  if (!raw) return ''
  if (/^IN[:：]/i.test(raw)) return raw.replace(/^IN[:：]/i, '').trim().toUpperCase()
  const match = raw.match(/IN\d{6,}/i)
  if (match) return match[0].toUpperCase()
  return raw.toUpperCase()
}

export function isInboundOrderBarcode(barcode) {
  const raw = (barcode || '').trim()
  if (!raw) return false
  try {
    const json = JSON.parse(raw)
    if (json.orderNo || json.inboundOrderNo) return true
    if (Array.isArray(json.lines) || Array.isArray(json.details)) return true
  } catch {
    // not json
  }
  if (/^IN[:：]/i.test(raw)) return true
  return /^IN\d{6,}/i.test(parseInboundOrderNo(raw))
}

/**
 * 解析入库单二维码：支持 JSON 内嵌明细或单号
 * @returns {{ type: 'embedded'|'orderNo'|'unknown', orderNo: string, lines: Array|null, raw: string }}
 */
export function parseInboundOrderQr(barcode) {
  const raw = (barcode || '').trim()
  if (!raw) return { type: 'unknown', orderNo: '', lines: null, raw: '' }
  try {
    const json = JSON.parse(raw)
    const orderNo = (json.orderNo || json.inboundOrderNo || '').toString().trim()
    const lines = json.lines || json.details || json.materials || null
    if (Array.isArray(lines) && lines.length) {
      return { type: 'embedded', orderNo, lines, raw }
    }
    if (orderNo) {
      return { type: 'orderNo', orderNo: parseInboundOrderNo(orderNo), lines: null, raw }
    }
  } catch {
    // not json
  }
  if (isInboundOrderBarcode(raw)) {
    return { type: 'orderNo', orderNo: parseInboundOrderNo(raw), lines: null, raw }
  }
  return { type: 'unknown', orderNo: '', lines: null, raw }
}

export default {
  parseInboundOrderNo,
  isInboundOrderBarcode,
  parseInboundOrderQr,
}
