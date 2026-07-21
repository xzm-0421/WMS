package com.wms.production.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.common.result.PageResult;
import com.wms.common.util.OrderNoGenerator;
import com.wms.production.dto.BomCreateRequest;
import com.wms.production.dto.BomVo;
import com.wms.production.entity.BomDetail;
import com.wms.production.entity.BomHeader;
import com.wms.production.entity.ProductionOrder;
import com.wms.production.mapper.BomDetailMapper;
import com.wms.production.mapper.BomHeaderMapper;
import com.wms.production.mapper.ProductionOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ProductionService {

    private final ProductionOrderMapper orderMapper;
    private final BomHeaderMapper bomHeaderMapper;
    private final BomDetailMapper bomDetailMapper;

    public PageResult<ProductionOrder> pageOrders(String orderNo, String productCode, String status,
                                                  long current, long size) {
        LambdaQueryWrapper<ProductionOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(orderNo), ProductionOrder::getOrderNo, orderNo)
                .eq(StringUtils.hasText(productCode), ProductionOrder::getProductCode, productCode)
                .eq(StringUtils.hasText(status), ProductionOrder::getStatus, status)
                .eq(ProductionOrder::getDeleted, 0)
                .orderByDesc(ProductionOrder::getCreateTime);
        Page<ProductionOrder> page = orderMapper.selectPage(new Page<>(current, size), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public ProductionOrder getOrder(String orderNo) {
        ProductionOrder order = orderMapper.selectOne(new LambdaQueryWrapper<ProductionOrder>()
                .eq(ProductionOrder::getOrderNo, orderNo)
                .eq(ProductionOrder::getDeleted, 0));
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "生产订单不存在", "ORDER_NOT_FOUND");
        }
        return order;
    }

    public void createOrder(ProductionOrder order) {
        order.setOrderNo(OrderNoGenerator.next("MO"));
        order.setStatus("PLANNED");
        order.setDeleted(0);
        order.setCreateTime(LocalDateTime.now());
        orderMapper.insert(order);
    }

    public void updateOrder(String orderNo, ProductionOrder order) {
        ProductionOrder existing = getOrder(orderNo);
        order.setId(existing.getId());
        order.setOrderNo(orderNo);
        orderMapper.updateById(order);
    }

    public void deleteOrder(String orderNo) {
        ProductionOrder existing = getOrder(orderNo);
        orderMapper.deleteById(existing.getId());
    }

    public void completeOrder(String orderNo) {
        ProductionOrder order = getOrder(orderNo);
        if ("COMPLETED".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "订单已完成", "ORDER_STATUS_CONFLICT");
        }
        order.setStatus("COMPLETED");
        order.setCompletedQty(order.getPlanQty() != null ? order.getPlanQty() : java.math.BigDecimal.ZERO);
        orderMapper.updateById(order);
    }

    public PageResult<BomHeader> pageBom(String bomCode, String productCode, long current, long size) {
        LambdaQueryWrapper<BomHeader> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(bomCode), BomHeader::getBomCode, bomCode)
                .eq(StringUtils.hasText(productCode), BomHeader::getProductCode, productCode)
                .orderByDesc(BomHeader::getCreateTime);
        Page<BomHeader> page = bomHeaderMapper.selectPage(new Page<>(current, size), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public BomVo getBom(String bomCode) {
        BomHeader header = bomHeaderMapper.selectOne(new LambdaQueryWrapper<BomHeader>()
                .eq(BomHeader::getBomCode, bomCode));
        if (header == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "BOM不存在", "BOM_NOT_FOUND");
        }
        BomVo vo = new BomVo();
        vo.setHeader(header);
        vo.setDetails(bomDetailMapper.selectList(new LambdaQueryWrapper<BomDetail>()
                .eq(BomDetail::getBomCode, bomCode)
                .orderByAsc(BomDetail::getLineNo)));
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public void createBom(BomCreateRequest request) {
        BomHeader header = request.getHeader();
        Long count = bomHeaderMapper.selectCount(new LambdaQueryWrapper<BomHeader>()
                .eq(BomHeader::getBomCode, header.getBomCode()));
        if (count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "BOM编码已存在", "BOM_CODE_EXISTS");
        }
        header.setCreateTime(LocalDateTime.now());
        if (header.getStatus() == null) {
            header.setStatus(1);
        }
        if (header.getVersionNo() == null) {
            header.setVersionNo("V1");
        }
        bomHeaderMapper.insert(header);
        if (request.getDetails() != null) {
            int lineNo = 1;
            for (BomDetail detail : request.getDetails()) {
                detail.setBomCode(header.getBomCode());
                detail.setLineNo(lineNo++);
                bomDetailMapper.insert(detail);
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateBom(String bomCode, BomCreateRequest request) {
        BomHeader existing = getBom(bomCode).getHeader();
        BomHeader header = request.getHeader();
        header.setId(existing.getId());
        header.setBomCode(bomCode);
        bomHeaderMapper.updateById(header);
        bomDetailMapper.delete(new LambdaQueryWrapper<BomDetail>().eq(BomDetail::getBomCode, bomCode));
        if (request.getDetails() != null) {
            int lineNo = 1;
            for (BomDetail detail : request.getDetails()) {
                detail.setBomCode(bomCode);
                detail.setLineNo(lineNo++);
                bomDetailMapper.insert(detail);
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteBom(String bomCode) {
        getBom(bomCode);
        bomDetailMapper.delete(new LambdaQueryWrapper<BomDetail>().eq(BomDetail::getBomCode, bomCode));
        bomHeaderMapper.delete(new LambdaQueryWrapper<BomHeader>().eq(BomHeader::getBomCode, bomCode));
    }
}
