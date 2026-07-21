/**
 * WMS 预览用模拟单据数据
 */
import type { DocumentData } from '../types/designer'

const MAT = {
  'MAT-1001': { FNumber: 'MAT-1001', FName: '贴片电阻 0805 1KΩ', FModel: 'RC0805FR-071KL', FBaseUnit: 'PCS', FBarCode: 'MAT-1001' },
  'MAT-2005': { FNumber: 'MAT-2005', FName: 'STM32F407VGT6', FModel: 'LQFP100', FBaseUnit: 'PCS', FBarCode: 'MAT-2005' },
}

function doc(
  mainSourceId: string,
  entrySourceId: string | undefined,
  billNo: string,
  header: Record<string, unknown>,
  entries: Record<string, unknown>[] = [],
): DocumentData {
  return {
    docId: billNo,
    mainSourceId,
    entrySourceId,
    header: { FBillNo: billNo, FBarCode: billNo, ...header },
    entries,
    relatedData: { t_bd_material: MAT, t_bd_supplier: {} },
  }
}

export const WMS_MOCK_DOCUMENTS: Record<string, DocumentData> = {
  t_wms_prep_notice: doc('t_wms_prep_notice', 't_wms_prep_notice_entry', 'PN202606001', {
    FDate: '2026-06-25', FDocumentStatus: '开放', FProductionPlanNo: 'PP-001',
    FWarehouseCode: 'WH01', FCreatorName: '计划员',
  }, [
    { FSeq: 1, FMaterialCode: 'MAT-1001', FMaterialName: '贴片电阻 0805 1KΩ', FDemandQty: 500, FPickedQty: 0, FRecommendLoc: 'WH01-A-01' },
    { FSeq: 2, FMaterialCode: 'MAT-2005', FMaterialName: 'STM32F407VGT6', FDemandQty: 100, FPickedQty: 0, FRecommendLoc: 'WH01-B-03' },
  ]),

  t_wms_pick_issue: doc('t_wms_pick_issue', 't_wms_pick_issue_entry', 'PI202606001', {
    FDate: '2026-06-25', FDocumentStatus: '拣货中', FNoticeNo: 'PN202606001',
    FWarehouseCode: 'WH01', FHandoverArea: 'A区交接台', FPickerName: '张三',
  }, [
    { FSeq: 1, FMaterialCode: 'MAT-1001', FMaterialName: '贴片电阻 0805 1KΩ', FMaterialModel: 'RC0805FR-071KL', FUnitID: 'PCS', FPickQty: 500, FPickedQty: 200, FLocation: 'WH01-A-01', FLot: 'LOT001' },
    { FSeq: 2, FMaterialCode: 'MAT-2005', FMaterialName: 'STM32F407VGT6', FMaterialModel: 'LQFP100', FUnitID: 'PCS', FPickQty: 100, FPickedQty: 0, FLocation: 'WH01-B-03', FLot: 'LOT002' },
  ]),

  t_wms_material_pickup: doc('t_wms_material_pickup', undefined, 'MP202606001', {
    FDate: '2026-06-25 10:30', FDocumentStatus: '已完成', FIssueNo: 'PI202606001', FReceiverName: '李四',
  }),

  t_wms_workshop_return: doc('t_wms_workshop_return', 't_wms_workshop_return_entry', 'WR202606001', {
    FDate: '2026-06-25', FDocumentStatus: '待确认', FIssueNo: 'PI202606001',
    FWarehouseCode: 'WH01', FReturnReason: '生产剩余', FOperatorName: '王五',
  }, [
    { FSeq: 1, FMaterialCode: 'MAT-1001', FMaterialName: '贴片电阻 0805 1KΩ', FReturnQty: 50, FTargetLocation: 'WH01-A-01', FLot: 'LOT001' },
  ]),

  t_wms_purchase_order: doc('t_wms_purchase_order', 't_wms_purchase_order_entry', 'PO202606001', {
    FDate: '2026-06-20', FDocumentStatus: '已审核', FSupplierCode: 'SUP001',
    FWarehouseCode: 'WH01', FPlanArriveDate: '2026-06-28', FCreatorName: '采购员',
  }, [
    { FSeq: 1, FMaterialCode: 'MAT-1001', FMaterialName: '贴片电阻 0805 1KΩ', FUnitID: 'PCS', FOrderQty: 1000, FReceivedQty: 500 },
  ]),

  t_wms_delivery_note: doc('t_wms_delivery_note', 't_wms_delivery_note_entry', 'DN202606001', {
    FDate: '2026-06-24', FDocumentStatus: '已收货', FPurchaseOrderNo: 'PO202606001', FSupplierCode: 'SUP001',
  }, [
    { FSeq: 1, FMaterialCode: 'MAT-1001', FPlanQty: 500, FActualQty: 500, FDiffQty: 0 },
  ]),

  t_wms_purchase_return: doc('t_wms_purchase_return', 't_wms_purchase_return_entry', 'PR202606001', {
    FDate: '2026-06-22', FDocumentStatus: '已审核', FSupplierCode: 'SUP001', FReason: '质量不合格',
  }, [
    { FSeq: 1, FMaterialCode: 'MAT-1001', FReturnQty: 20, FLot: 'LOT001' },
  ]),

  t_wms_inbound_order: doc('t_wms_inbound_order', 't_wms_inbound_order_entry', 'IN202606001', {
    FDate: '2026-06-24', FDocumentStatus: '入库中', FOrderType: '采购入库',
    FWarehouseCode: 'WH01', FSupplierCode: 'SUP001', FSourceOrderNo: 'PO202606001', FCreatorName: '仓管员',
  }, [
    { FSeq: 1, FMaterialCode: 'MAT-1001', FMaterialName: '贴片电阻 0805 1KΩ', FUnitID: 'PCS', FOrderQty: 500, FReceivedQty: 300, FTargetLocation: 'WH01-A-01', FLot: 'LOT001' },
  ]),

  t_wms_outbound_order: doc('t_wms_outbound_order', 't_wms_outbound_order_entry', 'OUT202606001', {
    FDate: '2026-06-25', FDocumentStatus: '出库中', FOrderType: '销售出库',
    FWarehouseCode: 'WH01', FCustomerCode: 'CUST001', FCreatorName: '仓管员',
  }, [
    { FSeq: 1, FMaterialCode: 'MAT-2005', FMaterialName: 'STM32F407VGT6', FMaterialModel: 'LQFP100', FUnitID: 'PCS', FDemandQty: 50, FIssuedQty: 20, FSourceLocation: 'WH01-B-03', FLot: 'LOT002' },
  ]),

  t_wms_pda_inbound: doc('t_wms_pda_inbound', undefined, 'PDIN202606001', {
    FMaterialCode: 'MAT-1001', FMaterialName: '贴片电阻 0805 1KΩ', FMaterialModel: 'RC0805FR-071KL',
    FUnitID: 'PCS', FWarehouseCode: 'WH01', FLocationCode: 'WH01-A-01', FLot: 'LOT001', FQty: 1, FOperatorName: 'PDA操作员',
  }),

  t_wms_other_inbound: doc('t_wms_other_inbound', 't_wms_other_inbound_entry', 'OI202606001', {
    FDate: '2026-06-23', FDocumentStatus: '已完成', FInboundType: '退料入库', FWarehouseCode: 'WH01', FSourceDesc: '车间退料',
  }, [
    { FSeq: 1, FMaterialCode: 'MAT-1001', FMaterialName: '贴片电阻 0805 1KΩ', FQty: 100, FLocationCode: 'WH01-A-01', FLot: 'LOT001' },
  ]),

  t_wms_other_outbound: doc('t_wms_other_outbound', 't_wms_other_outbound_entry', 'OO202606001', {
    FDate: '2026-06-23', FDocumentStatus: '已完成', FOutboundType: '样品出库', FWarehouseCode: 'WH01', FTargetDesc: '研发部',
  }, [
    { FSeq: 1, FMaterialCode: 'MAT-2005', FMaterialName: 'STM32F407VGT6', FQty: 5, FLocationCode: 'WH01-B-03', FLot: 'LOT002' },
  ]),

  t_wms_transfer_order: doc('t_wms_transfer_order', undefined, 'TF202606001', {
    FDate: '2026-06-24', FDocumentStatus: '已完成', FTransferType: '库内移库',
    FSourceWarehouse: 'WH01', FSourceLocation: 'WH01-A-01', FTargetWarehouse: 'WH01', FTargetLocation: 'WH01-A-02',
    FMaterialCode: 'MAT-1001', FLot: 'LOT001', FTransferQty: 200, FCreatorName: '仓管员',
  }),

  t_wms_stockcheck_task: doc('t_wms_stockcheck_task', 't_wms_stockcheck_task_entry', 'SC202606001', {
    FDate: '2026-06-25', FDocumentStatus: '盘点中', FPlanNo: 'SP202606001',
    FWarehouseCode: 'WH01', FAssigneeName: '盘点员A',
  }, [
    { FSeq: 1, FMaterialCode: 'MAT-1001', FLocationCode: 'WH01-A-01', FLot: 'LOT001', FBookQty: 500, FActualQty: 498, FDiffQty: -2 },
  ]),

  t_wms_qc_order: doc('t_wms_qc_order', undefined, 'QC202606001', {
    FDate: '2026-06-24', FDocumentStatus: '已完成', FSourceType: '入库', FSourceNo: 'IN202606001',
    FMaterialCode: 'MAT-1001', FLot: 'LOT001', FSampleQty: 10, FResult: '合格', FInspectorName: '质检员',
  }),

  t_wms_production_order: doc('t_wms_production_order', undefined, 'MO202606001', {
    FDate: '2026-06-20', FDocumentStatus: '生产中', FProductCode: 'PROD-001', FProductName: '控制板A型',
    FPlanQty: 200, FCompletedQty: 80, FWarehouseCode: 'WH01',
  }),

  t_wms_bom: doc('t_wms_bom', 't_wms_bom_entry', 'BOM-PROD001-V1', {
    FProductCode: 'PROD-001', FVersionNo: 'V1', FDocumentStatus: '已审核',
  }, [
    { FSeq: 1, FMaterialCode: 'MAT-1001', FMaterialName: '贴片电阻 0805 1KΩ', FUnitID: 'PCS', FQtyPer: 20 },
    { FSeq: 2, FMaterialCode: 'MAT-2005', FMaterialName: 'STM32F407VGT6', FUnitID: 'PCS', FQtyPer: 1 },
  ]),

  t_bd_material: {
    docId: 'MAT-1001',
    mainSourceId: 't_bd_material',
    header: { FNumber: 'MAT-1001', FName: '贴片电阻 0805 1KΩ', FModel: 'RC0805FR-071KL', FBaseUnit: 'PCS', FBarCode: 'MAT-1001', FMaterialGroup: '电子元器件' },
    entries: [],
    relatedData: { t_bd_material: {}, t_bd_supplier: {} },
  },
}

export function getMockDocument(mainSourceId: string): DocumentData {
  return WMS_MOCK_DOCUMENTS[mainSourceId] ?? WMS_MOCK_DOCUMENTS.t_wms_pick_issue
}

export const MOCK_DOCUMENT = WMS_MOCK_DOCUMENTS.t_wms_pick_issue
