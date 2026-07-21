package com.wms.inventoryext.controller;

import com.wms.common.result.ApiResult;
import com.wms.common.result.PageResult;
import com.wms.inventoryext.dto.OtherInboundCreateRequest;
import com.wms.inventoryext.entity.OtherInbound;
import com.wms.inventoryext.service.OtherInboundService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "库存管理-其他入库")
@RestController
@RequestMapping("/inventory/other-inbound")
@RequiredArgsConstructor
public class OtherInboundController {

    private final OtherInboundService otherInboundService;

    @GetMapping
    public ApiResult<PageResult<OtherInbound>> list(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String inboundType,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(otherInboundService.page(orderNo, inboundType, current, size));
    }

    @GetMapping("/{orderNo}")
    public ApiResult<Map<String, Object>> detail(@PathVariable String orderNo) {
        return ApiResult.ok(otherInboundService.detail(orderNo));
    }

    @PostMapping
    public ApiResult<String> create(@RequestBody OtherInboundCreateRequest request,
                                    @AuthenticationPrincipal UserDetails user) {
        String name = user != null ? user.getUsername() : "system";
        return ApiResult.ok("创建成功", otherInboundService.create(request, name));
    }
}
