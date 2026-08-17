import request from '@/utils/request'
import type { PageQuery, PageResult } from './types'
import type { ErpSyncLog, PdaInboundRecord } from './pdaInboundRecord'

export interface ReceiveSubmitBatch {
  id?: number
  batchNo: string
  billNo: string
  billType?: string
  direction?: string
  lineCount: number
  totalQty: number
  erpSyncStatus: string
  erpBillNo?: string
  erpSyncMessage?: string
  operatorName?: string
  deviceNo?: string
  submitTime?: string
  supplierCode?: string
  supplierName?: string
}

export interface ReceiveSubmitBatchDetail {
  batch: ReceiveSubmitBatch
  records: PdaInboundRecord[]
  syncLogs: ErpSyncLog[]
}

export function listReceiveBatches(params: PageQuery & { billNo?: string; erpSyncStatus?: string }) {
  return request.get<any, PageResult<ReceiveSubmitBatch>>('/inbound/receive-batches', { params })
}

export function getReceiveBatchDetail(batchNo: string) {
  return request.get<any, ReceiveSubmitBatchDetail>(`/inbound/receive-batches/${batchNo}`)
}

export function syncReceiveBatchToErp(batchNo: string) {
  return request.post<any, Record<string, unknown>>(`/inbound/receive-batches/${batchNo}/sync-erp`)
}
