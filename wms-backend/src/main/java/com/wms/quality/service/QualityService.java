package com.wms.quality.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wms.auth.security.LoginUser;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.common.result.PageResult;
import com.wms.common.util.OrderNoGenerator;
import com.wms.quality.dto.QcCompleteRequest;
import com.wms.quality.entity.QcOrder;
import com.wms.quality.entity.QcStandard;
import com.wms.quality.mapper.QcOrderMapper;
import com.wms.quality.mapper.QcStandardMapper;
import com.wms.inbound.entity.InboundOrderDetail;
import com.wms.inbound.mapper.InboundOrderDetailMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class QualityService {

    private final QcStandardMapper standardMapper;
    private final QcOrderMapper orderMapper;
    private final InboundOrderDetailMapper inboundDetailMapper;

    private static final String INBOUND_LINE_PREFIX = "INBOUND_LINE:";

    public PageResult<QcStandard> pageStandards(String standardCode, String materialCode,
                                                Integer status, long current, long size) {
        LambdaQueryWrapper<QcStandard> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(standardCode), QcStandard::getStandardCode, standardCode)
                .eq(StringUtils.hasText(materialCode), QcStandard::getMaterialCode, materialCode)
                .eq(status != null, QcStandard::getStatus, status)
                .eq(QcStandard::getDeleted, 0)
                .orderByDesc(QcStandard::getCreateTime);
        Page<QcStandard> page = standardMapper.selectPage(new Page<>(current, size), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public QcStandard getStandard(String standardCode) {
        QcStandard standard = standardMapper.selectOne(new LambdaQueryWrapper<QcStandard>()
                .eq(QcStandard::getStandardCode, standardCode)
                .eq(QcStandard::getDeleted, 0));
        if (standard == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "质检标准不存在", "STANDARD_NOT_FOUND");
        }
        return standard;
    }

    public void createStandard(QcStandard standard) {
        Long count = standardMapper.selectCount(new LambdaQueryWrapper<QcStandard>()
                .eq(QcStandard::getStandardCode, standard.getStandardCode()));
        if (count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "标准编码已存在", "STANDARD_CODE_EXISTS");
        }
        standard.setDeleted(0);
        standard.setCreateTime(LocalDateTime.now());
        if (standard.getStatus() == null) {
            standard.setStatus(1);
        }
        standardMapper.insert(standard);
    }

    public void updateStandard(String standardCode, QcStandard standard) {
        QcStandard existing = getStandard(standardCode);
        standard.setId(existing.getId());
        standard.setStandardCode(standardCode);
        standardMapper.updateById(standard);
    }

    public void deleteStandard(String standardCode) {
        QcStandard existing = getStandard(standardCode);
        standardMapper.deleteById(existing.getId());
    }

    public PageResult<QcOrder> pageOrders(String qcNo, String materialCode, String status,
                                          long current, long size) {
        LambdaQueryWrapper<QcOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(qcNo), QcOrder::getQcNo, qcNo)
                .eq(StringUtils.hasText(materialCode), QcOrder::getMaterialCode, materialCode)
                .eq(StringUtils.hasText(status), QcOrder::getStatus, status)
                .orderByDesc(QcOrder::getCreateTime);
        Page<QcOrder> page = orderMapper.selectPage(new Page<>(current, size), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public QcOrder getOrder(String qcNo) {
        QcOrder order = orderMapper.selectOne(new LambdaQueryWrapper<QcOrder>()
                .eq(QcOrder::getQcNo, qcNo));
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "质检单不存在", "QC_NOT_FOUND");
        }
        return order;
    }

    public void createOrder(QcOrder order) {
        order.setQcNo(OrderNoGenerator.next("QC"));
        order.setStatus("PENDING");
        order.setCreateTime(LocalDateTime.now());
        orderMapper.insert(order);
    }

    public void updateOrder(String qcNo, QcOrder order) {
        QcOrder existing = getOrder(qcNo);
        if (!"PENDING".equals(existing.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "仅待检状态可编辑", "QC_STATUS_CONFLICT");
        }
        order.setId(existing.getId());
        order.setQcNo(qcNo);
        orderMapper.updateById(order);
    }

    public void deleteOrder(String qcNo) {
        QcOrder existing = getOrder(qcNo);
        if (!"PENDING".equals(existing.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "仅待检状态可删除", "QC_STATUS_CONFLICT");
        }
        orderMapper.deleteById(existing.getId());
    }

    public void complete(String qcNo, QcCompleteRequest request) {
        QcOrder order = getOrder(qcNo);
        if (!"PENDING".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "质检单已完成", "QC_STATUS_CONFLICT");
        }
        if (!List.of("PASS", "FAIL").contains(request.getResult())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "质检结果必须为 PASS 或 FAIL");
        }
        LoginUser user = currentUser();
        order.setStatus("COMPLETED");
        order.setResult(request.getResult());
        order.setRemark(request.getRemark());
        order.setInspectorId(String.valueOf(user.getUserId()));
        order.setInspectorName(user.getRealName());
        order.setInspectTime(LocalDateTime.now());
        orderMapper.updateById(order);
        syncInboundQcStatus(order);
    }

    public boolean requiresInspection(String materialCode) {
        if (!StringUtils.hasText(materialCode)) {
            return false;
        }
        Long count = standardMapper.selectCount(new LambdaQueryWrapper<QcStandard>()
                .eq(QcStandard::getMaterialCode, materialCode)
                .eq(QcStandard::getStatus, 1)
                .eq(QcStandard::getDeleted, 0));
        return count != null && count > 0;
    }

    public void createFromInboundReceive(String orderNo, Integer lineNo, String materialCode,
                                         String batchNo, java.math.BigDecimal sampleQty) {
        if (!requiresInspection(materialCode)) {
            return;
        }
        long pending = orderMapper.selectCount(new LambdaQueryWrapper<QcOrder>()
                .eq(QcOrder::getSourceType, "INBOUND")
                .eq(QcOrder::getSourceNo, orderNo)
                .eq(QcOrder::getMaterialCode, materialCode)
                .eq(QcOrder::getStatus, "PENDING"));
        if (pending > 0) {
            return;
        }
        QcOrder order = new QcOrder();
        order.setSourceType("INBOUND");
        order.setSourceNo(orderNo);
        order.setMaterialCode(materialCode);
        order.setBatchNo(batchNo);
        order.setSampleQty(sampleQty);
        order.setRemark(INBOUND_LINE_PREFIX + lineNo);
        createOrder(order);
    }

    private void syncInboundQcStatus(QcOrder order) {
        if (!"INBOUND".equals(order.getSourceType()) || !StringUtils.hasText(order.getSourceNo())) {
            return;
        }
        Integer lineNo = parseInboundLineNo(order.getRemark());
        InboundOrderDetail detail;
        if (lineNo != null) {
            detail = inboundDetailMapper.selectOne(new LambdaQueryWrapper<InboundOrderDetail>()
                    .eq(InboundOrderDetail::getOrderNo, order.getSourceNo())
                    .eq(InboundOrderDetail::getLineNo, lineNo));
        } else {
            detail = inboundDetailMapper.selectOne(new LambdaQueryWrapper<InboundOrderDetail>()
                    .eq(InboundOrderDetail::getOrderNo, order.getSourceNo())
                    .eq(InboundOrderDetail::getMaterialCode, order.getMaterialCode())
                    .last("OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY"));
        }
        if (detail == null) {
            return;
        }
        detail.setQcStatus(order.getResult());
        inboundDetailMapper.updateById(detail);
    }

    private Integer parseInboundLineNo(String remark) {
        if (!StringUtils.hasText(remark) || !remark.startsWith(INBOUND_LINE_PREFIX)) {
            return null;
        }
        try {
            return Integer.parseInt(remark.substring(INBOUND_LINE_PREFIX.length()));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private LoginUser currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return (LoginUser) auth.getPrincipal();
    }
}
