import request from '@/utils/request'
import type { PageQuery, PageResult } from './types'
import { flattenOrderDetailVo } from './types'

export interface InboundOrder {
  id?: number
  orderNo?: string
  orderType: string
  warehouseCode: string
  supplierCode?: string
  productionOrderNo?: string
  sourceOrderNo?: string
  planDate: string
  actualDate?: string
  status?: string
  creatorId?: string
  creatorName?: string
  auditorId?: string
  auditorName?: string
  auditTime?: string
  remark?: string
  details?: InboundOrderDetail[]
}

export interface InboundOrderDetail {
  id?: number
  orderNo?: string
  lineNo?: number
  materialCode: string
  materialName?: string
  specification?: string
  unitCode: string
  orderQty: number
  receivedQty?: number
  batchNo?: string
  productionDate?: string
  expireDate?: string
  targetLocation?: string
  qcStatus?: string
  lineStatus?: string
  remark?: string
}

export function getInboundOrders(params: PageQuery) {
  return request.get<any, PageResult<InboundOrder>>('/inbound/orders', { params })
}

export function getInboundOrder(orderNo: string) {
  return request
    .get<any, InboundOrder | { order: InboundOrder; details: InboundOrderDetail[] }>(`/inbound/orders/${orderNo}`)
    .then(flattenOrderDetailVo<InboundOrder, InboundOrderDetail>)
}

export function createInboundOrder(data: InboundOrder) {
  return request.post<any, InboundOrder>('/inbound/orders', data)
}

export function updateInboundOrder(orderNo: string, data: InboundOrder) {
  return request.put(`/inbound/orders/${orderNo}`, data)
}

export function deleteInboundOrder(orderNo: string) {
  return request.delete(`/inbound/orders/${orderNo}`)
}

export function submitInboundOrder(orderNo: string) {
  return request.put(`/inbound/orders/${orderNo}/submit`)
}

export function auditInboundOrder(orderNo: string) {
  return request.put(`/inbound/orders/${orderNo}/audit`)
}

export function reverseAuditInboundOrder(orderNo: string) {
  return request.put(`/inbound/orders/${orderNo}/unaudit`)
}

export function cancelInboundOrder(orderNo: string) {
  return request.put(`/inbound/orders/${orderNo}/cancel`)
}

export function closeInboundOrder(orderNo: string) {
  return request.post(`/inbound/orders/${orderNo}/close`)
}

export function addInboundDetail(orderNo: string, data: InboundOrderDetail) {
  return request.post(`/inbound/orders/${orderNo}/details`, data)
}

export function updateInboundDetail(orderNo: string, lineNo: number, data: InboundOrderDetail) {
  return request.put(`/inbound/orders/${orderNo}/details/${lineNo}`, data)
}

export function deleteInboundDetail(orderNo: string, lineNo: number) {
  return request.delete(`/inbound/orders/${orderNo}/details/${lineNo}`)
}
