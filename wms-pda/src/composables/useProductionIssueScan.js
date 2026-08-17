import { createNoticeBillScan } from './createNoticeBillScan.js'

const BILL_TYPE = 'PRODUCTION_ISSUE'

export const useProductionIssueScan = createNoticeBillScan(BILL_TYPE, {
  notOnBill: '该物料不在本生产领料单中',
  linesNotReady: '领料单明细未加载完成，请返回重新进入',
  backOnSubmitSuccess: true,
})

export default useProductionIssueScan
