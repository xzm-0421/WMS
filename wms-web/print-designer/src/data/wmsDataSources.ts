/**
 * WMS 套打数据源定义 — 与 wms-backend 实体字段对齐
 */
import type { DataSource, DataSourceRelation, FieldDefinition } from '../types/designer'

type F = FieldDefinition

const h = (
  fields: [string, string, 'string' | 'number' | 'date' | 'boolean'][],
): F[] => fields.map(([name, label, type]) => ({ name, label, type }))

const main = (
  id: string,
  name: string,
  fields: [string, string, 'string' | 'number' | 'date' | 'boolean'][],
): DataSource => ({
  id,
  name,
  category: 'document',
  type: 'main',
  description: `${name}主表`,
  fields: h(fields),
})

const entry = (
  id: string,
  name: string,
  fields: [string, string, 'string' | 'number' | 'date' | 'boolean'][],
): DataSource => ({
  id,
  name,
  category: 'document',
  type: 'entry',
  description: `${name}明细`,
  fields: h(fields),
})

// ----- 拣配 -----
export const DS_PREP_NOTICE = main('t_wms_prep_notice', '备料通知单', [
  ['FBillNo', '通知单号', 'string'],
  ['FDate', '创建日期', 'date'],
  ['FDocumentStatus', '状态', 'string'],
  ['FProductionPlanNo', '生产计划号', 'string'],
  ['FWarehouseCode', '仓库', 'string'],
  ['FDemandTime', '需求时间', 'date'],
  ['FCreatorName', '创建人', 'string'],
])

export const DS_PREP_NOTICE_ENTRY = entry('t_wms_prep_notice_entry', '备料通知明细', [
  ['FSeq', '行号', 'number'],
  ['FMaterialCode', '物料编码', 'string'],
  ['FMaterialName', '物料名称', 'string'],
  ['FDemandQty', '需求数量', 'number'],
  ['FPickedQty', '已拣数量', 'number'],
  ['FRecommendLoc', '推荐库位', 'string'],
])

export const DS_PICK_ISSUE = main('t_wms_pick_issue', '拣配发料单', [
  ['FBillNo', '发料单号', 'string'],
  ['FBarCode', '条码', 'string'],
  ['FDate', '创建日期', 'date'],
  ['FDocumentStatus', '状态', 'string'],
  ['FNoticeNo', '备料通知单', 'string'],
  ['FProductionPlanNo', '生产计划号', 'string'],
  ['FProductCode', '产品编码', 'string'],
  ['FProductName', '产品名称', 'string'],
  ['FPlanQty', '计划数量', 'number'],
  ['FWarehouseCode', '仓库', 'string'],
  ['FHandoverArea', '交接区', 'string'],
  ['FPickerName', '拣配员', 'string'],
])

export const DS_PICK_ISSUE_ENTRY = entry('t_wms_pick_issue_entry', '拣配发料明细', [
  ['FSeq', '行号', 'number'],
  ['FMaterialCode', '物料编码', 'string'],
  ['FMaterialName', '物料名称', 'string'],
  ['FMaterialModel', '规格', 'string'],
  ['FUnitID', '单位', 'string'],
  ['FPickQty', '应拣数量', 'number'],
  ['FPickedQty', '已拣数量', 'number'],
  ['FLocation', '库位', 'string'],
  ['FLot', '批次', 'string'],
])

export const DS_MATERIAL_PICKUP = main('t_wms_material_pickup', '领料执行单', [
  ['FBillNo', '领料单号', 'string'],
  ['FDate', '领料时间', 'date'],
  ['FIssueNo', '发料单号', 'string'],
  ['FReceiverName', '领料人', 'string'],
  ['FDocumentStatus', '状态', 'string'],
])

export const DS_WORKSHOP_RETURN = main('t_wms_workshop_return', '车间退库单', [
  ['FBillNo', '退库单号', 'string'],
  ['FDate', '创建日期', 'date'],
  ['FIssueNo', '发料单号', 'string'],
  ['FWarehouseCode', '仓库', 'string'],
  ['FReturnReason', '退库原因', 'string'],
  ['FOperatorName', '操作员', 'string'],
  ['FDocumentStatus', '状态', 'string'],
])

export const DS_WORKSHOP_RETURN_ENTRY = entry('t_wms_workshop_return_entry', '车间退库明细', [
  ['FSeq', '行号', 'number'],
  ['FMaterialCode', '物料编码', 'string'],
  ['FMaterialName', '物料名称', 'string'],
  ['FReturnQty', '退库数量', 'number'],
  ['FTargetLocation', '目标库位', 'string'],
  ['FLot', '批次', 'string'],
])

// ----- 来料 -----
export const DS_PURCHASE_ORDER = main('t_wms_purchase_order', '采购订单', [
  ['FBillNo', '订单号', 'string'],
  ['FDate', '创建日期', 'date'],
  ['FSupplierCode', '供应商', 'string'],
  ['FWarehouseCode', '仓库', 'string'],
  ['FPlanArriveDate', '计划到货', 'date'],
  ['FDocumentStatus', '状态', 'string'],
  ['FTotalQty', '总数量', 'number'],
  ['FReceivedQty', '已收数量', 'number'],
  ['FCreatorName', '创建人', 'string'],
  ['FRemark', '备注', 'string'],
])

export const DS_PURCHASE_ORDER_ENTRY = entry('t_wms_purchase_order_entry', '采购订单明细', [
  ['FSeq', '行号', 'number'],
  ['FMaterialCode', '物料编码', 'string'],
  ['FMaterialName', '物料名称', 'string'],
  ['FUnitID', '单位', 'string'],
  ['FOrderQty', '订单数量', 'number'],
  ['FReceivedQty', '已收数量', 'number'],
  ['FLot', '批次', 'string'],
])

export const DS_DELIVERY_NOTE = main('t_wms_delivery_note', '送货单', [
  ['FBillNo', '送货单号', 'string'],
  ['FDate', '送货日期', 'date'],
  ['FPurchaseOrderNo', '采购订单', 'string'],
  ['FSupplierCode', '供应商', 'string'],
  ['FDocumentStatus', '状态', 'string'],
  ['FDiffFlag', '差异标记', 'string'],
  ['FRemark', '备注', 'string'],
])

export const DS_DELIVERY_NOTE_ENTRY = entry('t_wms_delivery_note_entry', '送货单明细', [
  ['FSeq', '行号', 'number'],
  ['FMaterialCode', '物料编码', 'string'],
  ['FPlanQty', '计划数量', 'number'],
  ['FActualQty', '实收数量', 'number'],
  ['FDiffQty', '差异数量', 'number'],
])

export const DS_PURCHASE_RETURN = main('t_wms_purchase_return', '采购退货单', [
  ['FBillNo', '退货单号', 'string'],
  ['FDate', '创建日期', 'date'],
  ['FReceiptRefNo', '入库参考', 'string'],
  ['FSupplierCode', '供应商', 'string'],
  ['FReason', '退货原因', 'string'],
  ['FDocumentStatus', '状态', 'string'],
  ['FCreatorName', '创建人', 'string'],
])

export const DS_PURCHASE_RETURN_ENTRY = entry('t_wms_purchase_return_entry', '采购退货明细', [
  ['FSeq', '行号', 'number'],
  ['FMaterialCode', '物料编码', 'string'],
  ['FReturnQty', '退货数量', 'number'],
  ['FLot', '批次', 'string'],
])

// ----- 出入库 -----
export const DS_INBOUND_ORDER = main('t_wms_inbound_order', '入库单', [
  ['FBillNo', '入库单号', 'string'],
  ['FDate', '创建日期', 'date'],
  ['FOrderType', '入库类型', 'string'],
  ['FWarehouseCode', '仓库', 'string'],
  ['FSupplierCode', '供应商', 'string'],
  ['FSourceOrderNo', '来源单号', 'string'],
  ['FPlanDate', '计划日期', 'date'],
  ['FDocumentStatus', '状态', 'string'],
  ['FCreatorName', '创建人', 'string'],
  ['FAuditorName', '审核人', 'string'],
  ['FRemark', '备注', 'string'],
])

export const DS_INBOUND_ORDER_ENTRY = entry('t_wms_inbound_order_entry', '入库单明细', [
  ['FSeq', '行号', 'number'],
  ['FMaterialCode', '物料编码', 'string'],
  ['FMaterialName', '物料名称', 'string'],
  ['FUnitID', '单位', 'string'],
  ['FOrderQty', '计划数量', 'number'],
  ['FReceivedQty', '已收数量', 'number'],
  ['FLot', '批次', 'string'],
  ['FTargetLocation', '目标库位', 'string'],
])

export const DS_OUTBOUND_ORDER = main('t_wms_outbound_order', '出库单', [
  ['FBillNo', '出库单号', 'string'],
  ['FDate', '创建日期', 'date'],
  ['FOrderType', '出库类型', 'string'],
  ['FWarehouseCode', '仓库', 'string'],
  ['FCustomerCode', '客户', 'string'],
  ['FProductionOrderNo', '生产订单', 'string'],
  ['FPlanDate', '计划日期', 'date'],
  ['FDocumentStatus', '状态', 'string'],
  ['FCreatorName', '创建人', 'string'],
  ['FRemark', '备注', 'string'],
])

export const DS_OUTBOUND_ORDER_ENTRY = entry('t_wms_outbound_order_entry', '出库单明细', [
  ['FSeq', '行号', 'number'],
  ['FMaterialCode', '物料编码', 'string'],
  ['FMaterialName', '物料名称', 'string'],
  ['FMaterialModel', '规格', 'string'],
  ['FUnitID', '单位', 'string'],
  ['FDemandQty', '需求数量', 'number'],
  ['FIssuedQty', '已出数量', 'number'],
  ['FLot', '批次', 'string'],
  ['FSourceLocation', '出库库位', 'string'],
])

export const DS_PDA_INBOUND = main('t_wms_pda_inbound', 'PDA快速入库', [
  ['FBillNo', '记录号', 'string'],
  ['FBarCode', '条码', 'string'],
  ['FMaterialCode', '物料编码', 'string'],
  ['FMaterialName', '物料名称', 'string'],
  ['FMaterialModel', '规格', 'string'],
  ['FUnitID', '单位', 'string'],
  ['FWarehouseCode', '仓库', 'string'],
  ['FLocationCode', '库位', 'string'],
  ['FLot', '批次', 'string'],
  ['FQty', '数量', 'number'],
  ['FOperatorName', '操作员', 'string'],
])

// ----- 库存扩展 -----
export const DS_OTHER_INBOUND = main('t_wms_other_inbound', '其他入库单', [
  ['FBillNo', '单号', 'string'],
  ['FDate', '创建日期', 'date'],
  ['FInboundType', '入库类型', 'string'],
  ['FWarehouseCode', '仓库', 'string'],
  ['FSourceDesc', '来源说明', 'string'],
  ['FDocumentStatus', '状态', 'string'],
  ['FCreatorName', '创建人', 'string'],
])

export const DS_OTHER_INBOUND_ENTRY = entry('t_wms_other_inbound_entry', '其他入库明细', [
  ['FSeq', '行号', 'number'],
  ['FMaterialCode', '物料编码', 'string'],
  ['FMaterialName', '物料名称', 'string'],
  ['FQty', '数量', 'number'],
  ['FLocationCode', '库位', 'string'],
  ['FLot', '批次', 'string'],
])

export const DS_OTHER_OUTBOUND = main('t_wms_other_outbound', '其他出库单', [
  ['FBillNo', '单号', 'string'],
  ['FDate', '创建日期', 'date'],
  ['FOutboundType', '出库类型', 'string'],
  ['FWarehouseCode', '仓库', 'string'],
  ['FTargetDesc', '去向说明', 'string'],
  ['FDocumentStatus', '状态', 'string'],
  ['FCreatorName', '创建人', 'string'],
])

export const DS_OTHER_OUTBOUND_ENTRY = entry('t_wms_other_outbound_entry', '其他出库明细', [
  ['FSeq', '行号', 'number'],
  ['FMaterialCode', '物料编码', 'string'],
  ['FMaterialName', '物料名称', 'string'],
  ['FQty', '数量', 'number'],
  ['FLocationCode', '库位', 'string'],
  ['FLot', '批次', 'string'],
])

export const DS_TRANSFER_ORDER = main('t_wms_transfer_order', '库存调拨单', [
  ['FBillNo', '调拨单号', 'string'],
  ['FDate', '操作时间', 'date'],
  ['FTransferType', '调拨类型', 'string'],
  ['FSourceWarehouse', '源仓库', 'string'],
  ['FSourceLocation', '源库位', 'string'],
  ['FTargetWarehouse', '目标仓库', 'string'],
  ['FTargetLocation', '目标库位', 'string'],
  ['FMaterialCode', '物料编码', 'string'],
  ['FLot', '批次', 'string'],
  ['FTransferQty', '调拨数量', 'number'],
  ['FDocumentStatus', '状态', 'string'],
  ['FCreatorName', '操作员', 'string'],
])

// ----- 盘点 / 质检 / 生产 -----
export const DS_STOCKCHECK_TASK = main('t_wms_stockcheck_task', '盘点任务', [
  ['FBillNo', '任务号', 'string'],
  ['FDate', '计划日期', 'date'],
  ['FPlanNo', '盘点计划', 'string'],
  ['FWarehouseCode', '仓库', 'string'],
  ['FLocationCode', '库位', 'string'],
  ['FAssigneeName', '盘点员', 'string'],
  ['FDocumentStatus', '状态', 'string'],
])

export const DS_STOCKCHECK_TASK_ENTRY = entry('t_wms_stockcheck_task_entry', '盘点明细', [
  ['FSeq', '行号', 'number'],
  ['FMaterialCode', '物料编码', 'string'],
  ['FLocationCode', '库位', 'string'],
  ['FLot', '批次', 'string'],
  ['FBookQty', '账面数量', 'number'],
  ['FActualQty', '实盘数量', 'number'],
  ['FDiffQty', '差异数量', 'number'],
])

export const DS_QC_ORDER = main('t_wms_qc_order', '质检单', [
  ['FBillNo', '质检单号', 'string'],
  ['FDate', '检验时间', 'date'],
  ['FSourceType', '来源类型', 'string'],
  ['FSourceNo', '来源单号', 'string'],
  ['FMaterialCode', '物料编码', 'string'],
  ['FLot', '批次', 'string'],
  ['FSampleQty', '抽样数量', 'number'],
  ['FResult', '检验结果', 'string'],
  ['FInspectorName', '检验员', 'string'],
  ['FDocumentStatus', '状态', 'string'],
])

export const DS_PRODUCTION_ORDER = main('t_wms_production_order', '生产订单', [
  ['FBillNo', '生产订单号', 'string'],
  ['FDate', '计划开始', 'date'],
  ['FProductCode', '产品编码', 'string'],
  ['FProductName', '产品名称', 'string'],
  ['FPlanQty', '计划数量', 'number'],
  ['FCompletedQty', '完工数量', 'number'],
  ['FWarehouseCode', '仓库', 'string'],
  ['FDocumentStatus', '状态', 'string'],
])

export const DS_BOM = main('t_wms_bom', 'BOM物料清单', [
  ['FBillNo', 'BOM编码', 'string'],
  ['FProductCode', '产品编码', 'string'],
  ['FVersionNo', '版本号', 'string'],
  ['FDocumentStatus', '状态', 'string'],
])

export const DS_BOM_ENTRY = entry('t_wms_bom_entry', 'BOM明细', [
  ['FSeq', '行号', 'number'],
  ['FMaterialCode', '物料编码', 'string'],
  ['FMaterialName', '物料名称', 'string'],
  ['FUnitID', '单位', 'string'],
  ['FQtyPer', '单位用量', 'number'],
])

// ----- 基础资料 -----
export const DS_MATERIAL: DataSource = {
  id: 't_bd_material',
  name: '物料',
  category: 'baseData',
  type: 'system',
  description: '物料基础资料 base_material',
  fields: h([
    ['FNumber', '物料编码', 'string'],
    ['FName', '物料名称', 'string'],
    ['FModel', '规格型号', 'string'],
    ['FBaseUnit', '基本单位', 'string'],
    ['FBarCode', '条形码', 'string'],
    ['FMaterialGroup', '物料分类', 'string'],
    ['FLot', '批次号', 'string'],
    ['FProductionDate', '生产日期', 'string'],
    ['FQtyDisplay', '数量显示', 'string'],
    ['FCompanyName', '公司名称', 'string'],
    ['FPartnerName', '客户/供应商', 'string'],
    ['FBoardNo', '板号', 'string'],
    ['FPackageNo', '包装号', 'string'],
    ['FLabelFormat', '标签类型', 'string'],
  ]),
}

export const DS_SUPPLIER: DataSource = {
  id: 't_bd_supplier',
  name: '供应商',
  category: 'baseData',
  type: 'system',
  description: '供应商 base_supplier',
  fields: h([
    ['FNumber', '供应商编码', 'string'],
    ['FName', '供应商名称', 'string'],
    ['FContact', '联系人', 'string'],
    ['FPhone', '联系电话', 'string'],
    ['FAddress', '地址', 'string'],
  ]),
}

export const DS_WAREHOUSE: DataSource = {
  id: 't_bd_warehouse',
  name: '仓库',
  category: 'baseData',
  type: 'system',
  description: '仓库 base_warehouse',
  fields: h([
    ['FNumber', '仓库编码', 'string'],
    ['FName', '仓库名称', 'string'],
    ['FAddress', '地址', 'string'],
  ]),
}

export const DS_SYS_VARIABLES: DataSource = {
  id: 'sys_variables',
  name: '系统变量',
  category: 'systemVar',
  type: 'system',
  description: '打印时自动填充',
  fields: h([
    ['$PRINT_DATE', '打印日期', 'date'],
    ['$PRINT_TIME', '打印时间', 'string'],
    ['$PRINT_USER', '打印用户', 'string'],
    ['$CURRENT_PAGE', '当前页', 'number'],
    ['$TOTAL_PAGES', '总页数', 'number'],
    ['$ORGANIZATION', '组织', 'string'],
    ['$DOC_TYPE', '单据类型', 'string'],
  ]),
}

/** 全部 WMS 数据源 */
export const WMS_DATA_SOURCES: DataSource[] = [
  DS_PREP_NOTICE,
  DS_PREP_NOTICE_ENTRY,
  DS_PICK_ISSUE,
  DS_PICK_ISSUE_ENTRY,
  DS_MATERIAL_PICKUP,
  DS_WORKSHOP_RETURN,
  DS_WORKSHOP_RETURN_ENTRY,
  DS_PURCHASE_ORDER,
  DS_PURCHASE_ORDER_ENTRY,
  DS_DELIVERY_NOTE,
  DS_DELIVERY_NOTE_ENTRY,
  DS_PURCHASE_RETURN,
  DS_PURCHASE_RETURN_ENTRY,
  DS_INBOUND_ORDER,
  DS_INBOUND_ORDER_ENTRY,
  DS_OUTBOUND_ORDER,
  DS_OUTBOUND_ORDER_ENTRY,
  DS_PDA_INBOUND,
  DS_OTHER_INBOUND,
  DS_OTHER_INBOUND_ENTRY,
  DS_OTHER_OUTBOUND,
  DS_OTHER_OUTBOUND_ENTRY,
  DS_TRANSFER_ORDER,
  DS_STOCKCHECK_TASK,
  DS_STOCKCHECK_TASK_ENTRY,
  DS_QC_ORDER,
  DS_PRODUCTION_ORDER,
  DS_BOM,
  DS_BOM_ENTRY,
  DS_MATERIAL,
  DS_SUPPLIER,
  DS_WAREHOUSE,
  DS_SYS_VARIABLES,
]

export const WMS_DATA_SOURCE_RELATIONS: DataSourceRelation[] = [
  { parentSource: 't_wms_prep_notice', childSource: 't_wms_prep_notice_entry', parentKey: 'FBillNo', childKey: 'FBillNo', description: '备料通知 → 明细' },
  { parentSource: 't_wms_pick_issue', childSource: 't_wms_pick_issue_entry', parentKey: 'FBillNo', childKey: 'FBillNo', description: '拣配发料 → 明细' },
  { parentSource: 't_wms_workshop_return', childSource: 't_wms_workshop_return_entry', parentKey: 'FBillNo', childKey: 'FBillNo', description: '车间退库 → 明细' },
  { parentSource: 't_wms_purchase_order', childSource: 't_wms_purchase_order_entry', parentKey: 'FBillNo', childKey: 'FBillNo', description: '采购订单 → 明细' },
  { parentSource: 't_wms_delivery_note', childSource: 't_wms_delivery_note_entry', parentKey: 'FBillNo', childKey: 'FBillNo', description: '送货单 → 明细' },
  { parentSource: 't_wms_purchase_return', childSource: 't_wms_purchase_return_entry', parentKey: 'FBillNo', childKey: 'FBillNo', description: '采购退货 → 明细' },
  { parentSource: 't_wms_inbound_order', childSource: 't_wms_inbound_order_entry', parentKey: 'FBillNo', childKey: 'FBillNo', description: '入库单 → 明细' },
  { parentSource: 't_wms_outbound_order', childSource: 't_wms_outbound_order_entry', parentKey: 'FBillNo', childKey: 'FBillNo', description: '出库单 → 明细' },
  { parentSource: 't_wms_other_inbound', childSource: 't_wms_other_inbound_entry', parentKey: 'FBillNo', childKey: 'FBillNo', description: '其他入库 → 明细' },
  { parentSource: 't_wms_other_outbound', childSource: 't_wms_other_outbound_entry', parentKey: 'FBillNo', childKey: 'FBillNo', description: '其他出库 → 明细' },
  { parentSource: 't_wms_stockcheck_task', childSource: 't_wms_stockcheck_task_entry', parentKey: 'FBillNo', childKey: 'FBillNo', description: '盘点任务 → 明细' },
  { parentSource: 't_wms_bom', childSource: 't_wms_bom_entry', parentKey: 'FBillNo', childKey: 'FBillNo', description: 'BOM → 明细' },
  { parentSource: 't_wms_pick_issue_entry', childSource: 't_bd_material', parentKey: 'FMaterialCode', childKey: 'FNumber', description: '明细 → 物料' },
  { parentSource: 't_wms_purchase_order', childSource: 't_bd_supplier', parentKey: 'FSupplierCode', childKey: 'FNumber', description: '采购订单 → 供应商' },
]

export function getDataSourceById(id: string): DataSource | undefined {
  return WMS_DATA_SOURCES.find((ds) => ds.id === id)
}
