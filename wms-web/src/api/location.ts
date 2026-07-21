import request from '@/utils/request'
import type { PageResult } from './types'

export interface LocationNode {
  id: string
  label: string
  type: 'warehouse' | 'zone' | 'location'
  warehouseCode?: string
  warehouseName?: string
  zoneCode?: string
  zoneName?: string
  occupyStatus?: string
  locationType?: string
  children?: LocationNode[]
}

export interface Location {
  locationCode: string
  locationName?: string
  warehouseCode: string
  zoneCode?: string
  locationType?: string
  rowNo?: number
  colNo?: number
  layerNo?: number
  occupyStatus?: string
  status?: number
}

export function getLocationTree(warehouseCode?: string) {
  return request.get<any, LocationNode[]>('/base/locations/tree', { params: { warehouseCode } })
}

export function getLocations(params: Record<string, unknown>) {
  return request.get<any, PageResult<Location>>('/base/locations', { params })
}

export function createLocation(data: Location) {
  return request.post('/base/locations', data)
}

export interface ZoneCreate {
  warehouseCode: string
  zoneCode: string
  zoneName?: string
}

export function createZone(data: ZoneCreate) {
  return request.post('/base/locations/zones', data)
}

export function getLocationQr(code: string) {
  return request.get<any, string>(`/base/locations/${code}/qr`)
}

export function getWarehouseUtilization(code: string) {
  return request.get(`/base/warehouses/${code}/utilization`)
}
