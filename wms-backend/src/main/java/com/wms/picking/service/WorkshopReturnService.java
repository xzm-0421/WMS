package com.wms.picking.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.common.result.PageResult;
import com.wms.common.util.OrderNoGenerator;
import com.wms.inventory.dto.InventoryChangeCommand;
import com.wms.inventory.service.InventoryService;
import com.wms.inventory.service.LocationAllocationService;
import com.wms.picking.dto.WorkshopReturnCreateRequest;
import com.wms.picking.entity.PickIssue;
import com.wms.picking.entity.WorkshopReturn;
import com.wms.picking.entity.WorkshopReturnLine;
import com.wms.picking.mapper.PickIssueMapper;
import com.wms.picking.mapper.WorkshopReturnLineMapper;
import com.wms.picking.mapper.WorkshopReturnMapper;
import com.wms.system.service.AuditTrailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WorkshopReturnService {

    private final WorkshopReturnMapper returnMapper;
    private final WorkshopReturnLineMapper lineMapper;
    private final PickIssueMapper issueMapper;
    private final InventoryService inventoryService;
    private final LocationAllocationService locationAllocationService;
    private final AuditTrailService auditTrailService;

    public PageResult<WorkshopReturn> page(String returnNo, long current, long size) {
        LambdaQueryWrapper<WorkshopReturn> w = new LambdaQueryWrapper<>();
        w.like(StringUtils.hasText(returnNo), WorkshopReturn::getReturnNo, returnNo)
                .eq(WorkshopReturn::getDeleted, 0)
                .orderByDesc(WorkshopReturn::getCreateTime);
        Page<WorkshopReturn> page = returnMapper.selectPage(new Page<>(current, size), w);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public Map<String, Object> detail(String returnNo) {
        WorkshopReturn ret = getByNo(returnNo);
        List<WorkshopReturnLine> lines = lineMapper.selectList(new LambdaQueryWrapper<WorkshopReturnLine>()
                .eq(WorkshopReturnLine::getReturnNo, returnNo));
        Map<String, Object> data = new HashMap<>();
        data.put("order", ret);
        data.put("lines", lines);
        return data;
    }

    @Transactional
    public String create(WorkshopReturnCreateRequest request, String operatorName) {
        if (StringUtils.hasText(request.getIssueNo())) {
            PickIssue issue = issueMapper.selectOne(new LambdaQueryWrapper<PickIssue>()
                    .eq(PickIssue::getIssueNo, request.getIssueNo())
                    .eq(PickIssue::getDeleted, 0));
            if (issue == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "原发料单不存在");
            }
        }
        String returnNo = OrderNoGenerator.next("WR");
        int lineNo = 1;
        if (request.getLines() != null) {
            for (WorkshopReturnCreateRequest.LineItem item : request.getLines()) {
                String loc = StringUtils.hasText(item.getTargetLocation())
                        ? item.getTargetLocation()
                        : locationAllocationService.resolveInboundLocation(
                                request.getWarehouseCode(), item.getMaterialCode(), item.getBatchNo(), null);
                WorkshopReturnLine line = new WorkshopReturnLine();
                line.setReturnNo(returnNo);
                line.setLineNo(lineNo++);
                line.setMaterialCode(item.getMaterialCode());
                line.setReturnQty(item.getReturnQty());
                line.setTargetLocation(loc);
                line.setBatchNo(item.getBatchNo());
                lineMapper.insert(line);
            }
        }
        WorkshopReturn ret = new WorkshopReturn();
        ret.setReturnNo(returnNo);
        ret.setIssueNo(request.getIssueNo());
        ret.setWarehouseCode(request.getWarehouseCode());
        ret.setReturnReason(request.getReturnReason());
        ret.setStatus("DRAFT");
        ret.setOperatorName(operatorName);
        ret.setCreateTime(LocalDateTime.now());
        ret.setDeleted(0);
        returnMapper.insert(ret);
        auditTrailService.log("WORKSHOP_RETURN", returnNo, "CREATE", operatorName, request.getIssueNo());
        return returnNo;
    }

    @Transactional
    public void confirm(String returnNo, String operatorName) {
        WorkshopReturn ret = getByNo(returnNo);
        List<WorkshopReturnLine> lines = lineMapper.selectList(new LambdaQueryWrapper<WorkshopReturnLine>()
                .eq(WorkshopReturnLine::getReturnNo, returnNo));
        for (WorkshopReturnLine line : lines) {
            InventoryChangeCommand cmd = InventoryChangeCommand.builder()
                    .warehouseCode(ret.getWarehouseCode())
                    .locationCode(line.getTargetLocation())
                    .materialCode(line.getMaterialCode())
                    .batchNo(line.getBatchNo())
                    .quantity(line.getReturnQty())
                    .transactionType("WORKSHOP_RETURN")
                    .sourceOrderNo(returnNo)
                    .operatorName(operatorName)
                    .build();
            inventoryService.increase(cmd);
        }
        ret.setStatus("COMPLETED");
        returnMapper.updateById(ret);
        auditTrailService.log("WORKSHOP_RETURN", returnNo, "CONFIRM", operatorName, null);
    }

    private WorkshopReturn getByNo(String returnNo) {
        WorkshopReturn ret = returnMapper.selectOne(new LambdaQueryWrapper<WorkshopReturn>()
                .eq(WorkshopReturn::getReturnNo, returnNo)
                .eq(WorkshopReturn::getDeleted, 0));
        if (ret == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "车间退库单不存在");
        }
        return ret;
    }
}
