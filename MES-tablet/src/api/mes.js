import { request, withDevice } from '@/utils/http.js'

export function getMesPlanList(params) {
  return request({
    url: '/mes/plans',
    method: 'GET',
    data: params,
  })
}

export function getMesPlanDetail(id) {
  return request({
    url: `/mes/plans/${id}`,
    method: 'GET',
  })
}

export function getReportContext(moNo) {
  return request({
    url: '/mobile/mes/reports/context',
    method: 'GET',
    data: { moNo },
  })
}

export function submitReport(data) {
  return request({
    url: '/mobile/mes/reports',
    method: 'POST',
    data: withDevice(data),
  })
}

export function printLabel(data) {
  return request({
    url: '/mobile/print/label/resolve',
    method: 'POST',
    data: withDevice(data),
  })
}