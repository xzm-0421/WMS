/**
 * PDA 金蝶盘点作业 API（未审核单据 → 实盘 → 提交审核）
 */
import request, { withDevice } from '../utils/http.js'

export function listStockCountBills(params = {}) {
  return request({ url: '/mobile/stock-count', data: params })
}

export function resolveStockCountBarcode(barcodeContent) {
  return request({
    url: '/mobile/stock-count/resolve-barcode',
    method: 'POST',
    data: { barcodeContent },
  })
}

export function getStockCountDetail(billNo, forceRefresh = false) {
  return request({
    url: `/mobile/stock-count/${encodeURIComponent(billNo)}`,
    data: forceRefresh ? { forceRefresh: true } : undefined,
  })
}

export function matchStockCountLine(billNo, data) {
  return request({
    url: `/mobile/stock-count/${encodeURIComponent(billNo)}/match`,
    method: 'POST',
    data: withDevice(data || {}),
    silent: true,
  })
}

export function scanStockCountLine(billNo, data) {
  return request({
    url: `/mobile/stock-count/${encodeURIComponent(billNo)}/scan`,
    method: 'POST',
    data: withDevice(data || {}),
  })
}

export function updateStockCountLineQty(billNo, lineNo, actualQty) {
  return request({
    url: `/mobile/stock-count/${encodeURIComponent(billNo)}/lines/${lineNo}/qty`,
    method: 'PUT',
    data: { actualQty },
  })
}

export function completeStockCount(billNo) {
  return request({
    url: `/mobile/stock-count/${encodeURIComponent(billNo)}/complete`,
    method: 'POST',
  })
}

export function heartbeatStockCountLock(billNo) {
  return request({
    url: `/mobile/stock-count/${encodeURIComponent(billNo)}/lock/heartbeat`,
    method: 'POST',
    silent: true,
  })
}

export function releaseStockCountLock(billNo) {
  return request({
    url: `/mobile/stock-count/${encodeURIComponent(billNo)}/lock/release`,
    method: 'POST',
    silent: true,
  })
}

export default {
  listStockCountBills,
  resolveStockCountBarcode,
  getStockCountDetail,
  matchStockCountLine,
  scanStockCountLine,
  updateStockCountLineQty,
  completeStockCount,
  heartbeatStockCountLock,
  releaseStockCountLock,
}
