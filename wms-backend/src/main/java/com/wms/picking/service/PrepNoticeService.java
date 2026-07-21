package com.wms.picking.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.common.result.PageResult;
import com.wms.common.util.OrderNoGenerator;
import com.wms.inventory.entity.Inventory;
import com.wms.inventory.mapper.InventoryMapper;
import com.wms.picking.dto.PrepNoticeCreateRequest;
import com.wms.picking.entity.PrepNotice;
import com.wms.picking.entity.PrepNoticeLine;
import com.wms.picking.mapper.PrepNoticeLineMapper;
import com.wms.picking.mapper.PrepNoticeMapper;
import com.wms.system.service.AuditTrailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PrepNoticeService {

    private final PrepNoticeMapper noticeMapper;
    private final PrepNoticeLineMapper lineMapper;
    private final InventoryMapper inventoryMapper;
    private final AuditTrailService auditTrailService;

    public PageResult<PrepNotice> page(String noticeNo, String status, long current, long size) {
        LambdaQueryWrapper<PrepNotice> w = new LambdaQueryWrapper<>();
        w.like(StringUtils.hasText(noticeNo), PrepNotice::getNoticeNo, noticeNo)
                .eq(StringUtils.hasText(status), PrepNotice::getStatus, status)
                .eq(PrepNotice::getDeleted, 0)
                .orderByDesc(PrepNotice::getCreateTime);
        Page<PrepNotice> page = noticeMapper.selectPage(new Page<>(current, size), w);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public Map<String, Object> detail(String noticeNo) {
        PrepNotice notice = getByNo(noticeNo);
        List<PrepNoticeLine> lines = lineMapper.selectList(new LambdaQueryWrapper<PrepNoticeLine>()
                .eq(PrepNoticeLine::getNoticeNo, noticeNo)
                .orderByAsc(PrepNoticeLine::getLineNo));
        Map<String, Object> data = new HashMap<>();
        data.put("notice", notice);
        data.put("lines", lines);
        return data;
    }

    @Transactional
    public String create(PrepNoticeCreateRequest request, String operatorName) {
        String noticeNo = OrderNoGenerator.next("PN");
        int lineNo = 1;
        if (request.getLines() != null) {
            for (PrepNoticeCreateRequest.LineItem item : request.getLines()) {
                PrepNoticeLine line = new PrepNoticeLine();
                line.setNoticeNo(noticeNo);
                line.setLineNo(lineNo++);
                line.setMaterialCode(item.getMaterialCode());
                line.setMaterialName(item.getMaterialName());
                line.setDemandQty(item.getDemandQty());
                line.setPickedQty(BigDecimal.ZERO);
                line.setRecommendLoc(findRecommendLoc(request.getWarehouseCode(), item.getMaterialCode()));
                lineMapper.insert(line);
            }
        }
        PrepNotice notice = new PrepNotice();
        notice.setNoticeNo(noticeNo);
        notice.setProductionPlanNo(request.getProductionPlanNo());
        notice.setWarehouseCode(request.getWarehouseCode());
        notice.setDemandTime(request.getDemandTime());
        notice.setStatus("OPEN");
        notice.setCreatorName(operatorName);
        notice.setCreateTime(LocalDateTime.now());
        notice.setDeleted(0);
        noticeMapper.insert(notice);
        auditTrailService.log("PREP_NOTICE", noticeNo, "CREATE", operatorName, null);
        return noticeNo;
    }

    private String findRecommendLoc(String warehouseCode, String materialCode) {
        Inventory inv = inventoryMapper.selectOne(new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getWarehouseCode, warehouseCode)
                .eq(Inventory::getMaterialCode, materialCode)
                .gt(Inventory::getStockQty, BigDecimal.ZERO)
                .orderByDesc(Inventory::getStockQty)
                .last("OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY"));
        return inv != null ? inv.getLocationCode() : null;
    }

    private PrepNotice getByNo(String noticeNo) {
        PrepNotice notice = noticeMapper.selectOne(new LambdaQueryWrapper<PrepNotice>()
                .eq(PrepNotice::getNoticeNo, noticeNo)
                .eq(PrepNotice::getDeleted, 0));
        if (notice == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "备料通知单不存在", "PN_NOT_FOUND");
        }
        return notice;
    }
}
