package com.wms.mobile.controller;

import com.wms.common.result.ApiResult;
import com.wms.outbound.dto.OutboundOrderVo;
import com.wms.outbound.dto.OutboundScanRequest;
import com.wms.outbound.dto.RecommendLocationDto;
import com.wms.outbound.service.OutboundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "PDA-出库")
@RestController
@RequestMapping("/mobile/outbound")
@RequiredArgsConstructor
public class MobileOutboundController {

    private final OutboundService outboundService;

    @Operation(summary = "获取出库任务详情")
    @GetMapping("/{orderNo}")
    public ApiResult<OutboundOrderVo> detail(@PathVariable String orderNo) {
        return ApiResult.ok(outboundService.getOrderVo(orderNo));
    }

    @Operation(summary = "推荐出库库位")
    @GetMapping("/{orderNo}/recommend")
    public ApiResult<Map<String, Object>> recommend(
            @PathVariable String orderNo,
            @RequestParam Integer lineNo) {
        List<RecommendLocationDto> recommendations = outboundService.recommendLocations(orderNo, lineNo);
        Map<String, Object> data = new HashMap<>();
        data.put("lineNo", lineNo);
        data.put("recommendations", recommendations);
        return ApiResult.ok(data);
    }

    @Operation(summary = "扫码出库")
    @PostMapping("/{orderNo}/scan")
    public ApiResult<Map<String, Object>> scan(@PathVariable String orderNo,
                                               @Valid @RequestBody OutboundScanRequest request) {
        return ApiResult.ok("出库成功", outboundService.scanIssue(orderNo, request));
    }

    @Operation(summary = "确认出库完成")
    @PostMapping("/{orderNo}/complete")
    public ApiResult<Void> complete(@PathVariable String orderNo) {
        outboundService.completeOrder(orderNo);
        return ApiResult.ok("出库完成", null);
    }
}
