package com.wms.base.controller;

import com.wms.base.entity.BaseWarehouse;
import com.wms.base.service.BaseWarehouseService;
import com.wms.common.result.ApiResult;
import com.wms.common.result.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "基础数据-仓库")
@RestController
@RequestMapping("/base/warehouses")
@RequiredArgsConstructor
public class BaseWarehouseController {

    private final BaseWarehouseService warehouseService;

    @Operation(summary = "仓库列表")
    @GetMapping
    public ApiResult<PageResult<BaseWarehouse>> list(
            @RequestParam(required = false) String warehouseCode,
            @RequestParam(required = false) String warehouseName,
            @RequestParam(required = false) String warehouseType,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(warehouseService.page(warehouseCode, warehouseName, warehouseType, status, current, size));
    }

    @Operation(summary = "仓库详情")
    @GetMapping("/{warehouseCode}")
    public ApiResult<BaseWarehouse> detail(@PathVariable String warehouseCode) {
        return ApiResult.ok(warehouseService.getByCode(warehouseCode));
    }

    @Operation(summary = "仓库容量使用率")
    @GetMapping("/{warehouseCode}/utilization")
    public ApiResult<Map<String, Object>> utilization(@PathVariable String warehouseCode) {
        return ApiResult.ok(warehouseService.utilization(warehouseCode));
    }

    @Operation(summary = "新增仓库")
    @PostMapping
    public ApiResult<Void> create(@RequestBody BaseWarehouse warehouse) {
        warehouseService.create(warehouse);
        return ApiResult.ok("创建成功", null);
    }

    @Operation(summary = "更新仓库")
    @PutMapping("/{warehouseCode}")
    public ApiResult<Void> update(@PathVariable String warehouseCode, @RequestBody BaseWarehouse warehouse) {
        warehouseService.update(warehouseCode, warehouse);
        return ApiResult.ok("更新成功", null);
    }

    @Operation(summary = "删除仓库")
    @DeleteMapping("/{warehouseCode}")
    public ApiResult<Void> delete(@PathVariable String warehouseCode) {
        warehouseService.delete(warehouseCode);
        return ApiResult.ok("删除成功", null);
    }
}
