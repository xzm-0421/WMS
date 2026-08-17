package com.wms.inbound.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wms.auth.security.LoginUser;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.common.result.PageResult;
import com.wms.common.util.OrderNoGenerator;
import com.wms.inbound.dto.InboundOrderCreateRequest;
import com.wms.inbound.dto.InboundOrderUpdateRequest;
import com.wms.inbound.dto.InboundOrderVo;
import com.wms.inbound.dto.InboundScanRequest;
import com.wms.inbound.entity.InboundOrder;
import com.wms.inbound.entity.InboundOrderDetail;
import com.wms.inbound.mapper.InboundOrderDetailMapper;
import com.wms.inbound.mapper.InboundOrderMapper;
import com.wms.barcode.service.BarcodeTraceLinkService;
import com.wms.inventory.dto.InventoryChangeCommand;
import com.wms.inventory.dto.InventoryChangeResult;
import com.wms.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class InboundService {

    private static final Set<String> TERMINAL_STATUS = Set.of("COMPLETED", "CLOSED", "CANCELLED");

    private final InboundOrderMapper orderMapper;
    private final InboundOrderDetailMapper detailMapper;
    private final InventoryService inventoryService;
    private final BarcodeTraceLinkService barcodeTraceLinkService;

    public PageResult<InboundOrder> page(String orderNo, String orderType, String warehouseCode,
                                         String status, long current, long size) {
        LambdaQueryWrapper<InboundOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(orderNo), InboundOrder::getOrderNo, orderNo)
                .eq(StringUtils.hasText(orderType), InboundOrder::getOrderType, orderType)
                .eq(StringUtils.hasText(warehouseCode), InboundOrder::getWarehouseCode, warehouseCode)
                .eq(StringUtils.hasText(status), InboundOrder::getStatus, status)
                .eq(InboundOrder::getDeleted, 0)
                .orderByDesc(InboundOrder::getCreateTime);
        Page<InboundOrder> page = orderMapper.selectPage(new Page<>(current, size), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public InboundOrder getOrder(String orderNo) {
        InboundOrder order = orderMapper.selectOne(new LambdaQueryWrapper<InboundOrder>()
                .eq(InboundOrder::getOrderNo, orderNo)
                .eq(InboundOrder::getDeleted, 0));
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "单据不存在", "ORDER_NOT_FOUND");
        }
        return order;
    }

    public InboundOrderVo getOrderVo(String orderNo) {
        InboundOrderVo vo = new InboundOrderVo();
        vo.setOrder(getOrder(orderNo));
        vo.setDetails(getDetails(orderNo));
        return vo;
    }

    public List<InboundOrderDetail> getDetails(String orderNo) {
        return detailMapper.selectList(new LambdaQueryWrapper<InboundOrderDetail>()
                .eq(InboundOrderDetail::getOrderNo, orderNo)
                .orderByAsc(InboundOrderDetail::getLineNo));
    }

    @Transactional(rollbackFor = Exception.class)
    public String createOrder(InboundOrderCreateRequest request) {
        LoginUser user = currentUser();
        String orderNo = OrderNoGenerator.next("RK");
        InboundOrder order = new InboundOrder();
        order.setOrderNo(orderNo);
        order.setOrderType(request.getOrderType());
        order.setWarehouseCode(request.getWarehouseCode());
        order.setSupplierCode(request.getSupplierCode());
        order.setProductionOrderNo(request.getProductionOrderNo());
        order.setSourceOrderNo(request.getSourceOrderNo());
        order.setPlanDate(request.getPlanDate());
        order.setStatus("DRAFT");
        order.setCreatorId(String.valueOf(user.getUserId()));
        order.setCreatorName(user.getRealName());
        order.setRemark(request.getRemark());
        order.setDeleted(0);
        order.setCreateTime(LocalDateTime.now());
        orderMapper.insert(order);
        if (request.getDetails() != null) {
            int lineNo = 1;
            for (InboundOrderDetail detail : request.getDetails()) {
                detail.setOrderNo(orderNo);
                detail.setLineNo(lineNo++);
                if (detail.getReceivedQty() == null) {
                    detail.setReceivedQty(BigDecimal.ZERO);
                }
                if (detail.getLineStatus() == null) {
                    detail.setLineStatus("PENDING");
                }
                detailMapper.insert(detail);
            }
        }
        return orderNo;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateOrder(String orderNo, InboundOrderUpdateRequest request) {
        InboundOrder order = getOrder(orderNo);
        assertDraft(order);
        order.setOrderType(request.getOrderType());
        order.setWarehouseCode(request.getWarehouseCode());
        order.setSupplierCode(request.getSupplierCode());
        order.setProductionOrderNo(request.getProductionOrderNo());
        order.setSourceOrderNo(request.getSourceOrderNo());
        order.setPlanDate(request.getPlanDate());
        order.setRemark(request.getRemark());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteOrder(String orderNo) {
        InboundOrder order = getOrder(orderNo);
        assertDraft(order);
        detailMapper.delete(new LambdaQueryWrapper<InboundOrderDetail>()
                .eq(InboundOrderDetail::getOrderNo, orderNo));
        orderMapper.deleteById(order.getId());
    }

    public void submit(String orderNo) {
        InboundOrder order = getOrder(orderNo);
        if (!"DRAFT".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "仅草稿状态可提交", "ORDER_STATUS_CONFLICT");
        }
        long detailCount = detailMapper.selectCount(new LambdaQueryWrapper<InboundOrderDetail>()
                .eq(InboundOrderDetail::getOrderNo, orderNo));
        if (detailCount == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "入库单明细不能为空");
        }
        order.setStatus("PENDING");
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    public void audit(String orderNo) {
        InboundOrder order = getOrder(orderNo);
        if (!"PENDING".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "仅待审核状态可审核", "ORDER_STATUS_CONFLICT");
        }
        LoginUser user = currentUser();
        order.setStatus("INBOUND");
        order.setAuditorId(String.valueOf(user.getUserId()));
        order.setAuditorName(user.getRealName());
        order.setAuditTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    public void reverseAudit(String orderNo) {
        InboundOrder order = getOrder(orderNo);
        if (!"INBOUND".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "仅入库中且未收货的单据可反审核", "ORDER_STATUS_CONFLICT");
        }
        boolean hasReceived = getDetails(orderNo).stream()
                .anyMatch(d -> d.getReceivedQty() != null && d.getReceivedQty().signum() > 0);
        if (hasReceived) {
            throw new BusinessException(ErrorCode.CONFLICT, "已有收货记录，无法反审核");
        }
        orderMapper.update(null, new LambdaUpdateWrapper<InboundOrder>()
                .eq(InboundOrder::getId, order.getId())
                .set(InboundOrder::getStatus, "PENDING")
                .set(InboundOrder::getAuditorId, null)
                .set(InboundOrder::getAuditorName, null)
                .set(InboundOrder::getAuditTime, null)
                .set(InboundOrder::getUpdateTime, LocalDateTime.now()));
    }

    public void cancel(String orderNo) {
        InboundOrder order = getOrder(orderNo);
        if (TERMINAL_STATUS.contains(order.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "单据状态不允许取消", "ORDER_STATUS_CONFLICT");
        }
        order.setStatus("CANCELLED");
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    public void close(String orderNo) {
        InboundOrder order = getOrder(orderNo);
        if (List.of("DRAFT", "CANCELLED", "CLOSED").contains(order.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "单据状态不允许关闭", "ORDER_STATUS_CONFLICT");
        }
        order.setStatus("CLOSED");
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    @Transactional(rollbackFor = Exception.class)
    public void addDetail(String orderNo, InboundOrderDetail detail) {
        InboundOrder order = getOrder(orderNo);
        assertDraft(order);
        Integer maxLine = detailMapper.selectList(new LambdaQueryWrapper<InboundOrderDetail>()
                        .eq(InboundOrderDetail::getOrderNo, orderNo)
                        .orderByDesc(InboundOrderDetail::getLineNo)
                        .last("OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY"))
                .stream().map(InboundOrderDetail::getLineNo).findFirst().orElse(0);
        detail.setOrderNo(orderNo);
        detail.setLineNo(maxLine + 1);
        if (detail.getReceivedQty() == null) {
            detail.setReceivedQty(BigDecimal.ZERO);
        }
        if (detail.getLineStatus() == null) {
            detail.setLineStatus("PENDING");
        }
        detailMapper.insert(detail);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateDetail(String orderNo, Integer lineNo, InboundOrderDetail detail) {
        InboundOrder order = getOrder(orderNo);
        assertDraft(order);
        InboundOrderDetail existing = getDetail(orderNo, lineNo);
        detail.setId(existing.getId());
        detail.setOrderNo(orderNo);
        detail.setLineNo(lineNo);
        detailMapper.updateById(detail);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteDetail(String orderNo, Integer lineNo) {
        InboundOrder order = getOrder(orderNo);
        assertDraft(order);
        InboundOrderDetail existing = getDetail(orderNo, lineNo);
        detailMapper.deleteById(existing.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> scanReceive(String orderNo, InboundScanRequest req) {
        InboundOrder order = getOrder(orderNo);
        if (TERMINAL_STATUS.contains(order.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "单据状态不允许此操作", "ORDER_STATUS_CONFLICT");
        }
        InboundOrderDetail detail = getDetail(orderNo, req.getLineNo());
        if (!detail.getMaterialCode().equals(req.getMaterialCode())) {
            throw new BusinessException(ErrorCode.CONFLICT, "物料不匹配", "MATERIAL_MISMATCH",
                    Map.of("scannedMaterialCode", req.getMaterialCode(),
                            "expectedMaterialCode", detail.getMaterialCode(),
                            "lineNo", req.getLineNo()));
        }
        BigDecimal received = detail.getReceivedQty() == null ? BigDecimal.ZERO : detail.getReceivedQty();
        BigDecimal newReceived = received.add(req.getQuantity());
        if (newReceived.compareTo(detail.getOrderQty()) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "数量超出订单数量", "QTY_EXCEED_ORDER");
        }

        LoginUser user = currentUser();
        InventoryChangeResult changeResult = inventoryService.increaseWithTransaction(InventoryChangeCommand.builder()
                .transactionType("PURCHASE_IN")
                .warehouseCode(order.getWarehouseCode())
                .locationCode(req.getTargetLocation())
                .materialCode(req.getMaterialCode())
                .batchNo(req.getBatchNo())
                .quantity(req.getQuantity())
                .sourceOrderType("INBOUND")
                .sourceOrderNo(orderNo)
                .sourceOrderLine(req.getLineNo())
                .operatorId(String.valueOf(user.getUserId()))
                .operatorName(user.getRealName())
                .deviceNo(req.getDeviceNo())
                .remark(req.getBarcodeContent())
                .build());

        barcodeTraceLinkService.linkInboundReceive(
                req.getBarcodeContent(),
                orderNo,
                changeResult.getTransactionNo(),
                req.getMaterialCode(),
                req.getBatchNo(),
                null);

        detail.setReceivedQty(newReceived);
        detail.setBatchNo(req.getBatchNo());
        detail.setTargetLocation(req.getTargetLocation());
        detail.setLineStatus(newReceived.compareTo(detail.getOrderQty()) >= 0 ? "COMPLETED" : "PARTIAL");
        // 质检功能已下线：收货行默认通过
        if (detail.getQcStatus() == null) {
            detail.setQcStatus("PASS");
        }
        detailMapper.updateById(detail);

        if ("PENDING".equals(order.getStatus()) || "DRAFT".equals(order.getStatus())) {
            order.setStatus("INBOUND");
        }
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);

        long remaining = detailMapper.selectCount(new LambdaQueryWrapper<InboundOrderDetail>()
                .eq(InboundOrderDetail::getOrderNo, orderNo)
                .ne(InboundOrderDetail::getLineStatus, "COMPLETED"));

        if (remaining == 0) {
            order.setStatus("COMPLETED");
            order.setActualDate(LocalDateTime.now());
            orderMapper.updateById(order);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("orderNo", orderNo);
        result.put("lineNo", req.getLineNo());
        result.put("receivedQty", newReceived);
        result.put("orderQty", detail.getOrderQty());
        result.put("lineStatus", detail.getLineStatus());
        result.put("orderStatus", order.getStatus());
        result.put("allCompleted", remaining == 0);
        result.put("remainingLines", remaining);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> batchScan(String orderNo, com.wms.mobile.dto.InboundBatchScanRequest request) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (com.wms.mobile.dto.InboundBatchScanRequest.InboundScanItem item : request.getItems()) {
            com.wms.inbound.dto.InboundScanRequest scan = new com.wms.inbound.dto.InboundScanRequest();
            scan.setLineNo(item.getLineNo());
            scan.setMaterialCode(item.getMaterialCode());
            scan.setBatchNo(item.getBatchNo());
            scan.setQuantity(item.getQuantity());
            scan.setTargetLocation(item.getTargetLocation());
            scan.setBarcodeContent(item.getBarcodeContent());
            scan.setDeviceNo(request.getDeviceNo());
            results.add(scanReceive(orderNo, scan));
        }
        Map<String, Object> summary = new HashMap<>();
        summary.put("successCount", results.size());
        summary.put("results", results);
        return summary;
    }

    @Transactional(rollbackFor = Exception.class)
    public void completeOrder(String orderNo) {
        InboundOrder order = getOrder(orderNo);
        if (TERMINAL_STATUS.contains(order.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "单据已结束", "ORDER_STATUS_CONFLICT");
        }
        order.setStatus("COMPLETED");
        order.setActualDate(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    private InboundOrderDetail getDetail(String orderNo, Integer lineNo) {
        InboundOrderDetail detail = detailMapper.selectOne(new LambdaQueryWrapper<InboundOrderDetail>()
                .eq(InboundOrderDetail::getOrderNo, orderNo)
                .eq(InboundOrderDetail::getLineNo, lineNo));
        if (detail == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "明细行不存在");
        }
        return detail;
    }

    private void assertDraft(InboundOrder order) {
        if (!"DRAFT".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "仅草稿状态可编辑", "ORDER_STATUS_CONFLICT");
        }
    }

    private LoginUser currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return (LoginUser) auth.getPrincipal();
    }
}
