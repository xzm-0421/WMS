package com.wms.mobile.controller;

import com.wms.common.result.ApiResult;
import com.wms.mobile.dto.MobileBarcodeBillResolveRequest;
import com.wms.mobile.dto.MobileBarcodeVerifyRequest;
import com.wms.mobile.service.MobilePanelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "PDA-条码校验")
@RestController
@RequestMapping("/mobile/panel")
@RequiredArgsConstructor
public class MobilePanelController {

    private final MobilePanelService panelService;

    @Operation(summary = "解析单据条码")
    @PostMapping("/resolve-bill")
    public ApiResult<Map<String, String>> resolveBill(@Valid @RequestBody MobileBarcodeBillResolveRequest request) {
        String billNo = panelService.resolveBillNo(request.getBarcodeContent());
        return ApiResult.ok(Map.of("billNo", billNo != null ? billNo : ""));
    }

    @Operation(summary = "获取单据待校验明细")
    @GetMapping("/bills/{billNo}")
    public ApiResult<Map<String, Object>> billDetail(@PathVariable String billNo) {
        return ApiResult.ok(panelService.getBillDetail(billNo));
    }

    @Operation(summary = "扫描物料条码匹配单据明细")
    @PostMapping("/bills/{billNo}/verify")
    public ApiResult<Map<String, Object>> verifyMaterial(
            @PathVariable String billNo,
            @Valid @RequestBody MobileBarcodeVerifyRequest request) {
        Map<String, Object> data = panelService.verifyMaterial(billNo, request.getBarcodeContent());
        String message = String.valueOf(data.get("message"));
        return ApiResult.ok(message, data);
    }
}
