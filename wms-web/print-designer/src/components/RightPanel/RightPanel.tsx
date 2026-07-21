import { useState } from 'react'
import { useDesignerStore } from '../../store/useDesignerStore'
import { DataSourceTree } from '../DataSourceTree/DataSourceTree'
import { PropertyPanel } from '../PropertyPanel/PropertyPanel'
import { DATA_SOURCE_REGISTRY } from '../../data/dataSourceRegistry'
import './RightPanel.css'

type RightTab = 'fields' | 'properties'

export function RightPanel() {
  const [activeTab, setActiveTab] = useState<RightTab>('fields')
  const viewMode = useDesignerStore((s) => s.viewMode)
  const isPreview = viewMode === 'preview'

  // 统计字段总数
  const totalFields = DATA_SOURCE_REGISTRY.reduce((a, ds) => a + ds.fields.length, 0)

  return (
    <div className="right-panel">
      {/* Tab 栏 */}
      <div className="rp-tabs">
        <button
          className={`rp-tab ${activeTab === 'fields' ? 'active' : ''} ${isPreview ? 'preview' : ''}`}
          onClick={() => !isPreview && setActiveTab('fields')}
        >
          <span className="rp-tab-icon">📋</span>
          选择项目
          <span className="rp-tab-badge">{totalFields}</span>
        </button>
        <button
          className={`rp-tab ${activeTab === 'properties' ? 'active' : ''} ${isPreview ? 'preview' : ''}`}
          onClick={() => !isPreview && setActiveTab('properties')}
        >
          <span className="rp-tab-icon">⚙️</span>
          属性
        </button>
      </div>

      {/* Tab 内容 */}
      <div className="rp-content">
        {activeTab === 'fields' && <DataSourceTree />}
        {activeTab === 'properties' && <PropertyPanel embedded />}
      </div>
    </div>
  )
}
