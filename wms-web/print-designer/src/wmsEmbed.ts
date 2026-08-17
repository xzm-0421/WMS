import { useDesignerStore } from './store/useDesignerStore'
import { waitForPageRender } from './utils/pagePdfExport'

/** biz → 模板 ID（与 wms-web/src/config/printConfig.ts 保持一致） */
const BIZ_TEMPLATE_MAP: Record<string, string> = {
  prep_notice: 'tpl-prep-notice',
  pick_issue: 'tpl-pick-issue',
  material_pickup: 'tpl-material-pickup',
  workshop_return: 'tpl-workshop-return',
  purchase_order: 'tpl-purchase-order',
  delivery_note: 'tpl-delivery-note',
  purchase_return: 'tpl-purchase-return',
  inbound_order: 'tpl-inbound-order',
  outbound_order: 'tpl-outbound-order',
  pda_inbound: 'tpl-pda-inbound',
  other_inbound: 'tpl-other-inbound',
  other_outbound: 'tpl-other-outbound',
  transfer_order: 'tpl-transfer-order',
  stockcheck_task: 'tpl-stockcheck-task',
  qc_order: 'tpl-qc-order',
  production_order: 'tpl-production-order',
  bom: 'tpl-bom',
  material_label: 'tpl-material-label',
  barcode_archive: 'tpl-barcode-label',
  kingdee_label: 'tpl-kingdee-material-label',
}

const KINGDEE_PUBLIC_BASE = '/api/v1/integration/kingdee/label-print'

function getWmsToken(): string | null {
  try {
    if (window.parent && window.parent !== window) {
      return window.parent.localStorage.getItem('wms_token')
    }
  } catch {
    /* cross-origin */
  }
  return localStorage.getItem('wms_token')
}

async function fetchJson(url: string, init?: RequestInit) {
  const res = await fetch(url, init)
  if (!res.ok) {
    throw new Error(`请求失败 (${res.status})`)
  }
  const json = await res.json()
  if (json.code !== 200) {
    throw new Error(json.message || '请求失败')
  }
  return json.data
}

async function fetchPrintData(biz: string, docNo: string, jobId?: string | null) {
  const token = getWmsToken()
  // 金蝶浏览器打开时通常无 WMS token：走免登录公共接口
  if (biz === 'kingdee_label' && jobId && !token) {
    return fetchJson(`${KINGDEE_PUBLIC_BASE}/jobs/${encodeURIComponent(jobId)}/data`)
  }
  const url = `/api/v1/print/data?biz=${encodeURIComponent(biz)}&docNo=${encodeURIComponent(docNo)}`
  return fetchJson(url, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
}

async function postKingdeeJob(path: string, body?: unknown) {
  const token = getWmsToken()
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  // 有 token 走业务接口；无 token 走金蝶公共接口
  const url = token
    ? `/api/v1/print/label-jobs/${path}`
    : `${KINGDEE_PUBLIC_BASE}/jobs/${path}`
  await fetch(url, {
    method: 'POST',
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  }).catch(() => {})
}

function triggerAutoPrint() {
  requestAnimationFrame(() => {
    requestAnimationFrame(() => {
      document.querySelector<HTMLButtonElement>('.toolbar-btn-print')?.click()
    })
  })
}

function mapBarcodeType(raw?: string): 'code128' | 'code39' | 'qr' | 'ean13' | null {
  if (!raw) return null
  const v = raw.toUpperCase()
  if (v === 'QR' || v === 'QRCODE') return 'qr'
  if (v === 'CODE39') return 'code39'
  if (v === 'EAN13') return 'ean13'
  return 'code128'
}

function applyArchivePrintOverrides(doc: Record<string, unknown>) {
  const header = doc?.header as Record<string, unknown> | undefined
  if (!header) return
  const store = useDesignerStore.getState()
  if (header.FPageWidth && header.FPageHeight) {
    store.setPageSettings({
      width: Number(header.FPageWidth),
      height: Number(header.FPageHeight),
    })
  }
  const bt = mapBarcodeType(String(header.FBarcodeType || 'QR'))
  if (bt) {
    store.elements
      .filter((el) => el.type === 'barcode')
      .forEach((el) => store.updateElement(el.id, { barcodeType: bt }))
  }
}

function applyKingdeeLabelOverrides(doc: Record<string, unknown>) {
  applyArchivePrintOverrides(doc)
}

export function getEmbedPrintContext() {
  const params = new URLSearchParams(window.location.search)
  const headerCopies = useDesignerStore.getState().documentData?.header?.FCopies
  const urlCopies = params.get('copies')
  const copies = Math.max(
    1,
    Number(headerCopies) || Number(urlCopies) || 1,
  )
  return {
    biz: params.get('biz'),
    jobId: params.get('jobId'),
    copies,
  }
}

export async function notifyLabelPrintResult(errorMessage?: string) {
  const { biz, jobId } = getEmbedPrintContext()
  if (biz !== 'kingdee_label' || !jobId) return
  await postKingdeeJob(
    `${encodeURIComponent(jobId)}/printed`,
    errorMessage ? { errorMessage } : {},
  )
}

/** WMS 打印引导：按 biz 加载对应模板与真实单据数据 */
export async function bootstrapWmsEmbed() {
  const params = new URLSearchParams(window.location.search)
  const biz = params.get('biz')
  const docNo = params.get('docNo') || params.get('issueNo')
  const autoPrint = params.get('autoPrint') === '1'
  const jobId = params.get('jobId')

  if (!biz || !docNo) {
    return
  }

  const templateId = BIZ_TEMPLATE_MAP[biz]
  if (!templateId) {
    console.warn('[WMS Print] 未知 biz:', biz)
    return
  }

  const store = useDesignerStore.getState()
  store.setViewMode('preview')

  try {
    if (jobId && biz === 'kingdee_label') {
      await postKingdeeJob(`${encodeURIComponent(jobId)}/open`)
    }
    const doc = await fetchPrintData(biz, docNo, jobId)
    store.loadTemplate(templateId)
    store.setDocumentData(doc)
    if (biz === 'barcode_archive') {
      applyArchivePrintOverrides(doc as Record<string, unknown>)
    } else if (biz === 'kingdee_label') {
      applyKingdeeLabelOverrides(doc as Record<string, unknown>)
    }
    const width = params.get('width')
    const height = params.get('height')
    if (width && height) {
      store.setPageSettings({ width: Number(width), height: Number(height) })
    }
    await waitForPageRender()
    if (autoPrint) {
      triggerAutoPrint()
    }
  } catch (err) {
    console.error('[WMS Print]', err)
    alert(err instanceof Error ? err.message : '加载打印数据失败')
    if (jobId && biz === 'kingdee_label') {
      await postKingdeeJob(`${encodeURIComponent(jobId)}/printed`, {
        errorMessage: err instanceof Error ? err.message : '加载失败',
      })
    }
  }
}

export function isEmbedMode(): boolean {
  return new URLSearchParams(window.location.search).get('embed') === '1'
}

export function hasPrintParams(): boolean {
  const params = new URLSearchParams(window.location.search)
  const biz = params.get('biz')
  const docNo = params.get('docNo') || params.get('issueNo')
  return !!(biz && docNo)
}
