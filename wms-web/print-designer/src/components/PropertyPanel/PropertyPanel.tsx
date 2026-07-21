import { useCallback, useState, useEffect, useRef } from 'react'
import { useDesignerStore } from '../../store/useDesignerStore'
import { FieldSelector } from '../FieldSelector/FieldSelector'
import { TablePropertyEditor } from '../TableEditor/TablePropertyEditor'
import type { CanvasElement } from '../../types/designer'
import './PropertyPanel.css'

/**
 * 延迟提交数字输入框
 * - 编辑时使用本地状态，不会立即回写 store
 * - 失焦时才将最终值提交到 store（空值时使用 fallback）
 */
function DelayedNumberInput({
  value,
  onChange,
  fallback = 0,
  min,
  max,
  className,
  step,
}: {
  value: number
  onChange: (v: number) => void
  fallback?: number
  min?: number
  max?: number
  className?: string
  step?: number
}) {
  const [local, setLocal] = useState(String(value))
  const inputRef = useRef<HTMLInputElement>(null)

  // 当外部 value 变化（非自身编辑导致）时同步本地状态
  useEffect(() => {
    // 只在非聚焦时同步，避免打字过程中被覆盖
    if (document.activeElement !== inputRef.current) {
      setLocal(String(value))
    }
  }, [value])

  const commit = useCallback(() => {
    const parsed = parseFloat(local)
    const finalVal = isNaN(parsed) ? fallback : parsed
    onChange(finalVal)
    setLocal(String(finalVal))
  }, [local, fallback, onChange])

  return (
    <input
      ref={inputRef}
      type="number"
      className={className || 'prop-input'}
      value={local}
      min={min}
      max={max}
      step={step}
      onChange={(e) => setLocal(e.target.value)}
      onBlur={commit}
      onKeyDown={(e) => {
        if (e.key === 'Enter') {
          commit()
          inputRef.current?.blur()
        }
      }}
    />
  )
}

/** 属性编辑区域组件 */
function PropertyGroup({ title, children }: { title: string; children: React.ReactNode }) {
  const [collapsed, setCollapsed] = useState(false)
  return (
    <div className="prop-group">
      <div className="prop-group-header" onClick={() => setCollapsed(!collapsed)}>
        <span className={`prop-arrow ${collapsed ? 'collapsed' : ''}`}>▶</span>
        <span className="prop-title">{title}</span>
      </div>
      {!collapsed && <div className="prop-group-body">{children}</div>}
    </div>
  )
}

/** 单个属性行 */
function PropRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="prop-row">
      <label className="prop-label">{label}</label>
      <div className="prop-value">{children}</div>
    </div>
  )
}

export function PropertyPanel({ embedded = false }: { embedded?: boolean }) {
  const {
    elements,
    selectedElementIds,
    updateElement,
    removeElement,
    duplicateElement,
    bringForward,
    sendBackward,
    bringToFront,
    sendToBack,
    viewMode,
  } = useDesignerStore()

  const isPreview = viewMode === 'preview'

  const selectedId = selectedElementIds.length === 1 ? selectedElementIds[0] : null
  const element = selectedId ? elements.find((e) => e.id === selectedId) ?? null : null

  const handleChange = useCallback(
    (key: keyof CanvasElement, value: unknown) => {
      if (!selectedId) return
      selectedElementIds.forEach((id) => {
        updateElement(id, { [key]: value })
      })
    },
    [selectedId, selectedElementIds, updateElement]
  )

  const handleFieldBind = useCallback(
    (binding: CanvasElement['fieldBinding']) => {
      if (!selectedId) return
      updateElement(selectedId, {
        fieldBinding: binding,
        text: binding ? `「${binding.tableName}.${binding.fieldLabel}」` : '{{字段}}',
      })
    },
    [selectedId, updateElement]
  )

  const isMultiSelect = selectedElementIds.length > 1

  // ==== 渲染内容 ====

  if (!element) {
    if (embedded) {
      return (
        <div className="props-empty">
          <span>📐</span>
          <p>请选择一个元素<br />以编辑其属性</p>
        </div>
      )
    }
    return (
      <div className="props-panel">
        <div className="props-header">属性</div>
        <div className="props-empty">
          <span>📐</span>
          <p>请选择一个元素<br />以编辑其属性</p>
        </div>
      </div>
    )
  }

  const bodyContent = (
    <>
      {/* 位置与大小 */}
      <PropertyGroup title="位置与大小">
        <PropRow label="X (mm)">
          <div className="prop-row-inline">
            <DelayedNumberInput value={Math.round(element.x * 10) / 10} onChange={(v) => handleChange('x', v)} fallback={0} className="prop-input" />
            <span className="prop-unit">mm</span>
          </div>
        </PropRow>
        <PropRow label="Y (mm)">
          <div className="prop-row-inline">
            <DelayedNumberInput value={Math.round(element.y * 10) / 10} onChange={(v) => handleChange('y', v)} fallback={0} className="prop-input" />
            <span className="prop-unit">mm</span>
          </div>
        </PropRow>
        <PropRow label="宽度">
          <div className="prop-row-inline">
            <DelayedNumberInput value={Math.round(element.width * 10) / 10} onChange={(v) => handleChange('width', v)} fallback={10} min={5} className="prop-input" />
            <span className="prop-unit">mm</span>
          </div>
        </PropRow>
        <PropRow label="高度">
          <div className="prop-row-inline">
            <DelayedNumberInput value={Math.round(element.height * 10) / 10} onChange={(v) => handleChange('height', v)} fallback={10} min={5} className="prop-input" />
            <span className="prop-unit">mm</span>
          </div>
        </PropRow>
      </PropertyGroup>

      {/* 文本样式 */}
      {(element.type === 'text' || element.type === 'data-field' || element.type === 'page-number') && (
        <PropertyGroup title="文本样式">
          {element.type !== 'data-field' && (
            <PropRow label="文本内容">
              <input type="text" value={element.text || ''} onChange={(e) => handleChange('text', e.target.value)} className="prop-input" />
            </PropRow>
          )}
          <PropRow label="字号">
            <div className="prop-row-inline">
              <DelayedNumberInput value={element.fontSize ?? 12} onChange={(v) => handleChange('fontSize', v)} fallback={12} min={6} max={72} className="prop-input" />
              <span className="prop-unit">pt</span>
            </div>
          </PropRow>
          <PropRow label="字体">
            <select value={element.fontFamily || 'SimSun'} onChange={(e) => handleChange('fontFamily', e.target.value)} className="prop-select">
              <option value="SimSun, serif">宋体</option>
              <option value="SimHei, sans-serif">黑体</option>
              <option value="KaiTi, serif">楷体</option>
              <option value="FangSong, serif">仿宋</option>
              <option value="Microsoft YaHei, sans-serif">微软雅黑</option>
              <option value="Arial, sans-serif">Arial</option>
              <option value="'Courier New', monospace">Courier New</option>
            </select>
          </PropRow>
          <PropRow label="加粗">
            <input type="checkbox" checked={element.fontWeight === 'bold'} onChange={(e) => handleChange('fontWeight', e.target.checked ? 'bold' : 'normal')} />
          </PropRow>
          <PropRow label="对齐">
            <select value={element.textAlign || 'left'} onChange={(e) => handleChange('textAlign', e.target.value)} className="prop-select">
              <option value="left">左对齐</option>
              <option value="center">居中</option>
              <option value="right">右对齐</option>
            </select>
          </PropRow>
          <PropRow label="文字颜色">
            <input type="color" value={element.color || '#000000'} onChange={(e) => handleChange('color', e.target.value)} className="prop-color" />
          </PropRow>
        </PropertyGroup>
      )}

      {/* 边框与背景 */}
      {element.type !== 'line' && element.type !== 'image' && (
        <PropertyGroup title="边框与背景">
          <PropRow label="边框样式">
            <select value={element.borderStyle || 'none'} onChange={(e) => handleChange('borderStyle', e.target.value)} className="prop-select">
              <option value="none">无边框</option>
              <option value="solid">实线</option>
              <option value="dashed">虚线</option>
              <option value="dotted">点线</option>
            </select>
          </PropRow>
          <PropRow label="边框颜色">
            <input type="color" value={element.borderColor || '#000000'} onChange={(e) => handleChange('borderColor', e.target.value)} className="prop-color" />
          </PropRow>
          <PropRow label="背景色">
            <input type="color" value={element.backgroundColor || '#ffffff'} onChange={(e) => handleChange('backgroundColor', e.target.value)} className="prop-color" />
          </PropRow>
        </PropertyGroup>
      )}

      {/* 线条样式 */}
      {element.type === 'line' && (
        <PropertyGroup title="线条样式">
          <PropRow label="线宽">
            <div className="prop-row-inline">
              <DelayedNumberInput value={element.lineWidth || 1} onChange={(v) => handleChange('lineWidth', v)} fallback={1} min={1} max={10} className="prop-input" />
              <span className="prop-unit">px</span>
            </div>
          </PropRow>
          <PropRow label="线条颜色">
            <input type="color" value={element.lineColor || '#000000'} onChange={(e) => handleChange('lineColor', e.target.value)} className="prop-color" />
          </PropRow>
        </PropertyGroup>
      )}

      {/* 图片设置 */}
      {element.type === 'image' && (
        <PropertyGroup title="图片设置">
          <PropRow label="图片来源">
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6, width: '100%' }}>
              <button
                className="prop-image-upload-btn"
                onClick={() => {
                  const input = document.createElement('input')
                  input.type = 'file'
                  input.accept = 'image/*'
                  input.onchange = (e) => {
                    const file = (e.target as HTMLInputElement).files?.[0]
                    if (!file) return
                    const reader = new FileReader()
                    reader.onload = (ev) => {
                      const dataUrl = ev.target?.result as string
                      handleChange('imageUrl', dataUrl)
                    }
                    reader.readAsDataURL(file)
                  }
                  input.click()
                }}
              >
                📁 选择本地图片
              </button>
              <div style={{ fontSize: 10, color: '#999', textAlign: 'center' }}>或输入图片链接</div>
              <input
                type="text"
                value={element.imageUrl || ''}
                onChange={(e) => handleChange('imageUrl', e.target.value)}
                className="prop-input"
                placeholder="https://example.com/image.png"
              />
              {element.imageUrl && (
                <button
                  className="prop-image-clear-btn"
                  onClick={() => handleChange('imageUrl', '')}
                >
                  ✕ 清除图片
                </button>
              )}
            </div>
          </PropRow>
          <PropRow label="适应方式">
            <select value={element.imageFit || 'contain'} onChange={(e) => handleChange('imageFit', e.target.value)} className="prop-select">
              <option value="contain">包含 (contain)</option>
              <option value="cover">填充 (cover)</option>
              <option value="fill">拉伸 (fill)</option>
              <option value="none">原始大小</option>
            </select>
          </PropRow>
        </PropertyGroup>
      )}

      {/* 字段绑定 */}
      {element.type === 'data-field' && (
        <PropertyGroup title="字段绑定">
          <FieldSelector currentBinding={element.fieldBinding} onBind={handleFieldBind} />
        </PropertyGroup>
      )}

      {/* 条码设置 */}
      {element.type === 'barcode' && (
        <PropertyGroup title="条码设置">
          <PropRow label="条码类型">
            <select value={element.barcodeType || 'code128'} onChange={(e) => handleChange('barcodeType', e.target.value)} className="prop-select">
              <option value="code128">Code 128</option>
              <option value="code39">Code 39</option>
              <option value="ean13">EAN-13</option>
              <option value="qr">二维码</option>
            </select>
          </PropRow>
          <PropRow label="条码内容">
            <input type="text" value={element.barcodeValue || ''} onChange={(e) => handleChange('barcodeValue', e.target.value)} className="prop-input" />
          </PropRow>
        </PropertyGroup>
      )}

      {/* 层级操作 */}
      <PropertyGroup title="层级">
        <div className="prop-action-row">
          <button className="prop-action-btn" onClick={() => selectedId && bringToFront(selectedId)}>置顶</button>
          <button className="prop-action-btn" onClick={() => selectedId && bringForward(selectedId)}>上移</button>
          <button className="prop-action-btn" onClick={() => selectedId && sendBackward(selectedId)}>下移</button>
          <button className="prop-action-btn" onClick={() => selectedId && sendToBack(selectedId)}>置底</button>
        </div>
      </PropertyGroup>

      {/* 操作按钮 */}
      <div className="prop-actions">
        <button className="prop-action-danger" onClick={() => selectedId && removeElement(selectedId)}>🗑 删除元素</button>
        <button className="prop-action-secondary" onClick={() => selectedId && duplicateElement(selectedId)}>📋 复制元素</button>
      </div>

      {/* ===== 表格单元格属性（仅 table 类型 + 有 tableGrid 时显示） ===== */}
      {element.type === 'table' && element.tableGrid && (
        <>
          <div style={{ borderTop: '1px solid var(--color-border)', margin: '4px 8px' }} />
          <TablePropertyEditor />
        </>
      )}
    </>
  )

  const header = (
    <div className="props-header">
      <span>属性</span>
      {isPreview && <span className="props-multi-badge" style={{ background: '#fef7e0', color: '#f9ab00' }}>只读</span>}
      {isMultiSelect && <span className="props-multi-badge">已选 {selectedElementIds.length} 个</span>}
    </div>
  )

  if (embedded) {
    return (
      <>
        {header}
        <div className="props-body">{bodyContent}</div>
      </>
    )
  }

  return (
    <div className="props-panel">
      {header}
      <div className="props-body">{bodyContent}</div>
    </div>
  )
}
