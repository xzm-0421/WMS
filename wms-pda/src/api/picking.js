import request from '../utils/http.js'

/** 扫描拣配发料单二维码确认领料 */
export function confirmMaterialPickup(issueNo, receiverName) {
  return request({
    url: '/mobile/picking/pickup/confirm',
    method: 'POST',
    data: { issueNo, receiverName },
  })
}

/** 获取发料单详情 */
export function getPickIssueDetail(issueNo) {
  return request({ url: `/mobile/picking/issues/${issueNo}` })
}

/** 按库位扫码拣货 */
export function scanPickIssue(issueNo, data) {
  return request({
    url: `/mobile/picking/issues/${issueNo}/scan`,
    method: 'POST',
    data,
  })
}

/** 待拣发料单列表 */
export function listPickIssues(status = 'PICKING') {
  return request({ url: '/mobile/picking/issues', params: { status, limit: 30 } })
}

/** 车间退库（扫码提交） */
export function submitWorkshopReturn(data) {
  return request({
    url: '/mobile/picking/workshop-return',
    method: 'POST',
    data,
  })
}
