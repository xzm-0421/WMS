package com.wms.base.controller;

import com.wms.base.entity.BaseMaterial;
import com.wms.base.service.BaseMaterialService;
import com.wms.common.result.ApiResult;
import com.wms.common.result.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "基础数据-物料")
@RestController
@RequestMapping("/base/materials")
@RequiredArgsConstructor
public class BaseMaterialController {

    private final BaseMaterialService materialService;

    @Operation(summary = "物料列表")
    @GetMapping
    public ApiResult<PageResult<BaseMaterial>> list(
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String materialName,
            @RequestParam(required = false) String materialType,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(materialService.page(materialCode, materialName, materialType, status, current, size));
    }

    @Operation(summary = "物料详情")
    @GetMapping("/{materialCode}")
    public ApiResult<BaseMaterial> detail(@PathVariable String materialCode) {
        return ApiResult.ok(materialService.getByCode(materialCode));
    }

    @Operation(summary = "删除物料（逻辑删除）")
    @DeleteMapping("/{materialCode}")
    public ApiResult<Void> delete(@PathVariable String materialCode) {
        materialService.delete(materialCode);
        return ApiResult.ok("删除成功", null);
    }
}
