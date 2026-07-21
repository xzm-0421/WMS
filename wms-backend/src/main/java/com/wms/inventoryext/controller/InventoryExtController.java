package com.wms.inventoryext.controller;

import com.wms.common.result.ApiResult;
import com.wms.common.result.PageResult;
import com.wms.inventoryext.dto.OtherOutboundCreateRequest;
import com.wms.inventoryext.dto.SampleResultSubmitRequest;
import com.wms.inventoryext.dto.TransferOrderCreateRequest;
import com.wms.inventoryext.entity.OtherOutbound;
import com.wms.inventoryext.entity.SamplePlan;
import com.wms.inventoryext.entity.SampleResult;
import com.wms.inventoryext.entity.TransferOrder;
import com.wms.inventoryext.service.OtherOutboundService;
import com.wms.inventoryext.service.SamplePlanService;
import com.wms.inventoryext.service.TransferOrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Tag(name = "库存扩展")
@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryExtController {

    private final OtherOutboundService otherOutboundService;
    private final TransferOrderService transferOrderService;
    private final SamplePlanService samplePlanService;

    @GetMapping("/other-outbound")
    public ApiResult<PageResult<OtherOutbound>> listOtherOutbound(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String outboundType,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(otherOutboundService.page(orderNo, outboundType, current, size));
    }

    @GetMapping("/other-outbound/{orderNo}")
    public ApiResult<Map<String, Object>> otherOutboundDetail(@PathVariable String orderNo) {
        return ApiResult.ok(otherOutboundService.detail(orderNo));
    }

    @PostMapping("/other-outbound")
    public ApiResult<String> createOtherOutbound(@RequestBody OtherOutboundCreateRequest request,
                                                 @AuthenticationPrincipal UserDetails user) {
        String name = user != null ? user.getUsername() : "system";
        return ApiResult.ok("创建成功", otherOutboundService.create(request, name));
    }

    @GetMapping("/transfers")
    public ApiResult<PageResult<TransferOrder>> listTransfers(
            @RequestParam(required = false) String transferNo,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(transferOrderService.page(transferNo, status, current, size));
    }

    @PostMapping("/transfers")
    public ApiResult<String> createTransfer(@RequestBody TransferOrderCreateRequest request,
                                            @AuthenticationPrincipal UserDetails user) {
        String name = user != null ? user.getUsername() : "system";
        return ApiResult.ok("创建成功", transferOrderService.create(request, name));
    }

    @PostMapping("/transfers/{transferNo}/approve")
    public ApiResult<Void> approveTransfer(@PathVariable String transferNo,
                                         @AuthenticationPrincipal UserDetails user) {
        String name = user != null ? user.getUsername() : "system";
        transferOrderService.approve(transferNo, name);
        return ApiResult.ok("审批成功", null);
    }

    @PostMapping("/transfers/{transferNo}/execute")
    public ApiResult<Void> executeTransfer(@PathVariable String transferNo,
                                           @AuthenticationPrincipal UserDetails user) {
        String name = user != null ? user.getUsername() : "system";
        transferOrderService.execute(transferNo, name);
        return ApiResult.ok("调拨执行成功", null);
    }

    @PostMapping("/transfers/{transferNo}/cancel")
    public ApiResult<Void> cancelTransfer(@PathVariable String transferNo,
                                          @AuthenticationPrincipal UserDetails user) {
        String name = user != null ? user.getUsername() : "system";
        transferOrderService.cancel(transferNo, name);
        return ApiResult.ok("已取消", null);
    }

    @GetMapping("/sample-plans")
    public ApiResult<PageResult<SamplePlan>> listSamplePlans(
            @RequestParam(required = false) String planNo,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(samplePlanService.page(planNo, status, current, size));
    }

    @PostMapping("/sample-plans")
    public ApiResult<String> createSamplePlan(
            @RequestParam String warehouseCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate planDate,
            @AuthenticationPrincipal UserDetails user) {
        String name = user != null ? user.getUsername() : "system";
        return ApiResult.ok("创建成功", samplePlanService.createPlan(warehouseCode, planDate, name));
    }

    @GetMapping("/sample-plans/{planNo}/checklist")
    public ApiResult<List<Map<String, Object>>> sampleChecklist(@PathVariable String planNo) {
        return ApiResult.ok(samplePlanService.generateChecklist(planNo));
    }

    @PostMapping("/sample-plans/results")
    public ApiResult<Void> submitSampleResult(@RequestBody SampleResultSubmitRequest request,
                                              @AuthenticationPrincipal UserDetails user) {
        String name = user != null ? user.getUsername() : "system";
        samplePlanService.submitResult(request, name);
        return ApiResult.ok("抽检结果已记录", null);
    }

    @GetMapping("/sample-plans/{planNo}/results")
    public ApiResult<List<SampleResult>> listSampleResults(@PathVariable String planNo) {
        return ApiResult.ok(samplePlanService.listResults(planNo));
    }

    @GetMapping("/sample-plans/alerts")
    public ApiResult<List<SampleResult>> sampleAlerts() {
        return ApiResult.ok(samplePlanService.listAlerts());
    }
}
