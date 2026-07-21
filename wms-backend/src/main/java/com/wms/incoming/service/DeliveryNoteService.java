package com.wms.incoming.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.common.result.PageResult;
import com.wms.common.util.OrderNoGenerator;
import com.wms.inbound.dto.InboundOrderCreateRequest;
import com.wms.inbound.entity.InboundOrderDetail;
import com.wms.inbound.service.InboundService;
import com.wms.incoming.dto.DeliveryNoteCreateRequest;
import com.wms.incoming.entity.DeliveryNote;
import com.wms.incoming.entity.DeliveryNoteLine;
import com.wms.incoming.entity.PurchaseOrder;
import com.wms.incoming.entity.PurchaseOrderLine;
import com.wms.incoming.mapper.DeliveryNoteLineMapper;
import com.wms.incoming.mapper.DeliveryNoteMapper;
import com.wms.incoming.mapper.PurchaseOrderLineMapper;
import com.wms.system.service.AuditTrailService;
import com.wms.system.service.WmsBusinessRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DeliveryNoteService {

    private final DeliveryNoteMapper noteMapper;
    private final DeliveryNoteLineMapper lineMapper;
    private final PurchaseOrderService purchaseOrderService;
    private final PurchaseOrderLineMapper poLineMapper;
    private final InboundService inboundService;
    private final WmsBusinessRuleService ruleService;
    private final AuditTrailService auditTrailService;

    public PageResult<DeliveryNote> page(String deliveryNo, String purchaseOrderNo, String status,
                                         long current, long size) {
        LambdaQueryWrapper<DeliveryNote> w = new LambdaQueryWrapper<>();
        w.like(StringUtils.hasText(deliveryNo), DeliveryNote::getDeliveryNo, deliveryNo)
                .eq(StringUtils.hasText(purchaseOrderNo), DeliveryNote::getPurchaseOrderNo, purchaseOrderNo)
                .eq(StringUtils.hasText(status), DeliveryNote::getStatus, status)
                .eq(DeliveryNote::getDeleted, 0)
                .orderByDesc(DeliveryNote::getCreateTime);
        Page<DeliveryNote> page = noteMapper.selectPage(new Page<>(current, size), w);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public Map<String, Object> detail(String deliveryNo) {
        DeliveryNote note = getByNo(deliveryNo);
        List<DeliveryNoteLine> lines = lineMapper.selectList(new LambdaQueryWrapper<DeliveryNoteLine>()
                .eq(DeliveryNoteLine::getDeliveryNo, deliveryNo)
                .orderByAsc(DeliveryNoteLine::getLineNo));
        Map<String, Object> data = new HashMap<>();
        data.put("note", note);
        data.put("lines", lines);
        return data;
    }

    @Transactional
    public String createFromPurchaseOrder(DeliveryNoteCreateRequest request, String operatorName) {
        PurchaseOrder po = purchaseOrderService.getByNo(request.getPurchaseOrderNo());
        String deliveryNo = OrderNoGenerator.next("DN");
        boolean hasDiff = false;
        int lineNo = 1;
        if (request.getLines() != null) {
            for (DeliveryNoteCreateRequest.LineItem item : request.getLines()) {
                PurchaseOrderLine poLine = poLineMapper.selectOne(new LambdaQueryWrapper<PurchaseOrderLine>()
                        .eq(PurchaseOrderLine::getOrderNo, po.getOrderNo())
                        .eq(PurchaseOrderLine::getMaterialCode, item.getMaterialCode()));
                BigDecimal planQty = poLine != null ? poLine.getOrderQty() : BigDecimal.ZERO;
                BigDecimal actual = item.getActualQty() != null ? item.getActualQty() : BigDecimal.ZERO;
                if (poLine != null) {
                    ruleService.validateOverReceive(planQty, poLine.getReceivedQty(), actual);
                }
                BigDecimal diff = actual.subtract(planQty);
                if (diff.compareTo(BigDecimal.ZERO) != 0) {
                    hasDiff = true;
                }
                DeliveryNoteLine line = new DeliveryNoteLine();
                line.setDeliveryNo(deliveryNo);
                line.setLineNo(lineNo++);
                line.setMaterialCode(item.getMaterialCode());
                line.setPlanQty(planQty);
                line.setActualQty(actual);
                line.setDiffQty(diff);
                lineMapper.insert(line);
            }
        }
        DeliveryNote note = new DeliveryNote();
        note.setDeliveryNo(deliveryNo);
        note.setPurchaseOrderNo(po.getOrderNo());
        note.setSupplierCode(StringUtils.hasText(request.getSupplierCode()) ? request.getSupplierCode() : po.getSupplierCode());
        note.setDeliveryDate(LocalDateTime.now());
        note.setStatus("PENDING");
        note.setDiffFlag(hasDiff ? 1 : 0);
        note.setRemark(request.getRemark());
        note.setCreateTime(LocalDateTime.now());
        note.setDeleted(0);
        noteMapper.insert(note);
        auditTrailService.log("DELIVERY_NOTE", deliveryNo, "CREATE", operatorName, po.getOrderNo());
        return deliveryNo;
    }

    @Transactional
    public String generateInbound(String deliveryNo, String operatorName) {
        DeliveryNote note = getByNo(deliveryNo);
        List<DeliveryNoteLine> lines = lineMapper.selectList(new LambdaQueryWrapper<DeliveryNoteLine>()
                .eq(DeliveryNoteLine::getDeliveryNo, deliveryNo));
        PurchaseOrder po = purchaseOrderService.getByNo(note.getPurchaseOrderNo());
        InboundOrderCreateRequest req = new InboundOrderCreateRequest();
        req.setOrderType("PURCHASE");
        req.setWarehouseCode(po.getWarehouseCode());
        req.setSupplierCode(note.getSupplierCode());
        req.setSourceOrderNo(deliveryNo);
        List<InboundOrderDetail> details = new ArrayList<>();
        for (DeliveryNoteLine line : lines) {
            if (line.getActualQty().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            InboundOrderDetail d = new InboundOrderDetail();
            d.setMaterialCode(line.getMaterialCode());
            d.setOrderQty(line.getActualQty());
            d.setReceivedQty(BigDecimal.ZERO);
            d.setTargetLocation("");
            details.add(d);
            purchaseOrderService.addReceivedQty(po.getOrderNo(), line.getMaterialCode(), line.getActualQty());
        }
        req.setDetails(details);
        String inboundNo = inboundService.createOrder(req);
        note.setStatus("INBOUND_CREATED");
        noteMapper.updateById(note);
        auditTrailService.log("DELIVERY_NOTE", deliveryNo, "GENERATE_INBOUND", operatorName, inboundNo);
        return inboundNo;
    }

    private DeliveryNote getByNo(String deliveryNo) {
        DeliveryNote note = noteMapper.selectOne(new LambdaQueryWrapper<DeliveryNote>()
                .eq(DeliveryNote::getDeliveryNo, deliveryNo)
                .eq(DeliveryNote::getDeleted, 0));
        if (note == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "送货单不存在", "DN_NOT_FOUND");
        }
        return note;
    }
}
