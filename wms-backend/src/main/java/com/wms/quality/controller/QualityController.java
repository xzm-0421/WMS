package com.wms.quality.controller;

import com.wms.common.result.ApiResult;
import com.wms.common.result.PageResult;
import com.wms.quality.dto.QcCompleteRequest;
import com.wms.quality.entity.QcOrder;
import com.wms.quality.entity.QcStandard;
import com.wms.quality.service.QualityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "质检管理")
@RestController
@RequestMapping("/quality")
@RequiredArgsConstructor
public class QualityController {

    private final QualityService qualityService;

    @Operation(summary = "质检标准列表")
    @GetMapping("/standards")
    public ApiResult<PageResult<QcStandard>> listStandards(
            @RequestParam(required = false) String standardCode,
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(qualityService.pageStandards(standardCode, materialCode, status, current, size));
    }

    @Operation(summary = "质检标准详情")
    @GetMapping("/standards/{standardCode}")
    public ApiResult<QcStandard> standardDetail(@PathVariable String standardCode) {
        return ApiResult.ok(qualityService.getStandard(standardCode));
    }

    @Operation(summary = "创建质检标准")
    @PostMapping("/standards")
    public ApiResult<Void> createStandard(@RequestBody QcStandard standard) {
        qualityService.createStandard(standard);
        return ApiResult.ok("创建成功", null);
    }

    @Operation(summary = "更新质检标准")
    @PutMapping("/standards/{standardCode}")
    public ApiResult<Void> updateStandard(@PathVariable String standardCode, @RequestBody QcStandard standard) {
        qualityService.updateStandard(standardCode, standard);
        return ApiResult.ok("更新成功", null);
    }

    @Operation(summary = "删除质检标准")
    @DeleteMapping("/standards/{standardCode}")
    public ApiResult<Void> deleteStandard(@PathVariable String standardCode) {
        qualityService.deleteStandard(standardCode);
        return ApiResult.ok("删除成功", null);
    }

    @Operation(summary = "质检单列表")
    @GetMapping("/orders")
    public ApiResult<PageResult<QcOrder>> listOrders(
            @RequestParam(required = false) String qcNo,
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(qualityService.pageOrders(qcNo, materialCode, status, current, size));
    }

    @Operation(summary = "质检单详情")
    @GetMapping("/orders/{qcNo}")
    public ApiResult<QcOrder> orderDetail(@PathVariable String qcNo) {
        return ApiResult.ok(qualityService.getOrder(qcNo));
    }

    @Operation(summary = "创建质检单")
    @PostMapping("/orders")
    public ApiResult<Void> createOrder(@RequestBody QcOrder order) {
        qualityService.createOrder(order);
        return ApiResult.ok("创建成功", null);
    }

    @Operation(summary = "更新质检单")
    @PutMapping("/orders/{qcNo}")
    public ApiResult<Void> updateOrder(@PathVariable String qcNo, @RequestBody QcOrder order) {
        qualityService.updateOrder(qcNo, order);
        return ApiResult.ok("更新成功", null);
    }

    @Operation(summary = "删除质检单")
    @DeleteMapping("/orders/{qcNo}")
    public ApiResult<Void> deleteOrder(@PathVariable String qcNo) {
        qualityService.deleteOrder(qcNo);
        return ApiResult.ok("删除成功", null);
    }

    @Operation(summary = "完成质检")
    @PutMapping("/orders/{qcNo}/complete")
    public ApiResult<Void> complete(@PathVariable String qcNo, @RequestBody QcCompleteRequest request) {
        qualityService.complete(qcNo, request);
        return ApiResult.ok("质检完成", null);
    }
}
