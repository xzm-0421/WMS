package com.wms.picking.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.picking.entity.PrepNotice;
import com.wms.picking.mapper.PrepNoticeMapper;
import com.wms.production.entity.ProductionOrder;
import com.wms.production.mapper.ProductionOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

/**
 * 拣配领料完成后同步备料通知与生产订单状态。
 */
@Service
@RequiredArgsConstructor
public class PickingFlowSyncService {

    private final PrepNoticeMapper noticeMapper;
    private final ProductionOrderMapper productionOrderMapper;

    @Transactional(rollbackFor = Exception.class)
    public void afterPickIssuePickedUp(String noticeNo) {
        if (!StringUtils.hasText(noticeNo)) {
            return;
        }
        PrepNotice notice = noticeMapper.selectOne(new LambdaQueryWrapper<PrepNotice>()
                .eq(PrepNotice::getNoticeNo, noticeNo)
                .eq(PrepNotice::getDeleted, 0));
        if (notice == null) {
            return;
        }
        notice.setStatus("COMPLETED");
        noticeMapper.updateById(notice);
        syncProductionOrder(notice.getProductionPlanNo());
    }

    private void syncProductionOrder(String productionPlanNo) {
        if (!StringUtils.hasText(productionPlanNo)) {
            return;
        }
        ProductionOrder order = productionOrderMapper.selectOne(new LambdaQueryWrapper<ProductionOrder>()
                .eq(ProductionOrder::getOrderNo, productionPlanNo)
                .eq(ProductionOrder::getDeleted, 0));
        if (order == null || "COMPLETED".equals(order.getStatus())) {
            return;
        }
        long total = noticeMapper.selectCount(new LambdaQueryWrapper<PrepNotice>()
                .eq(PrepNotice::getProductionPlanNo, productionPlanNo)
                .eq(PrepNotice::getDeleted, 0));
        if (total == 0) {
            return;
        }
        long incomplete = noticeMapper.selectCount(new LambdaQueryWrapper<PrepNotice>()
                .eq(PrepNotice::getProductionPlanNo, productionPlanNo)
                .eq(PrepNotice::getDeleted, 0)
                .ne(PrepNotice::getStatus, "COMPLETED"));
        if (incomplete > 0) {
            return;
        }
        order.setStatus("COMPLETED");
        order.setCompletedQty(order.getPlanQty() != null ? order.getPlanQty() : BigDecimal.ZERO);
        productionOrderMapper.updateById(order);
    }
}
