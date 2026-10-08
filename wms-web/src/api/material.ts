import request from '@/utils/request'
import type { PageResult } from './types'

export type { PageResult } from './types'

export interface Material {
  id?: number
  erpMaterialId?: number
  materialCode: string
  materialName: string
  categoryCode: string
  specification?: string
  unitCode: string
  materialType: string
  batchManaged?: number
  barcodeRule?: string
  status?: number
}

export function getMaterial(code: string) {
  return request.get<any, Material>(`/base/materials/${encodeURIComponent(code)}`)
}

export function getMaterials(params: Record<string, unknown>) {
  return request.get<any, PageResult<Material>>('/base/materials', { params })
}

export function deleteMaterial(code: string) {
  return request.delete(`/base/materials/${code}`)
}

/** 将选中物料的基本信息写入明细行或表单对象 */
export function applyMaterialBasic(target: Record<string, unknown>, material: Material) {
  target.materialCode = material.materialCode
  if ('erpMaterialId' in target) target.erpMaterialId = material.erpMaterialId
  if ('materialName' in target) target.materialName = material.materialName
  if ('specification' in target) target.specification = material.specification
  if ('unitCode' in target) target.unitCode = material.unitCode
  if ('categoryCode' in target) target.categoryCode = material.categoryCode
}

export const MATERIAL_TYPE_LABEL: Record<string, string> = {
  RAW: '原材料',
  FINISHED: '成品',
  AUX: '辅料',
}
