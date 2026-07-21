import { useState } from 'react'
import { useDraggable } from '@dnd-kit/core'
import { useDesignerStore } from '../../store/useDesignerStore'
import { TOOLBOX_ITEMS } from '../../data/toolboxItems'
import type { ElementCategory, ToolboxItem } from '../../types/designer'
import './Toolbox.css'

/** 分类信息 */
const CATEGORIES: { key: ElementCategory; label: string }[] = [
  { key: 'basic', label: '基础控件' },
  { key: 'drawing', label: '绘图控件' },
  { key: 'data', label: '高级控件' },
  { key: 'system', label: '系统控件' },
]

/** 单个工具箱项（可拖拽） */
function ToolboxDraggableItem({ item, disabled }: { item: ToolboxItem; disabled: boolean }) {
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({
    id: `toolbox-${item.type}`,
    data: { type: item.type, fromToolbox: true },
    disabled,
  })

  return (
    <div
      ref={setNodeRef}
      className={`toolbox-item ${isDragging ? 'dragging' : ''} ${disabled ? 'disabled' : ''}`}
      {...listeners}
      {...attributes}
    >
      <span className="toolbox-item-icon">{item.icon}</span>
      <span className="toolbox-item-label">{item.label}</span>
      <span className="toolbox-item-size">
        {item.defaultWidth}×{item.defaultHeight}
      </span>
    </div>
  )
}

export function Toolbox() {
  const [collapsed, setCollapsed] = useState<Set<string>>(new Set())
  const viewMode = useDesignerStore((s) => s.viewMode)
  const isPreview = viewMode === 'preview'

  const toggleCategory = (key: string) => {
    setCollapsed((prev) => {
      const next = new Set(prev)
      if (next.has(key)) next.delete(key)
      else next.add(key)
      return next
    })
  }

  return (
    <div className="toolbox-panel">
      <div className="toolbox-header">
        {isPreview ? '数据预览中' : '控件工具箱'}
      </div>
      <div className="toolbox-body" style={isPreview ? { opacity: 0.5, pointerEvents: 'none' } : {}}>
        {CATEGORIES.map((cat) => {
          const items = TOOLBOX_ITEMS.filter((i) => i.category === cat.key)
          if (items.length === 0) return null
          const isCollapsed = collapsed.has(cat.key)

          return (
            <div key={cat.key} className="toolbox-category">
              <div
                className="toolbox-category-header"
                onClick={() => toggleCategory(cat.key)}
              >
                <span className={`category-arrow ${isCollapsed ? 'collapsed' : ''}`}>
                  ▶
                </span>
                <span className="category-label">{cat.label}</span>
                <span className="category-count">{items.length}</span>
              </div>
              {!isCollapsed && (
                <div className="toolbox-category-items">
                  {items.map((item) => (
                    <ToolboxDraggableItem key={item.type} item={item} disabled={isPreview} />
                  ))}
                </div>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}
