/** 业务字典中文（非流程状态） */

export const DICT_LABELS = {
  inboundType: {
    PURCHASE: '采购入库',
    PRODUCTION: '生产入库',
    RETURN: '退货入库',
    GIFT: '赠品入库',
    BORROW: '借还入库',
  },
  outboundType: {
    SALES: '销售出库',
    PRODUCTION: '生产领料',
    SAMPLE: '样品出库',
    GIFT: '赠品出库',
    BORROW: '借还出库',
    TRANSFER: '调拨出库',
    SCRAP: '报废出库',
  },
  locationType: {
    STORAGE: '存储位',
    PICK: '拣货位',
    STAGE: '暂存位',
  },
} as const

export type DictCategory = keyof typeof DICT_LABELS

export function getDictLabel(category: DictCategory, value?: string | null): string {
  if (!value) return '-'
  const map = DICT_LABELS[category] as Record<string, string>
  return map[value] ?? value
}
