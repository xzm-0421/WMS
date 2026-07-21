import type { PrintBiz } from '@/config/printConfig'
import type { LabelPrintJob } from '@/api/labelPrint'

export interface OpenPrintOptions {
  /** 打开后自动触发打印 */
  autoPrint?: boolean
  /** 金蝶标签任务号，用于记录预览/打印日志 */
  jobId?: string
  width?: number
  height?: number
  copies?: number
}

/**
 * 打开套打预览/打印页：加载对应模板 + 后端真实单据数据
 */
export function openWmsPrint(biz: PrintBiz, docNo: string, options?: OpenPrintOptions) {
  if (!docNo) return
  const params = new URLSearchParams()
  params.set('biz', biz)
  params.set('docNo', docNo)
  params.set('embed', '1')
  if (options?.autoPrint) {
    params.set('autoPrint', '1')
  }
  if (options?.jobId) {
    params.set('jobId', options.jobId)
  }
  if (options?.width) {
    params.set('width', String(options.width))
  }
  if (options?.height) {
    params.set('height', String(options.height))
  }
  if (options?.copies && options.copies > 1) {
    params.set('copies', String(options.copies))
  }
  const url = `/print-designer/index.html?${params.toString()}`
  window.open(url, '_blank', 'width=1280,height=860')
}

/** @deprecated 物料标签请使用 printKingdeeLabelDirect */
export function openKingdeeLabelPrint(job: LabelPrintJob, options?: Pick<OpenPrintOptions, 'autoPrint'>) {
  openWmsPrint('kingdee_label', job.jobId, {
    autoPrint: options?.autoPrint,
    jobId: job.jobId,
    width: job.labelWidthMm ? Number(job.labelWidthMm) : undefined,
    height: job.labelHeightMm ? Number(job.labelHeightMm) : undefined,
    copies: job.copies,
  })
}
