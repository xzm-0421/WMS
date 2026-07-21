package com.wms.incoming.controller;

import com.wms.common.result.ApiResult;
import com.wms.common.result.PageResult;
import com.wms.incoming.dto.DeliveryNoteCreateRequest;
import com.wms.incoming.entity.DeliveryNote;
import com.wms.incoming.service.DeliveryNoteService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "来料管理-送货单")
@RestController
@RequestMapping("/incoming/delivery-notes")
@RequiredArgsConstructor
public class DeliveryNoteController {

    private final DeliveryNoteService deliveryNoteService;

    @GetMapping
    public ApiResult<PageResult<DeliveryNote>> list(
            @RequestParam(required = false) String deliveryNo,
            @RequestParam(required = false) String purchaseOrderNo,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(deliveryNoteService.page(deliveryNo, purchaseOrderNo, status, current, size));
    }

    @GetMapping("/{deliveryNo}")
    public ApiResult<Map<String, Object>> detail(@PathVariable String deliveryNo) {
        return ApiResult.ok(deliveryNoteService.detail(deliveryNo));
    }

    @PostMapping
    public ApiResult<String> create(@RequestBody DeliveryNoteCreateRequest request,
                                    @AuthenticationPrincipal UserDetails user) {
        String name = user != null ? user.getUsername() : "system";
        return ApiResult.ok("创建成功", deliveryNoteService.createFromPurchaseOrder(request, name));
    }

    @PostMapping("/{deliveryNo}/generate-inbound")
    public ApiResult<String> generateInbound(@PathVariable String deliveryNo,
                                             @AuthenticationPrincipal UserDetails user) {
        String name = user != null ? user.getUsername() : "system";
        return ApiResult.ok("入库单已生成", deliveryNoteService.generateInbound(deliveryNo, name));
    }
}
