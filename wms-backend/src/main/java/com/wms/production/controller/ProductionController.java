package com.wms.production.controller;

import com.wms.common.result.ApiResult;
import com.wms.common.result.PageResult;
import com.wms.common.security.SecurityUtils;
import com.wms.production.dto.BomVo;
import com.wms.production.entity.BomHeader;
import com.wms.production.entity.ProductionOrder;
import com.wms.production.service.ProductionPickingPlanService;
import com.wms.production.service.ProductionService;
import com.wms.integration.kingdee.KingdeeBomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "生产管理")
@RestController
@RequestMapping("/production")
@RequiredArgsConstructor
public class ProductionController {

    private final ProductionService productionService;
    private final ProductionPickingPlanService pickingPlanService;
    private final KingdeeBomService kingdeeBomService;

    @Operation(summary = "生产订单列表")
    @GetMapping("/orders")
    public ApiResult<PageResult<ProductionOrder>> listOrders(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String productCode,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(productionService.pageOrders(orderNo, productCode, status, current, size));
    }

    @Operation(summary = "生产订单详情")
    @GetMapping("/orders/{orderNo}")
    public ApiResult<ProductionOrder> orderDetail(@PathVariable String orderNo) {
        return ApiResult.ok(productionService.getOrder(orderNo));
    }

    @Operation(summary = "创建生产订单")
    @PostMapping("/orders")
    public ApiResult<Void> createOrder(@RequestBody ProductionOrder order) {
        productionService.createOrder(order);
        return ApiResult.ok("创建成功", null);
    }

    @Operation(summary = "更新生产订单")
    @PutMapping("/orders/{orderNo}")
    public ApiResult<Void> updateOrder(@PathVariable String orderNo, @RequestBody ProductionOrder order) {
        productionService.updateOrder(orderNo, order);
        return ApiResult.ok("更新成功", null);
    }

    @Operation(summary = "删除生产订单")
    @DeleteMapping("/orders/{orderNo}")
    public ApiResult<Void> deleteOrder(@PathVariable String orderNo) {
        productionService.deleteOrder(orderNo);
        return ApiResult.ok("删除成功", null);
    }

    @Operation(summary = "完工生产订单")
    @PostMapping("/orders/{orderNo}/complete")
    public ApiResult<Void> completeOrder(@PathVariable String orderNo) {
        productionService.completeOrder(orderNo);
        return ApiResult.ok("完工成功", null);
    }

    @Operation(summary = "BOM列表（金蝶云星空）")
    @GetMapping("/bom")
    public ApiResult<PageResult<BomHeader>> listBom(
            @RequestParam(required = false) String bomCode,
            @RequestParam(required = false) String productCode,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(kingdeeBomService.pageBom(bomCode, productCode, current, size));
    }

    @Operation(summary = "BOM详情（金蝶云星空）")
    @GetMapping("/bom/{bomCode}")
    public ApiResult<BomVo> bomDetail(@PathVariable String bomCode) {
        return ApiResult.ok(kingdeeBomService.getBom(bomCode));
    }

    @Operation(summary = "按产品查询有效BOM（金蝶云星空）")
    @GetMapping("/bom/by-product/{productCode}")
    public ApiResult<BomVo> bomByProduct(@PathVariable String productCode) {
        return ApiResult.ok(kingdeeBomService.getEffectiveBomByProductCode(productCode));
    }

    @Operation(summary = "根据生产订单生成备料通知（生产计划领料）")
    @PostMapping("/picking-plans/generate")
    public ApiResult<java.util.Map<String, String>> generatePickingPlan(@RequestBody java.util.Map<String, String> body) {
        String orderNo = body.get("orderNo");
        String operator = SecurityUtils.currentUser().getRealName();
        if (!org.springframework.util.StringUtils.hasText(operator)) {
            operator = SecurityUtils.currentUser().getUsername();
        }
        String noticeNo = pickingPlanService.generateFromProductionOrder(orderNo, operator);
        return ApiResult.ok(java.util.Map.of("noticeNo", noticeNo, "orderNo", orderNo));
    }
}
