package com.wms.incoming.controller;

import com.wms.common.result.ApiResult;
import com.wms.common.result.PageResult;
import com.wms.common.excel.ExcelImportHelper;
import com.wms.common.excel.PurchaseOrderImportRow;
import com.wms.incoming.dto.DeliveryNoteCreateRequest;
import com.wms.incoming.dto.PurchaseOrderCreateRequest;
import com.wms.incoming.dto.PurchaseOrderVo;
import com.wms.incoming.entity.PurchaseOrder;
import com.wms.incoming.service.DeliveryNoteService;
import com.wms.incoming.service.PurchaseOrderImportService;
import com.wms.incoming.service.PurchaseOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Tag(name = "来料管理-采购订单")
@RestController
@RequestMapping("/incoming/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;
    private final PurchaseOrderImportService importService;

    @GetMapping
    public ApiResult<PageResult<PurchaseOrder>> list(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String supplierCode,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(purchaseOrderService.page(orderNo, supplierCode, status, current, size));
    }

    @GetMapping("/{orderNo}")
    public ApiResult<PurchaseOrderVo> detail(@PathVariable String orderNo) {
        return ApiResult.ok(purchaseOrderService.getVo(orderNo));
    }

    @PostMapping
    public ApiResult<String> create(@RequestBody PurchaseOrderCreateRequest request,
                                    @AuthenticationPrincipal UserDetails user) {
        String name = user != null ? user.getUsername() : "system";
        return ApiResult.ok("创建成功", purchaseOrderService.create(request, name));
    }

    @DeleteMapping("/{orderNo}")
    public ApiResult<Void> delete(@PathVariable String orderNo) {
        purchaseOrderService.delete(orderNo);
        return ApiResult.ok("删除成功", null);
    }

    @GetMapping("/import/template")
    @Operation(summary = "下载采购订单导入模板")
    public void importTemplate(HttpServletResponse response) throws IOException {
        String fileName = URLEncoder.encode("采购订单导入模板.xlsx", StandardCharsets.UTF_8);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment;filename=" + fileName);
        ExcelImportHelper.writeTemplate(response.getOutputStream(), PurchaseOrderImportRow.class, "采购订单",
                List.of(PurchaseOrderImportRow.sample()));
    }

    @PostMapping("/import")
    public ApiResult<List<String>> importExcel(@RequestParam("file") MultipartFile file,
                                               @AuthenticationPrincipal UserDetails user) throws Exception {
        String name = user != null ? user.getUsername() : "system";
        return ApiResult.ok("导入成功", importService.importExcel(file, name));
    }
}
