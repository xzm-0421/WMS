/**
 * 库位 API（自动分配 / 自选）
 */
import request from '../utils/http.js'

function qs(params) {
  return Object.entries(params)
    .filter(([, v]) => v != null && v !== '')
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
    .join('&')
}

/** GET 推荐库位 */
export function recommendLocation(warehouseCode, materialCode, batchNo) {
  const query = qs({ warehouseCode, materialCode, batchNo })
  return request({
    url: `/mobile/locations/recommend?${query}`,
    method: 'GET',
  })
}

/** POST 分配库位（自动/手动，入库扫码专用） */
export function allocateLocation(data) {
  return request({
    url: '/mobile/locations/allocate',
    method: 'POST',
    data: {
      warehouseCode: data.warehouseCode,
      materialCode: data.materialCode,
      batchNo: data.batchNo,
      autoAllocate: data.autoAllocate !== false,
      manualLocationCode: data.manualLocationCode || data.locationCode,
    },
  })
}

export function listLocations(warehouseCode) {
  return request({
    url: `/mobile/locations?${qs({ warehouseCode })}`,
    method: 'GET',
  })
}

export default { recommendLocation, allocateLocation, listLocations }
