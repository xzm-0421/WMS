package com.wms.incoming.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.common.result.PageResult;
import com.wms.common.util.OrderNoGenerator;
import com.wms.incoming.dto.PurchaseOrderCreateRequest;
import com.wms.incoming.dto.PurchaseOrderVo;
import com.wms.incoming.entity.PurchaseOrder;
import com.wms.incoming.entity.PurchaseOrderLine;
import com.wms.incoming.mapper.PurchaseOrderLineMapper;
import com.wms.incoming.mapper.PurchaseOrderMapper;
import com.wms.system.service.AuditTrailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private final PurchaseOrderMapper orderMapper;
    private final PurchaseOrderLineMapper lineMapper;
    private final AuditTrailService auditTrailService;

    public PageResult<PurchaseOrder> page(String orderNo, String supplierCode, String status,
                                          long current, long size) {
        LambdaQueryWrapper<PurchaseOrder> w = new LambdaQueryWrapper<>();
        w.like(StringUtils.hasText(orderNo), PurchaseOrder::getOrderNo, orderNo)
                .eq(StringUtils.hasText(supplierCode), PurchaseOrder::getSupplierCode, supplierCode)
                .eq(StringUtils.hasText(status), PurchaseOrder::getStatus, status)
                .eq(PurchaseOrder::getDeleted, 0)
                .orderByDesc(PurchaseOrder::getCreateTime);
        Page<PurchaseOrder> page = orderMapper.selectPage(new Page<>(current, size), w);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public PurchaseOrderVo getVo(String orderNo) {
        PurchaseOrder order = getByNo(orderNo);
        List<PurchaseOrderLine> lines = lineMapper.selectList(new LambdaQueryWrapper<PurchaseOrderLine>()
                .eq(PurchaseOrderLine::getOrderNo, orderNo)
                .orderByAsc(PurchaseOrderLine::getLineNo));
        PurchaseOrderVo vo = new PurchaseOrderVo();
        vo.setOrder(order);
        vo.setLines(lines);
        if (order.getTotalQty() != null && order.getTotalQty().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal received = order.getReceivedQty() != null ? order.getReceivedQty() : BigDecimal.ZERO;
            vo.setProgressPercent(received.multiply(BigDecimal.valueOf(100))
                    .divide(order.getTotalQty(), 2, RoundingMode.HALF_UP));
        } else {
            vo.setProgressPercent(BigDecimal.ZERO);
        }
        return vo;
    }

    @Transactional
    public String create(PurchaseOrderCreateRequest request, String operatorName) {
        String orderNo = OrderNoGenerator.next("PO");
        BigDecimal totalQty = BigDecimal.ZERO;
        int lineNo = 1;
        if (request.getLines() != null) {
            for (PurchaseOrderCreateRequest.LineItem item : request.getLines()) {
                PurchaseOrderLine line = new PurchaseOrderLine();
                line.setOrderNo(orderNo);
                line.setLineNo(lineNo++);
                line.setMaterialCode(item.getMaterialCode());
                line.setMaterialName(item.getMaterialName());
                line.setUnitCode(item.getUnitCode());
                line.setOrderQty(item.getOrderQty());
                line.setReceivedQty(BigDecimal.ZERO);
                line.setBatchNo(item.getBatchNo());
                line.setLineStatus("OPEN");
                lineMapper.insert(line);
                totalQty = totalQty.add(item.getOrderQty());
            }
        }
        PurchaseOrder order = new PurchaseOrder();
        order.setOrderNo(orderNo);
        order.setSupplierCode(request.getSupplierCode());
        order.setWarehouseCode(request.getWarehouseCode());
        order.setPlanArriveDate(request.getPlanArriveDate());
        order.setStatus("OPEN");
        order.setTotalQty(totalQty);
        order.setReceivedQty(BigDecimal.ZERO);
        order.setCreatorName(operatorName);
        order.setRemark(request.getRemark());
        order.setCreateTime(LocalDateTime.now());
        order.setDeleted(0);
        orderMapper.insert(order);
        auditTrailService.log("PURCHASE_ORDER", orderNo, "CREATE", operatorName, null);
        return orderNo;
    }

    @Transactional
    public void delete(String orderNo) {
        PurchaseOrder order = getByNo(orderNo);
        if (!"DRAFT".equals(order.getStatus()) && !"OPEN".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅草稿/待收货状态可删除", "PO_STATUS_INVALID");
        }
        lineMapper.delete(new LambdaQueryWrapper<PurchaseOrderLine>()
                .eq(PurchaseOrderLine::getOrderNo, orderNo));
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getOrderNo, orderNo)
                .eq(PurchaseOrder::getDeleted, 0)
                .set(PurchaseOrder::getDeleted, 1)
                .set(PurchaseOrder::getUpdateTime, LocalDateTime.now()));
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "采购订单不存在或已删除", "PO_NOT_FOUND");
        }
        auditTrailService.log("PURCHASE_ORDER", orderNo, "DELETE", "system", null);
    }

    public PurchaseOrder getByNo(String orderNo) {
        PurchaseOrder order = orderMapper.selectOne(new LambdaQueryWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getOrderNo, orderNo)
                .eq(PurchaseOrder::getDeleted, 0));
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "采购订单不存在", "PO_NOT_FOUND");
        }
        return order;
    }

    @Transactional
    public void addReceivedQty(String orderNo, String materialCode, BigDecimal qty) {
        PurchaseOrder order = getByNo(orderNo);
        PurchaseOrderLine line = lineMapper.selectOne(new LambdaQueryWrapper<PurchaseOrderLine>()
                .eq(PurchaseOrderLine::getOrderNo, orderNo)
                .eq(PurchaseOrderLine::getMaterialCode, materialCode));
        if (line == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "采购订单明细不存在", "PO_LINE_NOT_FOUND");
        }
        BigDecimal newReceived = line.getReceivedQty().add(qty);
        if (newReceived.compareTo(line.getOrderQty()) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "收货数量超过订单数量", "OVER_RECEIVE");
        }
        line.setReceivedQty(newReceived);
        if (newReceived.compareTo(line.getOrderQty()) >= 0) {
            line.setLineStatus("CLOSED");
        }
        lineMapper.updateById(line);
        order.setReceivedQty(order.getReceivedQty().add(qty));
        if (order.getReceivedQty().compareTo(order.getTotalQty()) >= 0) {
            order.setStatus("COMPLETED");
        } else {
            order.setStatus("PARTIAL");
        }
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }
}
