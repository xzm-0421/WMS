/** 出入库业务模块配置（统一通知单扫码架构） */
export const ORDER_MODULES = [
  {
    id: 'purchase',
    name: '采购',
    icon: '🛒',
    color: '#3b82f6',
    inbound: {
      billType: 'PURCHASE_RECEIVE',
      orderType: 'PURCHASE',
      label: '收料通知单',
      listPage: '/pages/notice/list?billType=PURCHASE_RECEIVE&direction=INBOUND',
    },
    returnOut: {
      billType: 'PURCHASE_RETURN',
      orderType: 'PURCHASE',
      label: '采购退料单',
      listPage: '/pages/notice/list?billType=PURCHASE_RETURN&direction=OUTBOUND',
    },
    outbound: {
      billType: 'SALES_DELIVERY',
      orderType: 'SALES',
      label: '销售发货通知单',
      listPage: '/pages/notice/list?billType=SALES_DELIVERY&direction=OUTBOUND',
    },
    returnIn: {
      billType: 'SALES_RETURN',
      orderType: 'SALES',
      label: '销售退货通知单',
      listPage: '/pages/notice/list?billType=SALES_RETURN&direction=INBOUND',
    },
  },
  {
    id: 'outsource',
    name: '委外',
    icon: '🏭',
    color: '#8b5cf6',
    returnIn: {
      billType: 'OUTSOURCE_RETURN',
      orderType: 'OUTSOURCE',
      label: '委外退料',
      listPage: '/pages/picking/outsource-return',
    },
    outbound: {
      billType: 'OUTSOURCE_ISSUE',
      orderType: 'OUTSOURCE',
      label: '委外领料',
      listPage: '/pages/picking/outsource-issue',
    },
    feed: {
      billType: 'OUTSOURCE_FEED',
      orderType: 'OUTSOURCE',
      label: '委外补料',
      listPage: '/pages/picking/outsource-feed',
    },
  },
  {
    id: 'product',
    name: '产品',
    icon: '📦',
    color: '#22c55e',
    inbound: {
      billType: 'PRODUCTION_IN',
      orderType: 'PRODUCTION',
      label: '生产汇报入库',
      listPage: '/pages/notice/list?billType=PRODUCTION_IN&direction=INBOUND',
    },
    returnIn: {
      billType: 'PRODUCTION_RETURN',
      orderType: 'PRODUCTION',
      label: '生产退料',
      listPage: '/pages/picking/production-return',
    },
    outbound: {
      billType: 'PRODUCTION_ISSUE',
      orderType: 'PRODUCTION',
      label: '生产领料',
      listPage: '/pages/picking/production-issue',
    },
    feed: {
      billType: 'PRODUCTION_FEED',
      orderType: 'PRODUCTION',
      label: '生产补料',
      listPage: '/pages/picking/production-feed',
    },
    stockReturn: {
      billType: 'PRODUCTION_RET_STOCK',
      orderType: 'PRODUCTION',
      label: '生产退库',
      listPage: '/pages/notice/list?billType=PRODUCTION_RET_STOCK&direction=OUTBOUND',
    },
  },
  {
    id: 'other',
    name: '其他',
    icon: '📋',
    color: '#64748b',
    inbound: {
      billType: 'OTHER_IN',
      orderType: 'OTHER',
      label: '其他入库单',
      listPage: '/pages/notice/list?billType=OTHER_IN&direction=INBOUND',
    },
    outbound: {
      billType: 'OTHER_OUT',
      orderType: 'OTHER',
      label: '其他出库单',
      listPage: '/pages/notice/list?billType=OTHER_OUT&direction=OUTBOUND',
    },
  },
]

export function getModule(moduleId) {
  return ORDER_MODULES.find((m) => m.id === moduleId)
}

export function getModuleConfig(moduleId, direction) {
  const mod = getModule(moduleId)
  if (!mod) return null
  return direction === 'outbound' ? mod.outbound : mod.inbound
}

export function getModuleLabel(moduleId, direction) {
  return getModuleConfig(moduleId, direction)?.label || ''
}

export default ORDER_MODULES
