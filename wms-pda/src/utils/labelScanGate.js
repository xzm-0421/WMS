/**
 * 物料明细是否已扫过标签或已手动录入（用于展示状态，不再作为改数量硬门槛）。
 */
export function isLabelScanned(line) {
  if (!line) return false
  const barcode = line.scannedBarcode
  if (barcode != null && String(barcode).trim() !== '') return true
  // 兼容本地扫码瞬时态
  if (line.labelScanned === true) return true
  return false
}

export function requireLabelScanned(line, toastTitle = '请先扫码或手动填写数量') {
  if (isLabelScanned(line)) return true
  uni.showToast({ title: toastTitle, icon: 'none' })
  return false
}

export default { isLabelScanned, requireLabelScanned }
