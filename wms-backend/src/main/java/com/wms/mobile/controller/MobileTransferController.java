package com.wms.mobile.controller;

import com.wms.common.result.ApiResult;
import com.wms.mobile.dto.MobileTransferRequest;
import com.wms.mobile.service.MobileInventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "PDA-移库")
@RestController
@RequestMapping("/mobile/transfer")
@RequiredArgsConstructor
public class MobileTransferController {

    private final MobileInventoryService mobileInventoryService;

    @Operation(summary = "移库")
    @PostMapping
    public ApiResult<Map<String, Object>> transfer(@Valid @RequestBody MobileTransferRequest request) {
        return ApiResult.ok("移库成功", mobileInventoryService.transfer(request));
    }
}
