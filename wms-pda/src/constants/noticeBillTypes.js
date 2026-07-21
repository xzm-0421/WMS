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
    label: '生产入库单',
    direction: 'INBOUND',
    icon: '📦',
    color: '#22c55e',
    searchPlaceholder: '扫码或搜索生产入库单号',
  },
  PRODUCTION_RETURN: {
    code: 'PRODUCTION_RETURN',
    label: '生产退料',
    direction: 'INBOUND',
    icon: '↩️',
    color: '#0ea5e9',
    searchPlaceholder: '扫码或搜索生产领料单号/车间',
  },
  OUTSOURCE_RETURN: {
    code: 'OUTSOURCE_RETURN',
    label: '委外退料',
    direction: 'INBOUND',
    icon: '🔁',
    color: '#7c3aed',
    searchPlaceholder: '扫码或搜索委外领料单号/供应商',
  },
  OTHER_IN: {
    code: 'OTHER_IN',
    label: '其他入库单',
    direction: 'INBOUND',
    icon: '📋',
    color: '#64748b',
    searchPlaceholder: '扫码或搜索其他入库单号',
  },
  SALES_DELIVERY: {
    code: 'SALES_DELIVERY',
    label: '销售发货通知单',
    direction: 'OUTBOUND',
    icon: '🚚',
    color: '#ef4444',
    searchPlaceholder: '扫码或搜索发货通知单号',
  },
  PRODUCTION_ISSUE: {
    code: 'PRODUCTION_ISSUE',
    label: '生产领料',
    direction: 'OUTBOUND',
    icon: '🔧',
    color: '#f97316',
    searchPlaceholder: '扫码或搜索生产用料清单号/车间',
  },
  OUTSOURCE_ISSUE: {
    code: 'OUTSOURCE_ISSUE',
    label: '委外领料',
    direction: 'OUTBOUND',
    icon: '🏗️',
    color: '#a855f7',
    searchPlaceholder: '扫码或搜索委外用料清单号/供应商',
  },
  OTHER_OUT: {
    code: 'OTHER_OUT',
    label: '其他出库单',
    direction: 'OUTBOUND',
    icon: '📤',
    color: '#64748b',
    searchPlaceholder: '扫码或搜索其他出库单号',
  },
}

export function getNoticeBillType(code) {
  return NOTICE_BILL_TYPES[code] || NOTICE_BILL_TYPES.PURCHASE_RECEIVE
}

export function listNoticeBillTypes(direction) {
  return Object.values(NOTICE_BILL_TYPES).filter((t) => t.direction === direction)
}

export default NOTICE_BILL_TYPES
