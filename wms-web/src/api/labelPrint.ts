import request from '@/utils/request'
import type { PageQuery, PageResult } from './types'

export interface LabelPrintJob {
  id?: number
  jobId: string
  sourceType?: string
  sourceBillNo?: string
  materialCode: string
  materialName?: string
  specification?: string
  batchNo?: string
  productionDate?: string
  quantity?: number
  unitCode?: string
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
  materialCode: string
  batchNo?: string
  productionDate?: string
  quantity?: number
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

export function listLabelPrintJobs(params: PageQuery & { keyword?: string; status?: string }) {
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
