/**
 * WMS 业务对象注册表 — 左侧「选择项目」面板
 */
import type { DataSourceCategory } from '../types/designer'

export interface BusinessObject {
  id: string
  name: string
  category: DataSourceCategory
  dataSourceId: string
  templateIds: string[]
}

export interface CategoryNode {
  key: DataSourceCategory
  label: string
  icon: string
  objects: BusinessObject[]
}

export const BUSINESS_OBJECTS: BusinessObject[] = [
  // ----- 拣配 -----
  { id: 'bo-prep-notice', name: '备料通知单', category: 'document', dataSourceId: 't_wms_prep_notice', templateIds: ['tpl-prep-notice'] },
  { id: 'bo-pick-issue', name: '拣配发料单', category: 'document', dataSourceId: 't_wms_pick_issue', templateIds: ['tpl-pick-issue'] },
  { id: 'bo-material-pickup', name: '领料执行单', category: 'document', dataSourceId: 't_wms_material_pickup', templateIds: ['tpl-material-pickup'] },
  { id: 'bo-workshop-return', name: '车间退库单', category: 'document', dataSourceId: 't_wms_workshop_return', templateIds: ['tpl-workshop-return'] },
  // ----- 来料 -----
  { id: 'bo-purchase-order', name: '采购订单', category: 'document', dataSourceId: 't_wms_purchase_order', templateIds: ['tpl-purchase-order'] },
  { id: 'bo-delivery-note', name: '送货单', category: 'document', dataSourceId: 't_wms_delivery_note', templateIds: ['tpl-delivery-note'] },
  { id: 'bo-purchase-return', name: '采购退货单', category: 'document', dataSourceId: 't_wms_purchase_return', templateIds: ['tpl-purchase-return'] },
  // ----- 出入库 -----
  { id: 'bo-inbound-order', name: '入库单', category: 'document', dataSourceId: 't_wms_inbound_order', templateIds: ['tpl-inbound-order'] },
  { id: 'bo-outbound-order', name: '出库单', category: 'document', dataSourceId: 't_wms_outbound_order', templateIds: ['tpl-outbound-order'] },
  { id: 'bo-pda-inbound', name: 'PDA快速入库', category: 'document', dataSourceId: 't_wms_pda_inbound', templateIds: ['tpl-pda-inbound'] },
  // ----- 库存 -----
  { id: 'bo-other-inbound', name: '其他入库单', category: 'document', dataSourceId: 't_wms_other_inbound', templateIds: ['tpl-other-inbound'] },
  { id: 'bo-other-outbound', name: '其他出库单', category: 'document', dataSourceId: 't_wms_other_outbound', templateIds: ['tpl-other-outbound'] },
  { id: 'bo-transfer-order', name: '库存调拨单', category: 'document', dataSourceId: 't_wms_transfer_order', templateIds: ['tpl-transfer-order'] },
  // ----- 盘点 / 质检 / 生产 -----
  { id: 'bo-stockcheck-task', name: '盘点任务', category: 'document', dataSourceId: 't_wms_stockcheck_task', templateIds: ['tpl-stockcheck-task'] },
  { id: 'bo-qc-order', name: '质检单', category: 'document', dataSourceId: 't_wms_qc_order', templateIds: ['tpl-qc-order'] },
  { id: 'bo-production-order', name: '生产订单', category: 'document', dataSourceId: 't_wms_production_order', templateIds: ['tpl-production-order'] },
  { id: 'bo-bom', name: 'BOM物料清单', category: 'document', dataSourceId: 't_wms_bom', templateIds: ['tpl-bom'] },
  // ----- 基础资料 -----
  { id: 'bo-material-label', name: '物料标签', category: 'baseData', dataSourceId: 't_bd_material', templateIds: ['tpl-material-label'] },
  { id: 'bo-barcode-label', name: '条码标签', category: 'baseData', dataSourceId: 't_bd_material', templateIds: ['tpl-barcode-label'] },
]

export function getCategories(): CategoryNode[] {
  const categoryMeta: { key: DataSourceCategory; label: string; icon: string }[] = [
    { key: 'document', label: 'WMS 单据', icon: '📋' },
    { key: 'baseData', label: '基础资料', icon: '📚' },
    { key: 'systemVar', label: '系统变量', icon: '⚙️' },
  ]

  return categoryMeta.map((meta) => ({
    key: meta.key,
    label: meta.label,
    icon: meta.icon,
    objects: BUSINESS_OBJECTS.filter((bo) => bo.category === meta.key),
  })).filter((cat) => cat.objects.length > 0)
}
