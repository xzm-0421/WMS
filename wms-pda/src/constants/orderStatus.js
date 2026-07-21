/** 单据状态文案与分组 */
export const INBOUND_STATUS = {
  DRAFT: { label: '草稿', color: '#94a3b8', group: 'draft' },
  PENDING: { label: '待审核', color: '#f59e0b', group: 'pending' },
  INBOUND: { label: '入库中', color: '#3b82f6', group: 'work' },
  COMPLETED: { label: '已完成', color: '#22c55e', group: 'done' },
  CLOSED: { label: '已关闭', color: '#64748b', group: 'history' },
  CANCELLED: { label: '已取消', color: '#ef4444', group: 'history' },
}

export const OUTBOUND_STATUS = {
  DRAFT: { label: '草稿', color: '#94a3b8', group: 'draft' },
  PENDING: { label: '待审核', color: '#f59e0b', group: 'pending' },
  PICKING: { label: '拣货中', color: '#8b5cf6', group: 'work' },
  OUTBOUND: { label: '出库中', color: '#3b82f6', group: 'work' },
  COMPLETED: { label: '已完成', color: '#22c55e', group: 'done' },
  CLOSED: { label: '已关闭', color: '#64748b', group: 'history' },
  CANCELLED: { label: '已取消', color: '#ef4444', group: 'history' },
}

export const STATUS_TABS = [
  { key: 'all', label: '全部' },
  { key: 'draft', label: '草稿' },
  { key: 'pending', label: '待审' },
  { key: 'work', label: '作业中' },
  { key: 'done', label: '已完成' },
  { key: 'history', label: '历史' },
]

export function getStatusMap(direction) {
  return direction === 'outbound' ? OUTBOUND_STATUS : INBOUND_STATUS
}

export function getStatusLabel(status, direction) {
  const map = getStatusMap(direction)
  return map[status]?.label || status
}

export function getStatusColor(status, direction) {
  const map = getStatusMap(direction)
  return map[status]?.color || '#64748b'
}

/** 按 Tab 过滤状态列表 */
export function statusesForTab(tabKey, direction) {
  if (tabKey === 'all') return null
  const map = getStatusMap(direction)
  return Object.entries(map)
    .filter(([, v]) => v.group === tabKey)
    .map(([k]) => k)
}

export function canSubmit(status) {
  return status === 'DRAFT'
}

export function canAudit(status) {
  return status === 'PENDING'
}

export function canScanInbound(status) {
  return ['PENDING', 'INBOUND'].includes(status)
}

export function canScanOutbound(status) {
  return ['PENDING', 'PICKING', 'OUTBOUND'].includes(status)
}

export default { INBOUND_STATUS, OUTBOUND_STATUS, STATUS_TABS }
