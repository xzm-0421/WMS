/**
 * 扫码入口：入库/出库统一跳转快速入库、扫码出库
 */

export function getInboundPage() {
  return '/pages/inbound/notice-hub'
}

export function getOutboundPage() {
  return '/pages/outbound/notice-hub'
}

/** @deprecated 兼容旧入口，跳转采购收料通知单列表 */
export function getReceiveNoticeListPage() {
  return '/pages/notice/list?billType=PURCHASE_RECEIVE&direction=INBOUND'
}

export function navigateToInbound() {
  uni.navigateTo({ url: getInboundPage() })
}

export function navigateToOutbound() {
  uni.navigateTo({ url: getOutboundPage() })
}

export default { getInboundPage, getOutboundPage, navigateToInbound, navigateToOutbound }
