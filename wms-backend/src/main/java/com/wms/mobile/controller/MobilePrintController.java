package com.wms.mobile.controller;

import com.wms.common.result.ApiResult;
import com.wms.mobile.dto.MobileLabelResolveRequest;
import com.wms.mobile.service.MobilePrintService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "PDA-标签打印")
@RestController
@RequestMapping("/mobile/print")
@RequiredArgsConstructor
public class MobilePrintController {

    private final MobilePrintService mobilePrintService;

    @Operation(summary = "扫码解析标签数据")
    @PostMapping("/label/resolve")
    public ApiResult<Map<String, Object>> resolveLabel(@RequestBody MobileLabelResolveRequest request) {
        return ApiResult.ok(mobilePrintService.resolveLabel(request));
    }
}
