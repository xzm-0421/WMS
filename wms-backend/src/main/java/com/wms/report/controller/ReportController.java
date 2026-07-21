package com.wms.report.controller;

import com.wms.common.result.ApiResult;
import com.wms.common.result.PageResult;
import com.wms.inventory.dto.InventoryWarningDto;
import com.wms.inventory.service.InventoryService;
import com.wms.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "报表统计")
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final InventoryService inventoryService;

    @Operation(summary = "仪表盘")
    @GetMapping("/dashboard")
    public ApiResult<Map<String, Object>> dashboard() {
        return ApiResult.ok(reportService.dashboard());
    }

    @Operation(summary = "库存汇总报表")
    @GetMapping("/inventory/summary")
    public ApiResult<List<Map<String, Object>>> inventorySummary(
            @RequestParam(required = false) String warehouseCode) {
        return ApiResult.ok(reportService.inventorySummary(warehouseCode));
    }

    @Operation(summary = "低库存预警报表")
    @GetMapping("/inventory/low-stock")
    public ApiResult<PageResult<InventoryWarningDto>> lowStock(
            @RequestParam(required = false) String warehouseCode,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(reportService.lowStockReport(warehouseCode, current, size));
    }

    @Operation(summary = "入库统计")
    @GetMapping("/inbound/statistics")
    public ApiResult<Map<String, Object>> inboundStatistics(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return ApiResult.ok(reportService.inboundStatistics(startDate, endDate));
    }

    @Operation(summary = "出库统计")
    @GetMapping("/outbound/statistics")
    public ApiResult<Map<String, Object>> outboundStatistics(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return ApiResult.ok(reportService.outboundStatistics(startDate, endDate));
    }

    @Operation(summary = "拣配看板")
    @GetMapping("/dashboard/picking")
    public ApiResult<Map<String, Object>> pickingDashboard() {
        return ApiResult.ok(reportService.pickingDashboard());
    }

    @Operation(summary = "仓库统计看板")
    @GetMapping("/dashboard/warehouse")
    public ApiResult<Map<String, Object>> warehouseDashboard() {
        return ApiResult.ok(reportService.warehouseDashboard());
    }
}
