package com.wms.mobile.controller;

import com.wms.base.entity.BaseLocation;
import com.wms.common.result.ApiResult;
import com.wms.mobile.dto.MobileLocationAllocateRequest;
import com.wms.inventory.service.LocationAllocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "PDA-库位")
@RestController
@RequestMapping("/mobile/locations")
@RequiredArgsConstructor
public class MobileLocationController {

    private final LocationAllocationService locationAllocationService;

    @Operation(summary = "推荐入库库位")
    @GetMapping("/recommend")
    public ApiResult<Map<String, Object>> recommend(
            @RequestParam String warehouseCode,
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String batchNo) {
        return ApiResult.ok(locationAllocationService.recommendInboundLocation(warehouseCode, materialCode, batchNo));
    }

    @Operation(summary = "仓库库位列表")
    @GetMapping
    public ApiResult<List<BaseLocation>> list(@RequestParam String warehouseCode) {
        return ApiResult.ok(locationAllocationService.listByWarehouse(warehouseCode));
    }

    @Operation(summary = "分配入库库位(自动/手动)")
    @PostMapping("/allocate")
    public ApiResult<Map<String, Object>> allocate(@RequestBody MobileLocationAllocateRequest request) {
        if (request.getWarehouseCode() == null || request.getWarehouseCode().isBlank()) {
            return ApiResult.fail(400, "仓库编码不能为空");
        }
        boolean auto = request.getAutoAllocate() == null || Boolean.TRUE.equals(request.getAutoAllocate());
        String locationCode;
        String strategy;
        String message;
        if (auto) {
            Map<String, Object> rec = locationAllocationService.recommendInboundLocation(
                    request.getWarehouseCode(), request.getMaterialCode(), request.getBatchNo());
            locationCode = (String) rec.get("locationCode");
            strategy = (String) rec.get("strategy");
            message = (String) rec.get("message");
        } else {
            locationCode = locationAllocationService.resolveInboundLocation(
                    request.getWarehouseCode(),
                    request.getMaterialCode(),
                    request.getBatchNo(),
                    request.getManualLocationCode());
            strategy = "MANUAL";
            message = "使用指定库位";
        }
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("locationCode", locationCode);
        result.put("strategy", strategy);
        result.put("message", message);
        result.put("autoAllocate", auto);
        return ApiResult.ok(result);
    }
}
