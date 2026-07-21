export const STOCK_STATUS_OPTIONS = [
  { value: 'AVAILABLE', label: '可用' },
  { value: 'FROZEN', label: '冻结' },
  { value: 'INSPECTION', label: '待检' },
  { value: 'UNQUALIFIED', label: '不合格' },
  { value: 'SCRAPPED', label: '报废' },
] as const

export const STOCK_STATUS_LABEL: Record<string, string> = Object.fromEntries(
  STOCK_STATUS_OPTIONS.map((item) => [item.value, item.label]),
)

export function stockStatusLabel(code?: string | null) {
  if (!code) return '-'
  return STOCK_STATUS_LABEL[code] || code
}
