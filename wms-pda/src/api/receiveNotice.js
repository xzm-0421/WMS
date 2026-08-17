/**
 * PDA 收料通知单 API
 */
import request, { withDevice } from '../utils/http.js'

export function listReceiveNotices(params = {}) {
  return request({ url: '/mobile/receive-notice', data: params })
}

export function resolveReceiveBarcode(barcodeContent) {
  return request({
    url: '/mobile/receive-notice/resolve-barcode',
    method: 'POST',
    data: { barcodeContent },
  })
}

export function getReceiveNoticeDetail(billNo) {
  return request({ url: `/mobile/receive-notice/${billNo}` })
}

export function scanReceiveLine(billNo, barcodeContent) {
  return request({
    url: `/mobile/receive-notice/${billNo}/scan`,
    method: 'POST',
    data: withDevice({ barcodeContent }),
    silent: true,
  })
}

export function toggleReceiveLine(billNo, lineNo, checked) {
  return request({
    url: `/mobile/receive-notice/${billNo}/lines/${lineNo}/check?checked=${checked ? 'true' : 'false'}`,
    method: 'PUT',
  })
}

export function updateReceiveLineQty(billNo, lineNo, qty, auxQty) {
  const data = { qty }
  if (auxQty != null && auxQty !== '') {
    data.auxQty = auxQty
  }
  return request({
    url: `/mobile/receive-notice/${billNo}/lines/${lineNo}/qty`,
    method: 'PUT',
    data,
  })
}

export function submitReceiveInbound(billNo, data = {}) {
  return request({
    url: `/mobile/receive-notice/${billNo}/submit`,
    method: 'POST',
    data: withDevice(data),
    silent: true,
    timeout: 180000,
  })
}

export function heartbeatReceiveNoticeLock(billNo) {
  return request({
    url: `/mobile/receive-notice/${encodeURIComponent(billNo)}/lock/heartbeat`,
    method: 'POST',
    silent: true,
  })
}

export function releaseReceiveNoticeLock(billNo) {
  return request({
    url: `/mobile/receive-notice/${encodeURIComponent(billNo)}/lock/release`,
    method: 'POST',
    silent: true,
  })
}

export default {
  listReceiveNotices,
  resolveReceiveBarcode,
  getReceiveNoticeDetail,
  scanReceiveLine,
  toggleReceiveLine,
  updateReceiveLineQty,
  submitReceiveInbound,
  heartbeatReceiveNoticeLock,
  releaseReceiveNoticeLock,
}
