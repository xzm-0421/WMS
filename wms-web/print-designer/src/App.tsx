import { Canvas } from './components/Canvas/Canvas'
import { Toolbar } from './components/Toolbar/Toolbar'
import { isEmbedMode, hasPrintParams } from './wmsEmbed'
import { useDesignerStore } from './store/useDesignerStore'
import './App.css'

/** 套打预览/打印应用（仅渲染，不提供可视化设计） */
export default function App() {
  const documentData = useDesignerStore((s) => s.documentData)
  const embedMode = isEmbedMode()
  const ready = embedMode && hasPrintParams()

  return (
    <div className="app-shell app-shell--print-only">
      <Toolbar />
      <div className="app-content">
        {ready ? (
          <Canvas />
        ) : (
          <div className="print-empty-hint">
            <p>请从 WMS 业务页面点击「打印」打开本页面。</p>
            <p className="print-empty-hint-sub">模板数据已内置，无需使用套打设计器。</p>
          </div>
        )}
        {ready && !documentData && (
          <div className="print-loading-hint">正在加载打印数据…</div>
        )}
      </div>
    </div>
  )
}
