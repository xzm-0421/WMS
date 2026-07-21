package com.wms.inbound.controller;

import com.wms.common.result.ApiResult;
import com.wms.common.result.PageResult;
import com.wms.inbound.dto.InboundOrderCreateRequest;
import com.wms.inbound.dto.InboundOrderUpdateRequest;
import com.wms.inbound.dto.InboundOrderVo;
import com.wms.inbound.entity.InboundOrder;
import com.wms.inbound.entity.InboundOrderDetail;
import com.wms.inbound.service.InboundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "入库管理")
@RestController
@RequestMapping("/inbound/orders")
@RequiredArgsConstructor
public class InboundOrderController {

    private final InboundService inboundService;

    @Operation(summary = "入库单列表")
    @GetMapping
    public ApiResult<PageResult<InboundOrder>> list(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String orderType,
            @RequestParam(required = false) String warehouseCode,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(inboundService.page(orderNo, orderType, warehouseCode, status, current, size));
    }

    @Operation(summary = "入库单详情")
    @GetMapping("/{orderNo}")
    public ApiResult<InboundOrderVo> detail(@PathVariable String orderNo) {
        return ApiResult.ok(inboundService.getOrderVo(orderNo));
    }

    @Operation(summary = "创建入库单")
    @PostMapping
    public ApiResult<String> create(@RequestBody InboundOrderCreateRequest request) {
        return ApiResult.ok("创建成功", inboundService.createOrder(request));
    }

    @Operation(summary = "更新入库单")
    @PutMapping("/{orderNo}")
    public ApiResult<Void> update(@PathVariable String orderNo, @RequestBody InboundOrderUpdateRequest request) {
        inboundService.updateOrder(orderNo, request);
        return ApiResult.ok("更新成功", null);
    }

    @Operation(summary = "删除入库单")
    @DeleteMapping("/{orderNo}")
    public ApiResult<Void> delete(@PathVariable String orderNo) {
        inboundService.deleteOrder(orderNo);
        return ApiResult.ok("删除成功", null);
    }

    @Operation(summary = "提交入库单")
    @PutMapping("/{orderNo}/submit")
    public ApiResult<Void> submit(@PathVariable String orderNo) {
        inboundService.submit(orderNo);
        return ApiResult.ok("提交成功", null);
    }

    @Operation(summary = "审核入库单")
    @PutMapping("/{orderNo}/audit")
    public ApiResult<Void> audit(@PathVariable String orderNo) {
        inboundService.audit(orderNo);
        return ApiResult.ok("审核成功", null);
    }

    @Operation(summary = "反审核入库单")
    @PutMapping("/{orderNo}/unaudit")
    public ApiResult<Void> reverseAudit(@PathVariable String orderNo) {
        inboundService.reverseAudit(orderNo);
        return ApiResult.ok("反审核成功", null);
    }

    @Operation(summary = "取消入库单")
    @PutMapping("/{orderNo}/cancel")
    public ApiResult<Void> cancel(@PathVariable String orderNo) {
        inboundService.cancel(orderNo);
        return ApiResult.ok("取消成功", null);
    }

    @Operation(summary = "关闭入库单")
    @PutMapping("/{orderNo}/close")
    public ApiResult<Void> close(@PathVariable String orderNo) {
        inboundService.close(orderNo);
        return ApiResult.ok("关闭成功", null);
    }

    @Operation(summary = "新增明细")
    @PostMapping("/{orderNo}/details")
    public ApiResult<Void> addDetail(@PathVariable String orderNo, @RequestBody InboundOrderDetail detail) {
        inboundService.addDetail(orderNo, detail);
        return ApiResult.ok("添加成功", null);
    }

    @Operation(summary = "更新明细")
    @PutMapping("/{orderNo}/details/{lineNo}")
    public ApiResult<Void> updateDetail(@PathVariable String orderNo, @PathVariable Integer lineNo,
                                        @RequestBody InboundOrderDetail detail) {
        inboundService.updateDetail(orderNo, lineNo, detail);
        return ApiResult.ok("更新成功", null);
    }

    @Operation(summary = "删除明细")
    @DeleteMapping("/{orderNo}/details/{lineNo}")
    public ApiResult<Void> deleteDetail(@PathVariable String orderNo, @PathVariable Integer lineNo) {
        inboundService.deleteDetail(orderNo, lineNo);
        return ApiResult.ok("删除成功", null);
    }
}
