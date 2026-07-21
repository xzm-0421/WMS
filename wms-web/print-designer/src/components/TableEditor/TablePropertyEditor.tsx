/**
 * 表格单元格属性编辑器
 *
 * 选中表格的某个单元格后，右侧[属性]面板编辑该单元格：
 *   类型 / 字段绑定 / 格式 / 样式 / 边框 / 通用
 */
import { useState, useEffect, useRef, useCallback } from 'react'
import { useDesignerStore } from '../../store/useDesignerStore'
import { FieldSelector } from '../FieldSelector/FieldSelector'
import type { GridCell, GridCellType, FormatCategory, CellBorder, CellStyle } from '../../types/designer'
import './TableEditor.css'

// ===== 延迟提交数字输入框（失焦时才回写） =====
function DelayedInp({ value, onChange, fallback = 0, min, max, placeholder }: {
  value: number
  onChange: (v: number) => void
  fallback?: number
  min?: number
  max?: number
  placeholder?: string
}) {
  const [local, setLocal] = useState(String(value))
  const ref = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (document.activeElement !== ref.current) {
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
      ref={ref}
      className="ted-input"
      type="number"
      value={local}
      min={min}
      max={max}
      placeholder={placeholder}
      onChange={(e) => setLocal(e.target.value)}
      onBlur={commit}
      onKeyDown={(e) => { if (e.key === 'Enter') { commit(); ref.current?.blur() } }}
    />
  )
}

// ===== 折叠分组 =====
function Group({ title, children }: { title: string; children: React.ReactNode }) {
  const [collapsed, setCollapsed] = useState(false)
  return (
    <div className="ted-group">
      <div className="ted-group-header" onClick={() => setCollapsed(!collapsed)}>
        <span className={`ted-arrow ${collapsed ? 'collapsed' : ''}`}>▶</span>
        <span className="ted-title">{title}</span>
      </div>
      {!collapsed && <div className="ted-group-body">{children}</div>}
    </div>
  )
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return <div className="ted-row"><label className="ted-label">{label}</label><div className="ted-value">{children}</div></div>
}

function Sel({ value, onChange, options }: { value: string; onChange: (v: string) => void; options: { value: string; label: string }[] }) {
  return <select className="ted-select" value={value} onChange={(e) => onChange(e.target.value)}>{options.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}</select>
}

function Inp({ value, onChange, type = 'text', placeholder }: { value: string; onChange: (v: string) => void; type?: string; placeholder?: string }) {
  return <input className="ted-input" type={type} value={value} onChange={(e) => onChange(e.target.value)} placeholder={placeholder} />
}

function Chk({ checked, onChange }: { checked: boolean; onChange: (v: boolean) => void }) {
  return <input type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} />
}

// ===== 边框选择 =====
function Bdr({ border, onChange, label }: { border?: CellBorder; onChange: (b: CellBorder | undefined) => void; label: string }) {
  const current = border || { color: '#000000', style: 'solid', width: 1 }
  return (
    <div style={{ padding: '2px 0' }}>
      <span style={{ fontSize: 10, color: '#999', display: 'inline-block', width: 30 }}>{label}</span>
      <input className="ted-input" style={{ width: 40 }} type="number" min={0} max={10} value={current.width || 1} onChange={(e) => onChange({ ...current, width: Math.max(0, parseInt(e.target.value) || 0) })} onBlur={(e) => { const v = parseInt(e.target.value) || 1; if (v < 1) onChange({ ...current, width: 1 }) }} />
      <select className="ted-select" style={{ width: 55 }} value={current.style || 'solid'} onChange={(e) => onChange({ ...current, style: e.target.value as CellBorder['style'] })}>
        <option value="solid">实线</option><option value="dashed">虚线</option><option value="dotted">点线</option><option value="none">无</option>
      </select>
      <input className="ted-color" type="color" value={current.color || '#000'} onChange={(e) => onChange({ ...current, color: e.target.value })} />
    </div>
  )
}

// ===== 主组件 =====

export function TablePropertyEditor() {
  const { elements, selectedElementIds, selectedGridCell, updateGridCell } = useDesignerStore()

  const el = selectedElementIds.length === 1
    ? elements.find((e) => e.id === selectedElementIds[0] && e.type === 'table')
    : null

  const grid = el?.tableGrid
  const sc = selectedGridCell && selectedGridCell.elementId === el?.id ? selectedGridCell : null

  if (!el || !grid || !sc) {
    return <div style={{ padding: 16, textAlign: 'center', color: '#999', fontSize: 12 }}>请在表格中点击一个单元格</div>
  }

  const row = sc.rowKey === 'headerRow' ? grid.headerRow : grid.dataRow
  const cell = row.cells[sc.columnId]
  if (!cell) return null

  const colId = sc.columnId
  const col = grid.columns.find((c) => c.id === colId)
  const colIndex = grid.columns.indexOf(col!) + 1

  const update = (p: Partial<GridCell>) => updateGridCell(el.id, sc.rowKey, colId, p)
  const updateS = (p: Partial<CellStyle>) => update({ style: { ...cell.style, ...p } })
  const updateB = (edge: keyof CellStyle, b: CellBorder | undefined) => updateS({ [edge]: b })

  return (
    <div className="ted-wrapper">
      <div className="ted-header">
        <span>表格 · 第{colIndex}列</span>
        <span style={{ fontSize: 10, color: '#999' }}>{sc.rowKey === 'headerRow' ? '表头' : '数据'}·{col?.width}mm</span>
      </div>

      {/* 类型 */}
      <Group title="单元格类型">
        <Row label="类型">
          <Sel value={cell.type} onChange={(v) => update({ type: v as GridCellType })} options={[
            { value: 'text', label: '文本' }, { value: 'field', label: '字段' }, { value: 'statistic', label: '统计' }, { value: 'dynamic', label: '动态字段' }
          ]} />
        </Row>
        {cell.type === 'text' && <Row label="文本"><Inp value={cell.text || ''} onChange={(v) => update({ text: v })} /></Row>}
      </Group>

      {/* 字段绑定 */}
      {cell.type === 'field' && (
        <Group title="字段绑定">
          <FieldSelector currentBinding={cell.binding} onBind={(b) => update({ binding: b || undefined })} />
        </Group>
      )}

      {/* 格式 */}
      <Group title="格式设置">
        <Row label="分类">
          <Sel value={cell.formatCategory || 'general'} onChange={(v) => update({ formatCategory: v as FormatCategory })} options={[
            { value: 'general', label: '常规' }, { value: 'number', label: '数值' }, { value: 'currency', label: '货币' },
            { value: 'accounting', label: '会计专用' }, { value: 'date', label: '日期' }, { value: 'time', label: '时间' },
            { value: 'percentage', label: '百分比' }, { value: 'text', label: '文本格式' }, { value: 'custom', label: '自定义' },
          ]} />
        </Row>
        <Row label="格式"><Inp value={cell.format || ''} onChange={(v) => update({ format: v })} placeholder="#,##0.00" /></Row>
      </Group>

      {/* 样式 */}
      <Group title="样式">
        <Row label="字体">
          <Sel value={cell.style?.fontFamily || 'SimSun, serif'} onChange={(v) => updateS({ fontFamily: v })} options={[
            { value: 'SimSun, serif', label: '宋体' }, { value: 'SimHei, sans-serif', label: '黑体' }, { value: 'Arial, sans-serif', label: 'Arial' },
          ]} />
        </Row>
        <Row label="字号"><DelayedInp value={cell.style?.fontSize ?? 10} onChange={(v) => updateS({ fontSize: v })} fallback={10} min={6} max={72} /></Row>
        <Row label="加粗"><Chk checked={cell.style?.fontWeight === 'bold'} onChange={(v) => updateS({ fontWeight: v ? 'bold' : 'normal' })} /></Row>
        <Row label="前景色"><input className="ted-color" type="color" value={cell.style?.color || '#000'} onChange={(e) => updateS({ color: e.target.value })} /></Row>
        <Row label="背景色"><input className="ted-color" type="color" value={cell.style?.backgroundColor || 'transparent'} onChange={(e) => updateS({ backgroundColor: e.target.value })} /></Row>
        <Row label="水平对齐"><Sel value={cell.style?.horizontalAlign || 'center'} onChange={(v) => updateS({ horizontalAlign: v as any })} options={[
          { value: 'left', label: '左' }, { value: 'center', label: '中' }, { value: 'right', label: '右' }
        ]} /></Row>
        <Row label="自动换行"><Chk checked={cell.style?.wordWrap || false} onChange={(v) => updateS({ wordWrap: v })} /></Row>
        <Row label="填格"><Chk checked={cell.style?.fillGrid || false} onChange={(v) => updateS({ fillGrid: v })} /></Row>
      </Group>

      {/* 边框 */}
      <Group title="边框">
        <Bdr label="上" border={cell.style?.borderTop} onChange={(b) => updateB('borderTop', b)} />
        <Bdr label="下" border={cell.style?.borderBottom} onChange={(b) => updateB('borderBottom', b)} />
        <Bdr label="左" border={cell.style?.borderLeft} onChange={(b) => updateB('borderLeft', b)} />
        <Bdr label="右" border={cell.style?.borderRight} onChange={(b) => updateB('borderRight', b)} />
      </Group>

      {/* 通用 */}
      <Group title="通用">
        <Row label="前缀"><Inp value={cell.prefix || ''} onChange={(v) => update({ prefix: v })} /></Row>
        <Row label="后缀"><Inp value={cell.suffix || ''} onChange={(v) => update({ suffix: v })} /></Row>
        <Row label="空值不显示"><Chk checked={cell.hidePrefixSuffixWhenEmpty || false} onChange={(v) => update({ hidePrefixSuffixWhenEmpty: v })} /></Row>
      </Group>
    </div>
  )
}
