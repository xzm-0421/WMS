/** 收料/通知单是否已全部提交完成 */
export function isNoticeBillCompleted(detail) {
  if (!detail) return false
  if (detail.scanStatus === 'COMPLETED') return true
  const rows = detail.lines || []
  if (!rows.length) return false
  return rows.every((line) => {
    const submitted = Number(line.submittedQty) || 0
    const plan = Number(line.planQty) || 0
    return plan > 0 && submitted >= plan
  })
}

export default isNoticeBillCompleted
