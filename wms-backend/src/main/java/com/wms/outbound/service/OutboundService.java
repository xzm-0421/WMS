package com.wms.outbound.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wms.auth.security.LoginUser;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.common.result.PageResult;
import com.wms.common.util.OrderNoGenerator;
import com.wms.barcode.service.BarcodeTraceLinkService;
import com.wms.inventory.dto.InventoryChangeCommand;
import com.wms.inventory.dto.InventoryChangeResult;
import com.wms.inventory.entity.Inventory;
import com.wms.inventory.mapper.InventoryMapper;
import com.wms.inventory.service.InventoryService;
import com.wms.outbound.dto.*;
import com.wms.outbound.entity.OutboundOrder;
import com.wms.outbound.entity.OutboundOrderDetail;
import com.wms.outbound.mapper.OutboundOrderDetailMapper;
import com.wms.outbound.mapper.OutboundOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
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
public class OutboundService {

    private static final Set<String> TERMINAL_STATUS = Set.of("COMPLETED", "CLOSED", "CANCELLED");

    private final OutboundOrderMapper orderMapper;
    private final OutboundOrderDetailMapper detailMapper;
    private final InventoryMapper inventoryMapper;
    private final InventoryService inventoryService;
    private final BarcodeTraceLinkService barcodeTraceLinkService;

    public PageResult<OutboundOrder> page(String orderNo, String orderType, String warehouseCode,
                                          String status, long current, long size) {
        LambdaQueryWrapper<OutboundOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(orderNo), OutboundOrder::getOrderNo, orderNo)
                .eq(StringUtils.hasText(orderType), OutboundOrder::getOrderType, orderType)
                .eq(StringUtils.hasText(warehouseCode), OutboundOrder::getWarehouseCode, warehouseCode)
                .eq(StringUtils.hasText(status), OutboundOrder::getStatus, status)
                .eq(OutboundOrder::getDeleted, 0)
                .orderByDesc(OutboundOrder::getCreateTime);
        Page<OutboundOrder> page = orderMapper.selectPage(new Page<>(current, size), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public OutboundOrderVo getOrderVo(String orderNo) {
        OutboundOrderVo vo = new OutboundOrderVo();
        vo.setOrder(getOrder(orderNo));
        vo.setDetails(getDetails(orderNo));
        return vo;
    }

    public OutboundOrder getOrder(String orderNo) {
        OutboundOrder order = orderMapper.selectOne(new LambdaQueryWrapper<OutboundOrder>()
                .eq(OutboundOrder::getOrderNo, orderNo)
                .eq(OutboundOrder::getDeleted, 0));
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "单据不存在", "ORDER_NOT_FOUND");
        }
        return order;
    }

    public List<OutboundOrderDetail> getDetails(String orderNo) {
        return detailMapper.selectList(new LambdaQueryWrapper<OutboundOrderDetail>()
                .eq(OutboundOrderDetail::getOrderNo, orderNo)
                .orderByAsc(OutboundOrderDetail::getLineNo));
    }

    @Transactional(rollbackFor = Exception.class)
    public String createOrder(OutboundOrderCreateRequest request) {
        LoginUser user = currentUser();
        String orderNo = OrderNoGenerator.next("CK");
        OutboundOrder order = new OutboundOrder();
        order.setOrderNo(orderNo);
        order.setOrderType(request.getOrderType());
        order.setWarehouseCode(request.getWarehouseCode());
        order.setProductionOrderNo(request.getProductionOrderNo());
        order.setCustomerCode(request.getCustomerCode());
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
            for (OutboundOrderDetail detail : request.getDetails()) {
                detail.setOrderNo(orderNo);
                detail.setLineNo(lineNo++);
                if (detail.getIssuedQty() == null) {
                    detail.setIssuedQty(BigDecimal.ZERO);
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
    public void updateOrder(String orderNo, OutboundOrderUpdateRequest request) {
        OutboundOrder order = getOrder(orderNo);
        assertDraft(order);
        order.setOrderType(request.getOrderType());
        order.setWarehouseCode(request.getWarehouseCode());
        order.setProductionOrderNo(request.getProductionOrderNo());
        order.setCustomerCode(request.getCustomerCode());
        order.setPlanDate(request.getPlanDate());
        order.setRemark(request.getRemark());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteOrder(String orderNo) {
        OutboundOrder order = getOrder(orderNo);
        assertDraft(order);
        detailMapper.delete(new LambdaQueryWrapper<OutboundOrderDetail>()
                .eq(OutboundOrderDetail::getOrderNo, orderNo));
        orderMapper.deleteById(order.getId());
    }

    public void submit(String orderNo) {
        OutboundOrder order = getOrder(orderNo);
        if (!"DRAFT".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "仅草稿状态可提交", "ORDER_STATUS_CONFLICT");
        }
        long detailCount = detailMapper.selectCount(new LambdaQueryWrapper<OutboundOrderDetail>()
                .eq(OutboundOrderDetail::getOrderNo, orderNo));
        if (detailCount == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "出库单明细不能为空");
        }
        order.setStatus("PENDING");
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    public void audit(String orderNo) {
        OutboundOrder order = getOrder(orderNo);
        if (!"PENDING".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "仅待审核状态可审核", "ORDER_STATUS_CONFLICT");
        }
        LoginUser user = currentUser();
        order.setStatus("PICKING");
        order.setAuditorId(String.valueOf(user.getUserId()));
        order.setAuditorName(user.getRealName());
        order.setAuditTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    public void reverseAudit(String orderNo) {
        OutboundOrder order = getOrder(orderNo);
        if (!"PICKING".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "仅拣货中且未出库的单据可反审核", "ORDER_STATUS_CONFLICT");
        }
        boolean hasIssued = getDetails(orderNo).stream()
                .anyMatch(d -> d.getIssuedQty() != null && d.getIssuedQty().signum() > 0);
        if (hasIssued) {
            throw new BusinessException(ErrorCode.CONFLICT, "已有出库记录，无法反审核");
        }
        orderMapper.update(null, new LambdaUpdateWrapper<OutboundOrder>()
                .eq(OutboundOrder::getId, order.getId())
                .set(OutboundOrder::getStatus, "PENDING")
                .set(OutboundOrder::getAuditorId, null)
                .set(OutboundOrder::getAuditorName, null)
                .set(OutboundOrder::getAuditTime, null)
                .set(OutboundOrder::getUpdateTime, LocalDateTime.now()));
    }

    public void cancel(String orderNo) {
        OutboundOrder order = getOrder(orderNo);
        if (TERMINAL_STATUS.contains(order.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "单据状态不允许取消", "ORDER_STATUS_CONFLICT");
        }
        order.setStatus("CANCELLED");
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    public void close(String orderNo) {
        OutboundOrder order = getOrder(orderNo);
        if (List.of("DRAFT", "CANCELLED", "CLOSED").contains(order.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "单据状态不允许关闭", "ORDER_STATUS_CONFLICT");
        }
        order.setStatus("CLOSED");
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    public List<RecommendLocationDto> recommendLocations(String orderNo, Integer lineNo) {
        OutboundOrder order = getOrder(orderNo);
        List<OutboundOrderDetail> details;
        if (lineNo != null) {
            details = List.of(getDetail(orderNo, lineNo));
        } else {
            details = detailMapper.selectList(new LambdaQueryWrapper<OutboundOrderDetail>()
                    .eq(OutboundOrderDetail::getOrderNo, orderNo)
                    .orderByAsc(OutboundOrderDetail::getLineNo));
        }
        if (details.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "出库单无明细行", "DETAIL_NOT_FOUND");
        }
        List<RecommendLocationDto> result = new ArrayList<>();
        for (OutboundOrderDetail detail : details) {
            List<Inventory> inventories = inventoryMapper.selectList(new LambdaQueryWrapper<Inventory>()
                    .eq(Inventory::getWarehouseCode, order.getWarehouseCode())
                    .eq(Inventory::getMaterialCode, detail.getMaterialCode())
                    .gt(Inventory::getAvailableQty, 0)
                    .orderByAsc(Inventory::getInboundDate));
            for (Inventory inv : inventories) {
                RecommendLocationDto dto = new RecommendLocationDto();
                BeanUtils.copyProperties(inv, dto);
                result.add(dto);
            }
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> scanIssue(String orderNo, OutboundScanRequest req) {
        OutboundOrder order = getOrder(orderNo);
        if (TERMINAL_STATUS.contains(order.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "单据状态不允许此操作", "ORDER_STATUS_CONFLICT");
        }
        if (!List.of("PICKING", "PENDING", "OUTBOUND").contains(order.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "单据状态不允许出库", "ORDER_STATUS_CONFLICT");
        }
        OutboundOrderDetail detail = getDetail(orderNo, req.getLineNo());
        if (!detail.getMaterialCode().equals(req.getMaterialCode())) {
            throw new BusinessException(ErrorCode.CONFLICT, "物料不匹配", "MATERIAL_MISMATCH");
        }
        BigDecimal issued = detail.getIssuedQty() == null ? BigDecimal.ZERO : detail.getIssuedQty();
        BigDecimal newIssued = issued.add(req.getQuantity());
        if (newIssued.compareTo(detail.getDemandQty()) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "数量超出需求数量", "QTY_EXCEED_ORDER");
        }

        LoginUser user = currentUser();
        InventoryChangeResult changeResult = inventoryService.decreaseWithTransaction(InventoryChangeCommand.builder()
                .transactionType("SALES_OUT")
                .warehouseCode(order.getWarehouseCode())
                .locationCode(req.getSourceLocation())
                .materialCode(req.getMaterialCode())
                .batchNo(req.getBatchNo())
                .quantity(req.getQuantity())
                .sourceOrderType("OUTBOUND")
                .sourceOrderNo(orderNo)
                .sourceOrderLine(req.getLineNo())
                .operatorId(String.valueOf(user.getUserId()))
                .operatorName(user.getRealName())
                .deviceNo(req.getDeviceNo())
                .remark(req.getBarcodeContent())
                .build());

        barcodeTraceLinkService.linkOutboundIssue(
                req.getBarcodeContent(),
                orderNo,
                changeResult.getTransactionNo(),
                req.getMaterialCode(),
                req.getBatchNo(),
                null);

        detail.setIssuedQty(newIssued);
        detail.setBatchNo(req.getBatchNo());
        detail.setSourceLocation(req.getSourceLocation());
        detail.setLineStatus(newIssued.compareTo(detail.getDemandQty()) >= 0 ? "COMPLETED" : "PARTIAL");
        detailMapper.updateById(detail);

        if (!"OUTBOUND".equals(order.getStatus())) {
            order.setStatus("OUTBOUND");
        }
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);

        long remaining = detailMapper.selectCount(new LambdaQueryWrapper<OutboundOrderDetail>()
                .eq(OutboundOrderDetail::getOrderNo, orderNo)
                .ne(OutboundOrderDetail::getLineStatus, "COMPLETED"));

        if (remaining == 0) {
            order.setStatus("COMPLETED");
            order.setActualDate(LocalDateTime.now());
            orderMapper.updateById(order);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("orderNo", orderNo);
        result.put("lineNo", req.getLineNo());
        result.put("issuedQty", newIssued);
        result.put("demandQty", detail.getDemandQty());
        result.put("lineStatus", detail.getLineStatus());
        result.put("orderStatus", order.getStatus());
        result.put("allCompleted", remaining == 0);
        result.put("remainingLines", remaining);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void completeOrder(String orderNo) {
        OutboundOrder order = getOrder(orderNo);
        if (TERMINAL_STATUS.contains(order.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "单据已结束", "ORDER_STATUS_CONFLICT");
        }
        order.setStatus("COMPLETED");
        order.setActualDate(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    private OutboundOrderDetail getDetail(String orderNo, Integer lineNo) {
        OutboundOrderDetail detail = detailMapper.selectOne(new LambdaQueryWrapper<OutboundOrderDetail>()
                .eq(OutboundOrderDetail::getOrderNo, orderNo)
                .eq(OutboundOrderDetail::getLineNo, lineNo));
        if (detail == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "明细行不存在");
        }
        return detail;
    }

    private void assertDraft(OutboundOrder order) {
        if (!"DRAFT".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "仅草稿状态可编辑", "ORDER_STATUS_CONFLICT");
        }
    }

    private LoginUser currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return (LoginUser) auth.getPrincipal();
    }
}
