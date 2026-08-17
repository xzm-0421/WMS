import { createNoticeBillScan } from './createNoticeBillScan.js'

const BILL_TYPE = 'OUTSOURCE_ISSUE'

export const useOutsourceIssueScan = createNoticeBillScan(BILL_TYPE, {
  notOnBill: '该物料不在本委外领料单中',
  linesNotReady: '领料单明细未加载完成，请返回重新进入',
  backOnSubmitSuccess: true,
})

export default useOutsourceIssueScan
