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
  isConvergeOp?: boolean
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

export interface MesWorkCenter {
  id?: number
  erpId?: number
  workCenterCode?: string
  workCenterName?: string
  workShopCode?: string
  workShopName?: string
  deptCode?: string
  deptName?: string
  capacity?: number
  calendarCode?: string
  calendarName?: string
  status?: number
  syncStatus?: string
  lastSyncTime?: string
  failReason?: string
}

export interface MesResource {
  id?: number
  erpId?: number
  resourceCode?: string
  resourceName?: string
  resourceTypeCode?: string
  resourceType?: string
  workCenterCode?: string
  workCenterName?: string
  capacity?: number
  unitCode?: string
  refType?: string
  refCode?: string
  refName?: string
  status?: number
  syncStatus?: string
  lastSyncTime?: string
  failReason?: string
}

export interface MesPersonnel {
  id?: number
  erpId?: number
  personnelCode?: string
  personnelName?: string
  deptCode?: string
  deptName?: string
  postCode?: string
  postName?: string
  skillLevel?: string
  workCenterCode?: string
  productionFlag?: number
  sysUserId?: number
  sysUsername?: string
  status?: number
  syncStatus?: string
  lastSyncTime?: string
  failReason?: string
}

export interface MesRoute {
  id?: number
  productCode?: string
  erpMaterialId?: number
  productName?: string
  routeCode?: string
  routeName?: string
  versionNo?: string
  overReceiveRatio?: number
  status?: number
  syncStatus?: string
  lastSyncTime?: string
  failReason?: string
}

export interface MesRouteOp {
  seqNo?: number
  processCode?: string
  erpMaterialId?: number
  processName?: string
  stdHours?: number
  inspectFlag?: number
  reworkJoinFlag?: number
  isConvergeOp?: boolean
  workCenterCode?: string
}

export interface MesOpPlan {
  id?: number
  moNo?: string
  productCode?: string
  erpMaterialId?: number
  productName?: string
  processCode?: string
  processName?: string
  planQty?: number
  reportedQty?: number
  reworkReportedQty?: number
  overReceiveRatio?: number
  planStart?: string
  planEnd?: string
  erpStatus?: string
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
  id?: number
  reportNo?: string
  moNo?: string
  processCode?: string
  processName?: string
  reportType?: string
  qty?: number
  weightKg?: number
  equipmentCode?: string
  equipmentName?: string
  personnelCode?: string
  personnelName?: string
  operatorName?: string
  remark?: string
  defectNo?: string
  reportTime?: string
  syncStatus?: string
  syncTime?: string
  erpBillNo?: string
  failReason?: string
  retryCount?: number
  nextRetryTime?: string
  cancelReason?: string
  clientReportNo?: string
  clientTime?: string
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
  remark?: string
  transferTime?: string
  syncStatus?: string
  syncTime?: string
  erpBillNo?: string
  failReason?: string
  retryCount?: number
  nextRetryTime?: string
  cancelReason?: string
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
  defectSyncStatus?: string
  reworkSyncStatus?: string
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
  personnel?: MesPersonnel[]
  reworkProcessCodes?: string[]
  openDefectNos?: string[]
}

export interface MesSyncPanel {
  queueTotal?: number
  pendingCount?: number
  syncingCount?: number
  failedCount?: number
  todaySyncedCount?: number
  reportPendingCount?: number
  reportFailedCount?: number
  transferPendingCount?: number
  transferFailedCount?: number
  defectPendingCount?: number
  defectFailedCount?: number
  reworkPendingCount?: number
  reworkFailedCount?: number
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

export function getMesProcessDetail(processCode: string) {
  return request.get<any, MesProcess>(`/mes/master/processes/${encodeURIComponent(processCode)}`)
}

export const RESOURCE_TYPE_LABEL: Record<string, string> = {
  EQUIPMENT: '设备',
  TEAM: '团队',
  PERSONNEL: '人员',
  OTHER: '其他',
}

export function getMesWorkCenters(params: Record<string, unknown>) {
  return request.get<any, PageResult<MesWorkCenter>>('/mes/master/work-centers', { params })
}

export function getMesWorkCenter(workCenterCode: string) {
  return request.get<any, MesWorkCenter>(`/mes/master/work-centers/${encodeURIComponent(workCenterCode)}`)
}

export function getMesWorkCenterOptions() {
  return request.get<any, MesWorkCenter[]>('/mes/master/work-centers/options')
}

export function refreshMesWorkCenters() {
  return request.post<any, MesSyncResult>('/mes/master/work-centers/refresh')
}

export function getMesResources(params: Record<string, unknown>) {
  return request.get<any, PageResult<MesResource>>('/mes/master/resources', { params })
}

export function getMesResource(resourceCode: string) {
  return request.get<any, MesResource>(`/mes/master/resources/${encodeURIComponent(resourceCode)}`)
}

export function refreshMesResources() {
  return request.post<any, MesSyncResult>('/mes/master/resources/refresh')
}

export function getMesPersonnels(params: Record<string, unknown>) {
  return request.get<any, PageResult<MesPersonnel>>('/mes/master/personnel', { params })
}

export function getMesPersonnel(personnelCode: string) {
  return request.get<any, MesPersonnel>(`/mes/master/personnel/${encodeURIComponent(personnelCode)}`)
}

export function getMesPersonnelOptions(params?: Record<string, unknown>) {
  return request.get<any, MesPersonnel[]>('/mes/master/personnel/options', { params })
}

export function refreshMesPersonnels() {
  return request.post<any, MesSyncResult>('/mes/master/personnel/refresh')
}

export function bindMesPersonnel(personnelCode: string, data: { sysUserId?: number; sysUsername?: string }) {
  return request.put<any, MesPersonnel>(
    `/mes/master/personnel/${encodeURIComponent(personnelCode)}/binding`,
    data,
  )
}

export interface MesBomHeader {
  id?: number
  bomCode?: string
  productCode?: string
  erpMaterialId?: number
  versionNo?: string
  status?: number
}

export interface MesBomDetail {
  lineNo?: number
  materialCode?: string
  erpMaterialId?: number
  materialName?: string
  unitCode?: string
  qtyPer?: number
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

export function getMesTransfer(transferNo: string) {
  return request.get<any, MesTransfer>(`/mes/transfers/${encodeURIComponent(transferNo)}`)
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

export function completeMesRework(defectNo: string) {
  return request.post<any, MesDefect>(`/mes/defects/${encodeURIComponent(defectNo)}/complete`)
}

export function secondaryMesRework(defectNo: string) {
  return request.post<any, MesDefect>(`/mes/defects/${encodeURIComponent(defectNo)}/secondary`)
}

export function closeMesRework(defectNo: string) {
  return request.post<any, MesDefect>(`/mes/defects/${encodeURIComponent(defectNo)}/close`)
}

export function getMesSyncPanel() {
  return request.get<any, MesSyncPanel>('/mes/sync/panel')
}

export interface MesErpOutbox {
  id?: number
  bizType?: string
  bizNo?: string
  action?: string
  syncStatus?: string
  retryCount?: number
  erpBillNo?: string
  failReason?: string
  lastSyncTime?: string
}

export function getMesOutbox(params: Record<string, unknown>) {
  return request.get<any, PageResult<MesErpOutbox>>('/mes/sync/outbox', { params })
}

export function retryMesOutbox(id: number) {
  return request.post(`/mes/sync/outbox/${id}/retry`)
}
