import request from '@/utils/request'
import type { PageQuery, PageResult } from './types'

export interface Warehouse {
  id?: number
  warehouseCode: string
  warehouseName: string
  warehouseType: string
  erpWarehouseCode?: string
  factoryCode?: string
  address?: string
  managerId?: string
  phone?: string
  status?: number
  remark?: string
}

export function getWarehouses(params: PageQuery) {
  return request.get<any, PageResult<Warehouse>>('/base/warehouses', { params })
}

export function getWarehouse(code: string) {
  return request.get<any, Warehouse>(`/base/warehouses/${code}`)
}

export function createWarehouse(data: Warehouse) {
  return request.post('/base/warehouses', data)
}

export function updateWarehouse(code: string, data: Warehouse) {
  return request.put(`/base/warehouses/${code}`, data)
}

export function deleteWarehouse(code: string) {
  return request.delete(`/base/warehouses/${code}`)
}
