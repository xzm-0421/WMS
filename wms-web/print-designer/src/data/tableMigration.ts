/**
 * 模板数据迁移工具
 *
 * 将旧格式 (tableColumns + tableRows) 自动转换为新格式 (tableGrid)
 */

import { v4 as uuidv4 } from 'uuid'
import type { CanvasElement, TableGrid, GridCell } from '../types/designer'

/**
 * 将旧版表格列定义转换为新版 TableGrid
 */
function migrateTableColumnsToGrid(el: CanvasElement, entrySourceId?: string): TableGrid | null {
  const cols = el.tableColumns
  if (!cols || cols.length === 0) return null

  const gridCols = cols.map((c) => ({ id: c.id, width: c.width }))
  const emptyText = (t: string): GridCell => ({ type: 'text', text: t })
  const emptyField = (b?: typeof cols[0]['binding']): GridCell =>
    b ? { type: 'field', binding: b } : { type: 'field' }

  const inferredEntry =
    entrySourceId ?? cols.find((c) => c.binding?.dataSource)?.binding?.dataSource ?? ''

  // 表头行：使用 column.header
  const headerCells: Record<string, GridCell> = {}
  for (const c of cols) {
    headerCells[c.id] = emptyText(c.header)
  }

  // 数据行：如果列有绑定就用字段，否则用文本
  const dataCells: Record<string, GridCell> = {}
  for (const c of cols) {
    dataCells[c.id] = c.binding ? emptyField(c.binding) : emptyText('')
  }

  return {
    dataSourceId: inferredEntry,
    columns: gridCols,
    headerRow: { height: 12, cells: headerCells },
    dataRow: { height: 12, cells: dataCells },
    defaultStyle: {
      fontSize: 10,
      fontFamily: 'SimSun, serif',
      horizontalAlign: cols[0]?.align || 'center',
      verticalAlign: 'middle',
    },
    borderPreset: 'all',
  }
}

/**
 * 对模板中的旧格式表格元素自动升级
 * 返回一个新的 elements 数组
 */
export function migrateTableElements(elements: CanvasElement[], entrySourceId?: string): CanvasElement[] {
  return elements.map((el) => {
    // 只处理有 tableColumns 但没有 tableGrid 的 table 元素
    if (el.type === 'table' && el.tableColumns && !el.tableGrid) {
      const grid = migrateTableColumnsToGrid(el, entrySourceId)
      if (grid) {
        return { ...el, tableGrid: grid }
      }
    }
    return el
  })
}
