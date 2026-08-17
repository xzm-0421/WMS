/** WMS 套打业务类型与模板映射 */

export type PrintBiz =

  | 'inbound_order'

  | 'outbound_order'

  | 'pda_inbound'

  | 'other_inbound'

  | 'other_outbound'

  | 'transfer_order'

  | 'stockcheck_task'

  | 'material_label'

  | 'barcode_archive'

  | 'kingdee_label'



export interface PrintBizMeta {

  biz: PrintBiz

  templateId: string

  label: string

}



export const PRINT_BIZ_REGISTRY: Record<PrintBiz, PrintBizMeta> = {

  inbound_order: { biz: 'inbound_order', templateId: 'tpl-inbound-order', label: '入库单' },

  outbound_order: { biz: 'outbound_order', templateId: 'tpl-outbound-order', label: '出库单' },

  pda_inbound: { biz: 'pda_inbound', templateId: 'tpl-pda-inbound', label: 'PDA快速入库' },

  other_inbound: { biz: 'other_inbound', templateId: 'tpl-other-inbound', label: '其他入库单' },

  other_outbound: { biz: 'other_outbound', templateId: 'tpl-other-outbound', label: '其他出库单' },

  transfer_order: { biz: 'transfer_order', templateId: 'tpl-transfer-order', label: '库存调拨单' },

  stockcheck_task: { biz: 'stockcheck_task', templateId: 'tpl-stockcheck-task', label: '盘点任务' },

  material_label: { biz: 'material_label', templateId: 'tpl-material-label', label: '物料标签' },

  barcode_archive: { biz: 'barcode_archive', templateId: 'tpl-barcode-label', label: '条码标签' },

  kingdee_label: { biz: 'kingdee_label', templateId: 'tpl-kingdee-material-label', label: '金蝶物料标签' },

}

/** 常用标签纸尺寸（mm），11×8 指 110×80mm */
export const LABEL_PAPER_PRESETS = [
  { label: '11×8cm', width: 110, height: 80 },
  { label: '10×6cm', width: 100, height: 60 },
  { label: '6×4cm', width: 60, height: 40 },
] as const

export const DEFAULT_LABEL_PAPER = LABEL_PAPER_PRESETS[0]

