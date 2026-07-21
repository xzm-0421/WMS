import { createNoticeBillScan } from './createNoticeBillScan.js'

const BILL_TYPE = 'PRODUCTION_ISSUE'

export const useProductionIssueScan = createNoticeBillScan(BILL_TYPE, {
  notOnBill: '该物料不在本生产用料清单中',
  linesNotReady: '用料清单明细未加载完成，请返回重新进入',
})

export default useProductionIssueScan
