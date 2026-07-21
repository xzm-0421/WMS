/**
 * 出入库单据 API（复用 Web 端接口，PDA 完整业务流程）
 */
import request from '../utils/http.js'

// ========== 入库 ==========
export function listInboundOrders(params = {}) {
  return request({ url: '/inbound/orders', data: params })
}

export function getInboundOrderDetail(orderNo) {
  return request({ url: `/inbound/orders/${orderNo}` })
}

export function createInboundOrder(data) {
  return request({ url: '/inbound/orders', method: 'POST', data })
}

export function submitInboundOrder(orderNo) {
  return request({ url: `/inbound/orders/${orderNo}/submit`, method: 'PUT' })
}

export function auditInboundOrder(orderNo) {
  return request({ url: `/inbound/orders/${orderNo}/audit`, method: 'PUT' })
}

export function cancelInboundOrder(orderNo) {
  return request({ url: `/inbound/orders/${orderNo}/cancel`, method: 'PUT' })
}

export function addInboundDetail(orderNo, detail) {
  return request({ url: `/inbound/orders/${orderNo}/details`, method: 'POST', data: detail })
}

// ========== 出库 ==========
export function listOutboundOrders(params = {}) {
  return request({ url: '/outbound/orders', data: params })
}

export function getOutboundOrderDetail(orderNo) {
  return request({ url: `/outbound/orders/${orderNo}` })
}

export function createOutboundOrder(data) {
  return request({ url: '/outbound/orders', method: 'POST', data })
}

export function submitOutboundOrder(orderNo) {
  return request({ url: `/outbound/orders/${orderNo}/submit`, method: 'PUT' })
}

export function auditOutboundOrder(orderNo) {
  return request({ url: `/outbound/orders/${orderNo}/audit`, method: 'PUT' })
}

export function cancelOutboundOrder(orderNo) {
  return request({ url: `/outbound/orders/${orderNo}/cancel`, method: 'PUT' })
}

// ========== 基础数据 ==========
export function getMaterial(materialCode) {
  return request({ url: `/base/materials/${materialCode}` })
}

export function listMaterials(params = {}) {
  return request({ url: '/base/materials', data: params })
}

// ========== 统一封装 ==========
export function listOrders(direction, params) {
  return direction === 'outbound' ? listOutboundOrders(params) : listInboundOrders(params)
}

export function getOrderDetail(direction, orderNo) {
  return direction === 'outbound'
    ? getOutboundOrderDetail(orderNo)
    : getInboundOrderDetail(orderNo)
}

export function createOrder(direction, data) {
  return direction === 'outbound' ? createOutboundOrder(data) : createInboundOrder(data)
}

export function submitOrder(direction, orderNo) {
  return direction === 'outbound'
    ? submitOutboundOrder(orderNo)
    : submitInboundOrder(orderNo)
}

export function auditOrder(direction, orderNo) {
  return direction === 'outbound'
    ? auditOutboundOrder(orderNo)
    : auditInboundOrder(orderNo)
}

export function cancelOrder(direction, orderNo) {
  return direction === 'outbound'
    ? cancelOutboundOrder(orderNo)
    : cancelInboundOrder(orderNo)
}

export default {
  listOrders,
  getOrderDetail,
  createOrder,
  submitOrder,
  auditOrder,
  cancelOrder,
  getMaterial,
}
