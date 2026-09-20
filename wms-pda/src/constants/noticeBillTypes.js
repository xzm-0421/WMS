/**
 * 通知单类型配置（与后端 NoticeBillType 对齐）
 */
export const NOTICE_BILL_TYPES = {
  PURCHASE_RECEIVE: {
    code: 'PURCHASE_RECEIVE',
    label: '收料通知单',
    direction: 'INBOUND',
    icon: '🛒',
    color: '#3b82f6',
    searchPlaceholder: '扫码或搜索收料通知单号/供应商',
  },
  PRODUCTION_IN: {
    code: 'PRODUCTION_IN',
    label: '生产汇报入库',
    direction: 'INBOUND',
    icon: '📦',
    color: '#22c55e',
    searchPlaceholder: '扫码或搜索已审核生产汇报单号/车间/生产订单',
  },
  PRODUCTION_RETURN: {
    code: 'PRODUCTION_RETURN',
    label: '生产退料',
    direction: 'INBOUND',
    icon: '↩️',
    color: '#0ea5e9',
    searchPlaceholder: '扫码或搜索生产退料单号/车间',
  },
  OUTSOURCE_RETURN: {
    code: 'OUTSOURCE_RETURN',
    label: '委外退料',
    direction: 'INBOUND',
    icon: '🔁',
    color: '#7c3aed',
    searchPlaceholder: '扫码或搜索委外退料单号/供应商',
  },
  OTHER_IN: {
    code: 'OTHER_IN',
    label: '其他入库单',
    direction: 'INBOUND',
    icon: '📋',
    color: '#64748b',
    searchPlaceholder: '扫码或搜索未审核其他入库单号',
  },
  SALES_RETURN: {
    code: 'SALES_RETURN',
    label: '销售退货通知单',
    direction: 'INBOUND',
    icon: '🛍️',
    color: '#e11d48',
    searchPlaceholder: '扫码或搜索已审核退货通知单号/客户',
  },
  SALES_DELIVERY: {
    code: 'SALES_DELIVERY',
    label: '销售发货通知单',
    direction: 'OUTBOUND',
    icon: '🚚',
    color: '#ef4444',
    searchPlaceholder: '扫码或搜索未出库发货通知单号',
    emptyHint: '仅列出未出库数量不为 0 的已审核发货通知，也可扫码进入明细',
  },
  PRODUCTION_ISSUE: {
    code: 'PRODUCTION_ISSUE',
    label: '生产领料',
    direction: 'OUTBOUND',
    icon: '🔧',
    color: '#f97316',
    searchPlaceholder: '扫码或搜索审核中的领料单号/车间',
  },
  PRODUCTION_FEED: {
    code: 'PRODUCTION_FEED',
    label: '生产补料',
    direction: 'OUTBOUND',
    icon: '➕',
    color: '#fb923c',
    searchPlaceholder: '扫码或搜索生产补料单号/车间',
  },
  PRODUCTION_RET_STOCK: {
    code: 'PRODUCTION_RET_STOCK',
    label: '生产退库',
    direction: 'OUTBOUND',
    icon: '📤',
    color: '#ea580c',
    searchPlaceholder: '扫码或搜索未审核生产退库单号/车间',
  },
  OUTSOURCE_ISSUE: {
    code: 'OUTSOURCE_ISSUE',
    label: '委外领料',
    direction: 'OUTBOUND',
    icon: '🏗️',
    color: '#a855f7',
    searchPlaceholder: '扫码或搜索委外领料单号/供应商',
  },
  OUTSOURCE_FEED: {
    code: 'OUTSOURCE_FEED',
    label: '委外补料',
    direction: 'OUTBOUND',
    icon: '➕',
    color: '#c084fc',
    searchPlaceholder: '扫码或搜索委外补料单号/供应商',
  },
  OTHER_OUT: {
    code: 'OTHER_OUT',
    label: '其他出库单',
    direction: 'OUTBOUND',
    icon: '📤',
    color: '#64748b',
    searchPlaceholder: '扫码或搜索未审核其他出库单号',
  },
  PURCHASE_RETURN: {
    code: 'PURCHASE_RETURN',
    label: '采购退料单',
    direction: 'OUTBOUND',
    icon: '🔙',
    color: '#2563eb',
    searchPlaceholder: '扫码或搜索未审核采购退料单号/供应商',
  },
}

export function getNoticeBillType(code) {
  return NOTICE_BILL_TYPES[code] || NOTICE_BILL_TYPES.PURCHASE_RECEIVE
}

export function listNoticeBillTypes(direction) {
  return Object.values(NOTICE_BILL_TYPES).filter((t) => t.direction === direction)
}

export default NOTICE_BILL_TYPES
