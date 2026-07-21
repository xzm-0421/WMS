import { useState } from 'react'
import { useDesignerStore } from '../../store/useDesignerStore'
import {
  preparePageForOutput,
  waitForPageRender,
  getPageElement,
  buildExportFilename,
  exportPageElementToPdf,
  printPageElement,
} from '../../utils/pagePdfExport'
import { isEmbedMode, getEmbedPrintContext, notifyLabelPrintResult } from '../../wmsEmbed'
import './Toolbar.css'

export function Toolbar() {
  const {
    zoom,
    setZoom,
    pageSettings,
    viewMode,
    documentData,
  } = useDesignerStore()

  const [exporting, setExporting] = useState(false)
  const [printing, setPrinting] = useState(false)
  const printOnly = isEmbedMode()
  const isPreview = viewMode === 'preview' || printOnly

  const handleZoomIn = () => setZoom(zoom + 0.1)
  const handleZoomOut = () => setZoom(zoom - 0.1)
  const handleZoomReset = () => setZoom(1)

  const docTitle = documentData
    ? String(
        documentData.header.FBillNo
        || documentData.header.FArchiveNo
        || documentData.header.FBarCode
        || documentData.docId
        || '',
      )
    : ''

  const handlePrint = async () => {
    if (printing) return
    setPrinting(true)
    const ctx = preparePageForOutput()
    try {
      await waitForPageRender()
      const pageEl = getPageElement()
      if (!pageEl) return

      const store = useDesignerStore.getState()
      if (!store.documentData) {
        alert('单据数据尚未加载完成，请稍后再试')
        return
      }

      const embedCtx = getEmbedPrintContext()
      const copies = Math.max(1, embedCtx.copies)

      const result = await printPageElement(pageEl, {
        copies,
        onPrinted: () => {
          if (embedCtx.biz === 'kingdee_label' && embedCtx.jobId) {
            notifyLabelPrintResult().catch(() => {})
          }
        },
      })

      if (!result.ok && result.message) {
        alert(result.message)
      }
    } catch (err) {
      console.error('[Print]', err)
      alert(err instanceof Error ? err.message : '打印失败，请重试')
    } finally {
      ctx.restore()
      setPrinting(false)
    }
  }

  const handleExport = async () => {
    if (exporting) return
    setExporting(true)
    const ctx = preparePageForOutput()
    try {
      await waitForPageRender()
      const pageEl = getPageElement()
      if (!pageEl) {
        alert('未找到画布页面')
        return
      }
      if (!useDesignerStore.getState().documentData) {
        alert('单据数据尚未加载完成，请稍后再试')
        return
      }
      await exportPageElementToPdf(pageEl, buildExportFilename())
    } catch (err) {
      console.error('[Export PDF]', err)
      alert(err instanceof Error ? err.message : '导出 PDF 失败')
    } finally {
      ctx.restore()
      setExporting(false)
    }
  }

  const isLabelPaper = pageSettings.width <= 120 && pageSettings.height <= 100

  return (
    <div className="toolbar">
      <div className="toolbar-left">
        <button
          className="tb-btn toolbar-btn-print"
          onClick={handlePrint}
          disabled={printing}
          title="打印"
        >
          <span>🖨</span> {printing ? '打印中…' : '打印'}
        </button>
        {!printOnly && (
          <button
            className="tb-btn"
            onClick={handleExport}
            disabled={exporting}
            title="导出 PDF 文件"
          >
            <span>📄</span> {exporting ? '导出中…' : '导出 PDF'}
          </button>
        )}
      </div>

      <div className="toolbar-center" style={{ gap: 8 }}>
        {documentData && isPreview && docTitle && (
          <span className="tb-info" style={{ fontSize: 11 }}>
            {docTitle}
          </span>
        )}
        {!printOnly && (
          <span className="tb-info">
            {isLabelPaper
              ? `标签 ${pageSettings.width}×${pageSettings.height}mm`
              : `A4 (${pageSettings.width}×${pageSettings.height}mm)`}
          </span>
        )}
      </div>

      <div className="toolbar-right">
        <button className="tb-btn tb-btn-icon" onClick={handleZoomOut} title="缩小">
          −
        </button>
        <span className="tb-zoom-label">{Math.round(zoom * 100)}%</span>
        <button className="tb-btn tb-btn-icon" onClick={handleZoomIn} title="放大">
          +
        </button>
        <button className="tb-btn" onClick={handleZoomReset} title="重置缩放">
          1:1
        </button>
      </div>
    </div>
  )
}
