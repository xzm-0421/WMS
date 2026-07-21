package com.wms.stockcheck.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wms.auth.security.LoginUser;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.common.result.PageResult;
import com.wms.common.util.OrderNoGenerator;
import com.wms.inventory.dto.InventoryChangeCommand;
import com.wms.inventory.entity.Inventory;
import com.wms.inventory.mapper.InventoryMapper;
import com.wms.inventory.service.InventoryService;
import com.wms.stockcheck.entity.*;
import com.wms.stockcheck.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
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
public class StockcheckService {

    private final StockcheckPlanMapper planMapper;
    private final StockcheckTaskMapper taskMapper;
    private final StockcheckDetailMapper detailMapper;
    private final StockcheckDiffMapper diffMapper;
    private final InventoryMapper inventoryMapper;
    private final InventoryService inventoryService;

    public PageResult<StockcheckPlan> pagePlans(String planNo, String warehouseCode, String status,
                                                long current, long size) {
        LambdaQueryWrapper<StockcheckPlan> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(planNo), StockcheckPlan::getPlanNo, planNo)
                .eq(StringUtils.hasText(warehouseCode), StockcheckPlan::getWarehouseCode, warehouseCode)
                .eq(StringUtils.hasText(status), StockcheckPlan::getStatus, status)
                .eq(StockcheckPlan::getDeleted, 0)
                .orderByDesc(StockcheckPlan::getCreateTime);
        Page<StockcheckPlan> page = planMapper.selectPage(new Page<>(current, size), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public StockcheckPlan getPlan(String planNo) {
        StockcheckPlan plan = planMapper.selectOne(new LambdaQueryWrapper<StockcheckPlan>()
                .eq(StockcheckPlan::getPlanNo, planNo)
                .eq(StockcheckPlan::getDeleted, 0));
        if (plan == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "盘点计划不存在", "PLAN_NOT_FOUND");
        }
        return plan;
    }

    public void createPlan(StockcheckPlan plan) {
        LoginUser user = currentUser();
        plan.setPlanNo(OrderNoGenerator.next("PD"));
        plan.setStatus("DRAFT");
        plan.setCreatorId(String.valueOf(user.getUserId()));
        plan.setCreatorName(user.getRealName());
        plan.setDeleted(0);
        plan.setCreateTime(LocalDateTime.now());
        planMapper.insert(plan);
    }

    public void updatePlan(String planNo, StockcheckPlan plan) {
        StockcheckPlan existing = getPlan(planNo);
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "仅草稿状态可编辑", "PLAN_STATUS_CONFLICT");
        }
        plan.setId(existing.getId());
        plan.setPlanNo(planNo);
        plan.setUpdateTime(LocalDateTime.now());
        planMapper.updateById(plan);
    }

    public void deletePlan(String planNo) {
        StockcheckPlan plan = getPlan(planNo);
        if (!"DRAFT".equals(plan.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "仅草稿状态可删除", "PLAN_STATUS_CONFLICT");
        }
        planMapper.deleteById(plan.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void publishPlan(String planNo) {
        StockcheckPlan plan = getPlan(planNo);
        if (!"DRAFT".equals(plan.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "仅草稿状态可发布", "PLAN_STATUS_CONFLICT");
        }
        List<Inventory> inventories = inventoryMapper.selectList(new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getWarehouseCode, plan.getWarehouseCode())
                .gt(Inventory::getStockQty, 0));

        String taskNo = OrderNoGenerator.next("PT");
        StockcheckTask task = new StockcheckTask();
        task.setTaskNo(taskNo);
        task.setPlanNo(planNo);
        task.setWarehouseCode(plan.getWarehouseCode());
        task.setStatus("PENDING");
        task.setCreateTime(LocalDateTime.now());
        taskMapper.insert(task);

        int lineNo = 1;
        for (Inventory inv : inventories) {
            StockcheckDetail detail = new StockcheckDetail();
            detail.setTaskNo(taskNo);
            detail.setLineNo(lineNo++);
            detail.setMaterialCode(inv.getMaterialCode());
            detail.setLocationCode(inv.getLocationCode());
            detail.setBatchNo(inv.getBatchNo());
            detail.setBookQty(inv.getStockQty());
            detail.setLineStatus("PENDING");
            detailMapper.insert(detail);
        }

        plan.setStatus("PUBLISHED");
        plan.setUpdateTime(LocalDateTime.now());
        planMapper.updateById(plan);
    }

    public PageResult<StockcheckTask> pageTasks(String planNo, String warehouseCode, String status,
                                                long current, long size) {
        LambdaQueryWrapper<StockcheckTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(planNo), StockcheckTask::getPlanNo, planNo)
                .eq(StringUtils.hasText(warehouseCode), StockcheckTask::getWarehouseCode, warehouseCode)
                .eq(StringUtils.hasText(status), StockcheckTask::getStatus, status)
                .orderByDesc(StockcheckTask::getCreateTime);
        Page<StockcheckTask> page = taskMapper.selectPage(new Page<>(current, size), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public PageResult<StockcheckDiff> pageDiffs(String taskNo, String status, long current, long size) {
        LambdaQueryWrapper<StockcheckDiff> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(taskNo), StockcheckDiff::getTaskNo, taskNo)
                .eq(StringUtils.hasText(status), StockcheckDiff::getStatus, status)
                .orderByDesc(StockcheckDiff::getCreateTime);
        Page<StockcheckDiff> page = diffMapper.selectPage(new Page<>(current, size), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Transactional(rollbackFor = Exception.class)
    public void approveDiff(Long id) {
        StockcheckDiff diff = diffMapper.selectById(id);
        if (diff == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "差异记录不存在");
        }
        if (!"PENDING".equals(diff.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "差异已处理", "DIFF_STATUS_CONFLICT");
        }
        StockcheckTask task = getTask(diff.getTaskNo());
        LoginUser user = currentUser();
        String batchNo = StringUtils.hasText(diff.getBatchNo()) ? diff.getBatchNo() : "";
        BigDecimal diffQty = diff.getDiffQty();
        if (diffQty != null && diffQty.compareTo(BigDecimal.ZERO) != 0) {
            if (diffQty.compareTo(BigDecimal.ZERO) > 0) {
                inventoryService.increase(InventoryChangeCommand.builder()
                        .transactionType("STOCKTAKE_GAIN")
                        .warehouseCode(task.getWarehouseCode())
                        .locationCode(diff.getLocationCode())
                        .materialCode(diff.getMaterialCode())
                        .batchNo(batchNo)
                        .quantity(diffQty)
                        .sourceOrderType("STOCKCHECK")
                        .sourceOrderNo(diff.getTaskNo())
                        .operatorId(String.valueOf(user.getUserId()))
                        .operatorName(user.getRealName())
                        .remark("盘点差异盘盈")
                        .build());
            } else {
                inventoryService.decrease(InventoryChangeCommand.builder()
                        .transactionType("STOCKTAKE_LOSS")
                        .warehouseCode(task.getWarehouseCode())
                        .locationCode(diff.getLocationCode())
                        .materialCode(diff.getMaterialCode())
                        .batchNo(batchNo)
                        .quantity(diffQty.abs())
                        .sourceOrderType("STOCKCHECK")
                        .sourceOrderNo(diff.getTaskNo())
                        .operatorId(String.valueOf(user.getUserId()))
                        .operatorName(user.getRealName())
                        .remark("盘点差异盘亏")
                        .build());
            }
        }
        diff.setStatus("APPROVED");
        diff.setApproverId(String.valueOf(user.getUserId()));
        diff.setApproveTime(LocalDateTime.now());
        diffMapper.updateById(diff);
    }

    public StockcheckTask getTask(String taskNo) {
        StockcheckTask task = taskMapper.selectOne(new LambdaQueryWrapper<StockcheckTask>()
                .eq(StockcheckTask::getTaskNo, taskNo));
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "盘点任务不存在", "TASK_NOT_FOUND");
        }
        return task;
    }

    public List<StockcheckDetail> getTaskDetails(String taskNo) {
        getTask(taskNo);
        return detailMapper.selectList(new LambdaQueryWrapper<StockcheckDetail>()
                .eq(StockcheckDetail::getTaskNo, taskNo)
                .orderByAsc(StockcheckDetail::getLineNo));
    }

    @Transactional(rollbackFor = Exception.class)
    public void submitCount(String taskNo, Integer lineNo, BigDecimal actualQty) {
        StockcheckTask task = getTask(taskNo);
        if (!"PENDING".equals(task.getStatus()) && !"COUNTING".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "任务状态不可盘点", "TASK_STATUS_CONFLICT");
        }
        StockcheckDetail detail = detailMapper.selectOne(new LambdaQueryWrapper<StockcheckDetail>()
                .eq(StockcheckDetail::getTaskNo, taskNo)
                .eq(StockcheckDetail::getLineNo, lineNo));
        if (detail == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "盘点明细不存在");
        }
        detail.setActualQty(actualQty);
        detail.setDiffQty(actualQty.subtract(detail.getBookQty()));
        detail.setLineStatus("COUNTED");
        detailMapper.updateById(detail);

        if ("PENDING".equals(task.getStatus())) {
            task.setStatus("COUNTING");
            taskMapper.updateById(task);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void completeTask(String taskNo) {
        StockcheckTask task = getTask(taskNo);
        List<StockcheckDetail> details = getTaskDetails(taskNo);
        for (StockcheckDetail detail : details) {
            if (detail.getActualQty() == null) {
                continue;
            }
            if (detail.getDiffQty() != null && detail.getDiffQty().compareTo(BigDecimal.ZERO) != 0) {
                StockcheckDiff diff = new StockcheckDiff();
                diff.setTaskNo(taskNo);
                diff.setMaterialCode(detail.getMaterialCode());
                diff.setLocationCode(detail.getLocationCode());
                diff.setBatchNo(detail.getBatchNo());
                diff.setDiffQty(detail.getDiffQty());
                diff.setStatus("PENDING");
                diff.setCreateTime(LocalDateTime.now());
                diffMapper.insert(diff);
            }
        }
        task.setStatus("COMPLETED");
        task.setCompleteTime(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submitCountByScan(String taskNo, String locationCode, String materialCode,
                                                  String batchNo, BigDecimal actualQty) {
        getTask(taskNo);
        if (!StringUtils.hasText(materialCode) && !StringUtils.hasText(locationCode)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请扫码或填写物料/库位", "MISSING_SCAN_KEY");
        }
        StockcheckDetail detail = findDetailForScan(taskNo, locationCode, materialCode, batchNo);
        if (detail == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "盘点明细不存在，请核对库位物料或使用盘盈录入");
        }
        submitCount(taskNo, detail.getLineNo(), actualQty);
        detail = detailMapper.selectById(detail.getId());
        Map<String, Object> result = new HashMap<>();
        result.put("taskId", taskNo);
        result.put("lineNo", detail.getLineNo());
        result.put("locationCode", detail.getLocationCode());
        result.put("materialCode", detail.getMaterialCode());
        result.put("batchNo", detail.getBatchNo());
        result.put("bookQty", detail.getBookQty());
        result.put("actualQty", detail.getActualQty());
        result.put("diffQty", detail.getDiffQty());
        result.put("checkStatus", detail.getLineStatus());
        return result;
    }

    /**
     * 按扫码结果匹配盘点明细：优先未盘行；库位可缺省（仅物料+批次）。
     */
    private StockcheckDetail findDetailForScan(String taskNo, String locationCode,
                                               String materialCode, String batchNo) {
        LambdaQueryWrapper<StockcheckDetail> wrapper = new LambdaQueryWrapper<StockcheckDetail>()
                .eq(StockcheckDetail::getTaskNo, taskNo)
                .eq(StringUtils.hasText(materialCode), StockcheckDetail::getMaterialCode, materialCode)
                .eq(StringUtils.hasText(locationCode), StockcheckDetail::getLocationCode, locationCode)
                .eq(StringUtils.hasText(batchNo), StockcheckDetail::getBatchNo, batchNo)
                .orderByAsc(StockcheckDetail::getLineNo);
        List<StockcheckDetail> list = detailMapper.selectList(wrapper);
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.stream()
                .filter(d -> !"COUNTED".equals(d.getLineStatus()))
                .findFirst()
                .orElse(list.get(0));
    }

    @Transactional(rollbackFor = Exception.class)
    public void recordGain(String taskNo, String locationCode, String materialCode,
                           String batchNo, BigDecimal actualQty, String remark) {
        getTask(taskNo);
        Integer maxLine = detailMapper.selectList(new LambdaQueryWrapper<StockcheckDetail>()
                        .eq(StockcheckDetail::getTaskNo, taskNo)
                        .orderByDesc(StockcheckDetail::getLineNo)
                        .last("OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY"))
                .stream().map(StockcheckDetail::getLineNo).findFirst().orElse(0);
        StockcheckDetail detail = new StockcheckDetail();
        detail.setTaskNo(taskNo);
        detail.setLineNo(maxLine + 1);
        detail.setMaterialCode(materialCode);
        detail.setLocationCode(locationCode);
        detail.setBatchNo(StringUtils.hasText(batchNo) ? batchNo : "");
        detail.setBookQty(BigDecimal.ZERO);
        detail.setActualQty(actualQty);
        detail.setDiffQty(actualQty);
        detail.setLineStatus("COUNTED");
        detailMapper.insert(detail);
    }

    @Transactional(rollbackFor = Exception.class)
    public void confirmEmpty(String taskNo, String locationCode, String materialCode, String batchNo) {
        submitCountByScan(taskNo, locationCode, materialCode, batchNo, BigDecimal.ZERO);
    }

    private LoginUser currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return (LoginUser) auth.getPrincipal();
    }
}
