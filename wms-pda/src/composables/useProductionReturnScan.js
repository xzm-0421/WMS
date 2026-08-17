import { createNoticeBillScan } from './createNoticeBillScan.js'

const BILL_TYPE = 'PRODUCTION_RETURN'

export const useProductionReturnScan = createNoticeBillScan(BILL_TYPE, {
  notOnBill: '该物料不在本生产退料单中',
  linesNotReady: '退料单明细未加载完成，请返回重新进入',
  alreadyFull: '该物料已退满',
  backOnSubmitSuccess: true,
})

export default useProductionReturnScan
