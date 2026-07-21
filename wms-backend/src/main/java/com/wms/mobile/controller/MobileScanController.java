package com.wms.mobile.controller;

import com.wms.common.result.ApiResult;
import com.wms.mobile.dto.MobileScanBarcodeRequest;
import com.wms.mobile.dto.MobileScanRecognizeRequest;
import com.wms.mobile.service.MobileScanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "PDA-统一扫码")
@RestController
@RequestMapping("/mobile/scan")
@RequiredArgsConstructor
public class MobileScanController {

    private final MobileScanService scanService;

    @Operation(summary = "条码识别(物料+批次)")
    @PostMapping("/recognize")
    public ApiResult<Map<String, Object>> recognize(@RequestBody MobileScanRecognizeRequest request) {
        return ApiResult.ok(scanService.recognize(request));
    }

    @Operation(summary = "库存匹配(FIFO)")
    @PostMapping("/match-inventory")
    public ApiResult<Map<String, Object>> matchInventory(@RequestBody MobileScanRecognizeRequest request) {
        return ApiResult.ok(scanService.matchInventory(request));
    }

    @Operation(summary = "入库扫码登记")
    @PostMapping("/inbound/{orderNo}")
    public ApiResult<Map<String, Object>> inboundScan(
            @PathVariable String orderNo,
            @Valid @RequestBody MobileScanBarcodeRequest request) {
        return ApiResult.ok("入库成功", scanService.inboundScanByBarcode(orderNo, request));
    }

    @Operation(summary = "出库扫码(预览/确认)")
    @PostMapping("/outbound/{orderNo}")
    public ApiResult<Map<String, Object>> outboundScan(
            @PathVariable String orderNo,
            @Valid @RequestBody MobileScanBarcodeRequest request) {
        Map<String, Object> data = scanService.outboundScanByBarcode(orderNo, request);
        String msg = Boolean.TRUE.equals(request.getConfirm()) ? "出库成功" : "匹配成功，请确认";
        return ApiResult.ok(msg, data);
    }

    @Operation(summary = "无单扫码出库(预览/确认)")
    @PostMapping("/outbound-direct")
    public ApiResult<Map<String, Object>> outboundDirect(@Valid @RequestBody MobileScanBarcodeRequest request) {
        Map<String, Object> data = scanService.directOutbound(request);
        String msg = Boolean.TRUE.equals(request.getConfirm()) ? "出库成功" : "匹配成功，请确认";
        return ApiResult.ok(msg, data);
    }
}
