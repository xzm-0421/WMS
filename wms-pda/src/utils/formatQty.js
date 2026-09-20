/**
 * 数量展示/输入：
 * - 只读展示：重量非整数 → 2 位；刚好整数 → 按计价/非重量规则（不补小数）
 * - 输入框回填：重量非整数 → 6 位；刚好整数 → 按计价/非重量规则
 * - 件数等保持简短
 */

export function isWeightUnit(unitCode) {
  if (unitCode == null || unitCode === '') return false
  const raw = String(unitCode).trim()
  const u = raw.toUpperCase()
  return u === 'KG'
    || u === 'KGS'
    || u === 'KILOGRAM'
    || raw === '千克'
    || raw === '公斤'
    || u === 'G'
    || raw === '克'
    || u === 'T'
    || raw === '吨'
    || raw === '斤'
}

/** 输入框允许的小数位数：重量 6，其它 4 */
export function qtyDecimalScale(unitCode) {
  return isWeightUnit(unitCode) ? 6 : 4
}

/** 是否“刚好整数”（按 6 位精度判定，避免浮点噪声） */
export function isWholeQty(n) {
  if (!Number.isFinite(n)) return false
  const rounded = Number(n.toFixed(6))
  return Number.isInteger(rounded)
}

/** 计价单位 / 件数侧展示：整数不补零，小数最多 4 位去尾零 */
function formatPriceUnitQty(n) {
  if (isWholeQty(n)) return String(Math.round(Number(n.toFixed(6))))
  return n.toFixed(4).replace(/\.?0+$/, '')
}

/** 列表/只读展示 */
export function formatQty(v, unitCode) {
  const n = Number(v)
  if (Number.isNaN(n)) return '0'
  if (isWeightUnit(unitCode)) {
    if (isWholeQty(n)) return formatPriceUnitQty(n)
    return n.toFixed(2)
  }
  return formatPriceUnitQty(n)
}

/** 输入框回填 */
export function formatQtyInput(v, unitCode) {
  const n = Number(v)
  if (Number.isNaN(n)) return '0'
  if (isWeightUnit(unitCode)) {
    if (isWholeQty(n)) return formatPriceUnitQty(n)
    return n.toFixed(6)
  }
  return formatPriceUnitQty(n)
}

export default formatQty
