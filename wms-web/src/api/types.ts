export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
  pages: number
}

export interface PageQuery {
  current?: number
  size?: number
  [key: string]: unknown
}

/** 后端 OrderVo 为 { order, details }，前端统一展平为单对象 */
export function flattenOrderDetailVo<T extends { details?: D[] }, D = unknown>(
  data: T | { order: Omit<T, 'details'>; details?: D[] },
): T {
  if (data && typeof data === 'object' && 'order' in data && (data as { order?: unknown }).order) {
    const vo = data as { order: Omit<T, 'details'>; details?: D[] }
    return { ...vo.order, details: vo.details ?? [] } as T
  }
  return data as T
}
