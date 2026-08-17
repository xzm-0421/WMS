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

export function updateNoticeLineQty(billType, billNo, lineNo, qty, auxQty) {
  const data = { qty }
  if (auxQty != null && auxQty !== '') {
    data.auxQty = auxQty
  }
  return request({
    url: `${baseUrl(billType)}/${billNo}/lines/${lineNo}/qty`,
    method: 'PUT',
    data,
  })
}

export function submitNoticeBill(billType, billNo, data = {}) {
  return request({
    url: `${baseUrl(billType)}/${billNo}/submit`,
    method: 'POST',
    data: withDevice(data),
    // 由页面 Modal 展示完整金蝶成败信息，避免 http 层 Toast 截断/重复
    silent: true,
    timeout: 180000,
  })
}

export function heartbeatNoticeBillLock(billType, billNo) {
  return request({
    url: `${baseUrl(billType)}/${encodeURIComponent(billNo)}/lock/heartbeat`,
    method: 'POST',
    silent: true,
  })
}

export function releaseNoticeBillLock(billType, billNo) {
  return request({
    url: `${baseUrl(billType)}/${encodeURIComponent(billNo)}/lock/release`,
    method: 'POST',
    silent: true,
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
  heartbeatNoticeBillLock,
  releaseNoticeBillLock,
}
