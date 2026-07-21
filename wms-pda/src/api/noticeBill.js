/**
 * PDA 通知单统一扫码 API
 */
import request, { withDevice } from '../utils/http.js'

function baseUrl(billType) {
  return `/mobile/notice-bill/${billType}`
}

export function listNoticeBillTypes(direction) {
  return request({ url: '/mobile/notice-bill/types', data: direction ? { direction } : {} })
}

export function listNoticeBills(billType, params = {}) {
  return request({ url: baseUrl(billType), data: params })
}

export function resolveNoticeBarcode(billType, barcodeContent) {
  return request({
    url: `${baseUrl(billType)}/resolve-barcode`,
    method: 'POST',
    data: { barcodeContent },
  })
}

export function getNoticeBillDetail(billType, billNo, options = {}) {
  const data = {}
  if (options.refresh) data.refresh = true
  return request({ url: `${baseUrl(billType)}/${billNo}`, data })
}

export function scanNoticeLine(billType, billNo, barcodeContent) {
  return request({
    url: `${baseUrl(billType)}/${billNo}/scan`,
    method: 'POST',
    data: withDevice({ barcodeContent }),
    silent: true,
  })
}

export function toggleNoticeLine(billType, billNo, lineNo, checked) {
  return request({
    url: `${baseUrl(billType)}/${billNo}/lines/${lineNo}/check?checked=${checked ? 'true' : 'false'}`,
    method: 'PUT',
  })
}

export function updateNoticeLineQty(billType, billNo, lineNo, qty) {
  return request({
    url: `${baseUrl(billType)}/${billNo}/lines/${lineNo}/qty`,
    method: 'PUT',
    data: { qty },
  })
}

export function submitNoticeBill(billType, billNo, data = {}) {
  return request({
    url: `${baseUrl(billType)}/${billNo}/submit`,
    method: 'POST',
    data: withDevice(data),
  })
}

export default {
  listNoticeBillTypes,
  listNoticeBills,
  resolveNoticeBarcode,
  getNoticeBillDetail,
  scanNoticeLine,
  toggleNoticeLine,
  updateNoticeLineQty,
  submitNoticeBill,
}
