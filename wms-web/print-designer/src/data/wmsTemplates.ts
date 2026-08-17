/**
 * WMS 预置模板生成器 — 统一 A4 单据布局
 */
import type { CanvasElement, FieldBinding, PrintTemplate } from '../types/designer'
import { v4 as uuidv4 } from 'uuid'
import { DEFAULT_PAGE_SETTINGS } from './mockFields'

const PAGE = { ...DEFAULT_PAGE_SETTINGS }
const now = new Date().toISOString()

export interface TableColDef {
  header: string
  field: string
  width: number
  align?: 'left' | 'center' | 'right'
}

export interface DocTemplateDef {
  id: string
  name: string
  title: string
  mainSourceId: string
  entrySourceId?: string
  /** 表头字段行：每行最多 3 组 label+field */
  headerFields: { label: string; field: string }[]
  tableColumns?: TableColDef[]
  withBarcode?: boolean
}

function bind(mainSourceId: string, tableName: string, fieldName: string, fieldLabel: string, fieldType: FieldBinding['fieldType'] = 'string'): FieldBinding {
  return { dataSource: mainSourceId, tableName, fieldName, fieldLabel, fieldType }
}

function entryBind(entrySourceId: string, tableName: string, fieldName: string, fieldLabel: string, fieldType: FieldBinding['fieldType'] = 'string'): FieldBinding {
  return { dataSource: entrySourceId, tableName, fieldName, fieldLabel, fieldType }
}

function label(text: string, x: number, y: number, w = 22): CanvasElement {
  return { id: uuidv4(), type: 'text', x, y, width: w, height: 7, zIndex: 1, locked: true, text, fontSize: 10, fontFamily: 'SimSun, serif', fontWeight: 'normal', textAlign: 'left', color: '#000', backgroundColor: 'transparent', borderWidth: 0, borderColor: 'transparent', borderStyle: 'none' }
}

function field(mainSourceId: string, tableName: string, fieldName: string, fieldLabel: string, x: number, y: number, w: number, fieldType: FieldBinding['fieldType'] = 'string'): CanvasElement {
  return {
    id: uuidv4(), type: 'data-field', x, y, width: w, height: 7, zIndex: 2, locked: false,
    fontSize: 10, fontFamily: 'SimSun, serif', fontWeight: 'bold', textAlign: 'left', color: '#000',
    backgroundColor: 'transparent', borderWidth: 1, borderColor: '#ccc', borderStyle: 'solid',
    fieldBinding: bind(mainSourceId, tableName, fieldName, fieldLabel, fieldType),
  }
}

export function createWmsDocTemplate(def: DocTemplateDef): PrintTemplate {
  const tableName = def.name.replace(/打印模板$/, '')
  const elements: CanvasElement[] = []
  let z = 1

  elements.push({
    id: uuidv4(), type: 'text', x: 55, y: 8, width: 100, height: 10, zIndex: z++,
    locked: false, text: def.title, fontSize: 20, fontFamily: 'SimHei, sans-serif',
    fontWeight: 'bold', textAlign: 'center', color: '#000',
  })

  if (def.withBarcode) {
    elements.push({
      id: uuidv4(), type: 'barcode', x: 150, y: 4, width: 52, height: 14, zIndex: z++,
      locked: false, barcodeType: 'code128', barcodeValue: 'WMS',
      fieldBinding: bind(def.mainSourceId, tableName, 'FBarCode', '条码', 'string'),
    })
  }

  elements.push({ id: uuidv4(), type: 'line', x: 8, y: 20, width: 194, height: 2, zIndex: z++, locked: false, lineWidth: 1, lineColor: '#000' })

  // 表头字段：每行 3 列
  const cols = [
    { labelX: 8, fieldX: 30, fieldW: 55 },
    { labelX: 88, fieldX: 108, fieldW: 40 },
    { labelX: 152, fieldX: 168, fieldW: 34 },
  ]
  def.headerFields.forEach((hf, idx) => {
    const row = Math.floor(idx / 3)
    const col = idx % 3
    const y = 23 + row * 9
    const c = cols[col]
    elements.push(label(`${hf.label}：`, c.labelX, y, col === 0 ? 22 : 18))
    elements.push(field(def.mainSourceId, tableName, hf.field, hf.label, c.fieldX, y, c.fieldW, hf.field === 'FDate' ? 'date' : hf.field.includes('Qty') ? 'number' : 'string'))
  })

  const headerRows = Math.ceil(def.headerFields.length / 3)
  const tableY = 23 + headerRows * 9 + 6

  if (def.tableColumns && def.entrySourceId) {
    elements.push(label('明细清单', 8, tableY - 5, 30))
    const totalW = def.tableColumns.reduce((s, c) => s + c.width, 0)
    elements.push({
      id: uuidv4(), type: 'table', x: 8, y: tableY, width: totalW, height: 45, zIndex: z++,
      locked: false, fontSize: 9, fontFamily: 'SimSun, serif', borderWidth: 1, borderColor: '#000', borderStyle: 'solid',
      tableColumns: def.tableColumns.map((col) => ({
        id: uuidv4(),
        header: col.header,
        width: col.width,
        align: col.align ?? (col.field.includes('Qty') ? 'right' : 'left'),
        binding: entryBind(def.entrySourceId!, `${tableName}明细`, col.field, col.header, col.field.includes('Qty') ? 'number' : 'string'),
      })),
      tableRows: 8,
    })
  }

  const footerY = tableY + (def.tableColumns ? 52 : 8)
  elements.push(label('制单人：', 8, footerY, 22))
  elements.push(label('审核人：', 80, footerY, 22))
  elements.push(label('打印日期：', 155, footerY, 28))

  return {
    id: def.id,
    name: def.name,
    category: 'document',
    businessObjectId: def.mainSourceId,
    pageSettings: PAGE,
    elements,
    createdAt: now,
    updatedAt: now,
  }
}

/** 物料标签（小票） */
export function createMaterialLabelTemplate(): PrintTemplate {
  return {
    id: 'tpl-material-label',
    name: '物料标签',
    category: 'baseData',
    businessObjectId: 't_bd_material',
    pageSettings: { width: 100, height: 60, marginTop: 3, marginRight: 3, marginBottom: 3, marginLeft: 3, showGrid: false, gridSize: 5, snapToGrid: false },
    elements: [
      { id: uuidv4(), type: 'text', x: 10, y: 4, width: 80, height: 6, zIndex: 1, locked: false, text: '物料标签', fontSize: 12, fontFamily: 'SimHei, sans-serif', fontWeight: 'bold', textAlign: 'center', color: '#000' },
      { id: uuidv4(), type: 'text', x: 3, y: 12, width: 15, height: 6, zIndex: 2, locked: true, text: '编码：', fontSize: 9, fontFamily: 'SimSun, serif' },
      { id: uuidv4(), type: 'data-field', x: 18, y: 12, width: 35, height: 6, zIndex: 3, locked: false, fontSize: 9, fontFamily: 'SimSun, serif', fontWeight: 'bold', borderWidth: 1, borderColor: '#000', borderStyle: 'solid', fieldBinding: bind('t_bd_material', '物料', 'FNumber', '物料编码') },
      { id: uuidv4(), type: 'data-field', x: 55, y: 12, width: 42, height: 6, zIndex: 4, locked: false, fontSize: 9, fontFamily: 'SimSun, serif', fontWeight: 'bold', borderWidth: 1, borderColor: '#000', borderStyle: 'solid', fieldBinding: bind('t_bd_material', '物料', 'FName', '物料名称') },
      { id: uuidv4(), type: 'text', x: 3, y: 19, width: 20, height: 6, zIndex: 5, locked: true, text: '规格：', fontSize: 9, fontFamily: 'SimSun, serif' },
      { id: uuidv4(), type: 'data-field', x: 22, y: 19, width: 75, height: 6, zIndex: 6, locked: false, fontSize: 9, fontFamily: 'SimSun, serif', borderWidth: 1, borderColor: '#000', borderStyle: 'solid', fieldBinding: bind('t_bd_material', '物料', 'FModel', '规格型号') },
      { id: uuidv4(), type: 'barcode', x: 3, y: 27, width: 94, height: 15, zIndex: 7, locked: false, barcodeType: 'code128', barcodeValue: 'MAT001', fieldBinding: bind('t_bd_material', '物料', 'FBarCode', '条形码') },
      { id: uuidv4(), type: 'text', x: 3, y: 44, width: 15, height: 6, zIndex: 8, locked: true, text: '单位：', fontSize: 9, fontFamily: 'SimSun, serif' },
      { id: uuidv4(), type: 'data-field', x: 18, y: 44, width: 25, height: 6, zIndex: 9, locked: false, fontSize: 9, fontFamily: 'SimSun, serif', borderWidth: 1, borderColor: '#000', borderStyle: 'solid', fieldBinding: bind('t_bd_material', '物料', 'FBaseUnit', '基本单位') },
    ],
    createdAt: now,
    updatedAt: now,
  }
}

/**
 * 条码标签（名称+型号 / 规格行 / 码明文 / 二维码）
 * 用于条码存档补打，仅输出标签本身，非整张单据。
 */
export function createBarcodeLabelTemplate(): PrintTemplate {
  const main = 't_bd_material'
  return {
    id: 'tpl-barcode-label',
    name: '条码标签',
    category: 'baseData',
    businessObjectId: main,
    pageSettings: { width: 100, height: 55, marginTop: 3, marginRight: 3, marginBottom: 3, marginLeft: 3, showGrid: false, gridSize: 5, snapToGrid: false },
    elements: [
      {
        id: uuidv4(), type: 'data-field', x: 3, y: 3, width: 94, height: 7, zIndex: 1, locked: true,
        fontSize: 11, fontFamily: 'SimHei, sans-serif', fontWeight: 'bold', textAlign: 'left', color: '#000',
        backgroundColor: 'transparent', borderWidth: 0, borderColor: 'transparent', borderStyle: 'none',
        fieldBinding: bind(main, '物料', 'FTitleLine', '物料名称型号'),
      },
      {
        id: uuidv4(), type: 'text', x: 3, y: 11, width: 18, height: 5, zIndex: 2, locked: true,
        text: '规格 Model', fontSize: 8, fontFamily: 'SimSun, serif', fontWeight: 'normal', textAlign: 'left', color: '#000',
      },
      {
        id: uuidv4(), type: 'data-field', x: 21, y: 11, width: 76, height: 5, zIndex: 3, locked: true,
        fontSize: 8, fontFamily: 'SimSun, serif', fontWeight: 'normal', textAlign: 'left', color: '#000',
        backgroundColor: 'transparent', borderWidth: 0, borderColor: 'transparent', borderStyle: 'none',
        fieldBinding: bind(main, '物料', 'FModel', '规格型号'),
      },
      {
        id: uuidv4(), type: 'data-field', x: 3, y: 17, width: 94, height: 5, zIndex: 4, locked: true,
        fontSize: 9, fontFamily: 'Consolas, monospace', fontWeight: 'bold', textAlign: 'left', color: '#000',
        backgroundColor: 'transparent', borderWidth: 0, borderColor: 'transparent', borderStyle: 'none',
        fieldBinding: bind(main, '物料', 'FBarCode', '码明文'),
      },
      {
        id: uuidv4(), type: 'barcode', x: 3, y: 23, width: 26, height: 26, zIndex: 5, locked: true,
        barcodeType: 'qr', barcodeValue: 'QR',
        fieldBinding: bind(main, '物料', 'FBarCode', '二维码'),
      },
    ],
    createdAt: now,
    updatedAt: now,
  }
}

const STD_ENTRY_COLS: TableColDef[] = [
  { header: '序号', field: 'FSeq', width: 10, align: 'center' },
  { header: '物料编码', field: 'FMaterialCode', width: 28 },
  { header: '物料名称', field: 'FMaterialName', width: 38 },
  { header: '数量', field: 'FQty', width: 18, align: 'right' },
  { header: '单位', field: 'FUnitID', width: 14, align: 'center' },
  { header: '库位', field: 'FLocationCode', width: 22 },
  { header: '批次', field: 'FLot', width: 22 },
]

export const WMS_DOC_TEMPLATE_DEFS: DocTemplateDef[] = [
  {
    id: 'tpl-prep-notice',
    name: '备料通知单',
    title: '备料通知单',
    mainSourceId: 't_wms_prep_notice',
    entrySourceId: 't_wms_prep_notice_entry',
    withBarcode: true,
    headerFields: [
      { label: '通知单号', field: 'FBillNo' },
      { label: '日期', field: 'FDate' },
      { label: '状态', field: 'FDocumentStatus' },
      { label: '生产计划', field: 'FProductionPlanNo' },
      { label: '仓库', field: 'FWarehouseCode' },
      { label: '创建人', field: 'FCreatorName' },
    ],
    tableColumns: [
      { header: '序号', field: 'FSeq', width: 10, align: 'center' },
      { header: '物料编码', field: 'FMaterialCode', width: 28 },
      { header: '物料名称', field: 'FMaterialName', width: 38 },
      { header: '需求数量', field: 'FDemandQty', width: 18, align: 'right' },
      { header: '已拣数量', field: 'FPickedQty', width: 18, align: 'right' },
      { header: '推荐库位', field: 'FRecommendLoc', width: 24 },
    ],
  },
  {
    id: 'tpl-pick-issue',
    name: '拣配发料单',
    title: '拣配发料单',
    mainSourceId: 't_wms_pick_issue',
    entrySourceId: 't_wms_pick_issue_entry',
    withBarcode: true,
    headerFields: [
      { label: '发料单号', field: 'FBillNo' },
      { label: '日期', field: 'FDate' },
      { label: '状态', field: 'FDocumentStatus' },
      { label: '备料单', field: 'FNoticeNo' },
      { label: '生产计划', field: 'FProductionPlanNo' },
      { label: '产品', field: 'FProductName' },
      { label: '仓库', field: 'FWarehouseCode' },
      { label: '拣配员', field: 'FPickerName' },
      { label: '交接区', field: 'FHandoverArea' },
    ],
    tableColumns: [
      { header: '序号', field: 'FSeq', width: 10, align: 'center' },
      { header: '物料编码', field: 'FMaterialCode', width: 26 },
      { header: '物料名称', field: 'FMaterialName', width: 36 },
      { header: '应拣', field: 'FPickQty', width: 16, align: 'right' },
      { header: '已拣', field: 'FPickedQty', width: 16, align: 'right' },
      { header: '库位', field: 'FLocation', width: 22 },
      { header: '批次', field: 'FLot', width: 20 },
      { header: '单位', field: 'FUnitID', width: 12, align: 'center' },
    ],
  },
  {
    id: 'tpl-material-pickup',
    name: '领料执行单',
    title: '领料执行单',
    mainSourceId: 't_wms_material_pickup',
    withBarcode: true,
    headerFields: [
      { label: '领料单号', field: 'FBillNo' },
      { label: '领料时间', field: 'FDate' },
      { label: '状态', field: 'FDocumentStatus' },
      { label: '发料单号', field: 'FIssueNo' },
      { label: '领料人', field: 'FReceiverName' },
    ],
  },
  {
    id: 'tpl-workshop-return',
    name: '车间退库单',
    title: '车间退库单',
    mainSourceId: 't_wms_workshop_return',
    entrySourceId: 't_wms_workshop_return_entry',
    headerFields: [
      { label: '退库单号', field: 'FBillNo' },
      { label: '日期', field: 'FDate' },
      { label: '状态', field: 'FDocumentStatus' },
      { label: '发料单', field: 'FIssueNo' },
      { label: '仓库', field: 'FWarehouseCode' },
      { label: '操作员', field: 'FOperatorName' },
      { label: '退库原因', field: 'FReturnReason' },
    ],
    tableColumns: [
      { header: '序号', field: 'FSeq', width: 10, align: 'center' },
      { header: '物料编码', field: 'FMaterialCode', width: 28 },
      { header: '物料名称', field: 'FMaterialName', width: 38 },
      { header: '退库数量', field: 'FReturnQty', width: 20, align: 'right' },
      { header: '目标库位', field: 'FTargetLocation', width: 24 },
      { header: '批次', field: 'FLot', width: 22 },
    ],
  },
  {
    id: 'tpl-purchase-order',
    name: '采购订单',
    title: '采购订单',
    mainSourceId: 't_wms_purchase_order',
    entrySourceId: 't_wms_purchase_order_entry',
    headerFields: [
      { label: '订单号', field: 'FBillNo' },
      { label: '日期', field: 'FDate' },
      { label: '状态', field: 'FDocumentStatus' },
      { label: '供应商', field: 'FSupplierCode' },
      { label: '仓库', field: 'FWarehouseCode' },
      { label: '计划到货', field: 'FPlanArriveDate' },
    ],
    tableColumns: [
      { header: '序号', field: 'FSeq', width: 10, align: 'center' },
      { header: '物料编码', field: 'FMaterialCode', width: 28 },
      { header: '物料名称', field: 'FMaterialName', width: 38 },
      { header: '订单数量', field: 'FOrderQty', width: 18, align: 'right' },
      { header: '已收数量', field: 'FReceivedQty', width: 18, align: 'right' },
      { header: '单位', field: 'FUnitID', width: 14, align: 'center' },
    ],
  },
  {
    id: 'tpl-delivery-note',
    name: '送货单',
    title: '供应商送货单',
    mainSourceId: 't_wms_delivery_note',
    entrySourceId: 't_wms_delivery_note_entry',
    headerFields: [
      { label: '送货单号', field: 'FBillNo' },
      { label: '送货日期', field: 'FDate' },
      { label: '状态', field: 'FDocumentStatus' },
      { label: '采购订单', field: 'FPurchaseOrderNo' },
      { label: '供应商', field: 'FSupplierCode' },
    ],
    tableColumns: [
      { header: '序号', field: 'FSeq', width: 10, align: 'center' },
      { header: '物料编码', field: 'FMaterialCode', width: 30 },
      { header: '计划数量', field: 'FPlanQty', width: 22, align: 'right' },
      { header: '实收数量', field: 'FActualQty', width: 22, align: 'right' },
      { header: '差异', field: 'FDiffQty', width: 18, align: 'right' },
    ],
  },
  {
    id: 'tpl-purchase-return',
    name: '采购退货单',
    title: '采购退货单',
    mainSourceId: 't_wms_purchase_return',
    entrySourceId: 't_wms_purchase_return_entry',
    headerFields: [
      { label: '退货单号', field: 'FBillNo' },
      { label: '日期', field: 'FDate' },
      { label: '状态', field: 'FDocumentStatus' },
      { label: '供应商', field: 'FSupplierCode' },
      { label: '退货原因', field: 'FReason' },
    ],
    tableColumns: [
      { header: '序号', field: 'FSeq', width: 10, align: 'center' },
      { header: '物料编码', field: 'FMaterialCode', width: 30 },
      { header: '退货数量', field: 'FReturnQty', width: 22, align: 'right' },
      { header: '批次', field: 'FLot', width: 24 },
    ],
  },
  {
    id: 'tpl-inbound-order',
    name: '入库单',
    title: '入库单',
    mainSourceId: 't_wms_inbound_order',
    entrySourceId: 't_wms_inbound_order_entry',
    withBarcode: true,
    headerFields: [
      { label: '入库单号', field: 'FBillNo' },
      { label: '日期', field: 'FDate' },
      { label: '状态', field: 'FDocumentStatus' },
      { label: '入库类型', field: 'FOrderType' },
      { label: '仓库', field: 'FWarehouseCode' },
      { label: '供应商', field: 'FSupplierCode' },
      { label: '来源单号', field: 'FSourceOrderNo' },
    ],
    tableColumns: [
      { header: '序号', field: 'FSeq', width: 10, align: 'center' },
      { header: '物料编码', field: 'FMaterialCode', width: 26 },
      { header: '物料名称', field: 'FMaterialName', width: 36 },
      { header: '计划', field: 'FOrderQty', width: 16, align: 'right' },
      { header: '已收', field: 'FReceivedQty', width: 16, align: 'right' },
      { header: '库位', field: 'FTargetLocation', width: 22 },
      { header: '批次', field: 'FLot', width: 20 },
    ],
  },
  {
    id: 'tpl-outbound-order',
    name: '出库单',
    title: '出库单',
    mainSourceId: 't_wms_outbound_order',
    entrySourceId: 't_wms_outbound_order_entry',
    withBarcode: true,
    headerFields: [
      { label: '出库单号', field: 'FBillNo' },
      { label: '日期', field: 'FDate' },
      { label: '状态', field: 'FDocumentStatus' },
      { label: '出库类型', field: 'FOrderType' },
      { label: '仓库', field: 'FWarehouseCode' },
      { label: '客户', field: 'FCustomerCode' },
    ],
    tableColumns: [
      { header: '序号', field: 'FSeq', width: 10, align: 'center' },
      { header: '物料编码', field: 'FMaterialCode', width: 26 },
      { header: '物料名称', field: 'FMaterialName', width: 36 },
      { header: '需求', field: 'FDemandQty', width: 16, align: 'right' },
      { header: '已出', field: 'FIssuedQty', width: 16, align: 'right' },
      { header: '库位', field: 'FSourceLocation', width: 22 },
      { header: '批次', field: 'FLot', width: 20 },
    ],
  },
  {
    id: 'tpl-pda-inbound',
    name: 'PDA快速入库标签',
    title: 'PDA快速入库',
    mainSourceId: 't_wms_pda_inbound',
    withBarcode: true,
    headerFields: [
      { label: '记录号', field: 'FBillNo' },
      { label: '物料编码', field: 'FMaterialCode' },
      { label: '物料名称', field: 'FMaterialName' },
      { label: '仓库', field: 'FWarehouseCode' },
      { label: '库位', field: 'FLocationCode' },
      { label: '数量', field: 'FQty' },
      { label: '批次', field: 'FLot' },
      { label: '操作员', field: 'FOperatorName' },
    ],
  },
  {
    id: 'tpl-other-inbound',
    name: '其他入库单',
    title: '其他入库单',
    mainSourceId: 't_wms_other_inbound',
    entrySourceId: 't_wms_other_inbound_entry',
    headerFields: [
      { label: '单号', field: 'FBillNo' },
      { label: '日期', field: 'FDate' },
      { label: '状态', field: 'FDocumentStatus' },
      { label: '入库类型', field: 'FInboundType' },
      { label: '仓库', field: 'FWarehouseCode' },
      { label: '来源说明', field: 'FSourceDesc' },
    ],
    tableColumns: STD_ENTRY_COLS.map((c) =>
      c.field === 'FQty' ? { ...c, field: 'FQty' } : c.field === 'FLocationCode' ? { ...c, field: 'FLocationCode' } : c,
    ),
  },
  {
    id: 'tpl-other-outbound',
    name: '其他出库单',
    title: '其他出库单',
    mainSourceId: 't_wms_other_outbound',
    entrySourceId: 't_wms_other_outbound_entry',
    headerFields: [
      { label: '单号', field: 'FBillNo' },
      { label: '日期', field: 'FDate' },
      { label: '状态', field: 'FDocumentStatus' },
      { label: '出库类型', field: 'FOutboundType' },
      { label: '仓库', field: 'FWarehouseCode' },
      { label: '去向说明', field: 'FTargetDesc' },
    ],
    tableColumns: STD_ENTRY_COLS,
  },
  {
    id: 'tpl-transfer-order',
    name: '库存调拨单',
    title: '库存调拨单',
    mainSourceId: 't_wms_transfer_order',
    headerFields: [
      { label: '调拨单号', field: 'FBillNo' },
      { label: '操作时间', field: 'FDate' },
      { label: '状态', field: 'FDocumentStatus' },
      { label: '物料编码', field: 'FMaterialCode' },
      { label: '调拨数量', field: 'FTransferQty' },
      { label: '源库位', field: 'FSourceLocation' },
      { label: '目标库位', field: 'FTargetLocation' },
      { label: '批次', field: 'FLot' },
    ],
  },
  {
    id: 'tpl-stockcheck-task',
    name: '盘点任务',
    title: '盘点任务单',
    mainSourceId: 't_wms_stockcheck_task',
    entrySourceId: 't_wms_stockcheck_task_entry',
    headerFields: [
      { label: '任务号', field: 'FBillNo' },
      { label: '计划日期', field: 'FDate' },
      { label: '状态', field: 'FDocumentStatus' },
      { label: '盘点计划', field: 'FPlanNo' },
      { label: '仓库', field: 'FWarehouseCode' },
      { label: '盘点员', field: 'FAssigneeName' },
    ],
    tableColumns: [
      { header: '序号', field: 'FSeq', width: 10, align: 'center' },
      { header: '物料编码', field: 'FMaterialCode', width: 28 },
      { header: '库位', field: 'FLocationCode', width: 22 },
      { header: '账面', field: 'FBookQty', width: 18, align: 'right' },
      { header: '实盘', field: 'FActualQty', width: 18, align: 'right' },
      { header: '差异', field: 'FDiffQty', width: 18, align: 'right' },
      { header: '批次', field: 'FLot', width: 20 },
    ],
  },
  {
    id: 'tpl-qc-order',
    name: '质检单',
    title: '质检单',
    mainSourceId: 't_wms_qc_order',
    headerFields: [
      { label: '质检单号', field: 'FBillNo' },
      { label: '检验时间', field: 'FDate' },
      { label: '状态', field: 'FDocumentStatus' },
      { label: '来源单号', field: 'FSourceNo' },
      { label: '物料编码', field: 'FMaterialCode' },
      { label: '检验结果', field: 'FResult' },
      { label: '检验员', field: 'FInspectorName' },
    ],
  },
  {
    id: 'tpl-production-order',
    name: '生产订单',
    title: '生产订单',
    mainSourceId: 't_wms_production_order',
    headerFields: [
      { label: '订单号', field: 'FBillNo' },
      { label: '计划开始', field: 'FDate' },
      { label: '状态', field: 'FDocumentStatus' },
      { label: '产品编码', field: 'FProductCode' },
      { label: '产品名称', field: 'FProductName' },
      { label: '计划数量', field: 'FPlanQty' },
      { label: '完工数量', field: 'FCompletedQty' },
    ],
  },
  {
    id: 'tpl-bom',
    name: 'BOM物料清单',
    title: 'BOM物料清单',
    mainSourceId: 't_wms_bom',
    entrySourceId: 't_wms_bom_entry',
    headerFields: [
      { label: 'BOM编码', field: 'FBillNo' },
      { label: '产品编码', field: 'FProductCode' },
      { label: '版本号', field: 'FVersionNo' },
      { label: '状态', field: 'FDocumentStatus' },
    ],
    tableColumns: [
      { header: '序号', field: 'FSeq', width: 10, align: 'center' },
      { header: '物料编码', field: 'FMaterialCode', width: 30 },
      { header: '物料名称', field: 'FMaterialName', width: 40 },
      { header: '单位用量', field: 'FQtyPer', width: 22, align: 'right' },
      { header: '单位', field: 'FUnitID', width: 14, align: 'center' },
    ],
  },
]

/**
 * 金蝶云星空物料标签（110×80mm，左文右码）
 */
export function createKingdeeMaterialLabelTemplate(): PrintTemplate {
  const main = 't_bd_material'
  const labelStyle = {
    fontSize: 9,
    fontFamily: 'Microsoft YaHei, SimSun, sans-serif',
    fontWeight: 'normal' as const,
    textAlign: 'left' as const,
    color: '#888888',
    backgroundColor: 'transparent',
    borderWidth: 0,
    borderColor: 'transparent',
    borderStyle: 'none' as const,
  }
  const valueStyle = {
    fontFamily: 'Microsoft YaHei, SimHei, sans-serif',
    fontWeight: 'bold' as const,
    textAlign: 'left' as const,
    color: '#000000',
    backgroundColor: 'transparent',
    borderWidth: 0,
    borderColor: 'transparent',
    borderStyle: 'none' as const,
  }

  return {
    id: 'tpl-kingdee-material-label',
    name: '金蝶物料标签',
    category: 'baseData',
    businessObjectId: main,
    pageSettings: { width: 110, height: 80, marginTop: 3, marginRight: 3, marginBottom: 3, marginLeft: 3, showGrid: false, gridSize: 5, snapToGrid: false },
    elements: [
      {
        id: uuidv4(), type: 'text', x: 5, y: 4, width: 60, height: 9, zIndex: 1, locked: true,
        text: '物料标签', fontSize: 14, fontFamily: 'Microsoft YaHei, SimHei, sans-serif',
        fontWeight: 'bold', textAlign: 'left', color: '#000000',
      },
      {
        id: uuidv4(), type: 'text', x: 5, y: 14, width: 60, height: 5, zIndex: 2, locked: true,
        text: '物料编码', ...labelStyle,
      },
      {
        id: uuidv4(), type: 'data-field', x: 5, y: 19, width: 60, height: 7, zIndex: 3, locked: true,
        fontSize: 12, ...valueStyle,
        fieldBinding: bind(main, '物料', 'FNumber', '物料编码'),
      },
      {
        id: uuidv4(), type: 'text', x: 5, y: 27, width: 60, height: 5, zIndex: 4, locked: true,
        text: '物料名称', ...labelStyle,
      },
      {
        id: uuidv4(), type: 'data-field', x: 5, y: 32, width: 60, height: 7, zIndex: 5, locked: true,
        fontSize: 11, ...valueStyle,
        fieldBinding: bind(main, '物料', 'FName', '物料名称'),
      },
      {
        id: uuidv4(), type: 'text', x: 5, y: 40, width: 60, height: 5, zIndex: 6, locked: true,
        text: '规格型号', ...labelStyle,
      },
      {
        id: uuidv4(), type: 'data-field', x: 5, y: 45, width: 60, height: 6, zIndex: 7, locked: true,
        fontSize: 10, ...valueStyle,
        fieldBinding: bind(main, '物料', 'FModel', '规格型号'),
      },
      {
        id: uuidv4(), type: 'text', x: 5, y: 52, width: 28, height: 5, zIndex: 8, locked: true,
        text: '批次号', ...labelStyle,
      },
      {
        id: uuidv4(), type: 'text', x: 35, y: 52, width: 30, height: 5, zIndex: 9, locked: true,
        text: '生产日期', ...labelStyle,
      },
      {
        id: uuidv4(), type: 'data-field', x: 5, y: 57, width: 28, height: 6, zIndex: 10, locked: true,
        fontSize: 10, ...valueStyle,
        fieldBinding: bind(main, '物料', 'FLot', '批次号'),
      },
      {
        id: uuidv4(), type: 'data-field', x: 35, y: 57, width: 30, height: 6, zIndex: 11, locked: true,
        fontSize: 10, ...valueStyle,
        fieldBinding: bind(main, '物料', 'FProductionDate', '生产日期'),
      },
      {
        id: uuidv4(), type: 'text', x: 5, y: 64, width: 14, height: 5, zIndex: 12, locked: true,
        text: '数量', ...labelStyle,
      },
      {
        id: uuidv4(), type: 'data-field', x: 18, y: 63, width: 47, height: 7, zIndex: 13, locked: true,
        fontSize: 12, fontFamily: 'Microsoft YaHei, SimHei, sans-serif', fontWeight: 'bold',
        textAlign: 'left', color: '#1a8c44',
        backgroundColor: 'transparent', borderWidth: 0, borderColor: 'transparent', borderStyle: 'none',
        fieldBinding: bind(main, '物料', 'FQtyDisplay', '数量'),
      },
      {
        id: uuidv4(), type: 'barcode', x: 72, y: 12, width: 30, height: 30, zIndex: 15, locked: true,
        barcodeType: 'qr', barcodeValue: 'QR',
        fieldBinding: bind(main, '物料', 'FBarCode', '二维码'),
      },
      {
        id: uuidv4(), type: 'text', x: 72, y: 44, width: 30, height: 6, zIndex: 16, locked: true,
        text: '扫码追溯', fontSize: 9, fontFamily: 'Microsoft YaHei, SimSun, sans-serif',
        fontWeight: 'normal', textAlign: 'center', color: '#999999',
      },
    ],
    createdAt: now,
    updatedAt: now,
  }
}

export const PRESET_TEMPLATES = [
  ...WMS_DOC_TEMPLATE_DEFS.map(createWmsDocTemplate),
  createMaterialLabelTemplate(),
  createBarcodeLabelTemplate(),
  createKingdeeMaterialLabelTemplate(),
]
