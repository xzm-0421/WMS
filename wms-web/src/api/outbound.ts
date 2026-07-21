import request from '@/utils/request'
import type { PageQuery, PageResult } from './types'
import { flattenOrderDetailVo } from './types'

export interface OutboundOrder {
  id?: number
  orderNo?: string
  orderType: string
  warehouseCode: string
  productionOrderNo?: string
  customerCode?: string
  planDate: string
  actualDate?: string
  status?: string
  creatorId?: string
  creatorName?: string
  auditorId?: string
  auditorName?: string
  auditTime?: string
  remark?: string
  details?: OutboundOrderDetail[]
}

export interface OutboundOrderDetail {
  id?: number
  orderNo?: string
  lineNo?: number
  materialCode: string
  materialName?: string
  specification?: string
  unitCode: string
  demandQty: number
  issuedQty?: number
  batchNo?: string
  sourceLocation?: string
  lineStatus?: string
  remark?: string
}

export interface RecommendLocation {
  materialCode: string
  batchNo: string
  locationCode: string
  availableQty: number
  expireDate?: string
}

export function getOutboundOrders(params: PageQuery) {
  return request.get<any, PageResult<OutboundOrder>>('/outbound/orders', { params })
}

export function getOutboundOrder(orderNo: string) {
  return request
    .get<any, OutboundOrder | { order: OutboundOrder; details: OutboundOrderDetail[] }>(`/outbound/orders/${orderNo}`)
    .then(flattenOrderDetailVo<OutboundOrder, OutboundOrderDetail>)
}

export function createOutboundOrder(data: OutboundOrder) {
  return request.post<any, OutboundOrder>('/outbound/orders', data)
}

export function updateOutboundOrder(orderNo: string, data: OutboundOrder) {
  return request.put(`/outbound/orders/${orderNo}`, data)
}

export function deleteOutboundOrder(orderNo: string) {
  return request.delete(`/outbound/orders/${orderNo}`)
}

export function submitOutboundOrder(orderNo: string) {
  return request.put(`/outbound/orders/${orderNo}/submit`)
}

export function auditOutboundOrder(orderNo: string) {
  return request.put(`/outbound/orders/${orderNo}/audit`)
}

export function reverseAuditOutboundOrder(orderNo: string) {
  return request.put(`/outbound/orders/${orderNo}/unaudit`)
}

export function cancelOutboundOrder(orderNo: string) {
  return request.put(`/outbound/orders/${orderNo}/cancel`)
}

export function closeOutboundOrder(orderNo: string) {
  return request.post(`/outbound/orders/${orderNo}/close`)
}

export function recommendLocations(orderNo: string) {
  return request.get<any, RecommendLocation[]>(`/outbound/orders/${orderNo}/recommend-locations`)
}
