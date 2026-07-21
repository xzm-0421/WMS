/**
 * 采购单 / 送货单 API（PDA 扫码入库）
 */
import request from '../utils/http.js'

export function getPurchaseOrder(orderNo) {
  return request({ url: `/incoming/purchase-orders/${orderNo}` })
}

export function createDeliveryNote(data) {
  return request({
    url: '/incoming/delivery-notes',
    method: 'POST',
    data,
  })
}

export function generateInboundFromDelivery(deliveryNo) {
  return request({
    url: `/incoming/delivery-notes/${deliveryNo}/generate-inbound`,
    method: 'POST',
  })
}

export default {
  getPurchaseOrder,
  createDeliveryNote,
  generateInboundFromDelivery,
}
