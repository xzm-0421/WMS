package com.wms.mobile.controller;

import com.wms.common.result.ApiResult;
import com.wms.inventory.dto.InventorySummaryDto;
import com.wms.inventory.service.InventoryService;
import com.wms.mobile.dto.MobileInventoryQueryRequest;
import com.wms.mobile.service.MobileInventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "PDA-库存")
@RestController
@RequestMapping("/mobile/inventory")
@RequiredArgsConstructor
public class MobileInventoryController {

    private final InventoryService inventoryService;
    private final MobileInventoryService mobileInventoryService;

    @Operation(summary = "库存查询(GET)")
    @GetMapping("/query")
    public ApiResult<List<InventorySummaryDto>> queryGet(
            @RequestParam(required = false) String warehouseCode,
            @RequestParam(required = false) String materialCode) {
        return ApiResult.ok(inventoryService.getSummary(warehouseCode, materialCode));
    }

    @Operation(summary = "扫码查询库存(POST)")
    @PostMapping("/query")
    public ApiResult<Map<String, Object>> queryPost(@RequestBody MobileInventoryQueryRequest request) {
        return ApiResult.ok(mobileInventoryService.query(request));
    }
}
