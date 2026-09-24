package com.wms.quality.controller;

import com.wms.common.result.ApiResult;
import com.wms.common.result.PageResult;
import com.wms.quality.dto.QcConcessionRequest;
import com.wms.quality.dto.QcJudgeRequest;
import com.wms.quality.dto.QcOrderCreateRequest;
import com.wms.quality.dto.QcStandardCreateRequest;
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
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String qcType,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(qualityService.pageStandardsByQc(materialCode, qcType, current, size));
    }

    @Operation(summary = "创建质检标准")
    @PostMapping("/standards")
    public ApiResult<Void> createStandard(@RequestBody QcStandardCreateRequest request) {
        qualityService.createStandard(request);
        return ApiResult.ok("创建成功", null);
    }

    @Operation(summary = "质检单列表")
    @GetMapping("/orders")
    public ApiResult<PageResult<QcOrder>> listOrders(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(qualityService.pageOrders(orderNo, materialCode, status, current, size));
    }

    @Operation(summary = "创建质检单")
    @PostMapping("/orders")
    public ApiResult<String> createOrder(@RequestBody QcOrderCreateRequest request) {
        return ApiResult.ok("创建成功", qualityService.createOrder(request));
    }

    @Operation(summary = "质检判定")
    @PutMapping("/orders/{orderNo}/judge")
    public ApiResult<Void> judgeOrder(@PathVariable String orderNo, @RequestBody QcJudgeRequest request) {
        qualityService.judgeOrder(orderNo, request);
        return ApiResult.ok("判定成功", null);
    }

    @Operation(summary = "让步接收")
    @PutMapping("/orders/{orderNo}/concession")
    public ApiResult<Void> concessionAccept(@PathVariable String orderNo, @RequestBody QcConcessionRequest request) {
        qualityService.concessionAccept(orderNo, request);
        return ApiResult.ok("让步接收成功", null);
    }
}
