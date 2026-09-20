package com.wms.mobile.controller;

import com.wms.common.result.ApiResult;
import com.wms.mobile.dto.AppUpdateCheckVo;
import com.wms.mobile.service.MobileAppUpdateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "PDA-应用更新")
@RestController
@RequestMapping("/mobile/app")
@RequiredArgsConstructor
public class MobileAppUpdateController {

    private final MobileAppUpdateService appUpdateService;

    @Operation(summary = "检测 PDA 应用更新")
    @GetMapping("/update-check")
    public ApiResult<AppUpdateCheckVo> checkUpdate(
            @RequestParam(required = false) Integer versionCode) {
        return ApiResult.ok(appUpdateService.checkUpdate(versionCode));
    }
}
