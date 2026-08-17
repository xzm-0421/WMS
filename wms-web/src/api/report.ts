import request from '@/utils/request'
import type { PageQuery } from './types'

export interface DashboardStats {
  todayInboundCount: number
  todayOutboundCount: number
  skuCount: number
  pendingTaskCount: number
  pendingInbound?: number
  pendingOutbound?: number
  pendingStockcheck?: number
  pendingQc?: number
  weeklyTrend: {
    labels: string[]
    inbound: number[]
    outbound: number[]
  }
  /** @deprecated 工作台环形图已改为任务占比，保留兼容 */
  warehouseDistribution?: { name: string; value: number }[]
  /** 待处理任务分项（入库/出库/盘点/质检） */
  taskDistribution?: { name: string; value: number }[]
  warnings: InventoryWarningItem[]
  recentLogs: OperationLogItem[]
}

export interface InventoryWarningItem {
  materialCode: string
  materialName?: string
  warehouseCode: string
  currentQty: number
  safetyQty: number
  warningType: string
}

export interface OperationLogItem {
  operatorName: string
  module: string
  operationType: string
  operationContent: string
  operationTime: string
}

export interface ReportStatistics {
  labels: string[]
  inbound: number[]
  outbound: number[]
}

export function getDashboardStats() {
  return request.get<any, DashboardStats>('/reports/dashboard')
}

export function getInventorySummaryReport(params?: PageQuery) {
  return request.get('/reports/inventory/summary', { params })
}

export function getLowStockReport(params?: PageQuery) {
  return request.get('/reports/inventory/low-stock', { params })
}

export function getExpiryReport(params?: PageQuery) {
  return request.get('/reports/inventory/expiry', { params })
}

export function getInboundStatistics(params?: { startDate?: string; endDate?: string }) {
  return request.get<any, ReportStatistics>('/reports/inbound/statistics', { params })
}

export function getOutboundStatistics(params?: { startDate?: string; endDate?: string }) {
  return request.get<any, ReportStatistics>('/reports/outbound/statistics', { params })
}

export function getTurnoverAnalysis(params?: PageQuery) {
  return request.get('/reports/analysis/turnover', { params })
}

export function getDeadStockAnalysis(params?: PageQuery) {
  return request.get('/reports/analysis/dead-stock', { params })
}

export function getStockAgeAnalysis(params?: PageQuery) {
  return request.get('/reports/analysis/stock-age', { params })
}

export function getOperatorPerformance(params?: PageQuery) {
  return request.get('/reports/analysis/operator-performance', { params })
}

export function getWarehouseDashboard() {
  return request.get<any, Record<string, unknown>>('/reports/dashboard/warehouse')
}
