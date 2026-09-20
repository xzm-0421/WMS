import QRCode from 'qrcode'
import type { LabelPrintJob } from '@/api/labelPrint'
import { markLabelJobOpened, markLabelJobPrinted, syncLabelJobMaterial } from '@/api/labelPrint'
import {
  DEFAULT_LABEL_PAPER,
  FACTORY_LABEL_COMPANY_NAME,
  type MaterialLabelFormat,
} from '@/config/printConfig'
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

function displayText(value: unknown, fallback = '-'): string {
  const text = String(value ?? '').trim()
  return text || fallback
}

function buildQtyDisplay(job: LabelPrintJob): string {
  if (job.quantity == null) return job.unitCode || '-'
  const qty = Number(job.quantity)
  const text = Number.isFinite(qty) ? String(qty) : String(job.quantity)
  return job.unitCode ? `${text} ${job.unitCode}` : text
}

/** 厂内 FACTORY / 来料 INCOMING；未指定时金蝶推送来料，其余厂内 */
export function resolveMaterialLabelFormat(job: LabelPrintJob): MaterialLabelFormat {
  const raw = String(job.labelFormat || '').trim()
  if (raw === 'FACTORY' || raw === '厂内' || raw === '厂内标签') return 'FACTORY'
  if (raw === 'INCOMING' || raw === '来料' || raw === '来料标签') return 'INCOMING'
  if (String(job.sourceType || '').toUpperCase() === 'KINGDEE') return 'INCOMING'
  return 'FACTORY'
}

function fieldCell(label: string, value: string): string {
  return `<div class="cell"><span class="k">${escapeHtml(label)}</span><span class="v">${escapeHtml(value)}</span></div>`
}

function buildSingleLabelInnerHtml(job: LabelPrintJob, qrDataUrl: string): string {
  const format = resolveMaterialLabelFormat(job)
  const prodDate = job.productionDate ? formatDate(job.productionDate) : formatDate(new Date())
  const partnerLabel = format === 'FACTORY' ? '客户名称' : '供应商名称'
  const auxLabel = format === 'FACTORY' ? '板号' : '包装号'
  const auxValue = format === 'FACTORY'
    ? displayText(job.boardNo)
    : displayText(job.packageNo)

  const titleHtml = format === 'FACTORY'
    ? `<div class="label-title-block">
         <div class="company">${escapeHtml(FACTORY_LABEL_COMPANY_NAME)}</div>
         <div class="title">物料标签</div>
       </div>`
    : `<div class="label-title-block incoming">
         <div class="title">物料标签</div>
       </div>`

  return `
      ${titleHtml}
      <div class="label-main">
        <div class="field-grid">
          ${fieldCell(partnerLabel, displayText(job.partnerName))}
          ${fieldCell('生产日期', displayText(prodDate))}
          ${fieldCell('物料编码', displayText(job.materialCode))}
          ${fieldCell('数量', displayText(buildQtyDisplay(job)))}
          ${fieldCell('物料名称', displayText(job.materialName))}
          ${fieldCell('规格型号', displayText(job.specification))}
          ${fieldCell('批次号', displayText(job.batchNo))}
          ${fieldCell(auxLabel, auxValue)}
        </div>
        <div class="label-qr">
          <img src="${qrDataUrl}" alt="qr" />
        </div>
      </div>`
}

async function buildLabelItems(
  jobs: LabelPrintJob[],
  options?: DirectLabelPrintOptions,
  paperHeightMm?: number,
): Promise<LabelItem[]> {
  const qrPx = Math.min(512, Math.max(180, Math.round((paperHeightMm || 70) * 8)))
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
  const baseFontMm = Math.min(3.2, Math.max(1.8, contentHeightMm * 0.042))
  const padY = Math.max(1.2, contentHeightMm * 0.04)
  const padX = Math.max(1.4, contentWidthMm * 0.025)
  const qrBoxMm = Math.min(contentHeightMm * 0.48, contentWidthMm * 0.28, 30)
  const qrImgMm = qrBoxMm * 0.92
  const mainGapMm = Math.max(1.0, contentWidthMm * 0.02)

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
    color: #000;
  }
  .label-title-block {
    flex-shrink: 0;
    margin-bottom: 0.35em;
    line-height: 1.15;
  }
  .label-title-block .company {
    font-size: 0.95em;
    font-weight: bold;
    letter-spacing: 0.02em;
  }
  .label-title-block .title {
    font-size: 1.35em;
    font-weight: bold;
    margin-top: 0.1em;
  }
  .label-title-block.incoming .title {
    font-size: 1.55em;
    text-align: left;
  }
  .label-main {
    flex: 1;
    display: flex;
    gap: ${mainGapMm.toFixed(2)}mm;
    min-height: 0;
    align-items: stretch;
  }
  .field-grid {
    flex: 1;
    min-width: 0;
    display: grid;
    grid-template-columns: 1fr 1fr;
    grid-template-rows: repeat(4, minmax(0, 1fr));
    column-gap: 1.2mm;
    row-gap: 0.35mm;
    align-content: stretch;
  }
  .cell {
    min-width: 0;
    min-height: 0;
    display: flex;
    flex-direction: column;
    justify-content: center;
    border-bottom: 0.12mm solid #ddd;
    padding-bottom: 0.15mm;
  }
  .cell .k {
    color: #555;
    font-size: 0.62em;
    line-height: 1.1;
    flex-shrink: 0;
  }
  .cell .v {
    font-size: 0.95em;
    font-weight: bold;
    line-height: 1.15;
    word-break: break-all;
    overflow: hidden;
    display: -webkit-box;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
  }
  .label-qr {
    width: ${qrBoxMm.toFixed(2)}mm;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    align-self: center;
  }
  .label-qr img {
    width: ${qrImgMm.toFixed(2)}mm;
    height: ${qrImgMm.toFixed(2)}mm;
    display: block;
  }`
}

/** 按标签真实尺寸直打（默认宽100mm × 高70mm）。
 * 驱动纸张：宽100 × 高70；打印选「纵向」、缩放 100%、边距无。
 * @page 必须写死宽×高（勿 size:auto，否则会落到 A4，内容缩小居中并可能拆成多页）。
 * 勿加 portrait/landscape，方向交给打印对话框。 */
function buildSinglePrintHtml(
  items: LabelItem[],
  labelWidthMm: number,
  labelHeightMm: number,
  autoPrint: boolean,
): string {
  const pageWidthMm = labelWidthMm
  const pageHeightMm = labelHeightMm

  const pagesHtml = items
    .map((item, index) => {
      const breakClass = index < items.length - 1 ? ' has-break' : ''
      return `<div class="print-page${breakClass}"><div class="label-sheet">${item.html}</div></div>`
    })
    .join('')

  const toolbarHtml = `
  <div class="print-toolbar no-print">
    <div class="tip">请确认：纸张 <strong>USER ${pageWidthMm}×${pageHeightMm}mm</strong>、布局 <strong>纵向</strong>、缩放 <strong>100%</strong>、边距 <strong>无</strong>。<br/>
    预览应为横版（宽${pageWidthMm}×高${pageHeightMm}）；一页一张标签。</div>
    <div class="actions">
      <button type="button" class="primary" id="btn-print">打印</button>
    </div>
  </div>`

  const printScript = `
    (function(){
      try { localStorage.removeItem('wms_label_print_rot'); } catch(e) {}
      function doPrint(){ window.focus(); window.print(); }
      window.addEventListener('DOMContentLoaded', function(){
        var bp = document.getElementById('btn-print');
        if (bp) bp.addEventListener('click', doPrint);
        ${autoPrint ? 'setTimeout(doPrint, 400);' : ''}
      });
      window.onafterprint = function(){ setTimeout(function(){ window.close(); }, 500); };
    })();`

  return `<!DOCTYPE html>
<html><head><meta charset="utf-8">
<title>标签 ${labelWidthMm}×${labelHeightMm}mm</title>
<style>
  @page {
    size: ${pageWidthMm}mm ${pageHeightMm}mm;
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
    page-break-after: avoid;
    break-after: avoid;
  }
  .print-page.has-break {
    page-break-after: always;
    break-after: page;
  }
  ${labelStyles(pageWidthMm, pageHeightMm)}
  @media print {
    .no-print { display: none !important; }
    html, body {
      width: ${pageWidthMm}mm !important;
      height: ${pageHeightMm}mm !important;
      margin: 0 !important;
      padding: 0 !important;
      display: block !important;
      min-height: 0 !important;
      -webkit-print-color-adjust: exact;
      print-color-adjust: exact;
    }
    .print-page {
      width: ${pageWidthMm}mm !important;
      height: ${pageHeightMm}mm !important;
      max-width: ${pageWidthMm}mm !important;
      max-height: ${pageHeightMm}mm !important;
      overflow: hidden !important;
      position: relative !important;
      top: 0 !important;
      left: 0 !important;
    }
  }
  @media screen {
    html { background: #e8e8e8; width: auto; height: auto; }
    body {
      width: auto;
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: flex-start;
      gap: 12px;
      padding: 16px;
    }
    .print-toolbar {
      width: min(920px, 100%);
      background: #fff;
      border-radius: 8px;
      padding: 12px 14px;
      box-shadow: 0 2px 10px rgba(0,0,0,.08);
      font-size: 13px;
      color: #333;
      line-height: 1.5;
    }
    .print-toolbar .actions { margin-top: 10px; }
    .print-toolbar button.primary {
      border: none;
      background: #1d4ed8;
      color: #fff;
      border-radius: 6px;
      padding: 8px 16px;
      cursor: pointer;
    }
    .print-page {
      flex-shrink: 0;
      box-shadow: 0 2px 12px rgba(0,0,0,.12);
      background: #fff;
    }
  }
</style>
<script>${printScript}</script>
</head><body>${toolbarHtml}${pagesHtml}</body></html>`
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
