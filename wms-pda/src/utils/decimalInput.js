/**
 * 过滤数量/重量输入：仅保留数字与一个小数点；maxScale 默认 4，重量单位传 6。
 */
export function sanitizeDecimalInput(raw, maxScale = 4) {
  if (raw == null) return ''
  let s = String(raw).replace(/[^\d.]/g, '')
  const firstDot = s.indexOf('.')
  if (firstDot >= 0) {
    s = s.slice(0, firstDot + 1) + s.slice(firstDot + 1).replace(/\./g, '')
    if (maxScale >= 0) {
      const parts = s.split('.')
      if (parts[1] && parts[1].length > maxScale) {
        parts[1] = parts[1].slice(0, maxScale)
        s = parts.join('.')
      }
    }
  }
  return s
}

export default sanitizeDecimalInput
