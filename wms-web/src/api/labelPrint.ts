import request from '@/utils/request'
import type { PageQuery, PageResult } from './types'
import { downloadFile, uploadFile } from '@/utils/download'

export interface LabelPrintJob {
  id?: number
  jobId: string
  sourceType?: string
  sourceBillNo?: string
  warehouseCode?: string
  warehouseName?: string
  orgCode?: string
  orgName?: string
  materialCode: string
  materialName?: string
  specification?: string
  batchNo?: string
  productionDate?: string
  /** FACTORY 厂内 / INCOMING 来料 */
  labelFormat?: string
  /** 客户简称（厂内）或供应商简称（来料） */
  partnerName?: string
  /** 板号（厂内） */
  boardNo?: string
  /** 包装号（来料） */
  packageNo?: string
  quantity?: number
  /** 入库单位 */
  unitCode?: string
  /** 计价单位 */
  priceUnitCode?: string
  barcodeContent?: string
  barcodeType?: string
  labelWidthMm?: number
  labelHeightMm?: number
  copies?: number
  status?: string
  operatorId?: string
  operatorName?: string
  errorMessage?: string
  printUrl?: string
  createTime?: string
  openedTime?: string
  printedTime?: string
}

export interface LabelPrintCreateRequest {
  warehouseCode?: string
  warehouseName?: string
  orgCode?: string
  orgName?: string
  materialCode: string
  batchNo?: string
  productionDate?: string
  labelFormat?: string
  partnerName?: string
  boardNo?: string
  packageNo?: string
  quantity?: number
  /** 入库单位 */
  unitCode?: string
  /** 计价单位 */
  priceUnitCode?: string
  barcodeContent?: string
  barcodeType?: string
  labelWidthMm?: number
  labelHeightMm?: number
  copies?: number
}

export interface LabelPrintSettings {
  labelWidthMm?: number
  labelHeightMm?: number
  copies?: number
  barcodeType?: string
}

export function listLabelPrintJobs(params: PageQuery & {
  warehouseCode?: string
  warehouseName?: string
  materialKeyword?: string
  keyword?: string
}) {
  return request.get<any, PageResult<LabelPrintJob>>('/print/label-jobs', { params })
}

export function getLabelPrintJob(jobId: string) {
  return request.get<any, LabelPrintJob>(`/print/label-jobs/${jobId}`)
}

export function createLabelPrintJob(data: LabelPrintCreateRequest) {
  return request.post<any, LabelPrintJob>('/print/label-jobs', data)
}

export function updateLabelPrintSettings(jobId: string, data: LabelPrintSettings) {
  return request.put<any, LabelPrintJob>(`/print/label-jobs/${jobId}/settings`, data)
}

export function syncLabelJobMaterial(jobId: string) {
  return request.post<any, LabelPrintJob>(`/print/label-jobs/${encodeURIComponent(jobId)}/sync-material`)
}

export function markLabelJobOpened(jobId: string) {
  return request.post<any, void>(`/print/label-jobs/${encodeURIComponent(jobId)}/open`)
}

export function markLabelJobPrinted(jobId: string, errorMessage?: string) {
  return request.post<any, LabelPrintJob>(
    `/print/label-jobs/${encodeURIComponent(jobId)}/printed`,
    errorMessage ? { errorMessage } : {},
  )
}

export function downloadOpeningStockTemplate() {
  return downloadFile('/print/label-jobs/import/template', '期初库存导入模板.xlsx')
}

export function exportOpeningStockExcel(params: {
  warehouseCode?: string
  warehouseName?: string
  materialKeyword?: string
}) {
  return downloadFile('/print/label-jobs/export', '期初库存.xlsx', params)
}

export function importOpeningStockExcel(file: File) {
  return uploadFile<number>('/print/label-jobs/import', file)
}

/** 批量删除期初库存（按主键 id） */
export function deleteOpeningStockJobs(ids: number[]) {
  return request.delete<any, number>('/print/label-jobs/opening', { data: ids })
}
