export type StatusTagType = '' | 'success' | 'warning' | 'info' | 'danger'

export interface StatusMeta {
  label: string
  type: StatusTagType
}

/** 业务单据 / 流程状态中文 */
export const STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  PENDING: '待审核',
  SUBMITTED: '已提交',
  AUDITED: '已审核',
  APPROVED: '已审批',
  OPEN: '待收货',
  PARTIAL: '部分完成',
  INBOUND: '入库中',
  INBOUND_CREATED: '已生成入库单',
  OUTBOUND: '出库中',
  RECEIVING: '收货中',
  PICKING: '拣货中',
  PICKED_UP: '已领料',
  IN_PROGRESS: '进行中',
  EXECUTING: '执行中',
  COUNTING: '盘点中',
  COMPLETED: '已完成',
  CLOSED: '已关闭',
  CANCELLED: '已取消',
  PUBLISHED: '已发布',
  PLANNED: '计划中',
  REVERSED: '已冲销',
  PASS: '合格',
  FAIL: '不合格',
  CONCESSION: '让步接收',
  QUALIFIED: '合格',
  UNQUALIFIED: '不合格',
  IDLE: '空闲',
  OCCUPIED: '占用',
  LOCKED: '锁定',
  SYNCING: '同步中',
  SUCCESS: '已同步',
  FAILED: '失败',
  OPENED: '已预览',
  PRINTED: '已打印',
  NEW: '新建',
  SCANNING: '扫码中',
  PARTIAL_SUBMITTED: '已完成',
  KINGDEE: '金蝶云星空',
  MANUAL: '手工创建',
  FREE: '空闲',
  NOT_SYNCED: '未同步',
  SYNCED: '已同步',
  MANUAL_REQUIRED: '待人工介入',
  STALE: '长期未同步',
  RELEASED: '已下达',
  PAUSED: '已暂停',
  RUNNING: '生产中',
  REWORKING: '返工中',
  SECONDARY: '二次返工',
  DONE: '已完成',
  NORMAL: '正常',
  REWORK: '返工',
  ONLINE: '在线',
  OFFLINE: '离线',
}

export const STATUS_TYPE: Record<string, StatusTagType> = {
  DRAFT: 'info',
  PENDING: 'warning',
  SUBMITTED: 'warning',
  AUDITED: '',
  APPROVED: 'success',
  OPEN: 'warning',
  PARTIAL: 'warning',
  INBOUND: '',
  INBOUND_CREATED: 'success',
  OUTBOUND: '',
  RECEIVING: '',
  PICKING: '',
  PICKED_UP: 'success',
  IN_PROGRESS: '',
  EXECUTING: '',
  COUNTING: '',
  COMPLETED: 'success',
  CLOSED: 'info',
  CANCELLED: 'danger',
  PUBLISHED: 'success',
  PLANNED: 'info',
  REVERSED: 'info',
  PASS: 'success',
  FAIL: 'danger',
  CONCESSION: 'warning',
  QUALIFIED: 'success',
  UNQUALIFIED: 'danger',
  IDLE: 'success',
  OCCUPIED: 'warning',
  LOCKED: 'danger',
  SYNCING: 'warning',
  SUCCESS: 'success',
  FAILED: 'danger',
  OPENED: 'info',
  PRINTED: 'success',
  NEW: 'info',
  SCANNING: '',
  PARTIAL_SUBMITTED: 'success',
  KINGDEE: 'info',
  MANUAL: 'info',
  FREE: 'success',
  NOT_SYNCED: 'info',
  SYNCED: 'success',
  MANUAL_REQUIRED: 'danger',
  STALE: 'warning',
  RELEASED: '',
  PAUSED: 'warning',
  RUNNING: '',
  REWORKING: 'warning',
  SECONDARY: 'danger',
  DONE: 'success',
  NORMAL: '',
  REWORK: 'warning',
  ONLINE: 'success',
  OFFLINE: 'danger',
}

export interface StatusLabelOptions {
  /** PENDING 的上下文文案，如待审核 / 待检 / 待同步 */
  pendingLabel?: string
  /** OPEN 的上下文文案，如待收货 / 待备料 */
  openLabel?: string
  /** FAILED 的上下文文案，如同步失败 */
  failedLabel?: string
  /** PARTIAL 的上下文文案，如部分成功 / 部分收货 */
  partialLabel?: string
}

export function getStatusLabel(
  status?: string | number | null,
  options?: StatusLabelOptions,
): string {
  if (status === null || status === undefined || status === '') return '-'
  if (typeof status === 'number') {
    return status === 1 ? '启用' : '禁用'
  }
  const key = String(status)
  if (key === 'PENDING' && options?.pendingLabel) return options.pendingLabel
  if (key === 'OPEN' && options?.openLabel) return options.openLabel
  if (key === 'FAILED' && options?.failedLabel) return options.failedLabel
  if (key === 'PARTIAL' && options?.partialLabel) return options.partialLabel
  return STATUS_LABEL[key] ?? key
}

export function getStatusMeta(
  status?: string | number | null,
  options?: StatusLabelOptions,
): StatusMeta {
  if (status === null || status === undefined || status === '') {
    return { label: '-', type: 'info' }
  }
  if (typeof status === 'number') {
    return status === 1
      ? { label: '启用', type: 'success' }
      : { label: '禁用', type: 'info' }
  }
  const key = String(status)
  return {
    label: getStatusLabel(key, options),
    type: STATUS_TYPE[key] ?? 'info',
  }
}

/** ERP 同步状态（PENDING 显示待同步） */
export function getErpSyncMeta(status?: string | null): StatusMeta {
  return getStatusMeta(status, {
    pendingLabel: '待同步',
    failedLabel: '同步失败',
    partialLabel: '部分成功',
  })
}

/** 操作结果（成功/失败） */
export function getResultMeta(status?: string | null): StatusMeta {
  if (status === 'SUCCESS') return { label: '成功', type: 'success' }
  if (status === 'FAILED') return { label: '失败', type: 'danger' }
  return getStatusMeta(status)
}
