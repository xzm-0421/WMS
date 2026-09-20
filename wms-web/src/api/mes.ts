import request from '@/utils/request'
import { downloadFile } from '@/utils/download'
import type { PageResult } from './types'

export type { PageResult } from './types'

export interface MesSyncResult {
  type?: string
  fetched?: number
  inserted?: number
  updated?: number
  skipped?: number
  rejected?: number
  success?: boolean
  message?: string
}

export interface MesProcess {
  id?: number
  processCode?: string
  processName?: string
  deptCode?: string
  deptName?: string
  reportFlag?: number
  transferFlag?: number
  inspectFlag?: number
  overReceiveRatio?: number
  status?: number
  syncStatus?: string
  lastSyncTime?: string
  failReason?: string
}

export interface MesEquipment {
  id?: number
  equipmentCode?: string
  equipmentName?: string
  processCode?: string
  specModel?: string
  status?: number
  syncStatus?: string
  lastSyncTime?: string
  failReason?: string
}

export interface MesRoute {
  id?: number
  productCode?: string
  productName?: string
  routeCode?: string
  routeName?: string
  versionNo?: string
  status?: number
  syncStatus?: string
  lastSyncTime?: string
}

export interface MesRouteOp {
  seqNo?: number
  processCode?: string
  processName?: string
  stdHours?: number
  inspectFlag?: number
  reworkJoinFlag?: number
  workCenterCode?: string
}

export interface MesOpPlan {
  id?: number
  moNo?: string
  productCode?: string
  productName?: string
  processCode?: string
  processName?: string
  planQty?: number
  reportedQty?: number
  reworkReportedQty?: number
  planStart?: string
  planEnd?: string
  planStatus?: string
  syncStatus?: string
  lastSyncTime?: string
  failReason?: string
  changeRejectReason?: string
  erpBillNo?: string
  workShopCode?: string
  workShopName?: string
}

export interface MesReport {
  reportNo?: string
  moNo?: string
  processCode?: string
  processName?: string
  reportType?: string
  qty?: number
  weightKg?: number
  equipmentCode?: string
  equipmentName?: string
  operatorName?: string
  remark?: string
  defectNo?: string
  reportTime?: string
  syncStatus?: string
  syncTime?: string
  erpBillNo?: string
  failReason?: string
}

export interface MesTransfer {
  transferNo?: string
  moNo?: string
  fromProcessCode?: string
  fromProcessName?: string
  toProcessCode?: string
  toProcessName?: string
  qty?: number
  autoFlag?: number
  operatorName?: string
  transferTime?: string
  syncStatus?: string
  failReason?: string
}

export interface MesDefect {
  defectNo?: string
  moNo?: string
  sourceProcessCode?: string
  sourceProcessName?: string
  defectQty?: number
  defectType?: string
  defectDesc?: string
  ownerName?: string
  reworkStatus?: string
}

export interface MesReworkOp {
  seqNo?: number
  processCode?: string
  processName?: string
  planQty?: number
  reportedQty?: number
  opStatus?: string
}

export interface MesReworkSequence {
  defect?: MesDefect
  operations?: MesReworkOp[]
}

export interface MesReportContext {
  moNo?: string
  normalCompleted?: boolean
  allowedReportTypes?: string[]
  typeHint?: string
  plans?: MesOpPlan[]
  equipment?: MesEquipment[]
  reworkProcessCodes?: string[]
  openDefectNos?: string[]
}

export interface MesSyncPanel {
  queueTotal?: number
  pendingCount?: number
  syncingCount?: number
  failedCount?: number
  todaySyncedCount?: number
  networkStatus?: string
  lastSyncTime?: string
  lastCheckTime?: string
  lastError?: string
}

export function getMesProcesses(params: Record<string, unknown>) {
  return request.get<any, PageResult<MesProcess>>('/mes/master/processes', { params })
}

export function getMesEquipment(params: Record<string, unknown>) {
  return request.get<any, PageResult<MesEquipment>>('/mes/master/equipment', { params })
}

export function getMesRoutes(params: Record<string, unknown>) {
  return request.get<any, PageResult<MesRoute>>('/mes/master/routes', { params })
}

export function getMesRoute(id: number) {
  return request.get<any, { header: MesRoute; operations: MesRouteOp[] }>(`/mes/master/routes/${id}`)
}

export function refreshMesMaster() {
  return request.post<any, MesSyncResult[]>('/mes/master/refresh')
}

export function refreshMesProcesses() {
  return request.post<any, MesSyncResult>('/mes/master/processes/refresh')
}

export function refreshMesEquipment() {
  return request.post<any, MesSyncResult>('/mes/master/equipment/refresh')
}

export function refreshMesRoutes() {
  return request.post<any, MesSyncResult>('/mes/master/routes/refresh')
}

export function getMesEquipmentDetail(equipmentCode: string) {
  return request.get<any, MesEquipment>(`/mes/master/equipment/${encodeURIComponent(equipmentCode)}`)
}

export interface MesMaterial {
  materialCode?: string
  materialName?: string
  specification?: string
  unitCode?: string
  materialType?: string
  status?: number
}

export interface MesBomHeader {
  id?: number
  bomCode?: string
  productCode?: string
  versionNo?: string
  status?: number
}

export interface MesBomDetail {
  lineNo?: number
  materialCode?: string
  materialName?: string
  unitCode?: string
  qtyPer?: number
}

export function getMesMaterials(params: Record<string, unknown>) {
  return request.get<any, PageResult<MesMaterial>>('/mes/master/materials', { params })
}

export function refreshMesMaterials() {
  return request.post<any, MesSyncResult>('/mes/master/materials/refresh')
}

export function getMesBoms(params: Record<string, unknown>) {
  return request.get<any, PageResult<MesBomHeader>>('/mes/master/boms', { params })
}

export function getMesBom(bomCode: string) {
  return request.get<any, { header: MesBomHeader; details: MesBomDetail[] }>(
    `/mes/master/boms/${encodeURIComponent(bomCode)}`,
  )
}

export function refreshMesBoms() {
  return request.post<any, MesSyncResult>('/mes/master/boms/refresh')
}

export function getMesPlans(params: Record<string, unknown>) {
  return request.get<any, PageResult<MesOpPlan>>('/mes/plans', { params })
}

export function getMesPlan(id: number) {
  return request.get<any, { plan: MesOpPlan; routeOps: MesRouteOp[] }>(`/mes/plans/${id}`)
}

export function refreshMesPlans(moNo?: string) {
  return request.post<any, MesSyncResult>('/mes/plans/refresh', null, { params: moNo ? { moNo } : undefined })
}

export function retryMesPlan(id: number) {
  return request.post<any, MesSyncResult>(`/mes/plans/${id}/retry`)
}

export function getMesReportContext(moNo: string) {
  return request.get<any, MesReportContext>('/mes/reports/context', { params: { moNo } })
}

export function submitMesReport(data: Record<string, unknown>) {
  return request.post<any, MesReport>('/mes/reports', data)
}

export function getMesReports(params: Record<string, unknown>) {
  return request.get<any, PageResult<MesReport>>('/mes/reports', { params })
}

export function getMesReport(reportNo: string) {
  return request.get<any, MesReport>(`/mes/reports/${encodeURIComponent(reportNo)}`)
}

export function retryMesReport(reportNo: string) {
  return request.post(`/mes/reports/${encodeURIComponent(reportNo)}/retry`)
}

export function cancelMesReport(reportNo: string, reason: string) {
  return request.post(`/mes/reports/${encodeURIComponent(reportNo)}/cancel`, { reason })
}

export function exportMesReports(params: Record<string, string | number | undefined | null>) {
  return downloadFile('/mes/reports/export', 'mes-reports.xlsx', params)
}

export function submitMesTransfer(data: Record<string, unknown>) {
  return request.post<any, MesTransfer>('/mes/transfers', data)
}

export function getMesTransfers(params: Record<string, unknown>) {
  return request.get<any, PageResult<MesTransfer>>('/mes/transfers', { params })
}

export function retryMesTransfer(transferNo: string) {
  return request.post(`/mes/transfers/${encodeURIComponent(transferNo)}/retry`)
}

export function getMesDefects(params: Record<string, unknown>) {
  return request.get<any, PageResult<MesDefect>>('/mes/defects', { params })
}

export function createMesDefect(data: Record<string, unknown>) {
  return request.post<any, MesReworkSequence>('/mes/defects', data)
}

export function getMesReworkSequence(defectNo: string) {
  return request.get<any, MesReworkSequence>(`/mes/defects/${encodeURIComponent(defectNo)}`)
}

export function getMesSyncPanel() {
  return request.get<any, MesSyncPanel>('/mes/sync/panel')
}
