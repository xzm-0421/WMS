export function formatMaterialLineCount(item) {
  const n = Number(item?.totalLines)
  if (Number.isFinite(n) && n > 0) {
    return `${n} 项物料`
  }
  return ''
}

export default formatMaterialLineCount
