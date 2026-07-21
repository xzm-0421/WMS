package com.wms.print;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.barcode.service.BarcodeArchiveService;
import com.wms.barcode.entity.BarcodeArchive;
import com.wms.base.entity.BaseMaterial;
import com.wms.base.mapper.BaseMaterialMapper;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.inbound.entity.InboundOrder;
import com.wms.inbound.entity.InboundOrderDetail;
import com.wms.inbound.mapper.InboundOrderDetailMapper;
import com.wms.inbound.mapper.InboundOrderMapper;
import com.wms.incoming.entity.*;
import com.wms.incoming.mapper.*;
import com.wms.inventoryext.entity.*;
import com.wms.inventoryext.mapper.*;
import com.wms.outbound.entity.OutboundOrder;
import com.wms.outbound.entity.OutboundOrderDetail;
import com.wms.outbound.mapper.OutboundOrderDetailMapper;
import com.wms.outbound.mapper.OutboundOrderMapper;
import com.wms.pdainbound.entity.PdaInboundRecord;
import com.wms.pdainbound.mapper.PdaInboundRecordMapper;
import com.wms.picking.entity.*;
import com.wms.picking.mapper.*;
import com.wms.picking.service.PickIssueService;
import com.wms.production.entity.BomDetail;
import com.wms.production.entity.BomHeader;
import com.wms.production.entity.ProductionOrder;
import com.wms.production.mapper.BomDetailMapper;
import com.wms.production.mapper.BomHeaderMapper;
import com.wms.production.mapper.ProductionOrderMapper;
import com.wms.quality.entity.QcOrder;
import com.wms.quality.mapper.QcOrderMapper;
import com.wms.stockcheck.entity.StockcheckDetail;
import com.wms.stockcheck.entity.StockcheckTask;
import com.wms.stockcheck.mapper.StockcheckDetailMapper;
import com.wms.stockcheck.mapper.StockcheckTaskMapper;
import com.wms.print.service.LabelPrintJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

@Service
@RequiredArgsConstructor
public class PrintDataService {

    private final PickIssueService pickIssueService;
    private final PrepNoticeMapper prepNoticeMapper;
    private final PrepNoticeLineMapper prepNoticeLineMapper;
    private final MaterialPickupMapper materialPickupMapper;
    private final WorkshopReturnMapper workshopReturnMapper;
    private final WorkshopReturnLineMapper workshopReturnLineMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseOrderLineMapper purchaseOrderLineMapper;
    private final DeliveryNoteMapper deliveryNoteMapper;
    private final DeliveryNoteLineMapper deliveryNoteLineMapper;
    private final PurchaseReturnMapper purchaseReturnMapper;
    private final PurchaseReturnLineMapper purchaseReturnLineMapper;
    private final InboundOrderMapper inboundOrderMapper;
    private final InboundOrderDetailMapper inboundOrderDetailMapper;
    private final OutboundOrderMapper outboundOrderMapper;
    private final OutboundOrderDetailMapper outboundOrderDetailMapper;
    private final PdaInboundRecordMapper pdaInboundRecordMapper;
    private final OtherInboundMapper otherInboundMapper;
    private final OtherInboundLineMapper otherInboundLineMapper;
    private final OtherOutboundMapper otherOutboundMapper;
    private final OtherOutboundLineMapper otherOutboundLineMapper;
    private final TransferOrderMapper transferOrderMapper;
    private final StockcheckTaskMapper stockcheckTaskMapper;
    private final StockcheckDetailMapper stockcheckDetailMapper;
    private final QcOrderMapper qcOrderMapper;
    private final ProductionOrderMapper productionOrderMapper;
    private final BomHeaderMapper bomHeaderMapper;
    private final BomDetailMapper bomDetailMapper;
    private final BaseMaterialMapper materialMapper;
    private final BarcodeArchiveService barcodeArchiveService;
    private final LabelPrintJobService labelPrintJobService;

    public Map<String, Object> build(String biz, String docNo) {
        if (!StringUtils.hasText(biz) || !StringUtils.hasText(docNo)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "biz 与 docNo 不能为空");
        }
        return switch (biz) {
            case "pick_issue" -> pickIssueService.buildPrintData(docNo);
            case "prep_notice" -> buildPrepNotice(docNo);
            case "material_pickup" -> buildMaterialPickup(docNo);
            case "workshop_return" -> buildWorkshopReturn(docNo);
            case "purchase_order" -> buildPurchaseOrder(docNo);
            case "delivery_note" -> buildDeliveryNote(docNo);
            case "purchase_return" -> buildPurchaseReturn(docNo);
            case "inbound_order" -> buildInboundOrder(docNo);
            case "outbound_order" -> buildOutboundOrder(docNo);
            case "pda_inbound" -> buildPdaInbound(docNo);
            case "other_inbound" -> buildOtherInbound(docNo);
            case "other_outbound" -> buildOtherOutbound(docNo);
            case "transfer_order" -> buildTransferOrder(docNo);
            case "stockcheck_task" -> buildStockcheckTask(docNo);
            case "qc_order" -> buildQcOrder(docNo);
            case "production_order" -> buildProductionOrder(docNo);
            case "bom" -> buildBom(docNo);
            case "material_label" -> buildMaterialLabel(docNo);
            case "barcode_archive" -> buildBarcodeArchive(docNo);
            case "kingdee_label" -> labelPrintJobService.buildPrintDocument(docNo);
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的单据类型: " + biz);
        };
    }

    private BaseMaterial requireMaterial(String code) {
        return materialMapper.selectOne(new LambdaQueryWrapper<BaseMaterial>()
                .eq(BaseMaterial::getMaterialCode, code));
    }

    private void enrichMaterial(Map<String, Map<String, Object>> materials, String code, String name, String spec, String unit) {
        if (!StringUtils.hasText(code)) return;
        materials.putIfAbsent(code, PrintDocumentHelper.materialRow(code, name, spec, unit));
    }

    private void enrichMaterial(Map<String, Map<String, Object>> materials, BaseMaterial m) {
        if (m != null) {
            materials.put(m.getMaterialCode(), PrintDocumentHelper.materialRow(
                    m.getMaterialCode(), m.getMaterialName(), m.getSpecification(), m.getUnitCode()));
        }
    }

    private Map<String, Object> buildPrepNotice(String noticeNo) {
        PrepNotice notice = prepNoticeMapper.selectOne(new LambdaQueryWrapper<PrepNotice>()
                .eq(PrepNotice::getNoticeNo, noticeNo).eq(PrepNotice::getDeleted, 0));
        if (notice == null) throw notFound("备料通知单");
        List<PrepNoticeLine> lines = prepNoticeLineMapper.selectList(new LambdaQueryWrapper<PrepNoticeLine>()
                .eq(PrepNoticeLine::getNoticeNo, noticeNo).orderByAsc(PrepNoticeLine::getLineNo));
        Map<String, Object> header = PrintDocumentHelper.headerBill(noticeNo);
        header.put("FDate", PrintDocumentHelper.fmtDateTime(notice.getCreateTime()));
        header.put("FDocumentStatus", notice.getStatus());
        header.put("FProductionPlanNo", notice.getProductionPlanNo());
        header.put("FWarehouseCode", notice.getWarehouseCode());
        header.put("FDemandTime", PrintDocumentHelper.fmtDateTime(notice.getDemandTime()));
        header.put("FCreatorName", notice.getCreatorName());
        Map<String, Map<String, Object>> materials = new LinkedHashMap<>();
        List<Map<String, Object>> entries = new ArrayList<>();
        for (PrepNoticeLine line : lines) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("FSeq", line.getLineNo());
            e.put("FMaterialCode", line.getMaterialCode());
            e.put("FMaterialName", line.getMaterialName());
            e.put("FDemandQty", line.getDemandQty());
            e.put("FPickedQty", line.getPickedQty());
            e.put("FRecommendLoc", line.getRecommendLoc());
            entries.add(e);
            enrichMaterial(materials, line.getMaterialCode(), line.getMaterialName(), null, null);
        }
        return PrintDocumentHelper.document(noticeNo, "t_wms_prep_notice", "t_wms_prep_notice_entry", header, entries, materials);
    }

    private Map<String, Object> buildMaterialPickup(String pickupNo) {
        MaterialPickup p = materialPickupMapper.selectOne(new LambdaQueryWrapper<MaterialPickup>()
                .eq(MaterialPickup::getPickupNo, pickupNo));
        if (p == null) throw notFound("领料执行单");
        Map<String, Object> header = PrintDocumentHelper.headerBill(pickupNo);
        header.put("FDate", PrintDocumentHelper.fmtDateTime(p.getPickupTime()));
        header.put("FDocumentStatus", p.getStatus());
        header.put("FIssueNo", p.getIssueNo());
        header.put("FReceiverName", p.getReceiverName());
        return PrintDocumentHelper.document(pickupNo, "t_wms_material_pickup", null, header, List.of(), null);
    }

    private Map<String, Object> buildWorkshopReturn(String returnNo) {
        WorkshopReturn ret = workshopReturnMapper.selectOne(new LambdaQueryWrapper<WorkshopReturn>()
                .eq(WorkshopReturn::getReturnNo, returnNo).eq(WorkshopReturn::getDeleted, 0));
        if (ret == null) throw notFound("车间退库单");
        List<WorkshopReturnLine> lines = workshopReturnLineMapper.selectList(new LambdaQueryWrapper<WorkshopReturnLine>()
                .eq(WorkshopReturnLine::getReturnNo, returnNo).orderByAsc(WorkshopReturnLine::getLineNo));
        Map<String, Object> header = PrintDocumentHelper.headerBill(returnNo);
        header.put("FDate", PrintDocumentHelper.fmtDateTime(ret.getCreateTime()));
        header.put("FDocumentStatus", ret.getStatus());
        header.put("FIssueNo", ret.getIssueNo());
        header.put("FWarehouseCode", ret.getWarehouseCode());
        header.put("FReturnReason", ret.getReturnReason());
        header.put("FOperatorName", ret.getOperatorName());
        Map<String, Map<String, Object>> materials = new LinkedHashMap<>();
        List<Map<String, Object>> entries = new ArrayList<>();
        for (WorkshopReturnLine line : lines) {
            BaseMaterial m = requireMaterial(line.getMaterialCode());
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("FSeq", line.getLineNo());
            e.put("FMaterialCode", line.getMaterialCode());
            e.put("FMaterialName", m != null ? m.getMaterialName() : line.getMaterialCode());
            e.put("FReturnQty", line.getReturnQty());
            e.put("FTargetLocation", line.getTargetLocation());
            e.put("FLot", line.getBatchNo());
            entries.add(e);
            enrichMaterial(materials, m);
        }
        return PrintDocumentHelper.document(returnNo, "t_wms_workshop_return", "t_wms_workshop_return_entry", header, entries, materials);
    }

    private Map<String, Object> buildPurchaseOrder(String orderNo) {
        PurchaseOrder order = purchaseOrderMapper.selectOne(new LambdaQueryWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getOrderNo, orderNo).eq(PurchaseOrder::getDeleted, 0));
        if (order == null) throw notFound("采购订单");
        List<PurchaseOrderLine> lines = purchaseOrderLineMapper.selectList(new LambdaQueryWrapper<PurchaseOrderLine>()
                .eq(PurchaseOrderLine::getOrderNo, orderNo).orderByAsc(PurchaseOrderLine::getLineNo));
        Map<String, Object> header = PrintDocumentHelper.headerBill(orderNo);
        header.put("FDate", PrintDocumentHelper.fmtDateTime(order.getCreateTime()));
        header.put("FDocumentStatus", order.getStatus());
        header.put("FSupplierCode", order.getSupplierCode());
        header.put("FWarehouseCode", order.getWarehouseCode());
        header.put("FPlanArriveDate", PrintDocumentHelper.fmtDate(order.getPlanArriveDate()));
        header.put("FTotalQty", order.getTotalQty());
        header.put("FReceivedQty", order.getReceivedQty());
        header.put("FCreatorName", order.getCreatorName());
        header.put("FRemark", order.getRemark());
        Map<String, Map<String, Object>> materials = new LinkedHashMap<>();
        List<Map<String, Object>> entries = new ArrayList<>();
        for (PurchaseOrderLine line : lines) {
            Map<String, Object> e = lineEntry(line.getLineNo(), line.getMaterialCode(), line.getMaterialName(), line.getUnitCode());
            e.put("FOrderQty", line.getOrderQty());
            e.put("FReceivedQty", line.getReceivedQty());
            e.put("FLot", line.getBatchNo());
            entries.add(e);
            enrichMaterial(materials, line.getMaterialCode(), line.getMaterialName(), null, line.getUnitCode());
        }
        return PrintDocumentHelper.document(orderNo, "t_wms_purchase_order", "t_wms_purchase_order_entry", header, entries, materials);
    }

    private Map<String, Object> buildDeliveryNote(String deliveryNo) {
        DeliveryNote note = deliveryNoteMapper.selectOne(new LambdaQueryWrapper<DeliveryNote>()
                .eq(DeliveryNote::getDeliveryNo, deliveryNo));
        if (note == null) throw notFound("送货单");
        List<DeliveryNoteLine> lines = deliveryNoteLineMapper.selectList(new LambdaQueryWrapper<DeliveryNoteLine>()
                .eq(DeliveryNoteLine::getDeliveryNo, deliveryNo).orderByAsc(DeliveryNoteLine::getLineNo));
        Map<String, Object> header = PrintDocumentHelper.headerBill(deliveryNo);
        header.put("FDate", PrintDocumentHelper.fmtDateTime(note.getDeliveryDate()));
        header.put("FDocumentStatus", note.getStatus());
        header.put("FPurchaseOrderNo", note.getPurchaseOrderNo());
        header.put("FSupplierCode", note.getSupplierCode());
        header.put("FDiffFlag", note.getDiffFlag() != null && note.getDiffFlag() != 0 ? "有差异" : "无差异");
        header.put("FRemark", note.getRemark());
        List<Map<String, Object>> entries = new ArrayList<>();
        for (DeliveryNoteLine line : lines) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("FSeq", line.getLineNo());
            e.put("FMaterialCode", line.getMaterialCode());
            e.put("FPlanQty", line.getPlanQty());
            e.put("FActualQty", line.getActualQty());
            e.put("FDiffQty", line.getDiffQty());
            entries.add(e);
        }
        return PrintDocumentHelper.document(deliveryNo, "t_wms_delivery_note", "t_wms_delivery_note_entry", header, entries, null);
    }

    private Map<String, Object> buildPurchaseReturn(String returnNo) {
        PurchaseReturn ret = purchaseReturnMapper.selectOne(new LambdaQueryWrapper<PurchaseReturn>()
                .eq(PurchaseReturn::getReturnNo, returnNo));
        if (ret == null) throw notFound("采购退货单");
        List<PurchaseReturnLine> lines = purchaseReturnLineMapper.selectList(new LambdaQueryWrapper<PurchaseReturnLine>()
                .eq(PurchaseReturnLine::getReturnNo, returnNo).orderByAsc(PurchaseReturnLine::getLineNo));
        Map<String, Object> header = PrintDocumentHelper.headerBill(returnNo);
        header.put("FDate", PrintDocumentHelper.fmtDateTime(ret.getCreateTime()));
        header.put("FDocumentStatus", ret.getStatus());
        header.put("FReceiptRefNo", ret.getReceiptRefNo());
        header.put("FSupplierCode", ret.getSupplierCode());
        header.put("FReason", ret.getReason());
        header.put("FCreatorName", ret.getCreatorName());
        List<Map<String, Object>> entries = new ArrayList<>();
        for (PurchaseReturnLine line : lines) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("FSeq", line.getLineNo());
            e.put("FMaterialCode", line.getMaterialCode());
            e.put("FReturnQty", line.getReturnQty());
            e.put("FLot", line.getBatchNo());
            entries.add(e);
        }
        return PrintDocumentHelper.document(returnNo, "t_wms_purchase_return", "t_wms_purchase_return_entry", header, entries, null);
    }

    private Map<String, Object> buildInboundOrder(String orderNo) {
        InboundOrder order = inboundOrderMapper.selectOne(new LambdaQueryWrapper<InboundOrder>()
                .eq(InboundOrder::getOrderNo, orderNo).eq(InboundOrder::getDeleted, 0));
        if (order == null) throw notFound("入库单");
        List<InboundOrderDetail> lines = inboundOrderDetailMapper.selectList(new LambdaQueryWrapper<InboundOrderDetail>()
                .eq(InboundOrderDetail::getOrderNo, orderNo).orderByAsc(InboundOrderDetail::getLineNo));
        Map<String, Object> header = PrintDocumentHelper.headerBill(orderNo);
        header.put("FDate", PrintDocumentHelper.fmtDateTime(order.getCreateTime()));
        header.put("FDocumentStatus", order.getStatus());
        header.put("FOrderType", order.getOrderType());
        header.put("FWarehouseCode", order.getWarehouseCode());
        header.put("FSupplierCode", order.getSupplierCode());
        header.put("FSourceOrderNo", order.getSourceOrderNo());
        header.put("FPlanDate", PrintDocumentHelper.fmtDate(order.getPlanDate()));
        header.put("FCreatorName", order.getCreatorName());
        header.put("FAuditorName", order.getAuditorName());
        header.put("FRemark", order.getRemark());
        Map<String, Map<String, Object>> materials = new LinkedHashMap<>();
        List<Map<String, Object>> entries = new ArrayList<>();
        for (InboundOrderDetail line : lines) {
            Map<String, Object> e = lineEntry(line.getLineNo(), line.getMaterialCode(), line.getMaterialName(), line.getUnitCode());
            e.put("FOrderQty", line.getOrderQty());
            e.put("FReceivedQty", line.getReceivedQty());
            e.put("FTargetLocation", line.getTargetLocation());
            e.put("FLot", line.getBatchNo());
            entries.add(e);
            enrichMaterial(materials, line.getMaterialCode(), line.getMaterialName(), null, line.getUnitCode());
        }
        return PrintDocumentHelper.document(orderNo, "t_wms_inbound_order", "t_wms_inbound_order_entry", header, entries, materials);
    }

    private Map<String, Object> buildOutboundOrder(String orderNo) {
        OutboundOrder order = outboundOrderMapper.selectOne(new LambdaQueryWrapper<OutboundOrder>()
                .eq(OutboundOrder::getOrderNo, orderNo).eq(OutboundOrder::getDeleted, 0));
        if (order == null) throw notFound("出库单");
        List<OutboundOrderDetail> lines = outboundOrderDetailMapper.selectList(new LambdaQueryWrapper<OutboundOrderDetail>()
                .eq(OutboundOrderDetail::getOrderNo, orderNo).orderByAsc(OutboundOrderDetail::getLineNo));
        Map<String, Object> header = PrintDocumentHelper.headerBill(orderNo);
        header.put("FDate", PrintDocumentHelper.fmtDateTime(order.getCreateTime()));
        header.put("FDocumentStatus", order.getStatus());
        header.put("FOrderType", order.getOrderType());
        header.put("FWarehouseCode", order.getWarehouseCode());
        header.put("FCustomerCode", order.getCustomerCode());
        header.put("FProductionOrderNo", order.getProductionOrderNo());
        header.put("FPlanDate", PrintDocumentHelper.fmtDate(order.getPlanDate()));
        header.put("FCreatorName", order.getCreatorName());
        header.put("FRemark", order.getRemark());
        Map<String, Map<String, Object>> materials = new LinkedHashMap<>();
        List<Map<String, Object>> entries = new ArrayList<>();
        for (OutboundOrderDetail line : lines) {
            Map<String, Object> e = lineEntry(line.getLineNo(), line.getMaterialCode(), line.getMaterialName(), line.getUnitCode());
            e.put("FMaterialModel", line.getSpecification());
            e.put("FDemandQty", line.getDemandQty());
            e.put("FIssuedQty", line.getIssuedQty());
            e.put("FSourceLocation", line.getSourceLocation());
            e.put("FLot", line.getBatchNo());
            entries.add(e);
            enrichMaterial(materials, line.getMaterialCode(), line.getMaterialName(), line.getSpecification(), line.getUnitCode());
        }
        return PrintDocumentHelper.document(orderNo, "t_wms_outbound_order", "t_wms_outbound_order_entry", header, entries, materials);
    }

    private Map<String, Object> buildPdaInbound(String recordNo) {
        PdaInboundRecord r = pdaInboundRecordMapper.selectOne(new LambdaQueryWrapper<PdaInboundRecord>()
                .eq(PdaInboundRecord::getRecordNo, recordNo));
        if (r == null) throw notFound("PDA入库记录");
        Map<String, Object> header = PrintDocumentHelper.headerBill(recordNo);
        header.put("FMaterialCode", r.getMaterialCode());
        header.put("FMaterialName", r.getMaterialName());
        header.put("FMaterialModel", r.getSpecification());
        header.put("FUnitID", r.getUnitCode());
        header.put("FWarehouseCode", r.getWarehouseCode());
        header.put("FLocationCode", r.getLocationCode());
        header.put("FLot", r.getBatchNo());
        header.put("FQty", r.getQuantity());
        header.put("FOperatorName", r.getOperatorName());
        header.put("FBarCode", StringUtils.hasText(r.getBarcodeContent()) ? r.getBarcodeContent() : r.getMaterialCode());
        return PrintDocumentHelper.document(recordNo, "t_wms_pda_inbound", null, header, List.of(), null);
    }

    private Map<String, Object> buildOtherInbound(String orderNo) {
        OtherInbound order = otherInboundMapper.selectOne(new LambdaQueryWrapper<OtherInbound>()
                .eq(OtherInbound::getOrderNo, orderNo));
        if (order == null) throw notFound("其他入库单");
        List<OtherInboundLine> lines = otherInboundLineMapper.selectList(new LambdaQueryWrapper<OtherInboundLine>()
                .eq(OtherInboundLine::getOrderNo, orderNo).orderByAsc(OtherInboundLine::getId));
        Map<String, Object> header = PrintDocumentHelper.headerBill(orderNo);
        header.put("FDate", PrintDocumentHelper.fmtDateTime(order.getCreateTime()));
        header.put("FDocumentStatus", order.getStatus());
        header.put("FInboundType", order.getInboundType());
        header.put("FWarehouseCode", order.getWarehouseCode());
        header.put("FSourceDesc", order.getSourceDesc());
        header.put("FCreatorName", order.getCreatorName());
        List<Map<String, Object>> entries = new ArrayList<>();
        int seq = 1;
        for (OtherInboundLine line : lines) {
            Map<String, Object> e = lineEntry(seq++, line.getMaterialCode(), line.getMaterialName(), null);
            e.put("FQty", line.getQuantity());
            e.put("FLocationCode", line.getLocationCode());
            e.put("FLot", line.getBatchNo());
            entries.add(e);
        }
        return PrintDocumentHelper.document(orderNo, "t_wms_other_inbound", "t_wms_other_inbound_entry", header, entries, null);
    }

    private Map<String, Object> buildOtherOutbound(String orderNo) {
        OtherOutbound order = otherOutboundMapper.selectOne(new LambdaQueryWrapper<OtherOutbound>()
                .eq(OtherOutbound::getOrderNo, orderNo));
        if (order == null) throw notFound("其他出库单");
        List<OtherOutboundLine> lines = otherOutboundLineMapper.selectList(new LambdaQueryWrapper<OtherOutboundLine>()
                .eq(OtherOutboundLine::getOrderNo, orderNo).orderByAsc(OtherOutboundLine::getId));
        Map<String, Object> header = PrintDocumentHelper.headerBill(orderNo);
        header.put("FDate", PrintDocumentHelper.fmtDateTime(order.getCreateTime()));
        header.put("FDocumentStatus", order.getStatus());
        header.put("FOutboundType", order.getOutboundType());
        header.put("FWarehouseCode", order.getWarehouseCode());
        header.put("FTargetDesc", order.getTargetDesc());
        header.put("FCreatorName", order.getCreatorName());
        List<Map<String, Object>> entries = new ArrayList<>();
        int seq = 1;
        for (OtherOutboundLine line : lines) {
            Map<String, Object> e = lineEntry(seq++, line.getMaterialCode(), line.getMaterialName(), null);
            e.put("FQty", line.getQuantity());
            e.put("FLocationCode", line.getLocationCode());
            e.put("FLot", line.getBatchNo());
            entries.add(e);
        }
        return PrintDocumentHelper.document(orderNo, "t_wms_other_outbound", "t_wms_other_outbound_entry", header, entries, null);
    }

    private Map<String, Object> buildTransferOrder(String transferNo) {
        TransferOrder t = transferOrderMapper.selectOne(new LambdaQueryWrapper<TransferOrder>()
                .eq(TransferOrder::getTransferNo, transferNo));
        if (t == null) throw notFound("调拨单");
        Map<String, Object> header = PrintDocumentHelper.headerBill(transferNo);
        header.put("FDate", PrintDocumentHelper.fmtDateTime(t.getOperationTime()));
        header.put("FDocumentStatus", t.getStatus());
        header.put("FTransferType", t.getTransferType());
        header.put("FSourceWarehouse", t.getSourceWarehouse());
        header.put("FSourceLocation", t.getSourceLocation());
        header.put("FTargetWarehouse", t.getTargetWarehouse());
        header.put("FTargetLocation", t.getTargetLocation());
        header.put("FMaterialCode", t.getMaterialCode());
        header.put("FLot", t.getBatchNo());
        header.put("FTransferQty", t.getTransferQty());
        header.put("FCreatorName", t.getCreatorName());
        return PrintDocumentHelper.document(transferNo, "t_wms_transfer_order", null, header, List.of(), null);
    }

    private Map<String, Object> buildStockcheckTask(String taskNo) {
        StockcheckTask task = stockcheckTaskMapper.selectOne(new LambdaQueryWrapper<StockcheckTask>()
                .eq(StockcheckTask::getTaskNo, taskNo));
        if (task == null) throw notFound("盘点任务");
        List<StockcheckDetail> lines = stockcheckDetailMapper.selectList(new LambdaQueryWrapper<StockcheckDetail>()
                .eq(StockcheckDetail::getTaskNo, taskNo).orderByAsc(StockcheckDetail::getLineNo));
        Map<String, Object> header = PrintDocumentHelper.headerBill(taskNo);
        header.put("FDate", PrintDocumentHelper.fmtDateTime(task.getCreateTime()));
        header.put("FDocumentStatus", task.getStatus());
        header.put("FPlanNo", task.getPlanNo());
        header.put("FWarehouseCode", task.getWarehouseCode());
        header.put("FLocationCode", task.getLocationCode());
        header.put("FAssigneeName", task.getAssigneeId());
        List<Map<String, Object>> entries = new ArrayList<>();
        for (StockcheckDetail line : lines) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("FSeq", line.getLineNo());
            e.put("FMaterialCode", line.getMaterialCode());
            e.put("FLocationCode", line.getLocationCode());
            e.put("FLot", line.getBatchNo());
            e.put("FBookQty", line.getBookQty());
            e.put("FActualQty", line.getActualQty());
            e.put("FDiffQty", line.getDiffQty());
            entries.add(e);
        }
        return PrintDocumentHelper.document(taskNo, "t_wms_stockcheck_task", "t_wms_stockcheck_task_entry", header, entries, null);
    }

    private Map<String, Object> buildQcOrder(String qcNo) {
        QcOrder q = qcOrderMapper.selectOne(new LambdaQueryWrapper<QcOrder>()
                .eq(QcOrder::getQcNo, qcNo));
        if (q == null) throw notFound("质检单");
        Map<String, Object> header = PrintDocumentHelper.headerBill(qcNo);
        header.put("FDate", PrintDocumentHelper.fmtDateTime(q.getInspectTime()));
        header.put("FDocumentStatus", q.getStatus());
        header.put("FSourceType", q.getSourceType());
        header.put("FSourceNo", q.getSourceNo());
        header.put("FMaterialCode", q.getMaterialCode());
        header.put("FLot", q.getBatchNo());
        header.put("FSampleQty", q.getSampleQty());
        header.put("FResult", q.getResult());
        header.put("FInspectorName", q.getInspectorName());
        return PrintDocumentHelper.document(qcNo, "t_wms_qc_order", null, header, List.of(), null);
    }

    private Map<String, Object> buildProductionOrder(String orderNo) {
        ProductionOrder o = productionOrderMapper.selectOne(new LambdaQueryWrapper<ProductionOrder>()
                .eq(ProductionOrder::getOrderNo, orderNo).eq(ProductionOrder::getDeleted, 0));
        if (o == null) throw notFound("生产订单");
        Map<String, Object> header = PrintDocumentHelper.headerBill(orderNo);
        header.put("FDate", PrintDocumentHelper.fmtDate(o.getPlanStart()));
        header.put("FDocumentStatus", o.getStatus());
        header.put("FProductCode", o.getProductCode());
        header.put("FProductName", o.getProductName());
        header.put("FPlanQty", o.getPlanQty());
        header.put("FCompletedQty", o.getCompletedQty());
        header.put("FWarehouseCode", o.getWarehouseCode());
        return PrintDocumentHelper.document(orderNo, "t_wms_production_order", null, header, List.of(), null);
    }

    private Map<String, Object> buildBom(String bomCode) {
        BomHeader h = bomHeaderMapper.selectOne(new LambdaQueryWrapper<BomHeader>()
                .eq(BomHeader::getBomCode, bomCode));
        if (h == null) throw notFound("BOM");
        List<BomDetail> lines = bomDetailMapper.selectList(new LambdaQueryWrapper<BomDetail>()
                .eq(BomDetail::getBomCode, bomCode).orderByAsc(BomDetail::getLineNo));
        Map<String, Object> header = PrintDocumentHelper.headerBill(bomCode);
        header.put("FProductCode", h.getProductCode());
        header.put("FVersionNo", h.getVersionNo());
        header.put("FDocumentStatus", h.getStatus() != null && h.getStatus() == 1 ? "启用" : "停用");
        List<Map<String, Object>> entries = new ArrayList<>();
        for (BomDetail line : lines) {
            Map<String, Object> e = lineEntry(line.getLineNo(), line.getMaterialCode(), line.getMaterialName(), line.getUnitCode());
            e.put("FQtyPer", line.getQtyPer());
            entries.add(e);
        }
        return PrintDocumentHelper.document(bomCode, "t_wms_bom", "t_wms_bom_entry", header, entries, null);
    }

    private Map<String, Object> buildMaterialLabel(String materialCode) {
        BaseMaterial m = materialMapper.selectOne(new LambdaQueryWrapper<BaseMaterial>()
                .eq(BaseMaterial::getMaterialCode, materialCode));
        if (m == null) throw notFound("物料");
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("FNumber", m.getMaterialCode());
        header.put("FName", m.getMaterialName());
        header.put("FModel", m.getSpecification());
        header.put("FBaseUnit", m.getUnitCode());
        header.put("FBarCode", m.getMaterialCode());
        header.put("FMaterialGroup", m.getCategoryCode());
        return PrintDocumentHelper.document(materialCode, "t_bd_material", null, header, List.of(), null);
    }

    private Map<String, Object> buildBarcodeArchive(String archiveNo) {
        BarcodeArchive archive = barcodeArchiveService.requireArchive(archiveNo);
        BaseMaterial m = null;
        if (StringUtils.hasText(archive.getMaterialCode())) {
            m = materialMapper.selectOne(new LambdaQueryWrapper<BaseMaterial>()
                    .eq(BaseMaterial::getMaterialCode, archive.getMaterialCode()));
        }
        String materialName = m != null ? m.getMaterialName() : "";
        String model = m != null ? m.getModel() : "";
        String specification = m != null ? m.getSpecification() : "";
        String materialCode = archive.getMaterialCode();
        String titleLine;
        if (StringUtils.hasText(materialName)) {
            titleLine = StringUtils.hasText(model)
                    ? materialName + " " + model
                    : materialName;
        } else if (StringUtils.hasText(materialCode)) {
            titleLine = materialCode;
        } else {
            titleLine = archive.getBarcodeContent();
        }
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("FTitleLine", titleLine);
        header.put("FNumber", materialCode);
        header.put("FName", materialName);
        header.put("FModel", specification);
        header.put("FBaseUnit", m != null ? m.getUnitCode() : "");
        header.put("FBarCode", archive.getBarcodeContent());
        header.put("FLot", archive.getBatchNo());
        header.put("FSerialNo", archive.getSerialNo());
        header.put("FPackBarCode", archive.getPackBarcode());
        header.put("FBarcodeType", "QR");
        header.put("FPageWidth", archive.getLabelWidthMm());
        header.put("FPageHeight", archive.getLabelHeightMm());
        header.put("FArchiveNo", archive.getArchiveNo());
        header.put("FRuleCode", archive.getRuleCode());
        return PrintDocumentHelper.document(archiveNo, "t_bd_material", null, header, List.of(), null);
    }

    private Map<String, Object> lineEntry(Integer lineNo, String code, String name, String unit) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("FSeq", lineNo);
        e.put("FMaterialCode", code);
        e.put("FMaterialName", name);
        e.put("FUnitID", unit);
        return e;
    }

    private BusinessException notFound(String label) {
        return new BusinessException(ErrorCode.NOT_FOUND, label + "不存在");
    }
}
