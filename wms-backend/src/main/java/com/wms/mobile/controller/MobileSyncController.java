package com.wms.mobile.controller;

import com.wms.common.result.ApiResult;
import com.wms.mobile.dto.MobileSyncRequest;
import com.wms.mobile.service.MobileSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "PDA-离线同步")
@RestController
@RequestMapping("/mobile/sync")
@RequiredArgsConstructor
public class MobileSyncController {

    private final MobileSyncService mobileSyncService;

    @Operation(summary = "离线数据同步")
    @PostMapping
    public ApiResult<Map<String, Object>> sync(@RequestBody MobileSyncRequest request) {
        return ApiResult.ok("同步完成", mobileSyncService.sync(request));
    }
}
