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

export function updateStockcheckPlan(planId: number, data: StockcheckPlan) {
  return request.put(`/stockcheck/plans/${planId}`, data)
}

export function deleteStockcheckPlan(planId: number) {
  return request.delete(`/stockcheck/plans/${planId}`)
}

export function publishStockcheckPlan(planId: number) {
  return request.post(`/stockcheck/plans/${planId}/publish`)
}

export function getStockcheckTasks(params: PageQuery) {
  return request.get<any, PageResult<StockcheckTask>>('/stockcheck/tasks', { params })
}

export function getStockcheckTask(taskId: number) {
  return request.get<any, StockcheckTask>(`/stockcheck/tasks/${taskId}`)
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
