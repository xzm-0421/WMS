/**
 * PDA 提交后金蝶同步结果反馈：用 Modal 展示完整信息（Toast 会截断）。
 * 失败 / 未确认成功时不退出单据页。
 */

function truncate(text, max = 480) {
  const s = String(text || '').trim()
  if (!s) return ''
  return s.length > max ? `${s.slice(0, max)}…` : s
}

function showModalAsync({ title, content, confirmText = '知道了' }) {
  return new Promise((resolve) => {
    uni.showModal({
      title: title || '提示',
      content: truncate(content) || '无详细信息',
      showCancel: false,
      confirmText,
      success: () => resolve(true),
      fail: () => resolve(false),
    })
  })
}

/**
 * @returns {{ ok: boolean, pending: boolean, title: string, message: string }}
 */
export function resolveErpSyncOutcome(result) {
  const status = String(result?.erpSyncStatus || '').toUpperCase()
  const async = result?.erpSyncAsync === true || result?.erpSyncAsync === 'true'
  const qtyUnchanged = result?.qtyUnchanged === true || result?.qtyUnchanged === 'true'
  let erpMsg = result?.erpSyncMessage || result?.message || ''
  const billNo = result?.erpBillNo ? String(result.erpBillNo).trim() : ''

  if (status === 'FAILED' || status === 'ERROR' || status === 'PARTIAL') {
    if (qtyUnchanged && erpMsg && !erpMsg.includes('已处理')) {
      erpMsg = `${erpMsg}\n已处理/可处理数量未变更，可修改后重新提交。`
    }
    return {
      ok: false,
      pending: false,
      title: '金蝶同步失败',
      message: erpMsg || '金蝶同步失败，已处理/可处理数量未变更，请核对后重试',
    }
  }

  // 后台异步：尚未拿到金蝶结果，不能当成功退出
  if (async || (status === 'PENDING' && result?.batchNo && String(erpMsg).includes('后台同步'))) {
    return {
      ok: false,
      pending: true,
      title: '金蝶同步中',
      message: erpMsg
        || '已写入 WMS，金蝶仍在后台同步。请留在本页稍后重新进入明细查看，或到 Web 批次详情确认结果。',
    }
  }

  if (status === 'SUCCESS' || !status || status === 'PENDING') {
    let message = erpMsg
    if (billNo) {
      message = message && !message.includes(billNo)
        ? `${message}\n金蝶单号：${billNo}`
        : (message || `金蝶同步成功，单号 ${billNo}`)
    }
    if (!message) {
      message = result?.lineCount != null
        ? `提交成功，共 ${result.lineCount} 行`
        : '提交成功'
    }
    return {
      ok: true,
      pending: false,
      title: billNo ? '金蝶同步成功' : '提交成功',
      message,
    }
  }

  return {
    ok: false,
    pending: false,
    title: '金蝶同步失败',
    message: erpMsg || `金蝶状态异常：${status}`,
  }
}

export function formatErpSubmitError(e) {
  const type = e?.data?.errorType || e?.errorType
  const msg = e?.message || e?.data?.message
  if (type === 'ERP_SYNC_FAILED' || type === 'ERP_IN_STOCK_QTY_EXCEEDED') {
    return msg || '金蝶同步失败，数量未变更'
  }
  return msg || '提交失败'
}

/** 展示同步失败（含异常），停留当前页 */
export async function alertErpSubmitFailed(message, title = '金蝶同步失败') {
  await showModalAsync({
    title,
    content: message || '金蝶同步失败，请核对后重试（单据未退出）',
  })
}

/** 展示同步成功 */
export async function alertErpSubmitSuccess(result) {
  const outcome = resolveErpSyncOutcome(result)
  await showModalAsync({
    title: outcome.title,
    content: outcome.message,
  })
  return outcome
}

/**
 * 统一处理提交接口返回值。
 * @returns {Promise<{ ok: boolean, pending: boolean, result: any }>}
 */
export async function handleErpSubmitResult(result) {
  const outcome = resolveErpSyncOutcome(result)
  if (!outcome.ok) {
    await showModalAsync({
      title: outcome.title,
      content: outcome.message,
    })
    return { ok: false, pending: outcome.pending, result }
  }
  await showModalAsync({
    title: outcome.title,
    content: outcome.message,
  })
  return { ok: true, pending: false, result }
}

export default {
  resolveErpSyncOutcome,
  formatErpSubmitError,
  alertErpSubmitFailed,
  alertErpSubmitSuccess,
  handleErpSubmitResult,
}
