import request from '@/utils/request'
import type { PageQuery, PageResult } from './types'

export interface QcStandard {
  id?: number
  standardCode: string
  standardName: string
  materialCode?: string
  checkItems?: string
  status?: number
}

export interface QcOrder {
  id?: number
  qcNo?: string
  sourceType: string
  sourceNo: string
  materialCode: string
  batchNo?: string
  sampleQty: number
  status?: string
  result?: string
  inspectorId?: string
  inspectorName?: string
  inspectTime?: string
  remark?: string
}

export function getQcStandards(params: PageQuery) {
  return request.get<any, PageResult<QcStandard>>('/quality/standards', { params })
}

export function createQcStandard(data: QcStandard) {
  return request.post('/quality/standards', data)
}

export function updateQcStandard(code: string, data: QcStandard) {
  return request.put(`/quality/standards/${code}`, data)
}

export function deleteQcStandard(code: string) {
  return request.delete(`/quality/standards/${code}`)
}

export function getQcOrders(params: PageQuery) {
  return request.get<any, PageResult<QcOrder>>('/quality/orders', { params })
}

export function getQcOrder(orderNo: string) {
  return request.get<any, QcOrder>(`/quality/orders/${orderNo}`)
}

export function createQcOrder(data: QcOrder) {
  return request.post('/quality/orders', data)
}

export function submitQcResult(orderNo: string, data: { result: string; remark?: string }) {
  return request.put(`/quality/orders/${orderNo}/complete`, data)
}

export function concessionQcOrder(orderNo: string, remark?: string) {
  return request.post(`/quality/orders/${orderNo}/concession`, { remark })
}
