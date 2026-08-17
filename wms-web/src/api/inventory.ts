import request from '@/utils/request'
import type { PageQuery, PageResult } from './types'

export interface InventoryItem {
  id: number
  warehouseCode: string
  locationCode?: string
  materialCode: string
  /** 标签号（批次号） */
  labelNo?: string
  batchNo?: string
  materialName?: string
  specification?: string
  unitCode?: string
  stockQty: number
  availableQty: number
  frozenQty?: number
  stockStatus?: string
  productionDate?: string
  createTime?: string
}

export interface InventoryTransaction {
  transactionNo: string
  transactionType: string
  warehouseCode: string
  locationCode: string
  materialCode: string
  batchNo: string
  transactionQty: number
  beforeQty: number
  afterQty: number
  sourceOrderType?: string
  sourceOrderNo?: string
  operatorName: string
  operationTime: string
  remark?: string
}

export interface InventorySummary {
  warehouseCode: string
  materialCode: string
  materialName?: string
  totalQty: number
  availableQty: number
  frozenQty: number
}

export interface InventoryWarning {
  warehouseCode: string
  materialCode: string
  materialName?: string
  currentQty: number
  safetyQty: number
  warningType: string
}

export function getInventoryList(params: PageQuery) {
  return request.get<any, PageResult<InventoryItem>>('/inventory/list', { params })
}

export function getInventorySummary(params: PageQuery) {
  return request.get<any, PageResult<InventorySummary>>('/inventory/summary', { params })
}

export function getInventoryTransactions(params: PageQuery) {
  return request.get<any, PageResult<InventoryTransaction>>('/inventory/transactions', { params })
}

export function getInventoryWarnings(params: PageQuery) {
  return request.get<any, PageResult<InventoryWarning>>('/inventory/warnings', { params })
}

export function updateInventoryStockStatus(id: number, stockStatus: string) {
  return request.put<any, InventoryItem>(`/inventory/${id}/stock-status`, { stockStatus })
}
