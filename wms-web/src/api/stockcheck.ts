import request from '@/utils/request'
import type { PageQuery, PageResult } from './types'

export interface StockcheckPlan {
  id?: number
  planNo?: string
  planName: string
  warehouseCode: string
  planType: string
  planDate: string
  status?: string
  creatorId?: string
  creatorName?: string
  remark?: string
}

export interface StockcheckTask {
  id?: number
  taskNo: string
  planNo: string
  warehouseCode: string
  locationCode?: string
  assigneeId?: string
  status: string
  createTime?: string
  completeTime?: string
}

export interface StockcheckDiff {
  id?: number
  taskNo: string
  materialCode: string
  locationCode: string
  batchNo: string
  diffQty: number
  diffReason?: string
  status: string
  approverId?: string
  approveTime?: string
}

export function getStockcheckPlans(params: PageQuery) {
  return request.get<any, PageResult<StockcheckPlan>>('/stockcheck/plans', { params })
}

export function createStockcheckPlan(data: StockcheckPlan) {
  return request.post<any, StockcheckPlan>('/stockcheck/plans', data)
}

export function updateStockcheckPlan(planNo: string, data: StockcheckPlan) {
  return request.put(`/stockcheck/plans/${planNo}`, data)
}

export function deleteStockcheckPlan(planNo: string) {
  return request.delete(`/stockcheck/plans/${planNo}`)
}

export function publishStockcheckPlan(planNo: string) {
  return request.put(`/stockcheck/plans/${planNo}/publish`)
}

export function getStockcheckTasks(params: PageQuery) {
  return request.get<any, PageResult<StockcheckTask>>('/stockcheck/tasks', { params })
}

export function getStockcheckTask(taskNo: string) {
  return request.get<any, { task: StockcheckTask; details: Record<string, unknown>[] }>(
    `/stockcheck/tasks/${taskNo}`,
  )
}

export function getStockcheckDiffs(params: PageQuery) {
  return request.get<any, PageResult<StockcheckDiff>>('/stockcheck/diffs', { params })
}

export function approveStockcheckDiff(diffId: number) {
  return request.put(`/stockcheck/diffs/${diffId}/approve`)
}

export function rejectStockcheckDiff(diffId: number, reason?: string) {
  return request.post(`/stockcheck/diffs/${diffId}/reject`, { reason })
}
