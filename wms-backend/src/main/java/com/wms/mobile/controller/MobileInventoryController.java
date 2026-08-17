package com.wms.mobile.controller;

import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.common.result.ApiResult;
import com.wms.inventory.dto.InventorySummaryDto;
import com.wms.inventory.service.InventoryService;
import com.wms.mobile.dto.MobileInventoryQueryRequest;
import com.wms.mobile.service.MobileInventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
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

    @Operation(summary = "库存查询(GET)，须带物料编码")
    @GetMapping("/query")
    public ApiResult<List<InventorySummaryDto>> queryGet(
            @RequestParam(required = false) String warehouseCode,
            @RequestParam(required = false) String materialCode) {
        if (!StringUtils.hasText(materialCode)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请输入物料编码后再查询");
        }
        return ApiResult.ok(inventoryService.getSummary(warehouseCode, materialCode.trim()));
    }

    @Operation(summary = "扫码查询库存(POST)，须带条码或物料/库位/批次条件")
    @PostMapping("/query")
    public ApiResult<Map<String, Object>> queryPost(@RequestBody MobileInventoryQueryRequest request) {
        return ApiResult.ok(mobileInventoryService.query(request));
    }
}
