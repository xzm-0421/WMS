import { useCallback, useRef, useState } from 'react'
import { useDroppable } from '@dnd-kit/core'
import { useDesignerStore } from '../../store/useDesignerStore'
import { CanvasElementView } from './CanvasElement'
import './Canvas.css'

function mmToPx(mm: number, zoom: number): number {
  return mm * 3.78 * zoom
}

export function Canvas() {
  const { elements, pageSettings, zoom, clearSelection, selectedElementIds, viewMode, documentData } =
    useDesignerStore()
  const { setNodeRef, isOver } = useDroppable({ id: 'canvas-droppable' })

  const isPreview = viewMode === 'preview'
  const canvasRef = useRef<HTMLDivElement>(null)

  const [draggingId, setDraggingId] = useState<string | null>(null)
  const [dragOffset, setDragOffset] = useState({ x: 0, y: 0 })

  const pageWidth = mmToPx(pageSettings.width, zoom)
  const pageHeight = mmToPx(pageSettings.height, zoom)
  const gridSize = mmToPx(pageSettings.gridSize, zoom)
  const isLabelOval = pageSettings.width <= 120 && pageSettings.height <= 100

  const handleCanvasClick = useCallback(
    (e: React.MouseEvent) => {
      if (isPreview) return
      if (e.target === e.currentTarget || (e.target as HTMLElement).classList.contains('canvas-page')) {
        clearSelection()
      }
    },
    [clearSelection, isPreview]
  )

  // ==== 元素拖拽移动 ====
  const handleElementMouseDown = useCallback(
    (e: React.MouseEvent, elementId: string) => {
      if (isPreview) return
      e.stopPropagation()
      const store = useDesignerStore.getState()
      const el = store.elements.find((x) => x.id === elementId)
      if (!el || el.locked) return

      const startX = e.clientX
      const startY = e.clientY
      const startLeft = el.x
      const startTop = el.y

      setDraggingId(elementId)
      setDragOffset({ x: 0, y: 0 })

      const handleMouseMove = (moveEvent: MouseEvent) => {
        const dx = (moveEvent.clientX - startX) / zoom / 3.78
        const dy = (moveEvent.clientY - startY) / zoom / 3.78
        setDragOffset({ x: dx, y: dy })
      }

      const handleMouseUp = (upEvent: MouseEvent) => {
        const dx = (upEvent.clientX - startX) / zoom / 3.78
        const dy = (upEvent.clientY - startY) / zoom / 3.78

        let newX = startLeft + dx
        let newY = startTop + dy
        if (pageSettings.snapToGrid) {
          newX = Math.round(newX / pageSettings.gridSize) * pageSettings.gridSize
          newY = Math.round(newY / pageSettings.gridSize) * pageSettings.gridSize
        }
        newX = Math.max(0, Math.min(newX, pageSettings.width - el.width))
        newY = Math.max(0, Math.min(newY, pageSettings.height - el.height))

        // 只有实际移动才推历史（updateElement 会调用 _pushHistory）
        const moved = Math.abs(newX - startLeft) > 0.01 || Math.abs(newY - startTop) > 0.01
        if (moved) {
          store.updateElement(elementId, { x: newX, y: newY })
        }
        setDraggingId(null)
        setDragOffset({ x: 0, y: 0 })
        document.removeEventListener('mousemove', handleMouseMove)
        document.removeEventListener('mouseup', handleMouseUp)
      }

      document.addEventListener('mousemove', handleMouseMove)
      document.addEventListener('mouseup', handleMouseUp)
    },
    [zoom, pageSettings, isPreview]
  )

  // ==== 8 控制柄缩放 ====
  const handleResizeStart = useCallback(
    (e: React.MouseEvent, elementId: string, handle: string) => {
      if (isPreview) return
      e.stopPropagation()
      e.preventDefault()

      const store = useDesignerStore.getState()
      const el = store.elements.find((x) => x.id === elementId)
      if (!el || el.locked) return

      const startX = e.clientX
      const startY = e.clientY
      const startW = el.width
      const startH = el.height
      const startLeft = el.x
      const startTop = el.y
      const minSize = 5

      const handleMouseMove = (moveEvent: MouseEvent) => {
        const dpx = (moveEvent.clientX - startX) / zoom / 3.78 // mm
        const dpy = (moveEvent.clientY - startY) / zoom / 3.78

        let newW = startW
        let newH = startH
        let newX = startLeft
        let newY = startTop

        // 根据控制柄方向计算新尺寸和位置
        if (handle.includes('e')) {
          newW = Math.max(minSize, startW + dpx)
        }
        if (handle.includes('w')) {
          const ww = Math.max(minSize, startW - dpx)
          newX = startLeft + (startW - ww)
          newW = ww
        }
        if (handle.includes('s')) {
          newH = Math.max(minSize, startH + dpy)
        }
        if (handle.includes('n')) {
          const hh = Math.max(minSize, startH - dpy)
          newY = startTop + (startH - hh)
          newH = hh
        }

        // 使用 resizeElement（只在首次推历史，后续帧不推）
        store.resizeElement(elementId, newW, newH, newX, newY)
      }

      const handleMouseUp = () => {
        // 清除 resize 标记，下次 resize 会重新推历史
        store._endResize()
        document.removeEventListener('mousemove', handleMouseMove)
        document.removeEventListener('mouseup', handleMouseUp)
      }

      document.addEventListener('mousemove', handleMouseMove)
      document.addEventListener('mouseup', handleMouseUp)
    },
    [isPreview, zoom]
  )

  // ==== 预览模式表格高度 ====
  const getPreviewElementHeight = (elType: string, originalHeight: number): number | undefined => {
    if (!isPreview || !documentData || elType !== 'table') return undefined
    const rowHeight = 6
    const headerHeight = 6
    return headerHeight + documentData.entries.length * rowHeight
  }

  return (
    <div className={`canvas-container${isPreview ? ' preview-mode' : ''}`} ref={canvasRef}>
      <div className="canvas-scroll">
        <div
          ref={setNodeRef}
          className={`canvas-drop-zone ${isOver && !isPreview ? 'drop-active' : ''}`}
          style={{ padding: `${mmToPx(20, zoom)}px` }}
        >
          <div
            className={`canvas-page${isLabelOval ? ' label-oval' : ''}`}
            data-zoom={zoom}
            data-page-width-mm={pageSettings.width}
            data-page-height-mm={pageSettings.height}
            style={{
              width: pageWidth,
              height: pageHeight,
              transform: `scale(${zoom})`,
              transformOrigin: 'top left',
              backgroundImage:
                pageSettings.showGrid && !isPreview && gridSize >= 8
                  ? `
                    linear-gradient(0deg, #e8eaed 0px, transparent 0px, transparent ${gridSize}px),
                    linear-gradient(90deg, #e8eaed 0px, transparent 0px, transparent ${gridSize}px)
                  `
                  : 'none',
              backgroundSize: `${gridSize}px ${gridSize}px`,
            }}
            onClick={handleCanvasClick}
          >
            {!isPreview && (
              <div
                className="page-margin-guide"
                style={{
                  left: mmToPx(pageSettings.marginLeft, 1),
                  right: mmToPx(pageSettings.marginRight, 1),
                  top: mmToPx(pageSettings.marginTop, 1),
                  bottom: mmToPx(pageSettings.marginBottom, 1),
                }}
              />
            )}

            {elements
              .sort((a, b) => a.zIndex - b.zIndex)
              .map((el) => {
                const isSelected = selectedElementIds.includes(el.id)
                const isDragging = draggingId === el.id
                const offsetX = isDragging ? dragOffset.x : 0
                const offsetY = isDragging ? dragOffset.y : 0

                const previewHeight = getPreviewElementHeight(el.type, el.height)
                const previewEl = previewHeight ? { ...el, height: previewHeight } : el

                return (
                  <CanvasElementView
                    key={el.id}
                    element={previewEl}
                    zoom={zoom}
                    isSelected={isSelected}
                    offsetX={offsetX}
                    offsetY={offsetY}
                    onMouseDown={(e) => handleElementMouseDown(e, el.id)}
                    onResizeStart={(e, handle) => handleResizeStart(e, el.id, handle)}
                  />
                )
              })}
          </div>
        </div>
      </div>
    </div>
  )
}
