import { parseMobileBarcode } from '@/api/mobile.js'

/** 调起扫码；H5 无摄像头时弹窗手动输入 */
export function scanCode(title = '扫描条码') {
  return new Promise((resolve, reject) => {
    uni.scanCode({
      onlyFromCamera: false,
      success: (res) => resolve((res.result || '').trim()),
      fail: () => promptBarcode(title).then(resolve).catch(reject),
    })
  })
}

function promptBarcode(title) {
  return new Promise((resolve, reject) => {
    uni.showModal({
      title,
      editable: true,
      placeholderText: '请输入或粘贴条码',
      success: (res) => {
        if (res.confirm && res.content?.trim()) resolve(res.content.trim())
        else reject(new Error('cancel'))
      },
      fail: reject,
    })
  })
}

/** 本地启发式解析（无规则库时兜底） */
export function parseBarcodeLocal(content) {
  const raw = (content || '').trim()
  const result = {
    raw,
    materialCode: '',
    batchNo: '',
    locationCode: '',
    barcodeContent: raw,
  }
  if (!raw) return result
  if (/^WH\d{2}/i.test(raw)) {
    result.locationCode = raw
    return result
  }
  if (raw.includes('|')) {
    const [materialCode, batchNo] = raw.split('|')
    result.materialCode = materialCode.trim()
    result.batchNo = (batchNo || '').trim()
    return result
  }
  if (raw.length >= 11) {
    result.materialCode = raw.substring(0, 11)
    result.batchNo = raw.substring(11)
  } else {
    result.materialCode = raw
  }
  return result
}

/** 优先调用 PDA 服务端解析，失败则本地解析 */
export async function resolveBarcode(content) {
  const local = parseBarcodeLocal(content)
  if (!content) return local
  try {
    const data = await parseMobileBarcode(content)
    const segments = data.segments || {}
    return {
      raw: content,
      barcodeContent: content,
      materialCode: segments.materialCode || local.materialCode,
      batchNo: segments.batchNo || local.batchNo,
      locationCode: segments.locationCode || local.locationCode,
      segments,
    }
  } catch {
    return local
  }
}

/** 扫码并解析 */
export async function scanAndParse(title) {
  const content = await scanCode(title)
  return resolveBarcode(content)
}

/** 从条码内容解析采购单号（支持 PO 前缀及嵌入格式） */
export function parsePurchaseOrderNo(barcode) {
  const raw = (barcode || '').trim()
  if (!raw) return ''
  if (/^PO[\w-]+$/i.test(raw)) return raw.toUpperCase()
  const match = raw.match(/PO[\w-]{6,}/i)
  if (match) return match[0].toUpperCase()
  return raw
}

export default {
  scanCode,
  parseBarcodeLocal,
  resolveBarcode,
  scanAndParse,
  parsePurchaseOrderNo,
}
