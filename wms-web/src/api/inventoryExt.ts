import request from '@/utils/request'
import type { PageResult } from './types'

export interface OtherInbound {
  orderNo: string
  inboundType: string
  warehouseCode: string
  sourceDesc?: string
  status: string
}

export interface OtherOutbound {
  orderNo: string
  outboundType: string
  warehouseCode: string
  targetDesc?: string
  status: string
}

export interface TransferOrder {
  transferNo: string
  transferType: string
  sourceWarehouse: string
  sourceLocation: string
  targetWarehouse: string
  targetLocation: string
  materialCode: string
  transferQty: number
  status: string
}

export interface SamplePlan {
  planNo: string
  warehouseCode: string
  planDate: string
  status: string
}

export function getOtherInbounds(params: Record<string, unknown>) {
  return request.get<any, PageResult<OtherInbound>>('/inventory/other-inbound', { params })
}

export function createOtherInbound(data: Record<string, unknown>) {
  return request.post<any, string>('/inventory/other-inbound', data)
}

export function getOtherOutbounds(params: Record<string, unknown>) {
  return request.get<any, PageResult<OtherOutbound>>('/inventory/other-outbound', { params })
}

export function createOtherOutbound(data: Record<string, unknown>) {
  return request.post<any, string>('/inventory/other-outbound', data)
}

export function getTransfers(params: Record<string, unknown>) {
  return request.get<any, PageResult<TransferOrder>>('/inventory/transfers', { params })
}

export function createTransfer(data: Record<string, unknown>) {
  return request.post<any, string>('/inventory/transfers', data)
}

export function approveTransfer(transferNo: string) {
  return request.post(`/inventory/transfers/${transferNo}/approve`)
}

export function executeTransfer(transferNo: string) {
  return request.post(`/inventory/transfers/${transferNo}/execute`)
}

export function cancelTransfer(transferNo: string) {
  return request.post(`/inventory/transfers/${transferNo}/cancel`)
}

export function getSamplePlans(params: Record<string, unknown>) {
  return request.get<any, PageResult<SamplePlan>>('/inventory/sample-plans', { params })
}

export function createSamplePlan(warehouseCode: string, planDate?: string) {
  return request.post<any, string>('/inventory/sample-plans', null, { params: { warehouseCode, planDate } })
}

export function getSampleChecklist(planNo: string) {
  return request.get<any, Record<string, unknown>[]>(`/inventory/sample-plans/${planNo}/checklist`)
}

export function submitSampleResult(data: Record<string, unknown>) {
  return request.post('/inventory/sample-plans/results', data)
}

export function getSampleAlerts() {
  return request.get<any, Record<string, unknown>[]>('/inventory/sample-plans/alerts')
}

export function getBusinessRules() {
  return request.get<any, { ruleCode: string; ruleName: string; ruleValue: string; remark?: string }[]>('/system/rules')
}

export function updateBusinessRule(ruleCode: string, ruleValue: string) {
  return request.put(`/system/rules/${ruleCode}`, { ruleValue })
}
