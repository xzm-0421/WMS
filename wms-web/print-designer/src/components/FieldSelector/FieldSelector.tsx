import { useState, useMemo } from 'react'
import { MOCK_DATA_SOURCES, DATA_SOURCE_RELATIONS } from '../../data/mockFields'
import type { CanvasElement, FieldBinding, DataSource, FieldDefinition } from '../../types/designer'
import './FieldSelector.css'

interface FieldSelectorProps {
  currentBinding?: FieldBinding
  onBind: (binding: FieldBinding | undefined) => void
}

export function FieldSelector({ currentBinding, onBind }: FieldSelectorProps) {
  const [selectedSource, setSelectedSource] = useState<DataSource>(
    () => MOCK_DATA_SOURCES.find((s) => s.id === currentBinding?.dataSource) || MOCK_DATA_SOURCES[0]
  )
  const [searchTerm, setSearchTerm] = useState('')
  const [showRelations, setShowRelations] = useState(false)

  // 当前数据源的关联
  const relations = useMemo(
    () =>
      DATA_SOURCE_RELATIONS.filter(
        (r) =>
          r.parentSource === selectedSource.id || r.childSource === selectedSource.id
      ),
    [selectedSource.id]
  )

  // 关联的数据源
  const relatedSources = useMemo(() => {
    const ids = new Set<string>()
    relations.forEach((r) => {
      if (r.parentSource === selectedSource.id) ids.add(r.childSource)
      if (r.childSource === selectedSource.id) ids.add(r.parentSource)
    })
    return MOCK_DATA_SOURCES.filter((s) => ids.has(s.id))
  }, [relations, selectedSource.id])

  // 过滤字段
  const filteredFields = useMemo(() => {
    if (!searchTerm.trim()) return selectedSource.fields
    const term = searchTerm.toLowerCase()
    return selectedSource.fields.filter(
      (f) =>
        f.name.toLowerCase().includes(term) || f.label.includes(term)
    )
  }, [selectedSource.fields, searchTerm])

  // 获取字段类型图标
  const getFieldIcon = (type: FieldDefinition['type']) => {
    switch (type) {
      case 'string': return 'Aa'
      case 'number': return '#'
      case 'date': return '📅'
      case 'boolean': return '✓'
      default: return '?'
    }
  }

  // 选择字段并绑定
  const handleSelectField = (field: FieldDefinition) => {
    const binding: FieldBinding = {
      dataSource: selectedSource.id,
      tableName: selectedSource.name,
      fieldName: field.name,
      fieldLabel: field.label,
      fieldType: field.type,
    }
    onBind(binding)
  }

  // 切换数据源
  const handleSourceChange = (sourceId: string) => {
    const source = MOCK_DATA_SOURCES.find((s) => s.id === sourceId)
    if (source) {
      setSelectedSource(source)
      setSearchTerm('')
    }
  }

  // 清除绑定
  const handleClear = () => {
    onBind(undefined)
  }

  return (
    <div className="field-selector">
      {/* 当前绑定信息 */}
      {currentBinding && (
        <div className="fs-current-binding">
          <div className="fs-bound-label">当前绑定</div>
          <div className="fs-bound-value">
            <span className="fs-bound-table">{currentBinding.tableName}</span>
            <span className="fs-bound-sep">.</span>
            <span className="fs-bound-field">{currentBinding.fieldLabel}</span>
          </div>
          <button className="fs-clear-btn" onClick={handleClear} title="清除绑定">
            ✕
          </button>
        </div>
      )}

      {/* 数据源选择 */}
      <div className="fs-section">
        <label className="fs-section-label">数据源</label>
        <select
          value={selectedSource.id}
          onChange={(e) => handleSourceChange(e.target.value)}
          className="prop-select"
        >
          {MOCK_DATA_SOURCES.map((ds) => (
            <option key={ds.id} value={ds.id}>
              {ds.name}
              {ds.type === 'main' ? ' (主)' : ds.type === 'entry' ? ' (明)' : ' (基)'}
            </option>
          ))}
        </select>
        <p className="fs-desc">{selectedSource.description}</p>
      </div>

      {/* 关联数据源 */}
      {relations.length > 0 && (
        <div className="fs-section">
          <button
            className="fs-relation-toggle"
            onClick={() => setShowRelations(!showRelations)}
          >
            <span className={`prop-arrow ${!showRelations ? 'collapsed' : ''}`}>▶</span>
            关联数据源 ({relations.length})
          </button>
          {showRelations && (
            <div className="fs-relation-list">
              {relatedSources.map((rs) => (
                <button
                  key={rs.id}
                  className={`fs-relation-item ${selectedSource.id === rs.id ? 'active' : ''}`}
                  onClick={() => handleSourceChange(rs.id)}
                >
                  <span className="fs-relation-type">
                    {rs.type === 'main' ? '主' : rs.type === 'entry' ? '明' : '基'}
                  </span>
                  {rs.name}
                </button>
              ))}
            </div>
          )}
        </div>
      )}

      {/* 字段搜索 */}
      <div className="fs-section">
        <input
          type="text"
          placeholder="搜索字段..."
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          className="prop-input"
        />
      </div>

      {/* 字段列表 */}
      <div className="fs-fields">
        {filteredFields.map((field) => {
          const isActive =
            currentBinding?.fieldName === field.name &&
            currentBinding?.dataSource === selectedSource.id

          return (
            <button
              key={field.name}
              className={`fs-field-item ${isActive ? 'active' : ''}`}
              onClick={() => handleSelectField(field)}
              title={`${field.name} (${field.type})`}
            >
              <span className={`fs-field-icon fs-icon-${field.type}`}>
                {getFieldIcon(field.type)}
              </span>
              <span className="fs-field-name">{field.label}</span>
              <span className="fs-field-code">{field.name}</span>
            </button>
          )
        })}
        {filteredFields.length === 0 && (
          <div className="fs-no-results">无匹配字段</div>
        )}
      </div>
    </div>
  )
}
