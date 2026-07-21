package com.wms.base.controller;

import com.wms.base.dto.ZoneCreateRequest;
import com.wms.base.entity.BaseLocation;
import com.wms.base.service.BaseLocationService;
import com.wms.common.result.ApiResult;
import com.wms.common.result.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "基础数据-库位")
@RestController
@RequestMapping("/base/locations")
@RequiredArgsConstructor
public class BaseLocationController {

    private final BaseLocationService locationService;

    @Operation(summary = "库位列表")
    @GetMapping
    public ApiResult<PageResult<BaseLocation>> list(
            @RequestParam(required = false) String locationCode,
            @RequestParam(required = false) String warehouseCode,
            @RequestParam(required = false) String zoneCode,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(locationService.page(locationCode, warehouseCode, zoneCode, status, current, size));
    }

    @Operation(summary = "库位树形结构")
    @GetMapping("/tree")
    public ApiResult<List<Map<String, Object>>> tree(
            @RequestParam(required = false) String warehouseCode) {
        return ApiResult.ok(locationService.tree(warehouseCode));
    }

    @Operation(summary = "库位详情")
    @GetMapping("/{locationCode}")
    public ApiResult<BaseLocation> detail(@PathVariable String locationCode) {
        return ApiResult.ok(locationService.getByCode(locationCode));
    }

    @Operation(summary = "库位二维码内容")
    @GetMapping("/{locationCode}/qr")
    public ApiResult<String> qr(@PathVariable String locationCode) {
        return ApiResult.ok(locationService.generateQrContent(locationCode));
    }

    @Operation(summary = "新增区域")
    @PostMapping("/zones")
    public ApiResult<Void> createZone(@RequestBody ZoneCreateRequest request) {
        locationService.createZone(request);
        return ApiResult.ok("创建成功", null);
    }

    @Operation(summary = "新增库位")
    @PostMapping
    public ApiResult<Void> create(@RequestBody BaseLocation location) {
        locationService.create(location);
        return ApiResult.ok("创建成功", null);
    }

    @Operation(summary = "更新库位")
    @PutMapping("/{locationCode}")
    public ApiResult<Void> update(@PathVariable String locationCode, @RequestBody BaseLocation location) {
        locationService.update(locationCode, location);
        return ApiResult.ok("更新成功", null);
    }

    @Operation(summary = "删除库位")
    @DeleteMapping("/{locationCode}")
    public ApiResult<Void> delete(@PathVariable String locationCode) {
        locationService.delete(locationCode);
        return ApiResult.ok("删除成功", null);
    }
}
