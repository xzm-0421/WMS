import request from '@/utils/request'
import type { PageQuery, PageResult } from './types'

export interface BarcodeRule {
  id?: number
  ruleCode: string
  ruleName: string
  appliesTo?: string
  barcodeType?: string
  templateCode?: string
  separator?: string
  segmentsJson?: string
  description?: string
  remark?: string
  versionNo?: number
  status?: number
}

export interface BarcodeSegment {
  type: 'FIELD' | 'SEPARATOR' | 'FIXED' | 'DATE'
  source?: string
  value?: string
  label?: string
}

export interface BarcodeRuleVersion {
  id?: number
  ruleCode: string
  versionNo: number
  ruleName: string
  templateCode?: string
  separator?: string
  segmentsJson?: string
  status?: number
  changeLog?: string
  createTime?: string
}

export interface KingdeeMaterial {
  materialCode: string
  materialName: string
  barCode?: string
  packBarCode?: string
  batchManaged?: boolean
  serialManaged?: boolean
}

export interface KingdeeBatch {
  batchNo: string
  materialCode: string
  materialName?: string
  status?: string
}

export interface KingdeeSerial {
  serialNo: string
  materialCode: string
  batchNo?: string
  status?: string
}

export interface BarcodeGenerateRequest {
  ruleCode: string
  materialCode?: string
  batchNo?: string
  packBarcode?: string
  serialNo?: string
  autoSerial?: boolean
  refType?: string
  refNo?: string
  labelWidthMm?: number
  labelHeightMm?: number
  labelFormat?: string
}

export interface BarcodeGenerateResult {
  barcodeContent: string
  ruleCode: string
  versionNo?: number
  materialCode?: string
  batchNo?: string
  serialNo?: string
  packBarcode?: string
  barcodeType?: string
  instanceId?: number
  archiveNo?: string
}

export interface BarcodeArchive {
  id?: number
  archiveNo: string
  barcodeContent: string
  barcodeInstanceId?: number
  ruleCode?: string
  versionNo?: number
  materialCode?: string
  materialName?: string
  batchNo?: string
  serialNo?: string
  packBarcode?: string
  barcodeType?: string
  templateCode?: string
  labelFormat?: string
  labelWidthMm?: number
  labelHeightMm?: number
  printParamsJson?: string
  actionType?: string
  reprintCount?: number
  lastReprintTime?: string
  createTime?: string
}

export interface BarcodeParseResult {
  ruleCode?: string
  versionNo?: number
  barcodeContent?: string
  barcodeType?: string
  materialCode?: string
  batchNo?: string
  serialNo?: string
  packBarcode?: string
  segments?: Record<string, string>
}

export interface BarcodeTraceResult {
  barcodeContent: string
  materialCode?: string
  batchNo?: string
  serialNo?: string
  packBarcode?: string
  ruleCode?: string
  versionNo?: number
  links: Array<{
    refType: string
    refNo: string
    transactionNo?: string
    materialCode?: string
    batchNo?: string
    serialNo?: string
    remark?: string
    createTime?: string
  }>
}

export const SEGMENT_FIELD_OPTIONS = [
  { source: 'MATERIAL_CODE', label: '物料编码' },
  { source: 'BATCH_NO', label: '批号' },
  { source: 'PACK_BARCODE', label: '包装条码' },
  { source: 'SERIAL_NO', label: '序列号' },
]

export const TEMPLATE_OPTIONS = [
  { code: 'MAT_BATCH_SERIAL', label: '物料编码+批号+序列号' },
  { code: 'PACK_BATCH', label: '包装条码+批号' },
  { code: 'MAT_BATCH', label: '物料编码+批号' },
  { code: 'MAT_SERIAL', label: '物料编码+序列号' },
  { code: 'CUSTOM', label: '自定义分段' },
]

export function buildTemplateSegments(templateCode: string, separator = '-') {
  const sep = { type: 'SEPARATOR' as const, value: separator }
  const f = (source: string, label: string) => ({ type: 'FIELD' as const, source, label })
  switch (templateCode) {
    case 'MAT_BATCH_SERIAL':
      return [f('MATERIAL_CODE', '物料编码'), sep, f('BATCH_NO', '批号'), sep, f('SERIAL_NO', '序列号')]
    case 'PACK_BATCH':
      return [f('PACK_BARCODE', '包装条码'), sep, f('BATCH_NO', '批号')]
    case 'MAT_BATCH':
      return [f('MATERIAL_CODE', '物料编码'), sep, f('BATCH_NO', '批号')]
    case 'MAT_SERIAL':
      return [f('MATERIAL_CODE', '物料编码'), sep, f('SERIAL_NO', '序列号')]
    default:
      return []
  }
}

/** 将分段字段来源规范化为标准 key */
export function normalizeSegmentFieldSource(source: string): string {
  const u = source.toUpperCase()
  if (u === 'MATERIAL' || u === 'MATERIAL_CODE') return 'MATERIAL_CODE'
  if (u === 'BATCH' || u === 'BATCH_NO') return 'BATCH_NO'
  if (u === 'SERIAL' || u === 'SERIAL_NO') return 'SERIAL_NO'
  if (u === 'PACK' || u === 'PACK_BARCODE') return 'PACK_BARCODE'
  return u
}

/** 解析规则分段 JSON */
export function parseRuleSegments(json?: string): BarcodeSegment[] {
  if (!json) return []
  try {
    return JSON.parse(json) as BarcodeSegment[]
  } catch {
    return []
  }
}

/**
 * 从条码规则提取所需金蝶/业务字段（物料、批号、包装条码、序列号等）。
 * 优先读 segmentsJson；为空时回退到预置模板分段。
 */
export function extractRuleFieldSources(rule?: BarcodeRule | null): string[] {
  if (!rule) return []
  let segments = parseRuleSegments(rule.segmentsJson)
  if (!segments.length && rule.templateCode && rule.templateCode !== 'CUSTOM') {
    segments = buildTemplateSegments(rule.templateCode, rule.separator || '')
  }
  const sources = new Set<string>()
  for (const seg of segments) {
    if (seg.type === 'FIELD' && seg.source) {
      sources.add(normalizeSegmentFieldSource(seg.source))
    }
  }
  return [...sources]
}

/** 金蝶字段与界面数据源映射 */
export const KINGDEE_FIELD_META: Record<string, { label: string; kingdee: boolean }> = {
  MATERIAL_CODE: { label: '物料', kingdee: true },
  BATCH_NO: { label: '批号', kingdee: true },
  PACK_BARCODE: { label: '包装条码', kingdee: false },
  SERIAL_NO: { label: '序列号', kingdee: true },
}

export function getBarcodeRules(params: PageQuery & { appliesTo?: string; status?: number }) {
  return request.get<any, PageResult<BarcodeRule>>('/barcode/rules', { params })
}

export function getBarcodeTemplates() {
  return request.get<any, Record<string, string>>('/barcode/templates')
}

export function getBarcodeRule(code: string) {
  return request.get<any, BarcodeRule>(`/barcode/rules/${code}`)
}

export function getBarcodeRuleVersions(code: string) {
  return request.get<any, BarcodeRuleVersion[]>(`/barcode/rules/${code}/versions`)
}

export function createBarcodeRule(data: BarcodeRule) {
  return request.post('/barcode/rules', data)
}

export function updateBarcodeRule(code: string, data: BarcodeRule, changeLog?: string) {
  return request.put(`/barcode/rules/${code}`, data, { params: { changeLog } })
}

export function updateBarcodeRuleStatus(code: string, status: number, changeLog?: string) {
  return request.put(`/barcode/rules/${code}/status`, { status, changeLog })
}

export function deleteBarcodeRule(code: string) {
  return request.delete(`/barcode/rules/${code}`)
}

export function parseBarcode(ruleCode: string, barcodeContent: string) {
  return request.post<any, BarcodeParseResult>('/barcode/parse', { ruleCode, barcodeContent })
}

export function generateBarcode(data: BarcodeGenerateRequest) {
  return request.post<any, BarcodeGenerateResult>('/barcode/generate', data)
}

export function traceBarcode(barcodeContent: string) {
  return request.get<any, BarcodeTraceResult>('/barcode/trace', { params: { barcodeContent } })
}

export function getBarcodeArchives(params: PageQuery & {
  archiveNo?: string
  barcodeContent?: string
  materialCode?: string
  ruleCode?: string
  createTimeFrom?: string
  createTimeTo?: string
}) {
  return request.get<any, PageResult<BarcodeArchive>>('/barcode/archives', { params })
}

export function getBarcodeArchive(archiveNo: string) {
  return request.get<any, BarcodeArchive>(`/barcode/archives/${archiveNo}`)
}

export function reprintBarcodeArchive(archiveNo: string) {
  return request.post<any, { archiveNo: string; reprintCount: number }>(`/barcode/archives/${archiveNo}/reprint`)
}

export function cleanupBarcodeArchives() {
  return request.post<any, { removed: number }>('/barcode/archives/cleanup')
}

export function getKingdeeMaterials(params: PageQuery & { keyword?: string }) {
  return request.get<any, PageResult<KingdeeMaterial>>('/barcode/kingdee/materials', { params })
}

export function getKingdeeBatches(params: PageQuery & { materialCode?: string; keyword?: string }) {
  return request.get<any, PageResult<KingdeeBatch>>('/barcode/kingdee/batches', { params })
}

export function getKingdeeSerials(params: PageQuery & { materialCode?: string; batchNo?: string; keyword?: string }) {
  return request.get<any, PageResult<KingdeeSerial>>('/barcode/kingdee/serials', { params })
}
