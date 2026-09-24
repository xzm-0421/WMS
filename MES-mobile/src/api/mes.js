import request from '../utils/http.js'

// 报工
export function getReportContext(moNo) {
  return request({ url: '/mobile/mes/reports/context', data: { moNo } })
}

export function submitReport(data) {
  return request({ url: '/mobile/mes/reports', method: 'POST', data })
}

export function getMyReports(params = {}) {
  return request({ url: '/mobile/mes/reports', data: params })
}

export function retryReport(reportNo) {
  return request({ url: `/mobile/mes/reports/${reportNo}/retry`, method: 'POST' })
}

export function cancelReport(reportNo, reason) {
  return request({ url: `/mobile/mes/reports/${reportNo}/cancel`, method: 'POST', data: { reason } })
}

// 离线批量补传
export function syncReports(items, deviceNo) {
  return request({ url: '/mobile/mes/reports/sync', method: 'POST', data: { deviceNo, items } })
}

export function getSyncPanel() {
  return request({ url: '/mobile/mes/sync/panel' })
}

// 工序转移
export function submitTransfer(data) {
  return request({ url: '/mobile/mes/transfers', method: 'POST', data })
}

export function getTransfers(params = {}) {
  return request({ url: '/mobile/mes/transfers', data: params })
}

export function retryTransfer(transferNo) {
  return request({ url: `/mobile/mes/transfers/${transferNo}/retry`, method: 'POST' })
}

// 返工
export function createDefect(data) {
  return request({ url: '/mobile/mes/defects', method: 'POST', data })
}

export function getDefects(params = {}) {
  return request({ url: '/mobile/mes/defects', data: params })
}

export function getDefectSequence(defectNo) {
  return request({ url: `/mobile/mes/defects/${defectNo}` })
}
