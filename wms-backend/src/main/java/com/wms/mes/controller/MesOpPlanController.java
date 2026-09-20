package com.wms.mes.controller;

import com.wms.common.result.ApiResult;
import com.wms.common.result.PageResult;
import com.wms.mes.dto.MesOpPlanDetailVo;
import com.wms.mes.dto.MesSyncResult;
import com.wms.mes.entity.MesOpPlan;
import com.wms.mes.service.MesOpPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "轻MES-工序计划")
@RestController
@RequestMapping("/mes/plans")
@RequiredArgsConstructor
public class MesOpPlanController {

    private final MesOpPlanService opPlanService;

    @Operation(summary = "工序计划列表")
    @GetMapping
    public ApiResult<PageResult<MesOpPlan>> list(
            @RequestParam(required = false) String moNo,
            @RequestParam(required = false) String productCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate planStartFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate planStartTo,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(opPlanService.page(moNo, productCode, status, planStartFrom, planStartTo, current, size));
    }

    @Operation(summary = "工序计划详情")
    @GetMapping("/{id}")
    public ApiResult<MesOpPlanDetailVo> detail(@PathVariable Long id) {
        return ApiResult.ok(opPlanService.detail(id));
    }

    @Operation(summary = "刷新工序计划")
    @PostMapping("/refresh")
    public ApiResult<MesSyncResult> refresh(@RequestParam(required = false) String moNo) {
        return ApiResult.ok(opPlanService.refresh(moNo));
    }

    @Operation(summary = "手动重试同步")
    @PostMapping("/{id}/retry")
    public ApiResult<MesSyncResult> retry(@PathVariable Long id) {
        return ApiResult.ok(opPlanService.retry(id));
    }
}
