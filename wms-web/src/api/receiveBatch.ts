import request from '@/utils/request'
import type { PageQuery, PageResult } from './types'

export interface ReceiveSubmitBatch {
  id?: number
  batchNo: string
  billNo: string
  lineCount: number
  totalQty: number
  erpSyncStatus: string
  erpBillNo?: string
  erpSyncMessage?: string
  operatorName?: string
  deviceNo?: string
  submitTime?: string
}

export function listReceiveBatches(params: PageQuery & { billNo?: string; erpSyncStatus?: string }) {
  return request.get<any, PageResult<ReceiveSubmitBatch>>('/inbound/receive-batches', { params })
}

export function syncReceiveBatchToErp(batchNo: string) {
  return request.post<any, Record<string, unknown>>(`/inbound/receive-batches/${batchNo}/sync-erp`)
}
