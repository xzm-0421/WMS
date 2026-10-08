import request from '@/utils/request'
import type { PageResult } from './types'

export interface OperationLog {
  id?: number
  operatorId?: string
  operatorName?: string
  module?: string
  operationType?: string
  operationContent?: string
  requestParams?: string
  responseResult?: string
  ipAddress?: string
  deviceInfo?: string
  operationTime?: string
}

export function getOperationLogs(params: Record<string, unknown>) {
  return request.get<any, PageResult<OperationLog>>('/system/operation-logs', { params })
}

export interface SysAlert {
  id?: number
  alertType?: string
  level?: string
  title?: string
  content?: string
  bizNo?: string
  status?: string
  alertCount?: number
  firstTime?: string
  lastTime?: string
  ackBy?: string
  ackTime?: string
}

export function getAlerts(params: Record<string, unknown>) {
  return request.get<any, PageResult<SysAlert>>('/system/alerts', { params })
}

export function ackAlert(id: number) {
  return request.post(`/system/alerts/${id}/ack`)
}

export function closeAlert(id: number) {
  return request.post(`/system/alerts/${id}/close`)
}

export interface MesEvent {
  id?: number
  eventName?: string
  bizType?: string
  bizNo?: string
  operatorName?: string
  properties?: string
  eventTime?: string
}

export function getMesEvents(params: Record<string, unknown>) {
  return request.get<any, PageResult<MesEvent>>('/system/mes-events', { params })
}
