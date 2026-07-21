package com.wms.mobile.controller;

import com.wms.inbound.dto.InboundScanRequest;
import com.wms.inbound.service.InboundService;
import com.wms.common.result.ApiResult;
import com.wms.inbound.entity.InboundOrder;
import com.wms.inbound.entity.InboundOrderDetail;
import com.wms.mobile.dto.InboundBatchScanRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "PDA-入库")
@RestController
@RequestMapping("/mobile/inbound")
@RequiredArgsConstructor
public class MobileInboundController {

    private final InboundService inboundService;

    @Operation(summary = "获取入库任务详情")
    @GetMapping("/{orderNo}")
    public ApiResult<Map<String, Object>> detail(@PathVariable String orderNo) {
        InboundOrder order = inboundService.getOrder(orderNo);
        List<InboundOrderDetail> details = inboundService.getDetails(orderNo);
        Map<String, Object> data = new HashMap<>();
        data.put("orderNo", order.getOrderNo());
        data.put("orderType", order.getOrderType());
        data.put("warehouseCode", order.getWarehouseCode());
        data.put("supplierCode", order.getSupplierCode());
        data.put("status", order.getStatus());
        data.put("details", details);
        return ApiResult.ok(data);
    }

    @Operation(summary = "扫码入库")
    @PostMapping("/{orderNo}/scan")
    public ApiResult<Map<String, Object>> scan(@PathVariable String orderNo,
                                               @Valid @RequestBody InboundScanRequest request) {
        return ApiResult.ok("入库成功", inboundService.scanReceive(orderNo, request));
    }

    @Operation(summary = "批量扫码入库")
    @PostMapping("/{orderNo}/batch-scan")
    public ApiResult<Map<String, Object>> batchScan(@PathVariable String orderNo,
                                                    @Valid @RequestBody InboundBatchScanRequest request) {
        return ApiResult.ok("批量入库成功", inboundService.batchScan(orderNo, request));
    }

    @Operation(summary = "确认入库完成")
    @PostMapping("/{orderNo}/complete")
    public ApiResult<Void> complete(@PathVariable String orderNo) {
        inboundService.completeOrder(orderNo);
        return ApiResult.ok("入库完成", null);
    }
}
