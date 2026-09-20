import { createNoticeBillScan } from './createNoticeBillScan.js'



const BILL_TYPE = 'OUTSOURCE_FEED'



export const useOutsourceFeedScan = createNoticeBillScan(BILL_TYPE, {

  notOnBill: '该物料不在本委外补料单中',

  linesNotReady: '补料单明细未加载完成，请返回重新进入',

  backOnSubmitSuccess: true,

})



export default useOutsourceFeedScan


