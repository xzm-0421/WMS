package com.wms.incoming.controller;

import com.wms.common.result.ApiResult;
import com.wms.common.result.PageResult;
import com.wms.incoming.dto.PurchaseReturnCreateRequest;
import com.wms.incoming.entity.PurchaseReturn;
import com.wms.incoming.service.PurchaseReturnService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "来料管理-采购退货")
@RestController
@RequestMapping("/incoming/purchase-returns")
@RequiredArgsConstructor
public class PurchaseReturnController {

    private final PurchaseReturnService purchaseReturnService;

    @GetMapping
    public ApiResult<PageResult<PurchaseReturn>> list(
            @RequestParam(required = false) String returnNo,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(purchaseReturnService.page(returnNo, status, current, size));
    }

    @GetMapping("/{returnNo}")
    public ApiResult<Map<String, Object>> detail(@PathVariable String returnNo) {
        return ApiResult.ok(purchaseReturnService.detail(returnNo));
    }

    @PostMapping
    public ApiResult<String> create(@RequestBody PurchaseReturnCreateRequest request,
                                    @AuthenticationPrincipal UserDetails user) {
        String name = user != null ? user.getUsername() : "system";
        return ApiResult.ok("创建成功", purchaseReturnService.create(request, name));
    }

    @PostMapping("/{returnNo}/confirm")
    public ApiResult<Void> confirm(@PathVariable String returnNo,
                                   @RequestParam String warehouseCode,
                                   @RequestParam String locationCode,
                                   @AuthenticationPrincipal UserDetails user) {
        String name = user != null ? user.getUsername() : "system";
        purchaseReturnService.confirm(returnNo, warehouseCode, locationCode, name);
        return ApiResult.ok("退货确认成功", null);
    }
}
