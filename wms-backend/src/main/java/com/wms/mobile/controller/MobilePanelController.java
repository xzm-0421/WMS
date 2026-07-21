package com.wms.mobile.controller;

import com.wms.common.result.ApiResult;
import com.wms.mobile.dto.MobilePanelVerifyRequest;
import com.wms.mobile.service.MobilePanelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "PDA-板码校验")
@RestController
@RequestMapping("/mobile/panel")
@RequiredArgsConstructor
public class MobilePanelController {

    private final MobilePanelService panelService;

    @Operation(summary = "板码校验")
    @PostMapping("/verify")
    public ApiResult<Map<String, Object>> verify(@Valid @RequestBody MobilePanelVerifyRequest request) {
        Map<String, Object> data = panelService.verify(request);
        String message = String.valueOf(data.get("message"));
        if (Boolean.TRUE.equals(data.get("valid"))) {
            return ApiResult.ok(message, data);
        }
        return ApiResult.ok(message, data);
    }
}
