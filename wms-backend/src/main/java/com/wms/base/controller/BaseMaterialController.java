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

    @Operation(summary = "新增物料")
    @PostMapping
    public ApiResult<Void> create(@RequestBody BaseMaterial material) {
        materialService.create(material);
        return ApiResult.ok("创建成功", null);
    }

    @Operation(summary = "更新物料")
    @PutMapping("/{materialCode}")
    public ApiResult<Void> update(@PathVariable String materialCode, @RequestBody BaseMaterial material) {
        materialService.update(materialCode, material);
        return ApiResult.ok("更新成功", null);
    }

    @Operation(summary = "删除物料")
    @DeleteMapping("/{materialCode}")
    public ApiResult<Void> delete(@PathVariable String materialCode) {
        materialService.delete(materialCode);
        return ApiResult.ok("删除成功", null);
    }
}
