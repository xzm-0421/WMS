import { createNoticeBillScan } from './createNoticeBillScan.js'

const BILL_TYPE = 'OUTSOURCE_RETURN'

export const useOutsourceReturnScan = createNoticeBillScan(BILL_TYPE, {
  notOnBill: '该物料不在本委外退料单中',
  linesNotReady: '退料单明细未加载完成，请返回重新进入',
  alreadyFull: '该物料已退满',
  backOnSubmitSuccess: true,
})

export default useOutsourceReturnScan
