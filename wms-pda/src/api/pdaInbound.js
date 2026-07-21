/**
 * PDA 无单入库 API
 */
import request, { withDevice } from '../utils/http.js'

export function recognizePdaInbound(barcodeContent, warehouseCode) {
  return request({
    url: '/mobile/pda-inbound/recognize',
    method: 'POST',
    data: { barcodeContent, warehouseCode },
  })
}

export function submitPdaInbound(data) {
  return request({
    url: '/mobile/pda-inbound/submit',
    method: 'POST',
    data: withDevice(data),
  })
}

export default { recognizePdaInbound, submitPdaInbound }
