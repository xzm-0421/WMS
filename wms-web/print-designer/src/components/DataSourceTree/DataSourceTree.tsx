import { useState } from 'react'
import { useDesignerStore } from '../../store/useDesignerStore'
import { getCategories, BUSINESS_OBJECTS, type BusinessObject } from '../../data/businessObjects'
import './DataSourceTree.css'

export function DataSourceTree() {
  const { currentTemplateId, templates, loadTemplate } = useDesignerStore()
  const [expandedCats, setExpandedCats] = useState<Set<string>>(
    () => new Set(getCategories().map((c) => c.key))
  )
  const [searchTerm, setSearchTerm] = useState('')

  // 根据当前模板ID反查业务对象
  const currentBO = BUSINESS_OBJECTS.find(
    (bo) => bo.templateIds.includes(currentTemplateId || '')
  )

  const categories = getCategories()

  const toggleCat = (key: string) => {
    setExpandedCats((prev) => {
      const next = new Set(prev)
      if (next.has(key)) next.delete(key)
      else next.add(key)
      return next
    })
  }

  const handleBOClick = (bo: BusinessObject) => {
    // 加载该业务对象的模板（取第一个）
    if (bo.templateIds.length > 0) {
      loadTemplate(bo.templateIds[0])
    }
  }

  // 搜索过滤
  const kw = searchTerm.toLowerCase().trim()
  const filteredCats = kw
    ? categories.filter((cat) =>
        cat.objects.some((bo) => bo.name.toLowerCase().includes(kw))
      )
    : categories

  const filterObjects = (bos: BusinessObject[]) =>
    kw ? bos.filter((bo) => bo.name.toLowerCase().includes(kw)) : bos

  return (
    <div className="dst-wrapper">
      <div className="dst-search">
        <input
          className="dst-search-input"
          type="text"
          placeholder="🔍 搜索..."
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
        />
      </div>

      <div className="dst-body">
        {/* 根节点：套打 */}
        <div className="dst-category">
          <div className="dst-root-header">
            <span className="dst-cat-icon">📄</span>
            <span className="dst-cat-label" style={{ fontSize: 13, fontWeight: 700 }}>WMS 套打</span>
            {currentBO && (
              <span className="dst-current-obj" title="当前编辑的业务对象">
                → {currentBO.name}
              </span>
            )}
          </div>
        </div>

        {filteredCats.map((cat) => {
          const objects = filterObjects(cat.objects)
          if (objects.length === 0) return null
          const isExpanded = expandedCats.has(cat.key)

          return (
            <div key={cat.key} className="dst-category">
              {/* 分类标题 */}
              <div className="dst-cat-header" onClick={() => toggleCat(cat.key)}>
                <span className={`dst-cat-arrow ${isExpanded ? '' : 'collapsed'}`}>▼</span>
                <span className="dst-cat-icon">{cat.icon}</span>
                <span className="dst-cat-label">{cat.label}</span>
              </div>

              {/* 业务对象列表 */}
              {isExpanded &&
                objects.map((bo) => {
                  const isActive = bo.templateIds.includes(currentTemplateId || '')

                  return (
                    <div key={bo.id} className="dst-table-group" style={{ marginLeft: 8 }}>
                      <button
                        className={`dst-table-header ${isActive ? 'active' : ''}`}
                        onClick={() => handleBOClick(bo)}
                        style={
                          isActive
                            ? { background: 'var(--color-primary-light)', borderLeft: '3px solid var(--color-primary)' }
                            : { cursor: 'pointer' }
                        }
                      >
                        <span className="dst-table-icon">
                          {cat.key === 'document' ? '📋' : cat.key === 'baseData' ? '📚' : cat.key === 'report' ? '📊' : '🔧'}
                        </span>
                        <span className="dst-table-label" style={isActive ? { fontWeight: 700, color: 'var(--color-primary)' } : {}}>
                          {bo.name}
                        </span>
                      </button>
                    </div>
                  )
                })}
            </div>
          )
        })}
      </div>

      <div className="dst-hint">
        {currentBO ? `当前编辑: ${currentBO.name}` : '点击业务对象加载模板'}
      </div>
    </div>
  )
}
