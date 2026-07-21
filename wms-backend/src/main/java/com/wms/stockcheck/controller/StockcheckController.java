package com.wms.stockcheck.controller;

import com.wms.common.result.ApiResult;
import com.wms.common.result.PageResult;
import com.wms.stockcheck.entity.StockcheckDiff;
import com.wms.stockcheck.entity.StockcheckPlan;
import com.wms.stockcheck.entity.StockcheckTask;
import com.wms.stockcheck.service.StockcheckService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "盘点管理")
@RestController
@RequestMapping("/stockcheck")
@RequiredArgsConstructor
public class StockcheckController {

    private final StockcheckService stockcheckService;

    @Operation(summary = "盘点计划列表")
    @GetMapping("/plans")
    public ApiResult<PageResult<StockcheckPlan>> listPlans(
            @RequestParam(required = false) String planNo,
            @RequestParam(required = false) String warehouseCode,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(stockcheckService.pagePlans(planNo, warehouseCode, status, current, size));
    }

    @Operation(summary = "盘点计划详情")
    @GetMapping("/plans/{planNo}")
    public ApiResult<StockcheckPlan> planDetail(@PathVariable String planNo) {
        return ApiResult.ok(stockcheckService.getPlan(planNo));
    }

    @Operation(summary = "创建盘点计划")
    @PostMapping("/plans")
    public ApiResult<Void> createPlan(@RequestBody StockcheckPlan plan) {
        stockcheckService.createPlan(plan);
        return ApiResult.ok("创建成功", null);
    }

    @Operation(summary = "更新盘点计划")
    @PutMapping("/plans/{planNo}")
    public ApiResult<Void> updatePlan(@PathVariable String planNo, @RequestBody StockcheckPlan plan) {
        stockcheckService.updatePlan(planNo, plan);
        return ApiResult.ok("更新成功", null);
    }

    @Operation(summary = "删除盘点计划")
    @DeleteMapping("/plans/{planNo}")
    public ApiResult<Void> deletePlan(@PathVariable String planNo) {
        stockcheckService.deletePlan(planNo);
        return ApiResult.ok("删除成功", null);
    }

    @Operation(summary = "发布盘点计划")
    @PutMapping("/plans/{planNo}/publish")
    public ApiResult<Void> publishPlan(@PathVariable String planNo) {
        stockcheckService.publishPlan(planNo);
        return ApiResult.ok("发布成功", null);
    }

    @Operation(summary = "盘点任务列表")
    @GetMapping("/tasks")
    public ApiResult<PageResult<StockcheckTask>> listTasks(
            @RequestParam(required = false) String planNo,
            @RequestParam(required = false) String warehouseCode,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(stockcheckService.pageTasks(planNo, warehouseCode, status, current, size));
    }

    @Operation(summary = "盘点差异列表")
    @GetMapping("/diffs")
    public ApiResult<PageResult<StockcheckDiff>> listDiffs(
            @RequestParam(required = false) String taskNo,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(stockcheckService.pageDiffs(taskNo, status, current, size));
    }

    @Operation(summary = "审批盘点差异")
    @PutMapping("/diffs/{id}/approve")
    public ApiResult<Void> approveDiff(@PathVariable Long id) {
        stockcheckService.approveDiff(id);
        return ApiResult.ok("审批成功", null);
    }
}
