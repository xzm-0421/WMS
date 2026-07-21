import React, { useMemo } from 'react'
import { useDesignerStore } from '../../store/useDesignerStore'
import { resolveFieldBinding, resolveSystemVar } from '../../store/dataResolver'
import { BarcodeRenderer } from '../BarcodeRenderer/BarcodeRenderer'
import type { CanvasElement } from '../../types/designer'

interface CanvasElementViewProps {
  element: CanvasElement
  zoom: number
  isSelected: boolean
  offsetX: number
  offsetY: number
  onMouseDown: (e: React.MouseEvent) => void
  onResizeStart: (e: React.MouseEvent, handle: string) => void
}

function mmToPx(mm: number, zoom: number): number {
  return mm * 3.78 * zoom
}

export const CanvasElementView = React.memo(function CanvasElementView({
  element,
  zoom,
  isSelected,
  offsetX,
  offsetY,
  onMouseDown,
  onResizeStart,
}: CanvasElementViewProps) {
  const selectElement = useDesignerStore((s) => s.selectElement)
  const viewMode = useDesignerStore((s) => s.viewMode)
  const documentData = useDesignerStore((s) => s.documentData)

  const isPreview = viewMode === 'preview'

  const style = useMemo(() => {
    const w = mmToPx(element.width, zoom)
    const h = mmToPx(element.height, zoom)

    const base: React.CSSProperties = {
      left: mmToPx(element.x + offsetX, zoom),
      top: mmToPx(element.y + offsetY, zoom),
      width: w,
      height: h,
      zIndex: element.zIndex % 10000,
      fontSize: mmToPx(element.fontSize ?? 10, zoom) / 3.78,
      fontFamily: element.fontFamily ?? 'SimSun, serif',
      fontWeight: element.fontWeight ?? 'normal',
      fontStyle: element.fontStyle ?? 'normal',
      textAlign: element.textAlign ?? 'left',
      color: element.color ?? '#000',
      backgroundColor: element.backgroundColor ?? 'transparent',
      borderWidth: element.borderWidth ? Math.max(1, mmToPx(element.borderWidth, zoom) / 3.78) : 0,
      borderColor: element.borderColor ?? 'transparent',
      borderStyle: element.borderStyle ?? 'none',
      padding: element.padding ?? 2,
    }

    // 线条特殊处理
    if (element.type === 'line') {
      base.height = Math.max(1, element.lineWidth ?? 1)
      base.backgroundColor = element.lineColor ?? '#000'
      base.borderStyle = 'none'
      base.borderWidth = 0
      base.padding = 0
      base.fontSize = 0
    }

    // 文字类元素允许内容溢出显示
    if (element.type === 'text' || element.type === 'data-field' || element.type === 'page-number') {
      base.overflow = 'visible'
      base.whiteSpace = 'nowrap'
    }

    return base
  }, [element, zoom, offsetX, offsetY])

  const handleClick = (e: React.MouseEvent) => {
    if (isPreview) return // 预览模式不响应点击
    e.stopPropagation()
    selectElement(element.id, e.ctrlKey || e.metaKey)
  }

  const handleMouseDown = (e: React.MouseEvent) => {
    if (isPreview) return // 预览模式不可移动
    if (!isSelected) {
      selectElement(element.id, e.ctrlKey || e.metaKey)
    }
    onMouseDown(e)
  }

  // ===== 渲染内容（根据模式不同） =====
  const renderContent = () => {
    switch (element.type) {
      // ------ 静态文本 ------
      case 'text':
        return <span style={{ display: 'inline-block', whiteSpace: 'nowrap' }}>{element.text || '文本'}</span>

      // ------ 数据字段 ------
      case 'data-field': {
        const binding = element.fieldBinding

        // 预览模式 + 有绑定 → 解析实际数据
        if (isPreview && binding && documentData) {
          const resolvedValue = resolveFieldBinding(binding, documentData)
          return (
            <span className="preview-value" style={{ color: '#000' }}>
              {resolvedValue}
            </span>
          )
        }

        // 设计模式 → 显示占位符
        return (
          <span style={{ color: binding ? '#1a73e8' : '#999' }}>
            {binding
              ? `「${binding.tableName}.${binding.fieldLabel}」`
              : element.text || '{{字段}}'}
          </span>
        )
      }

      // ------ 线条 ------
      case 'line':
        return null

      // ------ 矩形 ------
      case 'rectangle':
        return <span style={{ fontSize: 0 }}>&nbsp;</span>

      // ------ 表格 ------
      case 'table': {
        const grid = element.tableGrid

        // ==== 新格式：tableGrid ====
        if (grid && grid.columns && grid.columns.length > 0) {
          const { columns, headerRow, dataRow, defaultStyle } = grid
          const totalW = columns.reduce((a, c) => a + (c.width || 1), 0) || 1

          // 安全的单元格样式
          const cellStyleSafe = (cell: any, isHeader: boolean): React.CSSProperties => {
            const s = (cell?.style || defaultStyle || {}) as any
            const result: React.CSSProperties = {
              textAlign: (s.horizontalAlign as any) || 'center',
              verticalAlign: (s.verticalAlign as any) || 'middle',
              fontWeight: s.fontWeight,
              fontFamily: s.fontFamily,
              fontSize: typeof s.fontSize === 'number' ? s.fontSize * 0.75 : 9,
              color: s.color || '#000',
              backgroundColor: s.backgroundColor || (isHeader ? '#f0f0f0' : 'transparent'),
              wordWrap: s.wordWrap ? 'break-word' : 'normal',
              padding: '1px 2px',
            }
            // 边框 - 防御性处理
            try {
              const bt = cell?.style?.borderTop
              if (bt && bt.style && bt.style !== 'none') result.borderTop = `${bt.width || 1}px ${bt.style} ${bt.color || '#000'}`
              const bb = cell?.style?.borderBottom
              if (bb && bb.style && bb.style !== 'none') result.borderBottom = `${bb.width || 1}px ${bb.style} ${bb.color || '#000'}`
              const bl = cell?.style?.borderLeft
              if (bl && bl.style && bl.style !== 'none') result.borderLeft = `${bl.width || 1}px ${bl.style} ${bl.color || '#000'}`
              const br = cell?.style?.borderRight
              if (br && br.style && br.style !== 'none') result.borderRight = `${br.width || 1}px ${br.style} ${br.color || '#000'}`
            } catch (_) {/* ignore border errors */}
            return result
          }

          // 单元格内容
          const renderCellContent = (cell: any, rowData?: Record<string, unknown>): string => {
            if (!cell) return ''
            try {
              if (cell.type === 'text') return cell.text || ''
              if (cell.type === 'field' && cell.binding) {
                if (isPreview && documentData) {
                  return resolveFieldBinding(cell.binding, documentData, rowData)
                }
                return `「${cell.binding?.fieldLabel || '?'}」`
              }
              if (cell.type === 'statistic') return '∑'
              if (cell.type === 'dynamic') return '{?}'
            } catch (_) {/* ignore */}
            return ''
          }

          const previewRows = isPreview && documentData ? documentData.entries : Array.from({ length: 3 })

          // 行高转 px（1mm ≈ 3.78px）
          const headerH = mmToPx(headerRow?.height || 12, 1) / 3.78
          const dataH = mmToPx(dataRow?.height || 12, 1) / 3.78

          return (
            <table className="table-element" style={{ borderCollapse: 'collapse', width: '100%', height: '100%' }}>
              <thead>
                <tr>
                  {columns.map((col) => {
                    const hc = headerRow?.cells?.[col.id]
                    const isCellSelected = !isPreview &&
                      useDesignerStore.getState().selectedGridCell?.elementId === element.id &&
                      useDesignerStore.getState().selectedGridCell?.rowKey === 'headerRow' &&
                      useDesignerStore.getState().selectedGridCell?.columnId === col.id
                    return (
                      <th
                        key={col.id}
                        style={{
                          width: `${((col.width || 1) / totalW) * 100}%`,
                          height: headerH,
                          lineHeight: `${headerH}px`,
                          ...cellStyleSafe(hc, true),
                          outline: isCellSelected ? '2px solid var(--color-primary)' : undefined,
                          outlineOffset: -2,
                        }}
                        onClick={(e) => {
                          if (!isPreview) {
                            e.stopPropagation()
                            useDesignerStore.getState().selectGridCell(element.id, 'headerRow', col.id)
                          }
                        }}
                      >
                        {renderCellContent(hc)}
                      </th>
                    )
                  })}
                </tr>
              </thead>
              <tbody>
                {previewRows.map((rowData: any, ri) => (
                  <tr key={ri}>
                    {columns.map((col) => {
                      const dc = dataRow?.cells?.[col.id]
                      const content = renderCellContent(dc, rowData)
                      const display = content
                      const isCellSelected = !isPreview &&
                        useDesignerStore.getState().selectedGridCell?.elementId === element.id &&
                        useDesignerStore.getState().selectedGridCell?.rowKey === 'dataRow' &&
                        useDesignerStore.getState().selectedGridCell?.columnId === col.id &&
                        (useDesignerStore.getState().selectedGridCell?.rowIndex ?? 0) === ri
                      return (
                        <td
                          key={col.id}
                          style={{
                            width: `${((col.width || 1) / totalW) * 100}%`,
                            height: dataH,
                            lineHeight: `${dataH}px`,
                            ...cellStyleSafe(dc, false),
                            outline: isCellSelected ? '2px solid var(--color-primary)' : undefined,
                            outlineOffset: -2,
                          }}
                          onClick={(e) => {
                            if (!isPreview) {
                              e.stopPropagation()
                              useDesignerStore.getState().selectGridCell(element.id, 'dataRow', col.id, ri)
                            }
                          }}
                        >
                          {display}
                        </td>
                      )
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          )
        }

        // ==== 旧格式兼容：tableColumns ====
        const columns = element.tableColumns || []
        const totalColWidth = columns.reduce((a, c) => a + c.width, 1)

        if (isPreview && documentData) {
          const entries = documentData.entries
          return (
            <table className="table-element">
              <thead>
                <tr>
                  {columns.map((col) => (
                    <th key={col.id} style={{ width: `${(col.width / totalColWidth) * 100}%`, textAlign: col.align || 'left' }}>
                      {col.header}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {entries.map((row, ri) => (
                  <tr key={ri}>
                    {columns.map((col) => (
                      <td key={col.id} style={{ textAlign: col.align || 'left' }}>
                        {col.binding ? resolveFieldBinding(col.binding, documentData, row) : col.header === '序号' ? String(ri + 1) : ''}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          )
        }

        return (
          <table className="table-element">
            <thead>
              <tr>
                {columns.map((col) => (
                  <th key={col.id} style={{ width: `${(col.width / totalColWidth) * 100}%`, textAlign: col.align || 'left' }}>
                    {col.binding ? `「${col.binding.fieldLabel}」` : col.header}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {Array.from({ length: Math.min(element.tableRows || 3, 3) }).map((_, ri) => (
                <tr key={ri}>
                  {columns.map((col) => (
                    <td key={col.id} style={{ textAlign: col.align || 'left' }}>&nbsp;</td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        )
      }

      // ------ 图片 ------
      case 'image':
        return (
          <div className="image-placeholder">
            {element.imageUrl ? (
              <img
                src={element.imageUrl}
                alt=""
                draggable={false}
                style={{
                  width: '100%',
                  height: '100%',
                  objectFit: element.imageFit || 'contain',
                  pointerEvents: 'none',
                  userSelect: 'none',
                }}
              />
            ) : (
              '图片'
            )}
          </div>
        )

      // ------ 条形码 ------
      case 'barcode': {
        let barcodeText = element.barcodeValue || '1234567890'
        if (isPreview && element.fieldBinding && documentData) {
          const resolved = resolveFieldBinding(element.fieldBinding, documentData)
          if (resolved) barcodeText = resolved
        }
        const barcodeType = element.barcodeType || 'code128'
        const wPx = mmToPx(element.width, zoom)
        const hPx = mmToPx(element.height, zoom)

        return (
          <BarcodeRenderer
            barcodeType={barcodeType}
            value={barcodeText}
            isPreview={isPreview}
            width={wPx}
            height={hPx}
          />
        )
      }

      // ------ 页码 ------
      case 'page-number': {
        const displayText = isPreview
          ? '第 1 页 / 共 1 页'
          : (element.text || '第 {current} 页 / 共 {total} 页')
        return (
          <span style={{
            display: 'inline-block',
            color: '#000',
            fontSize: 'inherit',
            whiteSpace: 'nowrap',
            lineHeight: '1.2',
          }}>
            {displayText}
          </span>
        )
      }

      default:
        return <span>未知元素</span>
    }
  }

  const showHandles = !isPreview && isSelected && element.type !== 'line'

  const handleNames = ['nw', 'n', 'ne', 'e', 'se', 's', 'sw', 'w'] as const

  return (
    <div
      className={`canvas-element${!isPreview && isSelected ? ' selected' : ''}${element.locked ? ' locked' : ''}${isPreview ? ' preview' : ''}`}
      data-type={element.type}
      data-id={element.id}
      style={style}
      onClick={handleClick}
      onMouseDown={handleMouseDown}
    >
      {renderContent()}
      {/* 8 控制柄 — 仅设计模式 + 被选中时显示 */}
      {showHandles &&
        handleNames.map((h) => (
          <div
            key={h}
            className={`resize-handle resize-${h}`}
            data-handle={h}
            onMouseDown={(e) => {
              e.stopPropagation()
              e.preventDefault()
              onResizeStart(e, h)
            }}
          />
        ))}
    </div>
  )
})
