/**
 * 收料/通知单是否已完结。
 * 规则：部分提交、全部提交 → 已完成；仅扫码/手改数量未提交 → 可继续编辑。
 */
export function isNoticeBillCompleted(detail) {
  if (!detail) return false
  const s = detail.scanStatus
  if (s === 'COMPLETED' || s === 'PARTIAL_SUBMITTED') return true
  const rows = detail.lines || []
  if (!rows.length) return false
  // 与后端一致：任意行已有提交量即完结
  const hasSubmitted = rows.some((line) => {
    return (Number(line.submittedQty) || 0) > 0 || (Number(line.submittedAuxQty) || 0) > 0
  })
  if (hasSubmitted) return true
  // 可处理余量全部为 0 也完结
  return rows.every((line) => {
    const remainQty = Number(line.remainQty)
    const qtyDone = Number.isFinite(remainQty)
      ? remainQty <= 0
      : (Number(line.planQty) || 0) - (Number(line.submittedQty) || 0) <= 0
    if (!line.multiUnit) return qtyDone
    const remainAux = Number(line.remainAuxQty)
    const auxDone = Number.isFinite(remainAux)
      ? remainAux <= 0
      : (Number(line.planAuxQty) || 0) - (Number(line.submittedAuxQty) || 0) <= 0
    return qtyDone && auxDone
  })
}

export default isNoticeBillCompleted
