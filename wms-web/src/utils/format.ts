/** 统一日期展示：yyyy-MM-dd */

const DATE_HEAD = /^(\d{4})-(\d{2})-(\d{2})/

function padDate(date: Date): string {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

/** 格式化为 yyyy-MM-dd，无效值返回 '-' */
export function formatDate(value?: string | number | Date | null): string {
  if (value === null || value === undefined || value === '') return '-'

  if (value instanceof Date) {
    return Number.isNaN(value.getTime()) ? '-' : padDate(value)
  }

  if (typeof value === 'number') {
    const date = new Date(value)
    return Number.isNaN(date.getTime()) ? '-' : padDate(date)
  }

  const text = String(value).trim()
  const matched = text.match(DATE_HEAD)
  if (matched) {
    return `${matched[1]}-${matched[2]}-${matched[3]}`
  }

  const parsed = new Date(text)
  if (!Number.isNaN(parsed.getTime())) {
    return padDate(parsed)
  }

  return text
}
