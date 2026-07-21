package com.wms.mobile.controller;

import com.wms.common.result.ApiResult;
import com.wms.common.result.PageResult;
import com.wms.quality.dto.QcCompleteRequest;
import com.wms.quality.entity.QcOrder;
import com.wms.quality.service.QualityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "PDA-质检")
@RestController
@RequestMapping("/mobile/quality")
@RequiredArgsConstructor
public class MobileQualityController {

    private final QualityService qualityService;

    @Operation(summary = "质检任务详情")
    @GetMapping("/{qcNo}")
    public ApiResult<QcOrder> detail(@PathVariable String qcNo) {
        return ApiResult.ok(qualityService.getOrder(qcNo));
    }

    @Operation(summary = "提交质检结果(POST)")
    @PostMapping("/{qcNo}/result")
    public ApiResult<Void> submitResultPost(@PathVariable String qcNo, @RequestBody QcCompleteRequest request) {
        qualityService.complete(qcNo, request);
        return ApiResult.ok("质检完成", null);
    }

    @Operation(summary = "提交质检结果(PUT)")
    @PutMapping("/{qcNo}/result")
    public ApiResult<Void> submitResultPut(@PathVariable String qcNo, @RequestBody QcCompleteRequest request) {
        qualityService.complete(qcNo, request);
        return ApiResult.ok("质检完成", null);
    }
}
