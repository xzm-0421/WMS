package com.wms.mobile.controller;

import com.wms.common.result.ApiResult;
import com.wms.common.result.PageResult;
import com.wms.pdareceive.dto.*;
import com.wms.pdareceive.service.PdaReceiveScanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "PDA-收料通知单")
@RestController
@RequestMapping("/mobile/receive-notice")
@RequiredArgsConstructor
public class MobileReceiveNoticeController {

    private final PdaReceiveScanService receiveScanService;

    @Operation(summary = "收料通知单列表（已审核+来料检验过滤）")
    @GetMapping
    public ApiResult<PageResult<ReceiveNoticeListItemVo>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "50") long size) {
        return ApiResult.ok(receiveScanService.list(keyword, current, size));
    }

    @Operation(summary = "解析条码获取收料通知单号")
    @PostMapping("/resolve-barcode")
    public ApiResult<Map<String, String>> resolveBarcode(@RequestBody Map<String, String> body) {
        String billNo = receiveScanService.resolveBillNoFromBarcode(body.get("barcodeContent"));
        return ApiResult.ok(Map.of("billNo", billNo != null ? billNo : ""));
    }

    @Operation(summary = "收料通知单详情（含扫码进度）")
    @GetMapping("/{billNo}")
    public ApiResult<ReceiveNoticeDetailVo> detail(@PathVariable String billNo) {
        return ApiResult.ok(receiveScanService.getDetail(billNo));
    }

    @Operation(summary = "扫描物料条码勾选")
    @PostMapping("/{billNo}/scan")
    public ApiResult<ReceiveNoticeLineVo> scan(
            @PathVariable String billNo,
            @Valid @RequestBody ReceiveNoticeScanRequest request) {
        return ApiResult.ok("扫描成功", receiveScanService.scanLine(billNo, request));
    }

    @Operation(summary = "手动勾选/取消明细行")
    @PutMapping("/{billNo}/lines/{lineNo}/check")
    public ApiResult<ReceiveNoticeLineVo> toggleCheck(
            @PathVariable String billNo,
            @PathVariable Integer lineNo,
            @RequestParam boolean checked) {
        return ApiResult.ok(receiveScanService.toggleLine(billNo, lineNo, checked));
    }

    @Operation(summary = "修改本次领取数量")
    @PutMapping("/{billNo}/lines/{lineNo}/qty")
    public ApiResult<ReceiveNoticeLineVo> updateQty(
            @PathVariable String billNo,
            @PathVariable Integer lineNo,
            @Valid @RequestBody ReceiveNoticeQtyRequest request) {
        return ApiResult.ok("数量已更新", receiveScanService.updateLineQty(billNo, lineNo, request));
    }

    @Operation(summary = "部分提交入库（同步WMS，待Web传金蝶）")
    @PostMapping("/{billNo}/submit")
    public ApiResult<Map<String, Object>> submit(
            @PathVariable String billNo,
            @RequestBody(required = false) ReceiveNoticeSubmitRequest request) {
        if (request == null) {
            request = new ReceiveNoticeSubmitRequest();
        }
        return ApiResult.ok("提交成功", receiveScanService.submit(billNo, request));
    }
}
