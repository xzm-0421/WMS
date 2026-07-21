import html2canvas from 'html2canvas'
import { jsPDF } from 'jspdf'
import { useDesignerStore } from '../store/useDesignerStore'
import { MOCK_DOCUMENT, getMockDocument } from '../data/mockDocument'

export interface PageOutputContext {
  restore: () => void
}

function isWmsEmbedWithDoc(): boolean {
  const params = new URLSearchParams(window.location.search)
  return Boolean(params.get('biz') && (params.get('docNo') || params.get('issueNo')))
}

/** 切换到预览 + 100% 缩放，便于打印/导出 */
export function preparePageForOutput(): PageOutputContext {
  const store = useDesignerStore.getState()
  const origZoom = store.zoom
  const origMode = store.viewMode
  const origDocData = store.documentData
  const embedDoc = isWmsEmbedWithDoc()

  if (!origMode || origMode === 'design') {
    if (!store.documentData && !embedDoc) {
      const tpl = store.templates.find((t) => t.id === store.currentTemplateId)
      store.setDocumentData(tpl ? getMockDocument(tpl.businessObjectId) : MOCK_DOCUMENT)
    }
    store.setViewMode('preview')
  }
  store.setZoom(1)

  return {
    restore: () => {
      store.setZoom(origZoom)
      if (origMode === 'design') {
        store.setViewMode('design')
        store.setDocumentData(origDocData)
      }
    },
  }
}

export function waitForPageRender(): Promise<void> {
  return new Promise((resolve) => {
    requestAnimationFrame(() => requestAnimationFrame(() => resolve()))
  })
}

export function getPageElement(): HTMLElement | null {
  return document.querySelector('.canvas-page')
}

export function getPageDimensionsMm(pageEl: HTMLElement): { widthMm: number; heightMm: number } {
  return {
    widthMm: parseFloat(pageEl.getAttribute('data-page-width-mm') || '210'),
    heightMm: parseFloat(pageEl.getAttribute('data-page-height-mm') || '297'),
  }
}

export function isLabelPaperSize(widthMm: number, heightMm: number): boolean {
  return widthMm <= 120 && heightMm <= 100
}

export function buildExportFilename(): string {
  const store = useDesignerStore.getState()
  const tpl = store.templates.find((t) => t.id === store.currentTemplateId)
  const docNo =
    store.documentData?.header?.FBillNo ??
    store.documentData?.docId ??
    'document'
  const base = tpl?.name ?? 'print'
  return `${base}_${String(docNo)}.pdf`.replace(/[\\/:*?"<>|]/g, '_')
}

/** 清理克隆页面上的设计辅助元素 */
export function cleanPrintClone(clone: HTMLElement): void {
  clone.querySelectorAll('.resize-handle, .page-margin-guide').forEach((el) => el.remove())
  clone.querySelectorAll('.canvas-element.selected').forEach((el) => {
    el.classList.remove('selected')
  })
  clone.style.backgroundImage = 'none'
  clone.style.boxShadow = 'none'
  clone.style.transform = 'none'
}

/** 等待条码/二维码 SVG 渲染完成 */
export function waitForBarcodesReady(pageEl: HTMLElement, timeoutMs = 3000): Promise<void> {
  return new Promise((resolve) => {
    const start = Date.now()
    const tick = () => {
      const svgs = pageEl.querySelectorAll('.barcode-renderer svg')
      const allReady = svgs.length === 0
        || Array.from(svgs).every((svg) => svg.querySelector('rect, path, line, g'))
      if (allReady || Date.now() - start > timeoutMs) {
        resolve()
        return
      }
      requestAnimationFrame(tick)
    }
    tick()
  })
}

export interface PrintPageResult {
  ok: boolean
  message?: string
}

function preparePrintClone(pageEl: HTMLElement): HTMLElement {
  const { widthMm, heightMm } = getPageDimensionsMm(pageEl)
  const clone = pageEl.cloneNode(true) as HTMLElement
  cleanPrintClone(clone)
  clone.classList.remove('label-oval')

  clone.querySelectorAll('.canvas-element.selected').forEach((el) => {
    el.classList.remove('selected')
    el.removeAttribute('style')
    const id = el.getAttribute('data-id')
    const origEl = pageEl.querySelector(`[data-id="${id}"]`) as HTMLElement
    if (origEl) {
      el.style.cssText = origEl.style.cssText
        .replace(/outline[^;]*;?/g, '')
        .replace(/box-shadow[^;]*;?/g, '')
    }
  })

  clone.style.width = `${widthMm}mm`
  clone.style.height = `${heightMm}mm`
  clone.style.position = 'relative'
  clone.style.margin = '0'
  clone.style.padding = '0'
  clone.style.border = 'none'
  clone.style.borderRadius = '0'
  clone.style.boxShadow = 'none'
  clone.style.transform = 'none'

  return clone
}

function buildHtmlPrintDocument(
  pagesHtml: string,
  widthMm: number,
  heightMm: number,
): string {
  const widthIn = (widthMm / 25.4).toFixed(4)
  const heightIn = (heightMm / 25.4).toFixed(4)
  const isLabel = isLabelPaperSize(widthMm, heightMm)

  const rootSizeCss = isLabel
    ? `html, body {
    width: ${widthMm}mm;
    height: ${heightMm}mm;
    margin: 0;
    padding: 0;
    overflow: hidden;
    background: #fff;
  }`
    : `html, body {
    margin: 0;
    padding: 0;
    background: #fff;
  }`

  const printRootCss = isLabel
    ? `html, body {
      width: ${widthMm}mm;
      height: ${heightMm}mm;
      margin: 0 !important;
      padding: 0 !important;
    }
    .print-page {
      width: ${widthMm}mm;
      height: ${heightMm}mm;
      margin: 0;
    }`
    : ''

  const screenPreviewCss = isLabel
    ? `@media screen {
    html { background: #e8e8e8; }
    body {
      width: 100%;
      height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      overflow: auto;
    }
    .print-page {
      box-shadow: 0 2px 12px rgba(0,0,0,.15);
    }
  }`
    : ''

  return `<!DOCTYPE html>
<html><head><meta charset="utf-8"><title>打印 ${widthMm}×${heightMm}mm</title>
<style>
  @page {
    size: ${widthMm}mm ${heightMm}mm;
    size: ${widthIn}in ${heightIn}in;
    margin: 0;
  }
  * { margin: 0; padding: 0; box-sizing: border-box; }
  ${rootSizeCss}
  .print-page {
    width: ${widthMm}mm;
    height: ${heightMm}mm;
    margin: 0 auto;
    padding: 0;
    overflow: hidden;
    page-break-after: always;
    display: flex;
    align-items: center;
    justify-content: center;
  }
  .print-page:last-child { page-break-after: auto; }
  .canvas-page {
    width: ${widthMm}mm !important;
    height: ${heightMm}mm !important;
    margin: 0 !important;
    padding: 0 !important;
    border: none !important;
    border-radius: 0 !important;
    box-shadow: none !important;
    transform: none !important;
    position: relative;
    background: #fff;
    overflow: hidden;
    flex-shrink: 0;
  }
  .canvas-element {
    position: absolute;
    box-sizing: border-box;
    display: flex;
    align-items: center;
  }
  .canvas-element[data-type='text'],
  .canvas-element[data-type='data-field'],
  .canvas-element[data-type='page-number'] {
    overflow: visible;
    white-space: nowrap;
  }
  .canvas-element[data-type='table'] { overflow: visible; }
  .canvas-element[data-type='line'] { overflow: visible; }
  .table-element {
    width: 100%; height: 100%;
    border-collapse: collapse;
    table-layout: fixed;
  }
  .table-element th, .table-element td {
    border: 1px solid #000;
    padding: 1px 2px;
    font-weight: normal;
    text-align: center;
    overflow: hidden;
    white-space: nowrap;
  }
  .barcode-renderer svg { max-width: 100%; max-height: 100%; }
  ${screenPreviewCss}
  @media print {
    ${printRootCss}
    .print-page { page-break-inside: avoid; }
    html, body { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
  }
</style></head><body>${pagesHtml}
<script>
  window.onload = function() {
    setTimeout(function() { window.focus(); window.print(); }, 500);
  };
  window.onafterprint = function() {
    setTimeout(function() { window.close(); }, 600);
  };
</script></body></html>`
}

function printHtmlDocument(html: string, onPrinted?: () => void): Promise<PrintPageResult> {
  return new Promise((resolve) => {
    const printWin = window.open('', '_blank', 'width=480,height=360')
    if (!printWin) {
      resolve({
        ok: false,
        message: '浏览器拦截了打印窗口，请允许弹窗后重试。',
      })
      return
    }

    let notified = false
    const notifyOnce = () => {
      if (notified) return
      notified = true
      onPrinted?.()
    }

    printWin.document.open()
    printWin.document.write(html)
    printWin.document.close()

    printWin.addEventListener('afterprint', notifyOnce)
    setTimeout(notifyOnce, 15000)

    resolve({ ok: true })
  })
}

/** 将画布页面打印输出（HTML 直打，标签纸 60×40mm 椭圆矩形） */
export async function printPageElement(
  pageEl: HTMLElement,
  options?: { copies?: number; onPrinted?: () => void },
): Promise<PrintPageResult> {
  const { widthMm, heightMm } = getPageDimensionsMm(pageEl)
  const copies = Math.max(1, options?.copies ?? 1)

  await waitForBarcodesReady(pageEl)
  await waitForPageRender()

  const clone = preparePrintClone(pageEl)
  const pagesHtml = Array.from({ length: copies }, (_, i) => {
    const pageClone = (i === 0 ? clone : clone.cloneNode(true)) as HTMLElement
    return `<div class="print-page">${pageClone.outerHTML}</div>`
  }).join('')

  const html = buildHtmlPrintDocument(pagesHtml, widthMm, heightMm)
  return printHtmlDocument(html, options?.onPrinted)
}

/** 将画布页面导出为 PDF 文件并下载 */
export async function exportPageElementToPdf(pageEl: HTMLElement, filename: string): Promise<void> {
  const { widthMm, heightMm } = getPageDimensionsMm(pageEl)

  const canvas = await html2canvas(pageEl, {
    scale: 2,
    backgroundColor: '#ffffff',
    useCORS: true,
    logging: false,
    onclone: (_doc, clonedEl) => {
      cleanPrintClone(clonedEl as HTMLElement)
    },
  })

  const pdf = new jsPDF({
    orientation: widthMm > heightMm ? 'landscape' : 'portrait',
    unit: 'mm',
    format: [widthMm, heightMm],
    compress: true,
  })

  const imgData = canvas.toDataURL('image/jpeg', 0.92)
  pdf.addImage(imgData, 'JPEG', 0, 0, widthMm, heightMm, undefined, 'FAST')
  pdf.save(filename.endsWith('.pdf') ? filename : `${filename}.pdf`)
}
