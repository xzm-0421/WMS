package com.wms.inventoryext.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.common.result.PageResult;
import com.wms.common.util.OrderNoGenerator;
import com.wms.inventory.entity.Inventory;
import com.wms.inventory.mapper.InventoryMapper;
import com.wms.inventoryext.dto.SampleResultSubmitRequest;
import com.wms.inventoryext.entity.SamplePlan;
import com.wms.inventoryext.entity.SampleResult;
import com.wms.inventoryext.mapper.SamplePlanMapper;
import com.wms.inventoryext.mapper.SampleResultMapper;
import com.wms.system.service.AuditTrailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SamplePlanService {

    private final SamplePlanMapper planMapper;
    private final SampleResultMapper resultMapper;
    private final InventoryMapper inventoryMapper;
    private final AuditTrailService auditTrailService;

    public PageResult<SamplePlan> page(String planNo, String status, long current, long size) {
        LambdaQueryWrapper<SamplePlan> w = new LambdaQueryWrapper<>();
        w.like(StringUtils.hasText(planNo), SamplePlan::getPlanNo, planNo)
                .eq(StringUtils.hasText(status), SamplePlan::getStatus, status)
                .eq(SamplePlan::getDeleted, 0)
                .orderByDesc(SamplePlan::getCreateTime);
        Page<SamplePlan> page = planMapper.selectPage(new Page<>(current, size), w);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Transactional
    public String createPlan(String warehouseCode, LocalDate planDate, String operatorName) {
        String planNo = OrderNoGenerator.next("SP");
        SamplePlan plan = new SamplePlan();
        plan.setPlanNo(planNo);
        plan.setWarehouseCode(warehouseCode);
        plan.setPlanDate(planDate != null ? planDate : LocalDate.now());
        plan.setStatus("PLANNED");
        plan.setCreatorName(operatorName);
        plan.setCreateTime(LocalDateTime.now());
        plan.setDeleted(0);
        planMapper.insert(plan);
        auditTrailService.log("SAMPLE_PLAN", planNo, "CREATE", operatorName, warehouseCode);
        return planNo;
    }

    public List<Map<String, Object>> generateChecklist(String planNo) {
        SamplePlan plan = getPlan(planNo);
        List<Inventory> stocks = inventoryMapper.selectList(new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getWarehouseCode, plan.getWarehouseCode())
                .gt(Inventory::getStockQty, BigDecimal.ZERO)
                .last("OFFSET 0 ROWS FETCH NEXT 50 ROWS ONLY"));
        List<Map<String, Object>> list = new ArrayList<>();
        for (Inventory inv : stocks) {
            Map<String, Object> row = new HashMap<>();
            row.put("materialCode", inv.getMaterialCode());
            row.put("batchNo", inv.getBatchNo());
            row.put("locationCode", inv.getLocationCode());
            row.put("stockQty", inv.getStockQty());
            list.add(row);
        }
        plan.setStatus("IN_PROGRESS");
        planMapper.updateById(plan);
        return list;
    }

    @Transactional
    public void submitResult(SampleResultSubmitRequest request, String operatorName) {
        SampleResult result = new SampleResult();
        result.setPlanNo(request.getPlanNo());
        result.setMaterialCode(request.getMaterialCode());
        result.setBatchNo(request.getBatchNo());
        result.setLocationCode(request.getLocationCode());
        result.setQualityStatus(request.getQualityStatus());
        result.setExpireDate(request.getExpireDate());
        result.setRemark(request.getRemark());
        result.setCheckTime(LocalDateTime.now());
        boolean alert = "UNQUALIFIED".equals(request.getQualityStatus())
                || (request.getExpireDate() != null && request.getExpireDate().isBefore(LocalDate.now().plusDays(30)));
        result.setAlertFlag(alert ? 1 : 0);
        resultMapper.insert(result);
        if (alert) {
            auditTrailService.log("SAMPLE_ALERT", request.getPlanNo(), "ALERT", operatorName,
                    request.getMaterialCode() + " " + request.getQualityStatus());
        }
    }

    public List<SampleResult> listResults(String planNo) {
        return resultMapper.selectList(new LambdaQueryWrapper<SampleResult>()
                .eq(SampleResult::getPlanNo, planNo)
                .orderByDesc(SampleResult::getCheckTime));
    }

    public List<SampleResult> listAlerts() {
        return resultMapper.selectList(new LambdaQueryWrapper<SampleResult>()
                .eq(SampleResult::getAlertFlag, 1)
                .orderByDesc(SampleResult::getCheckTime)
                .last("OFFSET 0 ROWS FETCH NEXT 20 ROWS ONLY"));
    }

    private SamplePlan getPlan(String planNo) {
        SamplePlan plan = planMapper.selectOne(new LambdaQueryWrapper<SamplePlan>()
                .eq(SamplePlan::getPlanNo, planNo)
                .eq(SamplePlan::getDeleted, 0));
        if (plan == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "抽检计划不存在");
        }
        return plan;
    }
}
