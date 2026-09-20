import request from '@/utils/request'
import type { PageQuery, PageResult } from './types'
import type { InventoryTransaction } from './inventory'

export interface PdaOutboundBatch {
  id?: number
  batchNo: string
  billType?: string
  direction?: string
  billNo: string
  supplierCode?: string
  supplierName?: string
  lineCount: number
  totalQty: number
  erpSyncStatus?: string
  erpBillNo?: string
  erpSyncMessage?: string
  operatorName?: string
  deviceNo?: string
  submitTime?: string
}

export interface PdaOutboundBatchDetail {
  batch: PdaOutboundBatch
  lines: InventoryTransaction[]
}

export function listPdaOutboundBatches(
  params: PageQuery & {
    billNo?: string
    batchNo?: string
    billType?: string
    erpSyncStatus?: string
  },
) {
  return request.get<any, PageResult<PdaOutboundBatch>>('/outbound/pda-batches', { params })
}

export function getPdaOutboundBatchDetail(batchNo: string) {
  return request.get<any, PdaOutboundBatchDetail>(`/outbound/pda-batches/${batchNo}`)
}

export function syncPdaOutboundBatchToErp(batchNo: string) {
  return request.post<any, Record<string, unknown>>(`/outbound/pda-batches/${batchNo}/sync-erp`)
}

export const PDA_OUTBOUND_BILL_TYPE_LABELS: Record<string, string> = {
  PRODUCTION_ISSUE: '生产备料',
  SALES_DELIVERY: '销售发货通知单',
  OUTSOURCE_ISSUE: '委外领料（用料清单）',
  OTHER_OUT: '其他出库单',
}
