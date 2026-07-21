/**
 * 数据解析引擎 — WMS 套打
 */
import type { DocumentData, FieldBinding } from '../types/designer'

const sysVars: Record<string, string> = {
  $PRINT_DATE: new Date().toISOString().slice(0, 10),
  $PRINT_TIME: new Date().toTimeString().slice(0, 8),
  $PRINT_USER: 'WMS用户',
  $CURRENT_PAGE: '1',
  $TOTAL_PAGES: '1',
  $ORGANIZATION: 'WMS仓储',
  $DOC_TYPE: '业务单据',
}

export function formatValue(
  value: unknown,
  fieldType: 'string' | 'number' | 'date' | 'boolean',
  format?: string,
): string {
  if (value === null || value === undefined) return ''

  switch (fieldType) {
    case 'number': {
      const num = Number(value)
      if (isNaN(num)) return String(value)
      if (!format || format === '#,##0.00') {
        return num.toLocaleString('zh-CN', { minimumFractionDigits: 0, maximumFractionDigits: 2 })
      }
      return String(num)
    }
    case 'date': {
      const d = new Date(String(value))
      if (isNaN(d.getTime())) return String(value)
      const fmt = format || 'yyyy-MM-dd'
      const pad = (n: number) => String(n).padStart(2, '0')
      return fmt
        .replace('yyyy', String(d.getFullYear()))
        .replace('MM', pad(d.getMonth() + 1))
        .replace('dd', pad(d.getDate()))
    }
    case 'boolean':
      return value ? '是' : '否'
    default:
      return String(value)
  }
}

export function resolveSystemVar(varName: string): string {
  return sysVars[varName] ?? ''
}

export function resolveFieldBinding(
  binding: FieldBinding,
  docData: DocumentData,
  currentRow?: Record<string, unknown> | null,
): string {
  const { dataSource, fieldName, fieldType, format } = binding

  if (fieldName.startsWith('$')) {
    return resolveSystemVar(fieldName)
  }

  let record: Record<string, unknown> | undefined
  const entrySourceId = docData.entrySourceId ?? `${docData.mainSourceId}_entry`

  if (dataSource === docData.mainSourceId || dataSource === 'sys_variables') {
    record = docData.header
  } else if (dataSource === entrySourceId) {
    record = currentRow ?? undefined
  } else if (dataSource === 't_bd_material') {
    if (docData.mainSourceId === 't_bd_material') {
      record = docData.header
    } else {
      const matKey = String(currentRow?.['FMaterialCode'] ?? currentRow?.['FMaterialId'] ?? docData.header['FMaterialCode'] ?? '')
      record = docData.relatedData?.['t_bd_material']?.[matKey]
    }
  } else if (dataSource === 't_bd_supplier') {
    const supKey = String(docData.header['FSupplierCode'] ?? '')
    record = docData.relatedData?.['t_bd_supplier']?.[supKey]
  }

  if (!record) return ''

  const rawValue = record[fieldName]
  return formatValue(rawValue, fieldType, format)
}

export function isSystemVar(fieldName: string): boolean {
  return fieldName.startsWith('$')
}
