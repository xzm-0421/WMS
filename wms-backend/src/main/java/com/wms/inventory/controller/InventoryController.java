package com.wms.inventory.controller;

import com.wms.common.result.ApiResult;
import com.wms.common.result.PageResult;
import com.wms.inventory.constant.StockStatus;
import com.wms.inventory.dto.InventoryListVo;
import com.wms.inventory.dto.InventoryStockStatusUpdateRequest;
import com.wms.inventory.dto.InventorySummaryDto;
import com.wms.inventory.dto.InventoryWarningDto;
import com.wms.inventory.entity.Inventory;
import com.wms.inventory.entity.InventoryTransaction;
import com.wms.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "库存管理")
@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @Operation(summary = "实时库存查询")
    @GetMapping("/list")
    public ApiResult<PageResult<InventoryListVo>> list(
            @RequestParam(required = false) String warehouseCode,
            @RequestParam(required = false) String locationCode,
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String batchNo,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(inventoryService.pageList(
                warehouseCode, locationCode, materialCode, batchNo, current, size));
    }

    @Operation(summary = "库存流水")
    @GetMapping("/transactions")
    public ApiResult<PageResult<InventoryTransaction>> transactions(
            @RequestParam(required = false) String warehouseCode,
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String transactionType,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(inventoryService.pageTransactions(warehouseCode, materialCode, transactionType, current, size));
    }

    @Operation(summary = "库存汇总")
    @GetMapping("/summary")
    public ApiResult<List<InventorySummaryDto>> summary(
            @RequestParam(required = false) String warehouseCode,
            @RequestParam(required = false) String materialCode) {
        return ApiResult.ok(inventoryService.getSummary(warehouseCode, materialCode));
    }

    @Operation(summary = "库存预警")
    @GetMapping("/warnings")
    public ApiResult<List<InventoryWarningDto>> warnings(
            @RequestParam(required = false) String warehouseCode) {
        return ApiResult.ok(inventoryService.getWarnings(warehouseCode));
    }

    @Operation(summary = "库存状态选项")
    @GetMapping("/stock-status-options")
    public ApiResult<List<java.util.Map<String, String>>> stockStatusOptions() {
        return ApiResult.ok(StockStatus.options());
    }

    @Operation(summary = "更新库存状态")
    @PutMapping("/{id}/stock-status")
    public ApiResult<Inventory> updateStockStatus(@PathVariable Long id,
                                                   @RequestBody InventoryStockStatusUpdateRequest request) {
        return ApiResult.ok("更新成功", inventoryService.updateStockStatus(id, request.getStockStatus()));
    }
}
