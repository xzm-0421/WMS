package com.wms.outbound.controller;

import com.wms.common.result.ApiResult;
import com.wms.common.result.PageResult;
import com.wms.outbound.dto.*;
import com.wms.outbound.entity.OutboundOrder;
import com.wms.outbound.service.OutboundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "出库管理")
@RestController
@RequestMapping("/outbound/orders")
@RequiredArgsConstructor
public class OutboundController {

    private final OutboundService outboundService;

    @Operation(summary = "出库单列表")
    @GetMapping
    public ApiResult<PageResult<OutboundOrder>> list(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String orderType,
            @RequestParam(required = false) String warehouseCode,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(outboundService.page(orderNo, orderType, warehouseCode, status, current, size));
    }

    @Operation(summary = "出库单详情")
    @GetMapping("/{orderNo}")
    public ApiResult<OutboundOrderVo> detail(@PathVariable String orderNo) {
        return ApiResult.ok(outboundService.getOrderVo(orderNo));
    }

    @Operation(summary = "创建出库单")
    @PostMapping
    public ApiResult<String> create(@RequestBody OutboundOrderCreateRequest request) {
        return ApiResult.ok("创建成功", outboundService.createOrder(request));
    }

    @Operation(summary = "更新出库单")
    @PutMapping("/{orderNo}")
    public ApiResult<Void> update(@PathVariable String orderNo, @RequestBody OutboundOrderUpdateRequest request) {
        outboundService.updateOrder(orderNo, request);
        return ApiResult.ok("更新成功", null);
    }

    @Operation(summary = "删除出库单")
    @DeleteMapping("/{orderNo}")
    public ApiResult<Void> delete(@PathVariable String orderNo) {
        outboundService.deleteOrder(orderNo);
        return ApiResult.ok("删除成功", null);
    }

    @Operation(summary = "提交出库单")
    @PutMapping("/{orderNo}/submit")
    public ApiResult<Void> submit(@PathVariable String orderNo) {
        outboundService.submit(orderNo);
        return ApiResult.ok("提交成功", null);
    }

    @Operation(summary = "审核出库单")
    @PutMapping("/{orderNo}/audit")
    public ApiResult<Void> audit(@PathVariable String orderNo) {
        outboundService.audit(orderNo);
        return ApiResult.ok("审核成功", null);
    }

    @Operation(summary = "反审核出库单")
    @PutMapping("/{orderNo}/unaudit")
    public ApiResult<Void> reverseAudit(@PathVariable String orderNo) {
        outboundService.reverseAudit(orderNo);
        return ApiResult.ok("反审核成功", null);
    }

    @Operation(summary = "取消出库单")
    @PutMapping("/{orderNo}/cancel")
    public ApiResult<Void> cancel(@PathVariable String orderNo) {
        outboundService.cancel(orderNo);
        return ApiResult.ok("取消成功", null);
    }

    @Operation(summary = "关闭出库单")
    @PutMapping("/{orderNo}/close")
    public ApiResult<Void> close(@PathVariable String orderNo) {
        outboundService.close(orderNo);
        return ApiResult.ok("关闭成功", null);
    }

    @Operation(summary = "推荐出库库位(FIFO)")
    @GetMapping("/{orderNo}/recommend-locations")
    public ApiResult<List<RecommendLocationDto>> recommendLocations(
            @PathVariable String orderNo,
            @RequestParam(required = false) Integer lineNo) {
        return ApiResult.ok(outboundService.recommendLocations(orderNo, lineNo));
    }
}
