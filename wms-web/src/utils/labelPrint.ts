import QRCode from 'qrcode'
import type { LabelPrintJob } from '@/api/labelPrint'
import { markLabelJobOpened, markLabelJobPrinted, syncLabelJobMaterial } from '@/api/labelPrint'
import { DEFAULT_LABEL_PAPER } from '@/config/printConfig'
import { formatDate } from '@/utils/format'

export interface DirectLabelPrintOptions {
  /** 仅预览，不自动弹出打印对话框 */
  preview?: boolean
  copies?: number
}

interface LabelItem {
  job: LabelPrintJob
  html: string
}

function paperKey(job: LabelPrintJob): string {
  const w = Number(job.labelWidthMm) || DEFAULT_LABEL_PAPER.width
  const h = Number(job.labelHeightMm) || DEFAULT_LABEL_PAPER.height
  return `${w}x${h}`
}

function resolveCopies(job: LabelPrintJob, options?: DirectLabelPrintOptions): number {
  return Math.max(1, options?.copies ?? job.copies ?? 1)
}

function parsePaperKey(key: string): { widthMm: number; heightMm: number } {
  const [w, h] = key.split('x').map(Number)
  return {
    widthMm: Number.isFinite(w) ? w : DEFAULT_LABEL_PAPER.width,
    heightMm: Number.isFinite(h) ? h : DEFAULT_LABEL_PAPER.height,
  }
}

function assertSamePaperSize(jobs: LabelPrintJob[]): { widthMm: number; heightMm: number } {
  const keys = [...new Set(jobs.map(paperKey))]
  if (keys.length > 1) {
    throw new Error('所选任务标签尺寸不一致，请仅选择相同尺寸的任务进行批量打印')
  }
  return parsePaperKey(keys[0]!)
}

function escapeHtml(value: unknown): string {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function buildQtyDisplay(job: LabelPrintJob): string {
  if (job.quantity == null) return job.unitCode || ''
  const qty = Number(job.quantity)
  const text = Number.isFinite(qty) ? String(qty) : String(job.quantity)
  return job.unitCode ? `${text} ${job.unitCode}` : text
}

function buildSingleLabelInnerHtml(job: LabelPrintJob, qrDataUrl: string): string {
  const prodDate = job.productionDate ? formatDate(job.productionDate) : formatDate(new Date())
  return `
      <div class="label-title">物料标签</div>
      <div class="label-main">
        <div class="label-fields">
          <div class="field-row"><span class="label">物料编码</span><span class="value">${escapeHtml(job.materialCode)}</span></div>
          <div class="field-row"><span class="label">物料名称</span><span class="value">${escapeHtml(job.materialName || '-')}</span></div>
          <div class="field-row"><span class="label">规格型号</span><span class="value">${escapeHtml(job.specification || '-')}</span></div>
          <div class="field-row split">
            <span><span class="label">批次号</span><span class="value">${escapeHtml(job.batchNo || '-')}</span></span>
            <span><span class="label">生产日期</span><span class="value">${escapeHtml(prodDate)}</span></span>
          </div>
          <div class="field-row"><span class="label">数量</span><span class="value qty">${escapeHtml(buildQtyDisplay(job))}</span></div>
        </div>
        <div class="label-qr">
          <img src="${qrDataUrl}" alt="qr" />
          <div class="qr-hint">扫码追溯</div>
        </div>
      </div>`
}

async function buildLabelItems(
  jobs: LabelPrintJob[],
  options?: DirectLabelPrintOptions,
  paperHeightMm?: number,
): Promise<LabelItem[]> {
  const qrPx = Math.min(512, Math.max(180, Math.round((paperHeightMm || 80) * 8)))
  const items: LabelItem[] = []
  for (const job of jobs) {
    const barcode = job.barcodeContent?.trim() || job.materialCode
    const copies = resolveCopies(job, options)
    const qrDataUrl = await QRCode.toDataURL(barcode, {
      width: qrPx,
      margin: 0,
      color: { dark: '#000000', light: '#ffffff' },
    })
    const inner = buildSingleLabelInnerHtml(job, qrDataUrl)
    for (let i = 0; i < copies; i += 1) {
      items.push({ job, html: inner })
    }
  }
  return items
}

function labelStyles(contentWidthMm: number, contentHeightMm: number): string {
  const baseFontMm = Math.min(3.8, Math.max(1.6, contentHeightMm * 0.048))
  const padY = Math.max(0.8, contentHeightMm * 0.035)
  const padX = Math.max(1.0, contentWidthMm * 0.022)
  const qrBoxMm = Math.min(contentHeightMm * 0.55, contentWidthMm * 0.3, contentHeightMm * 0.7)
  const qrImgMm = qrBoxMm * 0.9
  const mainGapMm = Math.max(0.8, contentWidthMm * 0.018)

  return `
  .label-sheet {
    width: ${contentWidthMm}mm;
    height: ${contentHeightMm}mm;
    padding: ${padY.toFixed(2)}mm ${padX.toFixed(2)}mm;
    display: flex;
    flex-direction: column;
    overflow: hidden;
    background: #fff;
    box-sizing: border-box;
    font-size: ${baseFontMm.toFixed(3)}mm;
  }
  .label-title {
    font-size: 1.3em;
    font-weight: bold;
    line-height: 1.1;
    margin-bottom: 0.25em;
    flex-shrink: 0;
  }
  .label-main {
    flex: 1;
    display: flex;
    gap: ${mainGapMm.toFixed(2)}mm;
    min-height: 0;
    align-items: stretch;
  }
  .label-fields {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 0.15em;
    justify-content: space-between;
  }
  .field-row {
    display: flex;
    flex-direction: column;
    gap: 0;
    line-height: 1.15;
    min-height: 0;
  }
  .field-row.split {
    flex-direction: row;
    gap: 0.6em;
  }
  .field-row.split > span {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 0;
  }
  .field-row .label {
    color: #666;
    font-size: 0.6em;
    line-height: 1.1;
  }
  .field-row .value {
    font-size: 0.92em;
    font-weight: bold;
    word-break: break-all;
    line-height: 1.15;
    overflow: hidden;
    display: -webkit-box;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
  }
  .field-row .value.qty {
    color: #1a8c44;
    font-size: 1.05em;
    -webkit-line-clamp: 1;
  }
  .label-qr {
    width: ${qrBoxMm.toFixed(2)}mm;
    flex-shrink: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
  }
  .label-qr img {
    width: ${qrImgMm.toFixed(2)}mm;
    height: ${qrImgMm.toFixed(2)}mm;
    display: block;
  }
  .qr-hint {
    font-size: 0.5em;
    color: #999;
    margin-top: 0.2em;
    text-align: center;
    line-height: 1.1;
  }`
}

/** 按标签真实尺寸直打（默认 110×80mm），打印对话框选「纵向」；驱动纸张宽110×高80。 */
function buildSinglePrintHtml(
  items: LabelItem[],
  labelWidthMm: number,
  labelHeightMm: number,
  autoPrint: boolean,
): string {
  const pageWidthMm = labelWidthMm
  const pageHeightMm = labelHeightMm

  const printScript = autoPrint
    ? `window.onload=function(){setTimeout(function(){window.focus();window.print();},350);};
       window.onafterprint=function(){setTimeout(function(){window.close();},500);};`
    : ''
  const pagesHtml = items
    .map((item, index) => {
      const breakClass = index < items.length - 1 ? ' has-break' : ''
      return `<div class="print-page${breakClass}"><div class="label-sheet">${item.html}</div></div>`
    })
    .join('')

  return `<!DOCTYPE html>
<html><head><meta charset="utf-8">
<title>标签 ${labelWidthMm}×${labelHeightMm}mm</title>
<style>
  @page {
    size: ${pageWidthMm}mm ${pageHeightMm}mm portrait;
    margin: 0;
  }
  * { margin: 0; padding: 0; box-sizing: border-box; }
  html, body {
    margin: 0;
    padding: 0;
    width: ${pageWidthMm}mm;
    background: #fff;
    font-family: "Microsoft YaHei", SimHei, sans-serif;
  }
  .print-page {
    width: ${pageWidthMm}mm;
    height: ${pageHeightMm}mm;
    max-width: ${pageWidthMm}mm;
    max-height: ${pageHeightMm}mm;
    overflow: hidden;
    page-break-inside: avoid;
    break-inside: avoid;
    page-break-after: auto;
    break-after: auto;
  }
  .print-page.has-break {
    page-break-after: always;
    break-after: page;
  }
  ${labelStyles(pageWidthMm, pageHeightMm)}
  @media print {
    html, body {
      width: ${pageWidthMm}mm !important;
      height: auto !important;
      margin: 0 !important;
      padding: 0 !important;
      -webkit-print-color-adjust: exact;
      print-color-adjust: exact;
    }
    .print-page {
      width: ${pageWidthMm}mm !important;
      height: ${pageHeightMm}mm !important;
      max-height: ${pageHeightMm}mm !important;
      overflow: hidden !important;
    }
  }
  @media screen {
    html { background: #e8e8e8; width: auto; }
    body {
      width: auto;
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 12px;
      padding: 16px;
    }
    .print-page {
      box-shadow: 0 2px 12px rgba(0,0,0,.12);
      background: #fff;
    }
  }
</style>
<script>${printScript}</script>
</head><body>${pagesHtml}</body></html>`
}

function openPrintWindow(html: string): Window | null {
  const win = window.open('', '_blank', 'width=900,height=640')
  if (!win) return null
  win.document.open()
  win.document.write(html)
  win.document.close()
  return win
}

function attachPrintedNotify(printWin: Window, jobs: LabelPrintJob[], preview?: boolean) {
  if (preview) return
  const uniqueJobs = [...new Map(jobs.map((job) => [job.jobId, job])).values()]
  let notified = false
  const notifyPrinted = () => {
    if (notified) return
    notified = true
    uniqueJobs.forEach((job) => markLabelJobPrinted(job.jobId).catch(() => {}))
  }
  printWin.addEventListener('afterprint', notifyPrinted)
  setTimeout(notifyPrinted, 15000)
}

function resolvePageSize(widthMm: number, heightMm: number): { pageWidthMm: number; pageHeightMm: number } {
  return {
    pageWidthMm: widthMm > 0 ? widthMm : DEFAULT_LABEL_PAPER.width,
    pageHeightMm: heightMm > 0 ? heightMm : DEFAULT_LABEL_PAPER.height,
  }
}

/** 直接打印/预览物料标签（HTML 直打，不用 Canvas） */
export async function printKingdeeLabelDirect(
  job: LabelPrintJob,
  options?: DirectLabelPrintOptions,
): Promise<void> {
  const fresh = await syncLabelJobMaterial(job.jobId).catch(() => job)
  const syncedJob = { ...job, ...fresh }
  await markLabelJobOpened(syncedJob.jobId).catch(() => {})

  const { widthMm, heightMm } = assertSamePaperSize([syncedJob])
  const { pageWidthMm, pageHeightMm } = resolvePageSize(widthMm, heightMm)
  const items = await buildLabelItems([syncedJob], options, pageHeightMm)
  const html = buildSinglePrintHtml(items, pageWidthMm, pageHeightMm, !options?.preview)
  const printWin = openPrintWindow(html)
  if (!printWin) {
    throw new Error('浏览器拦截了打印窗口，请允许弹窗后重试')
  }
  attachPrintedNotify(printWin, [syncedJob], options?.preview)
}

/** 批量打印/预览物料标签（要求标签尺寸一致） */
export async function printKingdeeLabelsBatch(
  jobs: LabelPrintJob[],
  options?: DirectLabelPrintOptions,
): Promise<void> {
  if (!jobs.length) {
    throw new Error('请至少选择一条打印任务')
  }

  const syncedJobs = await Promise.all(
    jobs.map(async (job) => {
      try {
        return await syncLabelJobMaterial(job.jobId)
      } catch {
        return job
      }
    }),
  )

  await Promise.all(syncedJobs.map((job) => markLabelJobOpened(job.jobId).catch(() => {})))

  const { widthMm, heightMm } = assertSamePaperSize(syncedJobs)
  const { pageWidthMm, pageHeightMm } = resolvePageSize(widthMm, heightMm)
  const items = await buildLabelItems(syncedJobs, options, pageHeightMm)
  const html = buildSinglePrintHtml(items, pageWidthMm, pageHeightMm, !options?.preview)
  const printWin = openPrintWindow(html)
  if (!printWin) {
    throw new Error('浏览器拦截了打印窗口，请允许弹窗后重试')
  }
  attachPrintedNotify(printWin, syncedJobs, options?.preview)
}
