/**
 * 统一扫码 API
 */
import request, { withDevice } from '../utils/http.js'

export function recognizeBarcode(barcodeContent, warehouseCode) {
  return request({
    url: '/mobile/scan/recognize',
    method: 'POST',
    data: { barcodeContent, warehouseCode },
  })
}

export function matchInventory(barcodeContent, warehouseCode) {
  return request({
    url: '/mobile/scan/match-inventory',
    method: 'POST',
    data: { barcodeContent, warehouseCode },
  })
}

export function scanInboundByBarcode(orderNo, data) {
  return request({
    url: `/mobile/scan/inbound/${orderNo}`,
    method: 'POST',
    data: withDevice(data),
  })
}

export function scanOutboundByBarcode(orderNo, data) {
  return request({
    url: `/mobile/scan/outbound/${orderNo}`,
    method: 'POST',
    data: withDevice(data),
  })
}

/** 无单扫码出库（预览/确认） */
export function scanOutboundDirect(data) {
  return request({
    url: '/mobile/scan/outbound-direct',
    method: 'POST',
    data: withDevice(data),
  })
}

export default {
  recognizeBarcode,
  matchInventory,
  scanInboundByBarcode,
  scanOutboundByBarcode,
  scanOutboundDirect,
}
