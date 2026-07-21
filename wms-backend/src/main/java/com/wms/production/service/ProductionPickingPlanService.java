package com.wms.production.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.picking.dto.PrepNoticeCreateRequest;
import com.wms.picking.service.PrepNoticeService;
import com.wms.production.dto.BomVo;
import com.wms.production.entity.BomDetail;
import com.wms.integration.kingdee.KingdeeBomService;
import com.wms.production.entity.ProductionOrder;
import com.wms.production.mapper.ProductionOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 根据生产订单 + BOM 展开生成备料通知单（生产计划领料）
 */
@Service
@RequiredArgsConstructor
public class ProductionPickingPlanService {

    private final ProductionOrderMapper orderMapper;
    private final KingdeeBomService kingdeeBomService;
    private final PrepNoticeService prepNoticeService;

    @Transactional
    public String generateFromProductionOrder(String orderNo, String operatorName) {
        ProductionOrder order = orderMapper.selectOne(new LambdaQueryWrapper<ProductionOrder>()
                .eq(ProductionOrder::getOrderNo, orderNo)
                .eq(ProductionOrder::getDeleted, 0));
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "生产订单不存在");
        }
        if (!"PLANNED".equals(order.getStatus()) && !"IN_PROGRESS".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅计划或进行中的生产订单可生成领料计划");
        }
        BomVo bomVo = kingdeeBomService.getEffectiveBomByProductCode(order.getProductCode());
        List<BomDetail> details = bomVo.getDetails();

        BigDecimal planQty = order.getPlanQty() == null ? BigDecimal.ONE : order.getPlanQty();
        PrepNoticeCreateRequest request = new PrepNoticeCreateRequest();
        request.setProductionPlanNo(orderNo);
        request.setWarehouseCode(StringUtils.hasText(order.getWarehouseCode()) ? order.getWarehouseCode() : "WH01");
        if (order.getPlanStart() != null) {
            request.setDemandTime(order.getPlanStart().atStartOfDay());
        } else {
            request.setDemandTime(LocalDateTime.now());
        }

        List<PrepNoticeCreateRequest.LineItem> lines = new ArrayList<>();
        for (BomDetail detail : details) {
            PrepNoticeCreateRequest.LineItem item = new PrepNoticeCreateRequest.LineItem();
            item.setMaterialCode(detail.getMaterialCode());
            item.setMaterialName(detail.getMaterialName());
            BigDecimal per = detail.getQtyPer() == null ? BigDecimal.ZERO : detail.getQtyPer();
            item.setDemandQty(per.multiply(planQty));
            lines.add(item);
        }
        request.setLines(lines);

        String noticeNo = prepNoticeService.create(request, operatorName);
        if ("PLANNED".equals(order.getStatus())) {
            order.setStatus("IN_PROGRESS");
            orderMapper.updateById(order);
        }
        return noticeNo;
    }
}
