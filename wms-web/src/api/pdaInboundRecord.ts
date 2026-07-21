import request from '@/utils/request'
import type { PageQuery, PageResult } from './types'

export interface PdaInboundRecord {
  id?: number
  recordNo?: string
  materialCode?: string
  materialName?: string
  specification?: string
  unitCode?: string
  warehouseCode?: string
  locationCode?: string
  batchNo?: string
  quantity?: number
  barcodeContent?: string
  operatorId?: string
  operatorName?: string
  deviceNo?: string
  status?: string
  erpSyncStatus?: string
  erpSyncTime?: string
  erpBillNo?: string
  erpSyncMessage?: string
  erpRetryCount?: number
  auditorName?: string
  auditTime?: string
  reverseBy?: string
  reverseTime?: string
  reverseReason?: string
  remark?: string
  createTime?: string
}

export interface ErpSyncLog {
  id?: number
  sourceType?: string
  sourceNo?: string
  requestPayload?: string
  responsePayload?: string
  status?: string
  errorMessage?: string
  retryCount?: number
  createTime?: string
}

export function getPdaInboundRecords(params: PageQuery) {
  return request.get<any, PageResult<PdaInboundRecord>>('/inbound/pda-records', { params })
}

export function getPdaInboundRecord(recordNo: string) {
  return request.get<any, PdaInboundRecord>(`/inbound/pda-records/${recordNo}`)
}

export function auditPdaInboundRecord(recordNo: string) {
  return request.put(`/inbound/pda-records/${recordNo}/audit`)
}

export function reversePdaInboundRecord(recordNo: string, reason?: string) {
  return request.put(`/inbound/pda-records/${recordNo}/reverse`, { reason })
}

export function resyncPdaInboundRecord(recordNo: string) {
  return request.post<any, PdaInboundRecord>(`/inbound/pda-records/${recordNo}/resync`)
}

export function getPdaInboundSyncLogs(recordNo: string) {
  return request.get<any, ErpSyncLog[]>(`/inbound/pda-records/${recordNo}/sync-logs`)
}
