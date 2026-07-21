/**
 * 仓库基础资料 API
 */
import request from '../utils/http.js'

export function listWarehouses(params = {}) {
  return request({
    url: '/base/warehouses',
    data: {
      status: 1,
      current: 1,
      size: 200,
      ...params,
    },
  })
}

export default { listWarehouses }
