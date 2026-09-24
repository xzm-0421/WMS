import request from '@/utils/request'
import type { PageQuery, PageResult } from './types'

export interface QcStandard {
  id?: number
  standardCode?: string
  standardName?: string
  materialCode?: string
  materialName?: string
  qcType?: string
  /** 创建请求字段 */
  qcItems?: string
  /** 响应字段（后端实体为 checkItems） */
  checkItems?: string
  status?: number
  createTime?: string
}

export interface QcOrder {
  id?: number
  qcNo?: string
  sourceType?: string
  sourceNo?: string
  materialCode?: string
  materialName?: string
  batchNo?: string
  qcType?: string
  qcQty?: number
  qualifiedQty?: number
  unqualifiedQty?: number
  status?: string
  result?: string
  judgeResult?: string
  judgeRemark?: string
  judgeTime?: string
  judgeBy?: string
  concessionReason?: string
  concessionApprover?: string
  concessionTime?: string
  createTime?: string
}

export interface QcJudgeRequest {
  qualifiedQty?: number
  unqualifiedQty?: number
  judgeResult: string
  judgeRemark?: string
  judgeBy?: string
}

export interface QcConcessionRequest {
  concessionReason: string
  approver?: string
}

export function getQcStandards(params: PageQuery) {
  return request.get<any, PageResult<QcStandard>>('/quality/standards', { params })
}

export function createQcStandard(data: QcStandard) {
  return request.post('/quality/standards', data)
}

export function getQcOrders(params: PageQuery) {
  return request.get<any, PageResult<QcOrder>>('/quality/orders', { params })
}

export function createQcOrder(data: QcOrder) {
  return request.post<any, string>('/quality/orders', data)
}

export function judgeQcOrder(orderNo: string, data: QcJudgeRequest) {
  return request.put(`/quality/orders/${orderNo}/judge`, data)
}

export function concessionQcOrder(orderNo: string, data: QcConcessionRequest) {
  return request.put(`/quality/orders/${orderNo}/concession`, data)
}
