package com.wms.mobile.controller;

import com.wms.common.result.ApiResult;
import com.wms.mobile.dto.MobileTraceRequest;
import com.wms.mobile.service.MobileInventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "PDA-追溯")
@RestController
@RequestMapping("/mobile/trace")
@RequiredArgsConstructor
public class MobileTraceController {

    private final MobileInventoryService mobileInventoryService;

    @Operation(summary = "批次追溯")
    @PostMapping
    public ApiResult<Map<String, Object>> trace(@RequestBody MobileTraceRequest request) {
        return ApiResult.ok(mobileInventoryService.trace(request));
    }
}
