package com.wms.base.controller;

import com.wms.base.entity.BaseSupplier;
import com.wms.base.service.BaseSupplierService;
import com.wms.common.result.ApiResult;
import com.wms.common.result.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "基础数据-供应商")
@RestController
@RequestMapping("/base/suppliers")
@RequiredArgsConstructor
public class BaseSupplierController {

    private final BaseSupplierService supplierService;

    @Operation(summary = "供应商列表")
    @GetMapping
    public ApiResult<PageResult<BaseSupplier>> list(
            @RequestParam(required = false) String supplierCode,
            @RequestParam(required = false) String supplierName,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(supplierService.page(supplierCode, supplierName, status, current, size));
    }

    @Operation(summary = "供应商详情")
    @GetMapping("/{supplierCode}")
    public ApiResult<BaseSupplier> detail(@PathVariable String supplierCode) {
        return ApiResult.ok(supplierService.getByCode(supplierCode));
    }

    @Operation(summary = "新增供应商")
    @PostMapping
    public ApiResult<Void> create(@RequestBody BaseSupplier supplier) {
        supplierService.create(supplier);
        return ApiResult.ok("创建成功", null);
    }

    @Operation(summary = "更新供应商")
    @PutMapping("/{supplierCode}")
    public ApiResult<Void> update(@PathVariable String supplierCode, @RequestBody BaseSupplier supplier) {
        supplierService.update(supplierCode, supplier);
        return ApiResult.ok("更新成功", null);
    }

    @Operation(summary = "删除供应商")
    @DeleteMapping("/{supplierCode}")
    public ApiResult<Void> delete(@PathVariable String supplierCode) {
        supplierService.delete(supplierCode);
        return ApiResult.ok("删除成功", null);
    }
}
