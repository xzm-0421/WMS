package com.wms.picking.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.common.result.PageResult;
import com.wms.common.util.OrderNoGenerator;
import com.wms.picking.entity.MaterialPickup;
import com.wms.picking.entity.PickIssue;
import com.wms.picking.mapper.MaterialPickupMapper;
import com.wms.picking.mapper.PickIssueMapper;
import com.wms.picking.entity.PickIssueLine;
import com.wms.picking.mapper.PickIssueLineMapper;
import com.wms.system.service.AuditTrailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MaterialPickupService {

    private final MaterialPickupMapper pickupMapper;
    private final PickIssueMapper issueMapper;
    private final PickIssueLineMapper issueLineMapper;
    private final PickingFlowSyncService pickingFlowSyncService;
    private final AuditTrailService auditTrailService;

    public PageResult<MaterialPickup> page(String issueNo, long current, long size) {
        LambdaQueryWrapper<MaterialPickup> w = new LambdaQueryWrapper<>();
        w.eq(StringUtils.hasText(issueNo), MaterialPickup::getIssueNo, issueNo)
                .orderByDesc(MaterialPickup::getCreateTime);
        Page<MaterialPickup> page = pickupMapper.selectPage(new Page<>(current, size), w);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Transactional
    public String confirmByIssueQr(String issueNo, String receiverName) {
        PickIssue issue = issueMapper.selectOne(new LambdaQueryWrapper<PickIssue>()
                .eq(PickIssue::getIssueNo, issueNo)
                .eq(PickIssue::getDeleted, 0));
        if (issue == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "拣配发料单不存在");
        }
        if (!List.of("PICKING", "COMPLETED").contains(issue.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "发料单状态不允许领料确认", "ORDER_STATUS_CONFLICT");
        }
        List<PickIssueLine> lines = issueLineMapper.selectList(new LambdaQueryWrapper<PickIssueLine>()
                .eq(PickIssueLine::getIssueNo, issueNo));
        for (PickIssueLine line : lines) {
            BigDecimal picked = line.getPickedQty() == null ? BigDecimal.ZERO : line.getPickedQty();
            if (picked.compareTo(line.getPickQty()) < 0) {
                throw new BusinessException(ErrorCode.CONFLICT,
                        "尚有物料未拣完: " + line.getMaterialCode(), "PICK_INCOMPLETE");
            }
        }
        String pickupNo = OrderNoGenerator.next("MP");
        MaterialPickup pickup = new MaterialPickup();
        pickup.setPickupNo(pickupNo);
        pickup.setIssueNo(issueNo);
        pickup.setReceiverName(receiverName);
        pickup.setPickupTime(LocalDateTime.now());
        pickup.setStatus("COMPLETED");
        pickup.setCreateTime(LocalDateTime.now());
        pickupMapper.insert(pickup);
        issue.setStatus("PICKED_UP");
        issueMapper.updateById(issue);
        pickingFlowSyncService.afterPickIssuePickedUp(issue.getNoticeNo());
        auditTrailService.log("MATERIAL_PICKUP", pickupNo, "CONFIRM", receiverName, issueNo);
        return pickupNo;
    }
}
